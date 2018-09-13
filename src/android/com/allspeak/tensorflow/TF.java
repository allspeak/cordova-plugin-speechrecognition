/**
 */
package com.allspeak.tensorflow;


import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONObject;
import org.json.JSONArray;

import android.util.Log;
//import com.allspeak.BuildConfig;

import com.allspeak.utility.Messaging;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;

import com.allspeak.utility.StringUtilities;
import com.allspeak.utility.FileUtilities;
//import com.allspeak.utility.TrackPerformance;
import com.allspeak.utility.Messaging;

import com.allspeak.audioprocessing.Framing;
import com.allspeak.audioprocessing.mfcc.MFCC;


import com.allspeak.tensorflow.TensorFlowSpeechClassifier;
import com.allspeak.tensorflow.Classifier.Recognition;
import com.allspeak.tensorflow.Classifier;
import java.util.List;
import java.util.Locale;

/*
it sends the following messages to Plugin Activity:
- data
- progress_file
- progress_folder
- error

*/

public class TF  
{
    private static final String LOG_TAG         = "TF";
    
    private TFParams mTfParams                  = null;                                   // MFCC parameters
    
    // properties to send results back to Plugin Main
    private Handler mStatusCallback             = null;     // Thread
    private Handler mCommandCallback            = null;     // Thread
    private Handler mResultCallback             = null;     // Thread
    private CallbackContext callbackContext     = null;

    private Classifier mClassifier              = null;

    //================================================================================================================
    // CONSTRUCTORS
    //================================================================================================================
    public TF(TFParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcallback)
    {
        mStatusCallback     = scb;
        mCommandCallback    = ccb;
        mResultCallback     = rcb;
        callbackContext     = wlcallback;
        setParams(params);    
    }
    //-----------------------------------------------------------------------------------------------------    
    // overloads
    //-----------------------------------------------------------------------------------------------------    
    public TF(TFParams params, Handler cb)
    {
        this(params, cb, cb, cb, null);
    }
    public TF(TFParams params, Handler scb, Handler ccb, Handler rcb)
    {
        this(params, scb, ccb, rcb, null);
    }
    public TF(TFParams params, Handler cb, CallbackContext wlcallback)
    {
        this(params, cb, cb, cb, wlcallback);
    }   
    //================================================================================================================
    //================================================================================================================
    public void setParams(TFParams params)
    {
        mTfParams = params;
    }   
    public void setWlCb(CallbackContext wlcb)
    {
        callbackContext     = wlcb;
    }    
    public void setCallbacks(Handler scb, Handler ccb, Handler rcb)
    {
        mStatusCallback     = scb;
        mCommandCallback    = ccb;
        mResultCallback     = rcb;
    } 
    public void setCallbacks(Handler cb)
    {
        setCallbacks(cb, cb, cb);
    }    
    //=================================================================================================================
    // PUBLIC
    //=================================================================================================================
    public boolean loadModel()
    {
        try
        {
            String sModelFilePath = mTfParams.sModelFilePath.startsWith("file://") ? mTfParams.sModelFilePath.split("file://")[1] : mTfParams.sModelFilePath;
        
            boolean exists = true;
            String err = "";
            if(!FileUtilities.existFile(sModelFilePath))
            {
                exists = false;
                err += (" " + sModelFilePath);
            }
            if(exists)
            {
                mClassifier = TensorFlowSpeechClassifier.create(
                                    mTfParams.mAssetManager,
                                    sModelFilePath,
                                    mTfParams.nInputParams,
                                    mTfParams.saInputNodeName,
                                    mTfParams.sOutputNodeName,
                                    mTfParams.getTitles());
            
                callbackContext.success(mTfParams.sLabel);
            }
            else callbackContext.error("the following files are missing: " + err);
            
            return true;
        }
        catch (Exception e) 
        {
            mClassifier = null;
            e.printStackTrace();
            callbackContext.error(e.getMessage());
            return false;
        }          
    }
   
    public void doRecognize(float[][] simplecepstra, int frames2recognize, int deltawindow)    // simplecepstra = [?][24]
    {
        try
        {
            int nscores         = simplecepstra[0].length;
            float[][] cepstra   = MFCC.finalizeData(simplecepstra, frames2recognize, mTfParams.nProcessingScheme, nscores, deltawindow);
            
            if(cepstra != null)
            {
                int validframes     = cepstra.length;

                if(validframes > 2*mTfParams.nContextFrames)
                {                
        //            Framing.normalizeFrames(cepstra, frames2recognize);

                    float[][] contextedCepstra = null;
                    if(cepstra[0].length != mTfParams.nInputParams)
                        contextedCepstra = Framing.getContextedFrames(cepstra, mTfParams.nContextFrames, mTfParams.nInputParams, validframes);  // [?][72] => [?][792]
                    else
                        contextedCepstra = cepstra;

                    if(mClassifier != null)
                    {
                        List<Recognition> results = null;
                        switch((int)mTfParams.nModelClass)
                        {
                            case ENUMS.TF_MODELCLASS_FF:
                                results = mClassifier.recognizeSpeech(contextedCepstra, validframes, mTfParams.fRecognitionThreshold);
                                break;

                            case ENUMS.TF_MODELCLASS_LSTM:
                                results = mClassifier.recognizeLSTMSpeech(contextedCepstra, validframes);
                                break;
                        }
        //            String recognizedWavPath = mTfParams.saAudioPath[Integer.parseInt(results.get(0).id)];
                        JSONObject output       = new JSONObject();  
                        output.put("type", ENUMS.TF_RESULT);

                        JSONArray items         = new JSONArray();  
                        for (Recognition result : results) 
                        {
                            JSONObject record   = new JSONObject();
                            record.put("title", result.getTitle());
                            record.put("confidence", String.format(Locale.US, "%.1f%%", result.getConfidence() * 100.0f)); 
                            record.put("id", result.getId()); 
                            items.put(record);
                        } 
                        output.put("items", items);
                        Messaging.sendUpdate2Web(callbackContext, output, true); 

                        switch((int)mTfParams.nDataDest)
                        {
                            case ENUMS.TF_DATADEST_MODEL_FILE:
                            case ENUMS.TF_DATADEST_FILEONLY:
        //                        String outfile      = "AllSpeak/audiofiles/temp/cepstra_live.dat";FileUtilities.write2DArrayToFile(cepstra, validframes, outfile, "%.4f", true);
        //                        String outfile_ctx  = "AllSpeak/audiofiles/temp/ctx_cepstra_live.dat";FileUtilities.write2DArrayToFile(contextedCepstra, validframes, outfile_ctx, "%.4f", true);
                                break;
                        }
                        return;
                    }
                    else
                    {
                        Messaging.sendErrorString2Web(callbackContext, "TF model not loaded", ERRORS.TF_ERROR_NOMODEL, true);
                        return;
                    }
                }
            }
            // 
            // valid cepstra are null or are < 2*contectframes. send a resume event to plugin javascript that directly call the plugin resumeSpeechRecognition
            JSONObject output       = new JSONObject();  
            output.put("type", ENUMS.TF_RESUME_RECOGNITION);                
            Messaging.sendUpdate2Web(callbackContext, output, true);                 
        }
        catch(Exception e)
        {
            e.printStackTrace();                  
            Log.e(LOG_TAG, e.getMessage(), e);
            Messaging.sendErrorString2Web(callbackContext, e.getMessage(), ERRORS.TF_ERROR, true);
        }             
    }
    //=================================================================================================================
    // PRIVATE
    //=================================================================================================================
    private boolean writeSentenceCepstra(String filename, float[][] cepstra, int frames2recognize) throws Exception
    {
        String str_cepstra  = StringUtilities.exportArray2String(cepstra, "%.4f", frames2recognize);
        FileUtilities.writeStringToFile(filename, str_cepstra, true);        
        return true;
    }
    //======================================================================================    
}

