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
//import com.allspeak.BuildConfig;
import java.io.FilenameFilter;

import android.os.ResultReceiver;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import com.allspeak.audioprocessing.Framing;
import com.allspeak.audioprocessing.WavFile;
import com.allspeak.utility.StringUtilities;
import com.allspeak.utility.Messaging;
import com.allspeak.utility.FileUtilities;

import com.allspeak.utility.TrackPerformance;

import com.allspeak.audioprocessing.mfcc.MFCCDerivatives;

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

    private int nScores                         = 0;        // either nNumberofFilters or nNumberOfMFCCParameters according to requeste measure
    
    private String sOutputPrecision             = "%.4f";       // label of the element the audio belongs to
    private final String sOutputMFCCPrecision   = "%.4f";       // label of the element the audio belongs to
    private final String sOutputFiltersPrecision= "%.4f";       // label of the element the audio belongs to
    
    public float[] hammingWnd = null;
    
    //================================================================================================================
    // CONSTRUCTORS
    //================================================================================================================
    public MFCC(MFCCParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcallback)
    {
        mfccParams          = params;
        mStatusCallback     = scb;
        mCommandCallback    = ccb;
        mResultCallback     = rcb;
        callbackContext     = wlcallback;
        setParams(params); 
    } 
    //-----------------------------------------------------------------------------------------------------    
    // overloads
    //-----------------------------------------------------------------------------------------------------  
    public MFCC(MFCCParams params, Handler cb)
    {
        this(params, cb, cb, cb, null);
    }
    public MFCC(MFCCParams params, Handler scb, Handler ccb, Handler rcb)
    {
        this(params, scb, ccb, rcb, null);
    }
    public MFCC(MFCCParams params, Handler cb, CallbackContext wlcallback)
    {
        this(params, cb, cb, cb, wlcallback);
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


    //=================================================================================================================
    // COMMANDS
    //=================================================================================================================
    // processFile  :   readWav
    //                  checkWhetherDeleteMFCCFile
    //                  processData 
    //                              
    // processData  :   getFeatures
    //                  exportData
    //   
    //                  getFeatures  :   getSimpleFeatures   :   samplesProcessing 
    //                                                           getMFCC
    //                                   finalizeData        :   calculate derivatives
    //                                                           threshold data
    //                                                           normalize
    //
    // ONE SHOT PROCESSING, e.g. file/folder,
    // read wav(String) => framing => processFrames(float[][])
    // if !overwrite : check if file exist, if YES => if it has the correct number of frames => skip it.
    public void processFile(String input_filepath, String output_filepath, boolean overwrite) 
    {
        try
        {
            // tp                  = new TrackPerformance(5); // I want to monitor : wav read (if applicable), params calculation, data export(if applicable), data write(if applicable), data json packaging(if applicable)
            String inputpath        = StringUtilities.removeExtension(input_filepath);
            String outputpath       = StringUtilities.removeExtension(output_filepath);
            
            String audio_relfile    = inputpath + ".wav";
            String mfcc_relfile     = outputpath + ".dat";
            
            float[] data            = WavFile.getWavData(Environment.getExternalStorageDirectory() + "/" + audio_relfile);  
            int nframes             = Framing.getFrames(data.length, mfccParams.nWindowLength, mfccParams.nWindowDistance);
            
            // Since I write appending, I have to decide what to do with the existing files. 1) go on appending to the existing file OR skip.... 2) delete it 
            checkWhetherDeleteMFCCFile(overwrite, mfcc_relfile, nframes);
            
            processData(data, mfcc_relfile);
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
    public void processFolder(String input_folderpath, String output_folderpath, boolean overwrite) 
    {
//        TrackPerformance tp_folder      = new TrackPerformance(1); // I want to monitor just to total time
        File directory                  = new File(Environment.getExternalStorageDirectory().toString() + "/" + input_folderpath);

        try
        {
            String infile             = "";
            String outfile            = "";
            
            // filter WAV
            File[] files              = directory.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".wav");
                }
            });
            // aiElapsedTimes               = new int[files.length][5];
            if(files.length == 0) 
            {
                Messaging.sendMessageToHandler(mStatusCallback, ERRORS.MFCC_ERROR, "error", "Input folder does not contain any file");
                return;
            }
                
            for (int i = 0; i < files.length; i++)
            {
                infile            = input_folderpath + File.separatorChar + files[i].getName();
                outfile           = output_folderpath + File.separatorChar + files[i].getName();
                processFile(infile, outfile, overwrite);
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
    // processFolder(String) => for files in... processFile(String) => processRawData(float[])
    public void processFolder(String input_folderpath, String output_folderpath, boolean overwrite, String[] filefilters) 
    {
//        TrackPerformance tp_folder      = new TrackPerformance(1); // I want to monitor just to total time
        File directory                  = new File(Environment.getExternalStorageDirectory().toString() + "/" + input_folderpath);

        try
        {
            String infile             = "";
            String outfile            = "";
            
            // filter WAV
            File[] files                = directory.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".wav");
                }
            });
//            aiElapsedTimes               = new int[files.length][5];
            if(files.length == 0) 
            {
                Messaging.sendMessageToHandler(mStatusCallback, ERRORS.MFCC_ERROR, "error", "Input folder does not contain any file");
                return;
            }
                
            boolean processit = false;
            for (int i = 0; i < files.length; i++)
            {
                for (int p = 0; p < filefilters.length; p++)
                {
                    if(files[i].getName().contains(filefilters[p]))
                    {
                        processit = true;
                        break;
                    }
                }
                if(processit)
                {
                    infile            = input_folderpath + File.separatorChar + files[i].getName();
                    outfile           = output_folderpath + File.separatorChar + files[i].getName();
                    processFile(infile, outfile, overwrite);                    
                }
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

    public void processData(float[] data, String output_filepath)
    {
        if(output_filepath != "")
            mfccParams.sOutputPath  = StringUtilities.removeExtension(output_filepath);   // exportData uses it (and appends dat, thus give it with no extension 16/3/18

        exportData(getFeatures(data));
    }
    //=================================================================================================================
    // returns: final_data  [nframes-2*deltawindow][3*nscores]    
    public synchronized float[][] getFeatures(float[] samples2beprocessed)
    {
        float[][] cepstra = getSimpleFeatures(samples2beprocessed);   // get only zero-order cepstra
        return finalizeData(cepstra, cepstra.length, mfccParams.nProcessingScheme, nScores, mfccParams.nDeltaWindow);
    }     
    //--------------------------------------------------------------------------
    // only preprocess samples, frame samples and calculate zero order cepstra
    // return: cepstra[nframes][nscores]
    public synchronized float[][] getSimpleFeatures(float[] samples2beprocessed)
    {
        float[][] frames2beprocessed = null;
        // preproc or not preproc
        switch((int)mfccParams.nProcessingScheme)
        {
            case ENUMS.MFCC_PROCSCHEME_F_S:
            case ENUMS.MFCC_PROCSCHEME_F_T:
            case ENUMS.MFCC_PROCSCHEME_F_S_NOTHR:
            case ENUMS.MFCC_PROCSCHEME_F_T_NOTHR:
            case ENUMS.MFCC_PROCSCHEME_F_NOTHR:
            case ENUMS.MFCC_PROCSCHEME_F:
                frames2beprocessed = Framing.samplesProcessing(samples2beprocessed, mfccParams.nWindowLength, mfccParams.nWindowDistance, 0.0f, null); // NO pre-emphasis, 
                break;
                
            case ENUMS.MFCC_PROCSCHEME_F_S_PP:
            case ENUMS.MFCC_PROCSCHEME_F_T_PP:
            case ENUMS.MFCC_PROCSCHEME_F_S_PP_NOTHR:
            case ENUMS.MFCC_PROCSCHEME_F_T_PP_NOTHR:
            case ENUMS.MFCC_PROCSCHEME_F_PP_NOTHR:
            case ENUMS.MFCC_PROCSCHEME_F_PP:
                frames2beprocessed  = Framing.samplesProcessing(samples2beprocessed, mfccParams.nWindowLength, mfccParams.nWindowDistance, 0.95f, hammingWnd); // pre-emphasis/framing/hamming-windowing
                break;
        }
        
        return (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccCalc.getMFCC(frames2beprocessed) : mfccCalc.getMFFilters(frames2beprocessed)); 
    }     
 
    //--------------------------------------------------------------------------
    // CALLED BY MFCC_CMD_FINALIZEDATA event & TF.doRecognize
    // input:   cepstra     [nframes]              [nscores]
    // returns: final_data  [nframes-2*deltawindow][3*nscores]
    // calculate derivatives
    // threshold data
    // normalize
    static public float[][] finalizeData(float[][] cepstra, int nframes, int procscheme, int scores, int deltawindow)
    {
        float[][] final_data;
 
        int lastvalidframes     = nframes - deltawindow;    // last two frames have wrong derivatives and represent only the last 20ms of speech.
        int firstvalidframes    = deltawindow;              // first two frames have wrong derivatives
        int validframes         = lastvalidframes - firstvalidframes;
        // DERIVATIVES
        switch(procscheme)
        {
            case ENUMS.MFCC_PROCSCHEME_F_S:
            case ENUMS.MFCC_PROCSCHEME_F_S_PP:
            case ENUMS.MFCC_PROCSCHEME_F_S_NOTHR:
            case ENUMS.MFCC_PROCSCHEME_F_S_PP_NOTHR:
                // spectral
                final_data = new float[validframes][3*scores];
                for(int f=firstvalidframes; f<lastvalidframes; f++) System.arraycopy(cepstra[f], 0, final_data[f-firstvalidframes], 0, scores);                   
                MFCCDerivatives.addSpectralDerivatives(final_data, deltawindow);
                break;

            case ENUMS.MFCC_PROCSCHEME_F_T:
            case ENUMS.MFCC_PROCSCHEME_F_T_PP:
            case ENUMS.MFCC_PROCSCHEME_F_T_NOTHR:
            case ENUMS.MFCC_PROCSCHEME_F_T_PP_NOTHR:
                // temporal
                 final_data = new float[validframes][3*scores];
                for(int f=firstvalidframes; f<lastvalidframes; f++) System.arraycopy(cepstra[f], 0, final_data[f-firstvalidframes], 0, scores);                   
                MFCCDerivatives.addTemporalDerivatives(final_data, deltawindow);
                break;
                
            case ENUMS.MFCC_PROCSCHEME_F_NOTHR:
            case ENUMS.MFCC_PROCSCHEME_F_PP_NOTHR:                
            case ENUMS.MFCC_PROCSCHEME_F:                
            case ENUMS.MFCC_PROCSCHEME_F_PP:                
                // no derivatives
                final_data = new float[validframes][scores];
                for(int f=firstvalidframes; f<lastvalidframes; f++) System.arraycopy(cepstra[f], 0, final_data[f-firstvalidframes], 0, scores);    
                break;
                
            default:
                return null;
        }                    
        
        // Thresholding
        switch(procscheme)
        {        
            case ENUMS.MFCC_PROCSCHEME_F_S:
            case ENUMS.MFCC_PROCSCHEME_F_S_PP:
            case ENUMS.MFCC_PROCSCHEME_F_T:
            case ENUMS.MFCC_PROCSCHEME_F_T_PP:
            case ENUMS.MFCC_PROCSCHEME_F:
            case ENUMS.MFCC_PROCSCHEME_F_PP:
                final_data = Framing.getSuprathresholdFrames(final_data, 0.0f);
                break;
        }
        // Normalize
        Framing.normalizeFrames(final_data);
        
        return final_data;
    }    
    
    //-----------------------------------------------------------------------------------------
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
            int nColumns                    = scores[0].length;
            
            //------------------------------------------------------------------
            // write data to FILE
            //------------------------------------------------------------------
            if(isFinal)
            {
                switch((int)mfccParams.nDataDest)
                {
                    case ENUMS.MFCC_DATADEST_FILE:
                    case ENUMS.MFCC_DATADEST_FILEWEB:
                    case ENUMS.MFCC_DATADEST_ALL:

                        boolean res = FileUtilities.write2DArrayToFile(scores, nValidFrames, mfccParams.sOutputPath + ".dat", sOutputPrecision, true);
    //                    strscores   = StringUtilities.exportArray2String(scores, );
    //                    boolean res = FileUtilities.writeStringToFile(mfccParams.sOutputPath + ".dat", strscores, true);
                        //                tp.addTimepoint(4);                
                        break;
                }
            }
            //------------------------------------------------------------------
            // send data to WEB LAYER
            //------------------------------------------------------------------
            switch((int)mfccParams.nDataDest)
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
            switch((int)mfccParams.nDataDest)
            {
                case ENUMS.MFCC_DATADEST_FILE:
                case ENUMS.MFCC_DATADEST_FILEWEB:
                    Messaging.sendMessageToHandler(mStatusCallback, ENUMS.MFCC_STATUS_PROGRESS_DATA, "progress", Integer.toString(nValidFrames));              
                    break;
            }             
            //------------------------------------------------------------------
            // send data to SERVICE MAIN    
            //------------------------------------------------------------------
            switch((int)mfccParams.nDataDest)
            {
                case ENUMS.MFCC_DATADEST_JSPROGRESS:            
                case ENUMS.MFCC_DATADEST_JSDATA:            
                case ENUMS.MFCC_DATADEST_ALL:            
                    Messaging.sendDataToHandler(mResultCallback, ENUMS.MFCC_RESULT, scores, nValidFrames, nColumns, mfccParams.sOutputPath);  
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

    //-----------------------------------------------------------------------------------------
    public void setOutputFile(String output_mfcc_path)
    {
        mfccParams.sOutputPath  = output_mfcc_path;
    }
    //======================================================================================
    //======================================================================================
    // P R I V A T E
    //======================================================================================
    //======================================================================================
    // now, since empty frames are deleted, you cannot check if the file is corrupt (has less lines).
    // thus when overwrite is false and file exist, skip it.
    private void checkWhetherDeleteMFCCFile(boolean overwrite, String mfcc_relfile, int nframes) throws Exception
    {
        // Since I write appending, I have to decide what to do with the existing files.
        // 1) go on appending to the existing file and skip 
        // 2) delete it 
        try
        {
            if(FileUtilities.existRelFile(mfcc_relfile))
            {
                if(overwrite) FileUtilities.deleteExternalStorageFile(mfcc_relfile); 
                else
                {
//                    File f = new File(Environment.getExternalStorageDirectory(), mfcc_relfile);
//                    int nlines = FileUtilities.countLines(f);
//                    if(nlines == nframes)   // is a valid file ?
//                    {
                        // send message & skip
                        Messaging.sendMessageToHandler(mStatusCallback, ENUMS.MFCC_STATUS_PROGRESS_FILE, "progress_file", mfccParams.sOutputPath);                    
                        return;
//                    }                        
//                    else    // the file exist but is corrupted...presumably a crash during processing..I delete it
//                        FileUtilities.deleteExternalStorageFile(mfcc_relfile); 
                }
            }
        }
        catch(Exception e)
        {
            throw e;
        }
    }

    //=================================================================================================================
    // DEPRECATED FUNCTIONS
    //=================================================================================================================

//    // spectral derivatives
//    // returns => [nframes][3*nscores]    
//    private synchronized float[][] processSpectral(float[][] frames)
//    {
//        float[][] scores    = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccCalc.getMFCC(frames) : mfccCalc.getMFFilters(frames)); 
//        mfccCalc.addSpectralDerivatives(scores);      // writes [:][2*nscore:3*nscore]          
//        return scores;
//    }
//    // temporal derivatives
//    // returns => [nframes][3*nscores]    
//    private synchronized float[][] processTemporal(float[][] frames)
//    {
//        float[][] scores    = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccCalc.getMFCC(frames) : mfccCalc.getMFFilters(frames)); 
//        mfccCalc.addTemporalDerivatives(scores);                
//        return scores;
//    }    
//        
//    //-----------------------------------------------------------------------------------------
//    // LIVE PROCESSING (normalization and contexting will be done when the sentences is complete)
//    public synchronized float[][] getFeaturesQueued(float[] samples2beprocessed, float[][] queuedcepstraframes)
//    {
//        float[][] frames2beprocessed = null;
//        float[][] cepstra = null;        
//        // preproc or not preproc
//        switch((int)mfccParams.nProcessingScheme)
//        {
//            case ENUMS.MFCC_PROCSCHEME_F_S:
//            case ENUMS.MFCC_PROCSCHEME_F_T:
//            case ENUMS.MFCC_PROCSCHEME_F_S_NOTHR:
//            case ENUMS.MFCC_PROCSCHEME_F_T_NOTHR:                
//                frames2beprocessed = Framing.samplesProcessing(samples2beprocessed, mfccParams.nWindowLength, mfccParams.nWindowDistance, 0.0f, null); // NO pre-emphasis, NO hamming-windowing
//                break;
//                
//            case ENUMS.MFCC_PROCSCHEME_F_S_PP:
//            case ENUMS.MFCC_PROCSCHEME_F_T_PP:
//            case ENUMS.MFCC_PROCSCHEME_F_S_PP_NOTHR:
//            case ENUMS.MFCC_PROCSCHEME_F_T_PP_NOTHR:                
//                frames2beprocessed  = Framing.samplesProcessing(samples2beprocessed, mfccParams.nWindowLength, mfccParams.nWindowDistance, 0.95f, hammingWnd); // pre-emphasis/framing/hamming-windowing
//                break;
//        }
//        int norigframes = frames2beprocessed.length;
//        
//        // spectral or temporal derivatives
//        switch((int)mfccParams.nProcessingScheme)
//        {
//            case ENUMS.MFCC_PROCSCHEME_F_S:
//            case ENUMS.MFCC_PROCSCHEME_F_S_PP:
//            case ENUMS.MFCC_PROCSCHEME_F_S_NOTHR:
//            case ENUMS.MFCC_PROCSCHEME_F_S_PP_NOTHR:                
//                cepstra = processQueuedSpectral(frames2beprocessed);
//                break;
//                
//            case ENUMS.MFCC_PROCSCHEME_F_T:
//            case ENUMS.MFCC_PROCSCHEME_F_T_PP:
//            case ENUMS.MFCC_PROCSCHEME_F_T_NOTHR:
//            case ENUMS.MFCC_PROCSCHEME_F_T_PP_NOTHR:                
//                cepstra = processQueuedTemporal(frames2beprocessed, queuedcepstraframes); // strange BUG....while solving it, I do temporal derivatives at the end
//                break;
//        }        
//        float[][] validframes;
//        // TODO : I should remove null frames after contexting...otherwise when I take the the previous 5, I may get frames before the cut
//        if((int)mfccParams.nProcessingScheme < ENUMS.MFCC_PROCSCHEME_F_S_NOTHR) validframes = Framing.getSuprathresholdFrames(cepstra, 0.0f);
//        else                                                                    validframes = cepstra;        
//        
//        return validframes;
//    }       
//    // temporal derivatives
//    // input:     frames = [nframes][nWindowLength], queue = [nframes][nscores]
//    // returns => [nframes-nDeltaWindow][3*nscores]
//    private synchronized float[][] processQueuedTemporal(float[][] frames, float[][] queue)
//    {
//        try
//        {
//            float[][] scores    = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccCalc.getMFCC(frames) : mfccCalc.getMFFilters(frames)); 
//            int nframes         = scores.length;
//            int ncols           = scores[0].length;
//            mfccCalc.addTemporalDerivatives(scores, queue);  
//            
//            // prune invalid frames
//            //  change [nframes][:] ==> [nframes-nDeltaWindow][:]             
//            float[][] validscores = new float[nframes-mfccParams.nDeltaWindow][ncols];
//            for(int f=0; f<nframes-mfccParams.nDeltaWindow; f++)
//                validscores[f] = scores[f];
//            return validscores;
//        }
//        catch(Exception e)
//        {
//            e.printStackTrace();
//           Log.e(TAG, "processFramesTemporal" + ": Error: " + e.toString());
//            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.MFCC_ERROR, "error", e.getMessage());
//            return null;
//        }        
//    }            
//    
//    private synchronized float[][] processQueuedSpectral(float[][] frames)
//    {
//        try
//        {
//            float[][] scores    = processSpectral(frames);
//            
//            int nframes         = scores.length;
//            int ncols           = scores[0].length;
//            // prune invalid frames (I want to preserve compatibility between temporal & spectral derivatives)
//            // change [nframes][:] ==> [nframes-nDeltaWindow][:]             
//            float[][] validscores = new float[nframes-mfccParams.nDeltaWindow][ncols];
//            for(int f=0; f<nframes-mfccParams.nDeltaWindow; f++)
//                validscores[f] = scores[f];
//            return validscores;
//        }
//        catch(Exception e)
//        {
//            e.printStackTrace();
//           Log.e(TAG, "processFramesTemporal" + ": Error: " + e.toString());
//            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.MFCC_ERROR, "error", e.getMessage());
//            return null;
//        }        
//    }     
    //======================================================================================
}

