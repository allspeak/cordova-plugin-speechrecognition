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
    private MFCCCalcJAudio mfcc                 = null; 
    
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
    
    private float[][] faFullMFCC;           // contains (nframes, numparams) calculated MFCC and its derivatives
    private float[][] faFullFilterBanks;    // contains (nframes, numparams) calculated FilterBanks and its derivatives
    
    private float[][][] faDerivatives;  // contains (2, nframes, numparams)  1st & 2nd derivatives of the calculated params
    private int nindices1;
    private int[] indices1 ;
    private int nindices2;
    private int[] indices2 ;
    private int nindicesout;
    private int[] indicesout;
    private int nDerivDenom;
    
    private float[][] mDerivativesQueue = null;  // store the last nDeltaWindow-samples to calculate derivatives 
    
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
        mfccParams      = params; 
        nScores         = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccParams.nNumberOfMFCCParameters : mfccParams.nNumberofFilters);
        sOutputPrecision= (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? sOutputMFCCPrecision : sOutputFiltersPrecision);
        
        mDerivativesQueue        = new float[mfccParams.nDeltaWindow][nScores];
        
        if ((boolean)mfccParams.bCalculate0ThCoeff) mfccParams.nNumberOfMFCCParameters++;//take in account the zero-th MFCC        

        mfcc = new MFCCCalcJAudio(  mfccParams.nNumberOfMFCCParameters,
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
    }   
    
    public void setWlCb(CallbackContext wlcb)
    {
        callbackContext     = wlcb;
    }    
    
    public void setCallbacks(Handler cb)
    {
        mStatusCallback = cb;
        mCommandCallback = cb;
        mResultCallback = cb;
    }    
    
    public void setCallbacks(Handler scb, Handler ccb, Handler rcb)
    {
        mStatusCallback     = scb;
        mCommandCallback    = ccb;
        mResultCallback     = rcb;
    }    
    //================================================================================================================
    //================================================================================================================
    //================================================================================================================
    //================================================================================================================
    //================================================================================================================
    public void setOutputFile(String output_mfcc_path)
    {
        mfccParams.sOutputPath  = output_mfcc_path;
    }
    //=================================================================================================================
    // PRIVATE
    //=================================================================================================================
    public float[][] processRawData(float[] data, int dataType, int dataDest, String output_path)
    {
        mfccParams.nDataType    = dataType;  
        mfccParams.nDataDest    = dataDest;    
        mfccParams.sOutputPath  = output_path;
        return processRawData(data);
    }

    public void clearData()
    {
        mfcc.clearData();
    }
    
    // get score, get derivatives => exportData
    public synchronized float[][] processRawData(float[] data)
    {
        try
        {
            if(mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS)
            {
                faFullMFCC   = mfcc.getFullMFCC(data);
                if(faFullMFCC == null || faFullMFCC.length == 0)
                {
                    Log.e(TAG, "processFile" + ": Error:  faFullMFCC is empty");
                    return null;
                }
                else
                {
                    nFrames     = faFullMFCC.length;
                    return exportData(faFullMFCC);                
                }
            }
            else
            {
                faFullFilterBanks   = mfcc.getFullMFFilters(data);                
                if(faFullFilterBanks == null || faFullFilterBanks.length == 0)
                {
                    Log.e(TAG, "processFile" + ": Error:  faFullFilterBanks is empty");
                    return null;
                }
                else
                {
                    nFrames     = faFullFilterBanks.length;
                    return exportData(faFullFilterBanks);
                }
            } 
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "processFile" + ": Error: " + e.toString());
            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.MFCC_ERROR, "error", e.getMessage());
            return null;
        }        
    }            

    // read wav(String) => processRawData(float[])
    public void processFile(String input_file_noext) 
    {
        try
        {
//            tp                  = new TrackPerformance(5); // I want to monitor : wav read (if applicable), params calculation, data export(if applicable), data write(if applicable), data json packaging(if applicable)
            mfccParams.sOutputPath  = input_file_noext;
            String sAudiofile       = input_file_noext + ".wav";
            float[] data            = readWav(sAudiofile);  
            FileUtilities.deleteExternalStorageFile(mfccParams.sOutputPath + "_scores.dat");
//            tp.addTimepoint(1);
            processRawData(data);
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
    
    // processFolder(String) => for files in... processFile(String) => processRawData(float[])
    public void processFolder(String input_folderpath) 
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
                processFile(StringUtilities.removeExtension(tempfile));
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
    
    public void sendData2TF()
    {
//        Messaging.sendDataToHandler(mCommandCallback, ENUMS.TF_CMD_RECOGNIZE, , ); ///
    }
    
    //======================================================================================
    // E X P O R T
    //======================================================================================
//    private void exportData(float[][] data, float[][][] derivatives) 
    private float[][] exportData(float[][] data) 
    {
        try
        {
            JSONArray res_jsonarray         = new JSONArray();
            res_jsonarray.put(0, "processed file:" + mfccParams.sOutputPath);
            String scores                   = "";
            
            //------------------------------------------------------------------
            // write data to FILE
            //------------------------------------------------------------------
            switch(mfccParams.nDataDest)
            {
                case ENUMS.MFCC_DATADEST_FILE:
                case ENUMS.MFCC_DATADEST_ALL:
                case ENUMS.MFCC_DATADEST_FILEWEB:
                    
                    if(mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS)    scores = StringUtilities.exportArray2String(faFullMFCC, sOutputPrecision);
                    else                                                            scores = StringUtilities.exportArray2String(faFullFilterBanks, sOutputPrecision);
                    
                    boolean res = FileUtilities.writeStringToFile(mfccParams.sOutputPath + "_scores.dat", scores, false);
//                    writeTextParams(scores, mfccParams.sOutputPath + "_scores.dat");
                    //                tp.addTimepoint(4);                
                    break;
            }
            //------------------------------------------------------------------
            // send data to WEB LAYER
            //------------------------------------------------------------------
            switch(mfccParams.nDataDest)
            {
                case ENUMS.MFCC_DATADEST_FILEWEB:
                case ENUMS.MFCC_DATADEST_JSDATAWEB:
                    //costruire json e chiamare
                    JSONObject info         = new JSONObject();
                    info.put("type",        ENUMS.MFCC_RESULT);
                    info.put("data",        new JSONArray(data));
                    info.put("progress",    mfccParams.sOutputPath);
                    Messaging.sendUpdate2Web(callbackContext, info, true);
                    break;                 
            }
            //------------------------------------------------------------------
            // send progress to PLUGIN
            //------------------------------------------------------------------
            switch(mfccParams.nDataDest)
            {
                case ENUMS.MFCC_DATADEST_FILE:
                case ENUMS.MFCC_DATADEST_FILEWEB:
                    Messaging.sendMessageToHandler(mStatusCallback, ENUMS.MFCC_STATUS_PROGRESS_DATA, "progress", Integer.toString(nFrames));              
                    break;
            }             
            //------------------------------------------------------------------
            // send data to PLUGIN    
            //------------------------------------------------------------------
            switch(mfccParams.nDataDest)
            {
                case ENUMS.MFCC_DATADEST_NONE:
                case ENUMS.MFCC_DATADEST_JSPROGRESS:            
                case ENUMS.MFCC_DATADEST_JSDATA:            
                case ENUMS.MFCC_DATADEST_ALL:            
                    Messaging.sendDataToHandler(mResultCallback, ENUMS.MFCC_RESULT, data, nFrames, nScores*3, mfccParams.sOutputPath);  
            }
            //------------------------------------------------------------------
//            else{ int[] elapsed = tp.endTracking();                res_jsonarray.put(1, new JSONArray(elapsed)); }
            return data;
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
    // ACCESSORY
    //======================================================================================    

    private float[] readWav(String filepath)
    {
        try
        {
            File f          = new File(Environment.getExternalStorageDirectory(), filepath);
            // Open the wav file specified as the first argument
            WavFile wavFile = WavFile.openWavFile(f);
            int frames      = (int)wavFile.getNumFrames();
            int numChannels = wavFile.getNumChannels();

            if(numChannels > 1)
                return null;           
            // Create the buffer
            float[] buffer = new float[frames * numChannels];
            int framesRead  = wavFile.readFrames(buffer, frames);
            
            if(frames != framesRead)
                return null;

            // Close the wavFile
            wavFile.close();  
            return buffer;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
    //======================================================================================    
}

