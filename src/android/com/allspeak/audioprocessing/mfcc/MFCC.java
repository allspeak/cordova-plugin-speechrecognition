/**
 */
package com.allspeak.audioprocessing.mfcc;

import android.os.Handler;

import org.apache.cordova.CallbackContext;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;

import android.os.Environment;
import android.util.Log;
import java.io.FilenameFilter;

import android.os.ResultReceiver;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import com.allspeak.audioprocessing.mfcc.Framing;
import com.allspeak.audioprocessing.WavFile;
import com.allspeak.utility.StringUtilities;
import com.allspeak.utility.Messaging;
import com.allspeak.utility.FileUtilities;

import com.allspeak.utility.TrackPerformance;

/*
it sends the following messages to Plugin Activity:
- data
- progress_file
- progress_folder
- error
*/

public class MFCC  
{
    private static final String TAG             = "MFCC";
    
  
    private MFCCParams mfccParams               = null;                                   // MFCC parameters
    private MFCCCalcJAudio mfccCalc             = null; 
    
    // properties to send results back to Plugin Main
    private ResultReceiver mReceiver            = null;     // IntentService
    private Handler mStatusCallback             = null;     // Thread
    private Handler mCommandCallback            = null;     // Thread
    private Handler mResultCallback             = null;     // Thread
    private CallbackContext callbackContext     = null;

//    private TrackPerformance tp;//    private int[][] aiElapsedTimes;

    private String sSource; // used as runnable param

    private int nFrames                         = 0;        // number of frames used to segment the input audio
    private int nScores                         = 0;        // either nNumberofFilters or nNumberOfMFCCParameters according to requeste measure
    
    private String sOutputPrecision             = "%.4f";       // label of the element the audio belongs to
    private final String sOutputMFCCPrecision   = "%.4f";       // label of the element the audio belongs to
    private final String sOutputFiltersPrecision= "%.4f";       // label of the element the audio belongs to
    
    private float[][][] faDerivatives;  // contains (2, nframes, numparams)  1st & 2nd derivatives of the calculated params
    private int nindices1;
    private int[] indices1 ;
    private int nindices2;
    private int[] indices2 ;
    private int nindicesout;
    private int[] indicesout;
    private int nDerivDenom;
    
    public float[] hammingWnd = null;
//    private float[][] mDerivativesQueue = null;  // store the last nDeltaWindow-samples to calculate derivatives 
    
    //================================================================================================================
    // CONSTRUCTORS
    //================================================================================================================
    // to be used with Thread implementation
    public MFCC(MFCCParams params, Handler cb)
    {
        mStatusCallback     = cb;
        mCommandCallback    = cb;
        mResultCallback     = cb;
        setParams(params);       
    }
    
    public MFCC(MFCCParams params, Handler scb, Handler ccb, Handler rcb)
    {
        mStatusCallback     = scb;
        mCommandCallback    = ccb;
        mResultCallback     = rcb;
        setParams(params);       
    }
    
    // may also send results directly to Web Layer
    public MFCC(MFCCParams params, Handler handl, CallbackContext wlcallback)
    {
        this(params, handl);
        callbackContext     = wlcallback;
    }   
    // may also send results directly to Web Layer
    public MFCC(MFCCParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcallback)
    {
        this(params, scb, ccb, rcb);
        callbackContext     = wlcallback;
    }   
    //================================================================================================================
    public void setParams(MFCCParams params)
    {
        // JS interface call params:     mfcc_json_params, source, datatype, origintype, write, [outputpath_noext];  params have been validated in the js interface
        mfccParams          = params; 
        nScores             = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccParams.nNumberOfMFCCParameters : mfccParams.nNumberofFilters);
        sOutputPrecision    = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? sOutputMFCCPrecision : sOutputFiltersPrecision);
        
        if ((boolean)mfccParams.bCalculate0ThCoeff) mfccParams.nNumberOfMFCCParameters++;//take in account the zero-th MFCC        

        mfccCalc = new MFCCCalcJAudio(  mfccParams.nNumberOfMFCCParameters,
                                        mfccParams.dSamplingFrequency,
                                        mfccParams.nNumberofFilters,
                                        mfccParams.nFftLength,
                                        mfccParams.bIsLifteringEnabled ,
                                        mfccParams.nLifteringCoefficient,
                                        mfccParams.bCalculate0ThCoeff,
                                        mfccParams.nWindowDistance,
                                        mfccParams.nWindowLength,
                                        mfccParams.nDataType,
                                        nScores,
                                        mfccParams.nDeltaWindow); 
        
        hammingWnd = Framing.initHamming(mfccParams.nWindowLength);
    }   
    
    public void setWlCb(CallbackContext wlcb)
    {
        callbackContext     = wlcb;
    }    
    
    public void setCallbacks(Handler cb)
    {
        setCallbacks(cb, cb, cb);
    }    
    
    public void setCallbacks(Handler scb, Handler ccb, Handler rcb)
    {
        mStatusCallback     = scb;
        mCommandCallback    = ccb;
        mResultCallback     = rcb;
    }    
    //================================================================================================================
    //================================================================================================================
    //=================================================================================================================
    // COMMANDS
    //=================================================================================================================
    //=================================================================================================================
    // FILE processing :  MFCC -> derivatives -> normalize -> contexting
    // LIVE processing :  MFCC -> derivatives 
    //=================================================================================================================
    // ONE SHOT PROCESSING, e.g. file/folder,
    //=================================================================================================================
   // read wav(String) => framing => processFrames(float[][])
    //if !overwrite : check if file exist, if YES => if it has the correct number of frames => skip it.
    //                                            => if not                                 => overwrite it
    public void processFile(String input_file_noext, boolean overwrite) 
    {
        try
        {
//            tp                  = new TrackPerformance(5); // I want to monitor : wav read (if applicable), params calculation, data export(if applicable), data write(if applicable), data json packaging(if applicable)
            mfccParams.sOutputPath  = StringUtilities.removeExtension(input_file_noext);
            String audio_relfile    = mfccParams.sOutputPath + ".wav";
            String mfcc_relfile     = mfccParams.sOutputPath + "_scores.dat";
            
            float[] data            = WavFile.getWavData(Environment.getExternalStorageDirectory() + "/" + audio_relfile);  
            int nframes             = Framing.getFrames(data.length, mfccParams.nWindowLength, mfccParams.nWindowDistance);
            
            // Since I write appending, I have to decide what to do with the existing files.
            // 1) go on appending to the existing file and skip 
            // 2) delete it 
            if(FileUtilities.existRelFile(mfcc_relfile))
            {
                if(overwrite) FileUtilities.deleteExternalStorageFile(mfcc_relfile); 
                else
                {
                    File f = new File(Environment.getExternalStorageDirectory(), mfcc_relfile);
                    int nlines = FileUtilities.countLines(f);
                    if(nlines == nframes)   // is a valid file ?
                    {
                        // send message & skip
                        Messaging.sendMessageToHandler(mStatusCallback, ENUMS.MFCC_STATUS_PROGRESS_FILE, "progress_file", mfccParams.sOutputPath);                    
                        return;
                    }                        
                    else    // the file exist but is corrupted...presumably a crash during processing..I delete it
                        FileUtilities.deleteExternalStorageFile(mfcc_relfile); 
                }
            }
            
//            tp.addTimepoint(1);
            float[][] frames        = Framing.preProcessing(data, mfccParams.nWindowLength, mfccParams.nWindowDistance, 0.95f, hammingWnd); // pre-emphasis/framing/hamming-windowing
            float[][] cepstra       = processFramesSpectral(frames);
            exportData(cepstra);
//            processFramesTemporal(Framing.frameVector(data, mfccParams.nWindowLength, mfccParams.nWindowDistance));
//            tp.addTimepoint(2);   
            Messaging.sendMessageToHandler(mStatusCallback, ENUMS.MFCC_STATUS_PROGRESS_FILE, "progress_file", mfccParams.sOutputPath);
         }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "processFile" + ": Error: " + e.toString());
            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.MFCC_ERROR, "error", e.getMessage());
        }        
    }
    //-----------------------------------------------------------------------------------------
    // processFolder(String) => for files in... processFile(String) => processRawData(float[])
    public void processFolder(String input_folderpath, boolean overwrite) 
    {
//        TrackPerformance tp_folder      = new TrackPerformance(1); // I want to monitor just to total time
        File directory                  = new File(Environment.getExternalStorageDirectory().toString() + "/" + input_folderpath);

        try
        {
            String tempfile             = "";
            File[] files                = directory.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".wav");
                }
            });
//            aiElapsedTimes               = new int[files.length][5];
            for (int i = 0; i < files.length; i++)
            {
                tempfile            = input_folderpath + File.separatorChar + files[i].getName();
                processFile(StringUtilities.removeExtension(tempfile), overwrite);
            } 
            // BUG....it doesn't work...since the last-1 file, in the target I get a Bundle with either process_file and process_folder messages
            // folder processing completion is presently resolved in the web layer.
            // Messaging.sendMessageToHandler(mStatusCallback, ENUMS.MFCC_STATUS_PROGRESS_FOLDER, "progress_folder", mfccParams.sOutputPath);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "processFolder" + ": Error: " + e.toString());
            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.MFCC_ERROR, "error", e.getMessage());
        }    
    }

    //-----------------------------------------------------------------------------------------
    // spectral derivatives
    // returns => [nframes][3*nscores]    
    public synchronized float[][] processFramesSpectral(float[][] frames)
    {
        float[][] scores    = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccCalc.getMFCC(frames) : mfccCalc.getMFFilters(frames)); 
        nFrames             = scores.length;

        mfccCalc.addSpectralDerivatives(scores);      // writes [:][2*nscore:3*nscore]          
        Framing.normalizeFrames(scores);
//            float[][] ctx_scores = getContextedFrames(scores, 11, 792);return exportData(ctx_scores);  
        return scores;
    }
    // temporal derivatives
    // returns => [nframes][3*nscores]    
    public synchronized float[][] processFramesTemporal(float[][] frames)
    {
        float[][] scores    = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccCalc.getMFCC(frames) : mfccCalc.getMFFilters(frames)); 
        nFrames             = scores.length;

        mfccCalc.addTemporalDerivatives(scores);                
        Framing.normalizeFrames(scores);
        return scores;
    }
    //=================================================================================================================
    // LIVE PROCESSING (normalization and contexting will be done when the sentences is complete)
    //=================================================================================================================
    // temporal derivatives
    // input:     frames = [nframes][nWindowLength], queue = [nframes][nscores]
    // returns => [nframes-nDeltaWindow][3*nscores]
    public synchronized float[][] processQueuedTemporal(float[][] frames, float[][] queue)
    {
        try
        {
            float[][] scores    = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccCalc.getMFCC(frames) : mfccCalc.getMFFilters(frames)); 
            nFrames             = scores.length;
            int ncols           = scores[0].length;
            mfccCalc.addTemporalDerivatives(scores, queue);  
            
            // prune invalid frames
            //  change [nframes][:] ==> [nframes-nDeltaWindow][:]             
            float[][] validscores = new float[nFrames-mfccParams.nDeltaWindow][ncols];
            for(int f=0; f<nFrames-mfccParams.nDeltaWindow; f++)
                validscores[f] = scores[f];
            return validscores;
//            return exportData(validscores);   
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "processFramesTemporal" + ": Error: " + e.toString());
            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.MFCC_ERROR, "error", e.getMessage());
            return null;
        }        
    }            
    
    public synchronized float[][] processQueuedSpectral(float[][] frames, float[][] queue)
    {
        try
        {
            float[][] scores    = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccCalc.getMFCC(frames) : mfccCalc.getMFFilters(frames)); 
            nFrames             = scores.length;
            int ncols           = scores[0].length;
            mfccCalc.addSpectralDerivatives(scores);  
            
            // prune invalid frames (I want to preserve compatibility between temporal & spectral derivatives)
            // change [nframes][:] ==> [nframes-nDeltaWindow][:]             
            float[][] validscores = new float[nFrames-mfccParams.nDeltaWindow][ncols];
            for(int f=0; f<nFrames-mfccParams.nDeltaWindow; f++)
                validscores[f] = scores[f];
            return validscores;
//            return exportData(validscores);   
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "processFramesTemporal" + ": Error: " + e.toString());
            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.MFCC_ERROR, "error", e.getMessage());
            return null;
        }        
    }            
    //-----------------------------------------------------------------------------------------
     public void setOutputFile(String output_mfcc_path)
    {
        mfccParams.sOutputPath  = output_mfcc_path;
    }
    
    //-----------------------------------------------------------------------------------------
    public void clearData()
    {
        //mDerivativesQueue = null;
    }
        
    //======================================================================================
    //======================================================================================
    // P R I V A T E
    //======================================================================================
    //======================================================================================
    // writes cepstra to file
    // send cepstra to WebLayer
    // send progress to SERVICE MAIN
    // send cepstra to SERVICE MAIN
    public float[][] exportData(float[][] scores)
    {
        return exportData(scores, true);
    }
    
    public float[][] exportData(float[][] scores, boolean isFinal)
    {
        try
        {
            JSONArray res_jsonarray         = new JSONArray();
            res_jsonarray.put(0, "processed file:" + mfccParams.sOutputPath);
            String strscores                = "";
            int nValidFrames                = scores.length;
            
            //------------------------------------------------------------------
            // write data to FILE
            //------------------------------------------------------------------
            if(isFinal)
            {
                switch(mfccParams.nDataDest)
                {
                    case ENUMS.MFCC_DATADEST_FILE:
                    case ENUMS.MFCC_DATADEST_FILEWEB:
                    case ENUMS.MFCC_DATADEST_ALL:

                        boolean res = FileUtilities.write2DArrayToFile(scores, nValidFrames, mfccParams.sOutputPath + "_scores.dat", sOutputPrecision, true);
    //                    strscores   = StringUtilities.exportArray2String(scores, );
    //                    boolean res = FileUtilities.writeStringToFile(mfccParams.sOutputPath + "_scores.dat", strscores, true);
                        //                tp.addTimepoint(4);                
                        break;
                }
            }
            //------------------------------------------------------------------
            // send data to WEB LAYER
            //------------------------------------------------------------------
            switch(mfccParams.nDataDest)
            {
                case ENUMS.MFCC_DATADEST_FILEWEB:
                case ENUMS.MFCC_DATADEST_JSDATAWEB:
                    JSONObject info         = new JSONObject();
                    info.put("type",        ENUMS.MFCC_RESULT);
                    info.put("data",        new JSONArray(scores));
                    info.put("progress",    mfccParams.sOutputPath);
                    Messaging.sendUpdate2Web(callbackContext, info, true);
                    break;                 
            }
            //------------------------------------------------------------------
            // send progress to SERVICE MAIN
            //------------------------------------------------------------------
            switch(mfccParams.nDataDest)
            {
                case ENUMS.MFCC_DATADEST_FILE:
                case ENUMS.MFCC_DATADEST_FILEWEB:
                    Messaging.sendMessageToHandler(mStatusCallback, ENUMS.MFCC_STATUS_PROGRESS_DATA, "progress", Integer.toString(nValidFrames));              
                    break;
            }             
            //------------------------------------------------------------------
            // send data to SERVICE MAIN    
            //------------------------------------------------------------------
            switch(mfccParams.nDataDest)
            {
                case ENUMS.MFCC_DATADEST_JSPROGRESS:            
                case ENUMS.MFCC_DATADEST_JSDATA:            
                case ENUMS.MFCC_DATADEST_ALL:            
                    Messaging.sendDataToHandler(mResultCallback, ENUMS.MFCC_RESULT, scores, nValidFrames, nScores*3, mfccParams.sOutputPath);  
            }
            //------------------------------------------------------------------
//            else{ int[] elapsed = tp.endTracking();                res_jsonarray.put(1, new JSONArray(elapsed)); }
            return scores;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "exportData" + ": Error: " + e.toString());
            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.MFCC_ERROR, "error", e.getMessage());
            return null;
        }
    }    
    
    //======================================================================================
    //======================================================================================
    // S T A T I C 
    //======================================================================================
    //======================================================================================
    
//    public static float[] initHamming(int frame_length)
//    {
//        float w[] = new float[frame_length];
//        for (int n = 0; n < frame_length; n++)   w[n] = (float)(0.54 - 0.46 * Math.cos((2*Math.PI*n)/(frame_length - 1)));
//        return w;
//    }
//    public static float[][] preProcessing(float[] inputSignal, int fwidth, int fdistance, float alpha, float[] hammWnd)
//    {
//        float[] data        = preEmphasis(inputSignal, alpha);
//        float[][] frames    = Framing.frameVector(data, fwidth, fdistance);
//        Framing.hammingWindow(frames, hammWnd);
//        return frames;
//    }
//    /**
//     * perform pre-emphasis to equalize amplitude of high and low frequency<br>
//     * calls: none<br>
//     * called by: featureExtraction
//     * @param inputSignal Speech Signal (16 bit integer data)
//     * @return Speech signal after pre-emphasis (16 bit integer data)
//     */
//    public static float[] preEmphasis(float inputSignal[], float alpha)
//     {
//        float outputSignal[] = new float[inputSignal.length];
//        
//        // apply pre-emphasis to each sample
//        for (int n = 1; n < inputSignal.length; n++)  outputSignal[n] = inputSignal[n] - alpha*inputSignal[n - 1];
//        return outputSignal;
//    }    
//    
//    public static void normalizeFrames(float[][] cepstra)
//    {
//        normalizeFrames(cepstra, cepstra.length);
//    }
//    
//    public static void normalizeFrames(float[][] cepstra, int frames2recognize)
//    {
//        int nscores         = cepstra[0].length;
//        float[] means       = new float[nscores];
//        float[] sd          = new float[nscores];
//        
//        // means
//        for(int sc=0; sc<nscores; sc++)
//        {
//            for(int f=0; f<frames2recognize; f++)
//                means[sc] += cepstra[f][sc]; 
//            means[sc] /= frames2recognize;
//        }
//
//        // standard Deviations
//        float variance=0;
//        for(int sc=0; sc<nscores; sc++)
//        {
//            variance=0;
//            for(int f=0; f<frames2recognize; f++)
//                variance += (cepstra[f][sc] - means[sc])*(cepstra[f][sc] - means[sc]); 
//            variance   /= frames2recognize;
//            sd[sc]      = (float)Math.sqrt(variance);
//        }        
//        
//        // normalize data
//        for(int sc=0; sc<nscores; sc++)
//        {
//            if(sd[sc] == 0)
//                for(int f=0; f<frames2recognize; f++)
//                    cepstra[f][sc] = 0;                 
//            else
//                for(int f=0; f<frames2recognize; f++)
//                    cepstra[f][sc] = (cepstra[f][sc] - means[sc])/sd[sc]; 
//        }
//    }  
//    
//    public static float[][] getContextedFrames(float[][] cepstra, int nctxframes, int noutparams)    // cepstra = [:][72] => [:][792] 
//    {
//        return getContextedFrames(cepstra, nctxframes, noutparams, cepstra.length);
//    }
//
//    // cepstra can be only partially filled, thus I process up to frames2recognize
//    public static float[][] getContextedFrames(float[][] cepstra, int nctxframes, int noutparams, int frames2recognize)    // cepstra = [:][72] => [:][792] 
//    {
//        int ncepstra                    = cepstra[0].length;
//        float[][] contextedCepstra      = new float[frames2recognize][noutparams];
//        int startId, endId, cnt,corr_pf = 0;
//        
//        int preFrames  = (int)Math.floor((double)(nctxframes*1.0)/2);
//        // append Context frames (from 72 => 792 = tfParams.nInputParam)
//        for (int f=0; f<frames2recognize; f++)
//        {
//            startId = f - preFrames;
//            endId   = f + preFrames + 1; // won't be included in the for...
//            cnt     = 0;
//            
//            if(f < (frames2recognize-preFrames))
//            {
//                // from frames = [0 : frames2recognize-preFrames-1]
//                for(int pf=startId; pf<endId; pf++)
//                {
//                    if(pf < 0)  corr_pf = 0;
//                    else        corr_pf = pf;
//                    for(int ff=0; ff<ncepstra; ff++)
//                    {
//                        contextedCepstra[f][cnt] = cepstra[corr_pf][ff];                
//                        cnt++;
//                    }
//                }                
//            }
//            else
//            {
//                // from frames = [frames2recognize-preFrames : frames2recognize-1]
//                for(int pf=startId; pf<endId; pf++)
//                {
//                    if(pf > (frames2recognize-1))   corr_pf = frames2recognize-1;
//                    else                            corr_pf = pf;
//                    
//                    for(int ff=0; ff<ncepstra; ff++)
//                    {
//                        contextedCepstra[f][cnt] = cepstra[corr_pf][ff];                
//                        cnt++;
//                    }
//                }                
//            }
//        }
//        return contextedCepstra;
//    }  
    //======================================================================================
}

