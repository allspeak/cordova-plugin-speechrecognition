package com.allspeak.audioprocessing.vad;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Bundle;

import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.json.JSONException;
import org.json.JSONObject;


import com.allspeak.utility.Messaging;
import com.allspeak.audiocapture.CFGParams;
import com.allspeak.audiocapture.AudioInputCapture;
import com.allspeak.audioprocessing.vad.VADParams;
import com.allspeak.audioprocessing.vad.VAD;
import com.allspeak.audioprocessing.mfcc.MFCC;
import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import java.util.Arrays;

/*
it's a layer which call the VAD functions on a new thread
sends the following messages to Plugin Activity:

*/

public class VADHandlerThread extends HandlerThread implements Handler.Callback
{
    private static final String LOG_TAG                 = "VADHandlerThread";
    
  
    private Handler mInternalHandler                = null;   // manage internal messages
    private Handler mStatusCallback                 = null;   // destination handler of status messages
    private Handler mResultCallback                 = null;   // destination handler of data result
    private Handler mCommandCallback                = null;   // destination handler of output command
    private CallbackContext mWlCb                   = null;   // access to web layer
    
    private VAD vad                                 = null;
    private VADParams mVadParams                    = null;
    
    private MFCC mfcc                               = null;
    private MFCCParams mMfccParams                  = null;
    private Handler mMfccHandler                    = null;

    private CFGParams mCfgParams                    = null;
    
    private boolean bIsCapturing                    = false;
 
  
    //================================================================================================================
    public VADHandlerThread(String name)
    {
        super(name);
    }
    public VADHandlerThread(String name, int priority)
    {
        super(name, priority);
    }    
    //===============================================================================================
    public void setParams(VADParams vadParams, CFGParams cfgParams, MFCCParams mfccParams)
    {
        mVadParams      = vadParams;
        mCfgParams      = cfgParams;
        mMfccParams     = mfccParams;
        
        vad.setParams(vadParams, cfgParams);
        mfcc.setParams(mfccParams);
    }
    public void setParams(VADParams vadParams)
    {
        mVadParams      = vadParams;
        
        vad.setParams(mVadParams, mCfgParams);
    }
    public void setWlCb(CallbackContext wlcb)
    {
        mWlCb = wlcb;        
//        vad.setWlCb(wlcb);
    }
    public void setCallbacks(Handler cb)
    {
        mStatusCallback = cb;        
        mCommandCallback = cb;        
        mResultCallback = cb;        
        vad.setCallbacks(cb);
    }
    public void setCallbacks(Handler scb, Handler ccb, Handler rcb)
    {
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;        
        vad.setCallbacks(mStatusCallback, mCommandCallback, mResultCallback);
    }    
//--------------------------------------------------------------------------------------------------
    public void init(VADParams vadParams, CFGParams cfgParams, Handler cb)
    {
        mVadParams          = vadParams;
        mCfgParams          = cfgParams;  
        mStatusCallback     = cb;        
        mCommandCallback    = cb;        
        mResultCallback     = cb; 
        vad                 = new VAD(mVadParams, mCfgParams, cb);
        bIsCapturing        = true;
    }
    public void init(VADParams vadParams, CFGParams cfgParams, Handler cb, CallbackContext wlcb)
    {
        init(vadParams, cfgParams, cb);
        mWlCb = wlcb;
    }
    public void init(VADParams vadParams, CFGParams cfgParams, Handler scb, Handler ccb, Handler rcb)
    {
        mVadParams          = vadParams;
        mCfgParams          = cfgParams;  
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;   
        vad                 = new VAD(mVadParams, mCfgParams, scb, ccb, rcb);
        bIsCapturing        = true;
    }
    public void init(VADParams vadParams, CFGParams cfgParams, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcb)
    {
        init(vadParams, cfgParams, scb, ccb, rcb);
        mWlCb = wlcb;
    }
    
    public int getMaxSpeechLengthSamples()
    {
        return vad.getMaxSpeechLengthSamples();
    }
    //================================================================================================================    
    public void adjustVADThreshold(int newthreshold)
    {
        Bundle bundle          = new Bundle();
        bundle.putInt("newthreshold", newthreshold);
        
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.VAD_CMD_ADJUST_THRESHOLD;
        message.setData(bundle);
        mInternalHandler.sendMessage(message);   
    }
    //================================================================================================================    
    public void stopSpeechRecognition(CallbackContext wlcb)
    {
//        Message message = mInternalHandler.obtainMessage();
//        message.what    = ENUMS.CAPTURE_STATUS_STOPPED;
//        mInternalHandler.sendMessage(message);      
        
        vad.stopCapture();
        bIsCapturing = false;
    }
    
    private void onCaptureError(String error)
    {
        
    }
    // got from MFCC
    private void onMFCCError(String error)
    {
        
    }
    //================================================================================================================
    @Override
    public boolean handleMessage(Message msg) 
    {
        Bundle b = msg.getData();
        float[] data;
        switch((int)msg.what)
        {
            case ENUMS.CAPTURE_RESULT:
                if(bIsCapturing)
                {
                    data            = b.getFloatArray("data");
                    vad.OnNewData(data);

                    if(mCfgParams.nDataDest != ENUMS.CAPTURE_DATADEST_NONE)
                    {
                        try
                        {
                            JSONObject info = new JSONObject(); 
                            info.put("data_type", mCfgParams.nDataDest); 
                            info.put("type", ENUMS.CAPTURE_RESULT);  
                            
                            String decoded;
                            float rms, decibels, threshold;
                            switch(mCfgParams.nDataDest)
                            {
                                case ENUMS.CAPTURE_DATADEST_JS_RAW:
                                    decoded  = Arrays.toString(data);
                                    info.put("data", decoded);
                                    break;
                                    
                                case ENUMS.CAPTURE_DATADEST_JS_DB:
                                    rms       = AudioInputCapture.getAudioLevels(data);
                                    decibels  = AudioInputCapture.getDecibelFromAmplitude(rms);
                                    info.put("decibels", Float.toString(decibels));
                                    threshold = vad.getCurrentThreshold();
                                    info.put("threshold", Float.toString(threshold));
                                    break;

                                case ENUMS.CAPTURE_DATADEST_JS_RAWDB:
                                    decoded  = Arrays.toString(data);
                                    info.put("data", decoded);
                                    rms       = AudioInputCapture.getAudioLevels(data);
                                    decibels  = AudioInputCapture.getDecibelFromAmplitude(rms);
                                    info.put("decibels", decibels);
                                    break;                                    
                            }
                            Messaging.sendUpdate2Web(mWlCb, info, true);
                        }
                        catch(JSONException e)
                        {
                            Messaging.sendErrorString2Web(mWlCb, e.getMessage(), ERRORS.CAPTURE_ERROR, true);              
                        }                                    
                    }
                }
                break;

            case ENUMS.CAPTURE_STATUS_STOPPED:
                vad.stopCapture();
                bIsCapturing = false;
                break;

            case ERRORS.CAPTURE_ERROR:
                onCaptureError(b.getString("error"));
                break;

            case ENUMS.CAPTURE_STATUS_STARTED:
                break;

            case ERRORS.MFCC_ERROR:
                onMFCCError(b.getString("error"));     // is an error
                break;

            case ENUMS.VAD_CMD_ADJUST_THRESHOLD:
                int newthreshold = b.getInt("newthreshold");
                vad.adjustVADThreshold(newthreshold);
                break;
        }
        return true;
    }    

    @Override
    protected void onLooperPrepared() 
    {
        mInternalHandler = new Handler(getLooper(), this);
    }
    
    public Handler getHandlerLooper()
    {
        if(mInternalHandler == null)   Log.w(LOG_TAG, "VADHandlerThread mInternalHandler is NULL !!!!!!!!!!!!!!!!!");
        return mInternalHandler;
    }    
    //================================================================================================================
}