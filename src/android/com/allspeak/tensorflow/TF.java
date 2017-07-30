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


import com.allspeak.utility.Messaging;


import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import com.allspeak.utility.StringUtilities;
import com.allspeak.utility.FileUtilities;
import com.allspeak.utility.TrackPerformance;

import com.allspeak.utility.Messaging;

import com.allspeak.tensorflow.TensorFlowSpeechClassifier;
import com.allspeak.tensorflow.Classifier.Recognition;
import com.allspeak.tensorflow.Classifier;
import java.util.List;
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
    // to be used with Thread implementation
    public TF(TFParams params, Handler cb)
    {
        mStatusCallback     = cb;
        mCommandCallback    = cb;
        mResultCallback     = cb;
        setParams(params);       
    }
    
    public TF(TFParams params, Handler scb, Handler ccb, Handler rcb)
    {
        mStatusCallback     = scb;
        mCommandCallback    = ccb;
        mResultCallback     = rcb;
        setParams(params);       
    }
    
    // may also send results directly to Web Layer
    public TF(TFParams params, Handler handl, CallbackContext wlcallback)
    {
        this(params, handl);
        callbackContext     = wlcallback;
    }   
    // may also send results directly to Web Layer
    public TF(TFParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcallback)
    {
        this(params, scb, ccb, rcb);
        callbackContext     = wlcallback;
    }   
    //================================================================================================================
    
    public void setParams(TFParams params)
    {
        mTfParams = params;
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
    //=================================================================================================================
    // PUBLIC
    //=================================================================================================================
    public boolean loadModel()
    {
        try
        {
            String sModelFilePath = mTfParams.sModelFilePath.startsWith("file://") ? mTfParams.sModelFilePath.split("file://")[1] : mTfParams.sModelFilePath;
            String sLabelFilePath = mTfParams.sLabelFilePath.startsWith("file://") ? mTfParams.sLabelFilePath.split("file://")[1] : mTfParams.sLabelFilePath;
        
            boolean exists = true;
            String err = "";
            if(!FileUtilities.existFile(sModelFilePath))
            {
                exists = false;
                err += (" " + sModelFilePath);
            }
            if(!FileUtilities.existFile(sLabelFilePath))
            {            
                exists = false;
                err += (" " + sLabelFilePath);               
            }

            if(exists)
            {
                mClassifier = TensorFlowSpeechClassifier.create(
                        mTfParams.mAssetManager,
                        sModelFilePath,
                        sLabelFilePath,
                        mTfParams.nInputParams,
                        mTfParams.sInputNodeName,
                        mTfParams.sOutputNodeName);
            
                callbackContext.success(1);
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
   
    public void doRecognize(float[][] cepstra, int frames2recognize)    // cepstra = [?][72]
    {
        float[][] contextedCepstra = null;
        
        if(cepstra[0].length != mTfParams.nInputParams)
            contextedCepstra = getContextedFrames(cepstra, frames2recognize);  // [?][72] => [?][792]
        else
            contextedCepstra = cepstra;
        
        if(mClassifier != null)
        {
            List<Recognition> results = mClassifier.recognizeSpeech(contextedCepstra);
            
            try
            {
                JSONObject output       = new JSONObject();  
                output.put("type", ENUMS.TF_RESULT);

                JSONArray items         = new JSONArray();  
                for (Recognition result : results) 
                {
                    JSONObject record   = new JSONObject();
                    record.put("title", result.getTitle());
                    record.put("confidence", String.format("%.1f%%", result.getConfidence() * 100.0f)); 
                    items.put(record);
                } 
                output.put("items", items);
                Messaging.sendUpdate2Web(callbackContext, output, true); 
                
                switch((int)mTfParams.nDataDest)
                {
                    case ENUMS.TF_DATADEST_MODEL_FILE:
                    case ENUMS.TF_DATADEST_FILEONLY:
                        String outfile      = "AllSpeak/audiofiles/temp/cepstra.dat";
                        String outfile_ctx  = "AllSpeak/audiofiles/temp/ctx_cepstra.dat";
                        
                        String scores = StringUtilities.exportArray2String(cepstra, "%.4f", frames2recognize);
                        FileUtilities.writeStringToFile(outfile, scores, true);
                        
//                        FileUtilities.write2DArrayToFile(cepstra, frames2recognize, outfile, "%.4f", true);
//                        FileUtilities.write2DArrayToFile(contextedCepstra, frames2recognize, outfile_ctx, "%.4f", true);
                        break;
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();                  
                Log.e(LOG_TAG, e.getMessage(), e);
                Messaging.sendErrorString2Web(callbackContext, e.getMessage(), ERRORS.TF_ERROR, true);
            }            
        }
        else Messaging.sendErrorString2Web(callbackContext, "TF model not loaded", ERRORS.TF_ERROR_NOMODEL, true);
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
    
    private float[][] getContextedFrames(float[][] cepstra, int frames2recognize)    // cepstra = [?][72]
    {
        int ncepstra                    = cepstra[0].length;
        float[][] contextedCepstra      = new float[frames2recognize][mTfParams.nInputParams];
        int startId, endId, cnt,corr_pf = 0;
        
        int preFrames  = (int)Math.floor((double)(mTfParams.nContextFrames*1.0)/2);
        // append Context frames (from 72 => 792 = tfParams.nInputParam)
        for (int f=0; f<frames2recognize; f++)
        {
            if(f<preFrames)
            {
                startId = f - preFrames;
                endId   = f + preFrames;  
                cnt     = 0;
                for(int pf=startId; pf<endId; pf++)
                {
                    if(pf < 0) corr_pf = 0;
                    for(int ff=0; ff<ncepstra; ff++)
                    {
                        contextedCepstra[f][cnt] = cepstra[corr_pf][ff];                
                        cnt++;
                    }
                }
            }
            else if(f > (frames2recognize-preFrames-1))
            {
                startId = f - preFrames;
                endId   = f + preFrames;  
                cnt     = 0;
                for(int pf=startId; pf<endId; pf++)
                {
                    if(pf > (frames2recognize-1)) corr_pf = frames2recognize-1;
                    for(int ff=0; ff<ncepstra; ff++)
                    {
                        contextedCepstra[f][cnt] = cepstra[corr_pf][ff];                
                        cnt++;
                    }
                }                
            }
            else
            {
                startId = f - preFrames;
                endId   = f + preFrames;  
                cnt     = 0;
                for(int pf=startId; pf<endId; pf++)
                {
                    for(int ff=0; ff<ncepstra; ff++)
                    {
                        contextedCepstra[f][cnt] = cepstra[pf][ff];                
                        cnt++;
                    }
                }
            }
        }
        return contextedCepstra;
    } 
    
    

    //======================================================================================    
}

