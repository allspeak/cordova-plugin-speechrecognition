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
import org.json.JSONException;

import java.io.File;
import java.io.FileWriter;
import java.lang.System;

import android.os.Environment;
import android.util.Log;
import java.io.FilenameFilter;

import android.os.ResultReceiver;

import com.allspeak.utility.Messaging;


import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import com.allspeak.audioprocessing.WavFile;
import com.allspeak.utility.StringUtility;
import com.allspeak.utility.TrackPerformance;



/*
it sends the following messages to Plugin Activity:
- data
- progress_file
- progress_folder
- error

*/

public class TF  
{
    private static final String TAG             = "TF";
    
  
    private TFParams mTfParams               = null;                                   // MFCC parameters
    
    // properties to send results back to Plugin Main
    private Handler mStatusCallback             = null;     // Thread
    private Handler mCommandCallback            = null;     // Thread
    private Handler mResultCallback             = null;     // Thread
    private CallbackContext callbackContext     = null;

   
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
    public void doRecognize(float[][] cepstra, int frames2recognize)    // cepstra = [?][72]
    {
        float[][] contextedCepstra = getContextedFrames(cepstra, frames2recognize);
        
        // inform service that a TF call has been submitted
        Messaging.sendMessageToHandler(mStatusCallback, ENUMS.TF_STATUS_PROCESS_STARTED);
    }
    //=================================================================================================================
    // PRIVATE
    //=================================================================================================================
    public float[][] getContextedFrames(float[][] cepstra, int frames2recognize)    // cepstra = [?][72]
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

