package com.allspeak;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import android.util.Log;

import android.content.Context;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;

import android.os.Handler;
import android.os.Message;
import android.os.Bundle;

import android.util.Log;
import java.lang.ref.WeakReference;
import java.util.Arrays;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import com.allspeak.utility.Messaging;
import com.allspeak.audioprocessing.mfcc.*;
import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.audioprocessing.vad.*;
import com.allspeak.audiocapture.*;
import com.allspeak.audiocapture.AudioInputCapture;
import com.allspeak.audiocapture.CFGParams;
import com.allspeak.tensorflow.TFParams;
import com.allspeak.tensorflow.TFHandlerThread;
//==========================================================================================================================
public class SpeechRecognitionService extends Service 
{
    private final static String LOG_TAG         = "SpeechRecognitionService";
    private final IBinder mBinder               = new LocalBinder();

    private CallbackContext callbackContext     = null;    
    private WakeLock cpuWeakLock                = null;
    
    // CAPTURE
    private CFGParams mCfgParams                = null;
    private final AudioCaptureHandler aicHandler= new AudioCaptureHandler(this);
    private AudioInputCapture aicCapture        = null;                                   // Capture instance

    private boolean bIsCapturing                = false;

    // what to do with captured data
    private int nCapturedDataDest               = ENUMS.CAPTURE_DATADEST_NONE;
    private int nCapturedBlocks                 = 0;
    private int nCapturedBytes                  = 0;
    
    //-----------------------------------------------------------------------------------------------
    // PLAYBACK
    private AudioManager mAudioManager          = null;
    
    //-----------------------------------------------------------------------------------------------
    // VAD
    private VADParams mVadParams                = null;
    private final VADHandler mVadHandler        = new VADHandler(this);
    private VADHandlerThread vad                = null;        
//    private boolean    bStartVAD                   = false;              // do not start VAD on startCapture

    private int nDetectedChunks                 = 0;    
    //-----------------------------------------------------------------------------------------------
    // MFCC
    private MFCCParams mMfccParams              = null;
    private final MFCCHandler mMfccHandler      = new MFCCHandler(this);
    private MFCCHandlerThread mfcc              = null;        

//    private boolean bIsCalculatingMFCC          = false;              // do not calculate any mfcc score on startCatpure
    private int nMFCCProcessedFrames            = 0;
    private int nMFCCFrames2beProcessed         = 0;

    // what to do with MFCC data
    private int nMFCCDataDest                   = MFCCParams.DATADEST_NONE;        // send back to Web Layer or not  
    private boolean bTriggerAction              = false;    // monitor nMFCCFrames2beProcessed zero-ing, when it goes to 0 and bTriggerAction=true => 
    
    //-----------------------------------------------------------------------------------------------
    // TF
    private TFParams mTfParams                  = null;
    private final TFHandler mTfHandler          = new TFHandler(this);
    private TFHandlerThread tf                  = null;        
    //-----------------------------------------------------------------------------------------------
    
    //===============================================================================
    //binding
    public class LocalBinder extends Binder { SpeechRecognitionService getService() { return SpeechRecognitionService.this; } }

    @Override
    public IBinder onBind(Intent intent) { return mBinder; }
    
    //===============================================================================
    //called after plugin got the service interface (ServiceConnection::onServiceConnected)
    public String initService()
    {
        try
        {
            // set PARTIAL_WAKE_LOCK to keep on using CPU resources also when the App is in background
            PowerManager pm         = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            cpuWeakLock             = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cpuWeakLock");              
            
            mAudioManager           = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE); 

            //start the VAD HandlerThread
            vad                     = new VADHandlerThread("VADHandlerThread", Process.THREAD_PRIORITY_MORE_FAVORABLE);
            vad.start();   

            //start the MFCC HandlerThread
            mfcc                    = new MFCCHandlerThread("MFCCHandlerThread", Process.THREAD_PRIORITY_MORE_FAVORABLE);
            mfcc.start();   

            //start the TF HandlerThread
            tf                      = new TFHandlerThread("TFHandlerThread", Process.THREAD_PRIORITY_MORE_FAVORABLE);
            tf.start();   

            Log.d(LOG_TAG, "========> initService() <========="); 
            return "ok";
        }
        catch (Exception e) 
        {
            vad             = null;
            mfcc            = null;
            tf              = null;
            e.printStackTrace();
            return e.toString();
        }            
    }
    
    //===============================================================================
    //===============================================================================
    // PUBLIC SERVICE INTERFACE
    //
    //  startCapture
    //  startMicPlayback
    //  startSpeechRecognition
    //  stopCapture
    //  setPlayBackPercVol
    //  getMFCC
    //  isCapturing
    //===============================================================================
    //===============================================================================
    public boolean startCapture(CFGParams cfgParams, MFCCParams mfccParams, CallbackContext cb)
    {
        try 
        {
            callbackContext             = cb;
            mCfgParams                  = cfgParams;
            Handler mMfccHandlerThread   = null;
            if(mfccParams != null)
            {
                mMfccParams             = mfccParams;
                nMFCCDataDest           = mMfccParams.nDataDest;

                mfcc.setParams(mMfccParams);
                mfcc.setCallbacks(mMfccHandler);
                mMfccHandlerThread       = mfcc.getHandlerLooper();                  
            }            
            aicCapture                  = new AudioInputCapture(mCfgParams, aicHandler, null, mMfccHandlerThread);    // if(mfccParams != null) : CAPTURE => MFCC              
//            bIsCalculatingMFCC          = cfgParams.bStartMFCC;
            nCapturedDataDest           = cfgParams.nDataDest;

        }
        catch (Exception e) 
        {
            Messaging.sendErrorString2Web(callbackContext, e.toString(), ERRORS.SERVICE_INIT_CAPTURE, true);
            aicCapture      = null;
            return false;
        }

        try
        {
            nCapturedBlocks         = 0;
            nCapturedBytes          = 0;
            nMFCCProcessedFrames    = 0;
            nMFCCFrames2beProcessed = 0;
            bTriggerAction          = false;            
            aicCapture.start();
            return true;
        }
        catch (Exception e) 
        {
            aicCapture.stop();  // onCaptureStop calls unlockCPU and set bIsCapturing=false
            onCaptureError(e.getMessage());
            return false;
        }        
    }
    
    public boolean startMicPlayback(CFGParams cfgParams, CallbackContext cb)
    {
        try
        {
            callbackContext     = cb;
            mCfgParams          = cfgParams;            
            aicCapture          = new AudioInputCapture(mCfgParams, aicHandler, ENUMS.PLAYBACK_MODE);                  
            aicCapture.start();
            return true;
        }
        catch (Exception e) 
        {
            aicCapture.stop();
            aicCapture = null;
            onCaptureError(e.getMessage());
            return false;
        }        
    }

    public void stopCapture(CallbackContext cb)
    {
        callbackContext             = cb;        
        aicCapture.stop();
        aicCapture = null;
//        bIsCalculatingMFCC          = false;
        nCapturedDataDest           = 0;     
    }    

    public void setPlayBackPercVol(int percvol, CallbackContext cb)
    {
        callbackContext             = cb;        
        aicCapture.setPlayBackPercVol(percvol);
    }    
        
    public boolean startSpeechRecognition(CFGParams cfgParams, VADParams vadParams, MFCCParams mfccParams, TFParams tfParams, CallbackContext wlcb)
    {
        try 
        {
            callbackContext             = wlcb;
            
            mCfgParams                  = cfgParams;
            mVadParams                  = vadParams;
            mMfccParams                 = mfccParams;
            mTfParams                   = tfParams;
            
            Handler mTfHandlerThread    = tf.getHandlerLooper();            // get the tf looper
            Handler mVadHandlerThread   = vad.getHandlerLooper();           // get the vad looper
            Handler mMfccHandlerThread  = mfcc.getHandlerLooper();          // get the mfcc looper   
            
            // MFCCParams params, Handler statuscb, Handler commandcb, Handler resultcb
            mfcc.init(mMfccParams, mMfccHandler, mTfHandlerThread, mTfHandlerThread);       // MFCC send commands & data to TF , status here
            vad.init(mVadParams, mCfgParams, mVadHandler, mMfccHandlerThread, mVadHandler, callbackContext);        // VAD send commands to MFCC, status here 
            tf.init(mTfParams, mTfHandler, null, mTfHandler); 
            
            aicCapture                  = new AudioInputCapture(mCfgParams, aicHandler, null, mVadHandlerThread); // CAPTURE send data to VAD, status here
            aicCapture.start();
            return true;
        }
        catch (Exception e) 
        {
            aicCapture.stop();  // onCaptureStop calls unlockCPU and set bIsCapturing=false
            onCaptureError(e.toString());
            aicCapture = null;
            return false;
        }
    }    
    
    // i decided to stop recognition immediately, not wait for AudioCapture callback
    public void stopSpeechRecognition(CallbackContext cb)
    {
        callbackContext = cb;      
        aicCapture.stop();
        vad.stopSpeechRecognition(callbackContext);
    }    
        
    public void getMFCC(MFCCParams mfccParams, String inputpathnoext, CallbackContext cb)
    {
        try 
        {
            callbackContext             = cb;   
            mMfccParams                 = mfccParams;           
            nMFCCDataDest               = mMfccParams.nDataDest;
            mfcc.setParams(mMfccParams);
            mfcc.setWlCb(callbackContext);                

            mfcc.getMFCC(inputpathnoext);   
        }
        catch (Exception e) 
        {
            onMFCCError(e.toString());
            callbackContext = null;
        }            
    }
    
    public boolean isCapturing() {
        return bIsCapturing;
    }    
    
    //=========================================================================================
    // callback called by handlers 
    //=========================================================================================
    
    //------------------------------------------------------------------------------------------------
    // CAPTURE
    //------------------------------------------------------------------------------------------------    
    // receive new audio data from AudioInputReceiver:
    // - start calculating MFCC
    // - return raw data to web layer if selected
    public void onCaptureData(float[] data)
    {
        nCapturedBlocks++;  
        nCapturedBytes += data.length;

//        Log.d(LOG_TAG, "new raw data arrived in MainPlugin Activity: " + Integer.toString(nCapturedBlocks));
        
//        if(bIsCalculatingMFCC) mfcc.getQueueMFCC(data); // calculate MFCC/MFFILTERS ??
           
        // send raw to WEB ??
        if(mCfgParams.nDataDest != ENUMS.CAPTURE_DATADEST_NONE)
        {
            try
            {
                JSONObject info = new JSONObject(); 
                info.put("data_type", mCfgParams.nDataDest); 
                info.put("type", ENUMS.CAPTURE_RESULT);  

                switch((int)mCfgParams.nDataDest)
                {
                    case ENUMS.CAPTURE_DATADEST_JS_RAW:
                        String decoded  = Arrays.toString(data);
                        info.put("data", decoded);
                        break;

                    case ENUMS.CAPTURE_DATADEST_JS_DB:

                        float rms       = AudioInputCapture.getAudioLevels(data);
                        float decibels  = AudioInputCapture.getDecibelFromAmplitude(rms);
                        info.put("data", decibels);
                        break;
                }
                Messaging.sendUpdate2Web(callbackContext, info, true);
            }
            catch(JSONException e)
            {
                e.printStackTrace();                  
                Log.e(LOG_TAG, e.getMessage(), e);
                onCaptureError(e.getMessage());            
            }                         
                        
        }
    }
    
    public void onCaptureStart()
    {
        try
        {
            lockCPU();
            bIsCapturing    = true;
            JSONObject info = new JSONObject();
            info.put("type", ENUMS.CAPTURE_STATUS_STARTED);
            Messaging.sendUpdate2Web(callbackContext, info, true);    
        }
        catch (JSONException e){e.printStackTrace();}            
    }
    
    public void onCaptureStop(String bytesread)
    {
//        Log.d(LOG_TAG, "StopCapture: read " + bytesread + "bytes, captured " + Integer.toString(nCapturedBlocks) + " blocks, processed " + Integer.toString(nMFCCFrames2beProcessed) + " frames");            
        bTriggerAction  = true;
        try
        {
            unlockCPU();
            bIsCapturing    = false;
            JSONObject info = new JSONObject();
            info.put("type", ENUMS.CAPTURE_STATUS_STOPPED);
            info.put("datacaptured", nCapturedBlocks);        
            info.put("dataprocessed", nMFCCFrames2beProcessed);        
            info.put("bytesread", bytesread);        
            Messaging.sendUpdate2Web(callbackContext, info, true);
        }
        catch (JSONException e){e.printStackTrace();}
    }
    
    //------------------------------------------------------------------------------------------------
    // MFCC
    //------------------------------------------------------------------------------------------------
    // called by MFCC class when sending frames to be processed
    public void onMFCCStartProcessing(int nframes)
    {
        Log.d(LOG_TAG, "start to process: " + Integer.toString(nframes));
        nMFCCFrames2beProcessed += nframes;
    }
    
    public void onMFCCData(float[][] params, float[][] params_1st, float[][] params_2nd, String source)
    {
        onMFCCProgress(params.length);        
        
        // send raw data to Web Layer?
        JSONObject info = new JSONObject();
        try 
        {
            switch(nMFCCDataDest)
            {
                case MFCCParams.DATADEST_ALL:
                case MFCCParams.DATADEST_JSDATA:        //   "" + send progress(filename) + data(JSONArray) to WEB
                    info.put("type", ENUMS.MFCC_RESULT);
                    JSONArray data = new JSONArray(params);
                    info.put("data", data);
                    info.put("progress", source);
                    Messaging.sendUpdate2Web(callbackContext, info, true);
                    break;

                case MFCCParams.DATADEST_JSPROGRESS:    //   "" + send progress(filename) to WEB
                    info.put("type", ENUMS.MFCC_STATUS_PROGRESS_DATA);
                    info.put("progress", source);
                    Messaging.sendUpdate2Web(callbackContext, info, true);
                    break;                   
            }
        }
        catch (JSONException e) 
        {
            e.printStackTrace();                  
            Log.e(LOG_TAG, e.getMessage(), e);
            onMFCCError(e.toString());
        }
     }   
    
    public void onMFCCProgress(int frames)
    {
        nMFCCProcessedFrames    += frames;
        nMFCCFrames2beProcessed -= frames;
        Log.d(LOG_TAG, "processed frames : " + Integer.toString(nMFCCProcessedFrames) +  ", still to be processed: " + Integer.toString(nMFCCFrames2beProcessed));
        
        if(bTriggerAction && nMFCCFrames2beProcessed == 0)
        {
            Log.d(LOG_TAG, "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            bTriggerAction = false;
        }
    }
    
    //------------------------------------------------------------------------------------------------
    // ON ERRORS
    //------------------------------------------------------------------------------------------------
    // if called by AudioInputReceiver....the thread already stopped/suspended itself
    public void onCaptureError(String message)
    {
        try
        {
            JSONObject info = new JSONObject();
            info.put("type", ERRORS.CAPTURE_ERROR);
            info.put("message", message);        
            Messaging.sendError2Web(callbackContext, info, true);
        }
        catch (JSONException e){e.printStackTrace();}
    }    
    
    public void onMFCCError(String message)
    {
        try
        {
            JSONObject info = new JSONObject();
            info.put("type", ERRORS.MFCC_ERROR);
            info.put("message", message);        
            Messaging.sendError2Web(callbackContext, info, true);
        }
        catch (JSONException e){e.printStackTrace();}
    }    
    
    public void onVADError(String message)
    {
        try
        {
            JSONObject info = new JSONObject();
            info.put("type", ERRORS.VAD_ERROR);
            info.put("message", message);        
            Messaging.sendError2Web(callbackContext, info, true);
        }
        catch (JSONException e){e.printStackTrace();}
    }    
    
    //=================================================================================================
    // HANDLERS (from THREADS to this SERVICE)   receive input from other Threads
    //=================================================================================================
    private static class AudioCaptureHandler extends Handler 
    {
        private final WeakReference<SpeechRecognitionService> mService;

        public AudioCaptureHandler(SpeechRecognitionService service) {
            mService = new WeakReference<SpeechRecognitionService>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            SpeechRecognitionService service = mService.get();
            if (service != null) 
            {
                try 
                {
                    Bundle b = msg.getData();   //get message type
                    switch((int)msg.what) //get message type
                    {
                        case ENUMS.CAPTURE_RESULT:
                            service.onCaptureData(b.getFloatArray("data"));
                            break;
                        
                        case ENUMS.CAPTURE_STATUS_STOPPED:
                            service.onCaptureStop(b.getString("stop"));    
                            break;
                        
                        case ERRORS.CAPTURE_ERROR:
                            service.onCaptureError(b.getString("error"));
                            break;

                        case ENUMS.CAPTURE_STATUS_STARTED:
                            service.onCaptureStart();
                            break;
                    }                    
                }
                catch (Exception e) 
                {
                    e.printStackTrace();                      
                    Log.e(LOG_TAG, e.getMessage(), e);
                    service.onCaptureError(e.toString());
                }
            }
        }
    }

    private static class MFCCHandler extends Handler 
    {
        private final WeakReference<SpeechRecognitionService> mService;

        public MFCCHandler(SpeechRecognitionService service) { mService = new WeakReference<SpeechRecognitionService>(service);  }

        // progress_file & progress_folder are directly sent to WEB
        // error & data are sent to activity methods
        public void handleMessage(Message msg) 
        {
            SpeechRecognitionService service = mService.get();
            if (service != null) 
            {
                // expected messeges: error, progress_file, progress_folder => web
                //                    data  => plugin onMFCCData
                try 
                {
                    JSONObject info = new JSONObject();
                    Bundle b        = msg.getData();
                    switch((int)msg.what) //get message type
                    {
                        case ENUMS.MFCC_STATUS_PROGRESS_DATA:
                            
                            service.onMFCCProgress(Integer.parseInt(b.getString("progress")));
                            break;
                            
                        case ENUMS.MFCC_RESULT:
                            
                            String source       = b.getString("source");
                            int nframes         = b.getInt("nframes");
                            int nparams         = b.getInt("nparams");
                            float[][] res       = Messaging.deFlattenArray(b.getFloatArray("data"), nframes, nparams);
                            float[][] res_1st   = Messaging.deFlattenArray(b.getFloatArray("data_1st"), nframes, nparams);
                            float[][] res_2nd   = Messaging.deFlattenArray(b.getFloatArray("data_2nd"), nframes, nparams);
                            service.onMFCCData(res, res_1st, res_2nd, source);                            
                            break;
                            
                        case ENUMS.MFCC_STATUS_PROGRESS_FILE:
                            
                            info.put("type", ENUMS.MFCC_STATUS_PROGRESS_FILE);
                            info.put("progress", b.getString("progress_file"));
                            Messaging.sendUpdate2Web(service.callbackContext, info, true);                            
                            break;
                            
                        case ENUMS.MFCC_STATUS_PROGRESS_FOLDER:
                            
                            info.put("type", ENUMS.MFCC_STATUS_PROGRESS_FOLDER);
                            info.put("progress", b.getString("progress_folder"));
                            Messaging.sendUpdate2Web(service.callbackContext, info, true);                        
                            break;
                            
                        case ERRORS.MFCC_ERROR:
                            
                            service.onMFCCError(b.getString("error"));     // is an error
                            break;
                            
                        case ENUMS.MFCC_STATUS_PROCESS_STARTED:
                            
                            service.onMFCCStartProcessing((int)msg.obj);                            
                            break;
                    }
                }
                catch (JSONException e) 
                {
                    e.printStackTrace();                    
                    Log.e(LOG_TAG, e.getMessage(), e);
                    service.onMFCCError(e.toString());
                }
            }
        }
    }
    
    private static class VADHandler extends Handler 
    {
        private final WeakReference<SpeechRecognitionService> mService;

        public VADHandler(SpeechRecognitionService service) {
            mService = new WeakReference<SpeechRecognitionService>(service);
        }

        public void handleMessage(Message msg) 
        {
            SpeechRecognitionService service = mService.get();
            if (service != null) 
            {
                try 
                {
                    JSONObject info = new JSONObject();
                    info.put("type", (int)msg.what);
                    Messaging.sendUpdate2Web(service.callbackContext, info, true);                    
                }
                catch (Exception e) {
                    e.printStackTrace();                    
                    Log.e(LOG_TAG, e.getMessage(), e);
                }
            }
        }
    }    
    
    private static class TFHandler extends Handler 
    {
        private final WeakReference<SpeechRecognitionService> mService;

        public TFHandler(SpeechRecognitionService service) {
            mService = new WeakReference<SpeechRecognitionService>(service);
        }

        public void handleMessage(Message msg) 
        {
            SpeechRecognitionService service = mService.get();
            if (service != null) 
            {
                try 
                {
                    //get message type
                    Bundle b = msg.getData();
                    switch((int)msg.what) //get message type
                    {
                    }                    

                }
                catch (Exception e) {

                }
            }
        }
    }    
    
    //=================================================================================================
    // ACCESSORY FUNCTIONS
    //=================================================================================================
    private void lockCPU()
    {
        if(!cpuWeakLock.isHeld())    cpuWeakLock.acquire();       
    }
    
    private void unlockCPU()
    {
        if(cpuWeakLock.isHeld())    cpuWeakLock.release();
    }
    //=================================================================================================
}
