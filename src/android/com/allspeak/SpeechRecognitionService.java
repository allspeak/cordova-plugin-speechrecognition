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
import android.os.Environment;

import android.os.Handler;
import android.os.Message;
import android.os.Bundle;

import android.util.Log;
import java.lang.ref.WeakReference;
import java.util.Arrays;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import com.allspeak.utility.Messaging;
import com.allspeak.utility.FileUtilities;
import com.allspeak.audioprocessing.mfcc.*;
import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.audioprocessing.mfcc.Framing;
import com.allspeak.audioprocessing.vad.*;
import com.allspeak.audiocapture.*;
import com.allspeak.audiocapture.AudioInputCapture;
import com.allspeak.audiocapture.CaptureParams;
import com.allspeak.tensorflow.TFParams;
import com.allspeak.tensorflow.TFHandlerThread;
import com.allspeak.audioprocessing.WavFile;

//==========================================================================================================================
public class SpeechRecognitionService extends Service 
{
    private final static String LOG_TAG                     = "SpeechRecognitionService";
    private final IBinder mBinder                           = new LocalBinder();

    private CallbackContext callbackContext                 = null;    
    private WakeLock cpuWeakLock                            = null;
    
    //-----------------------------------------------------------------------------------------------
    // CAPTURE
    private CaptureParams mCaptureParams                    = null;
    private final AudioCaptureHandler mAicServiceHandler    = new AudioCaptureHandler(this);
    private AudioInputCapture aicCapture                    = null;                                   // Capture instance

    private float[] faCapturedChunk                         = null;
    private boolean bIsCapturing                            = false;
    
    // what to do with captured data
    private int nCapturedDataDest                           = ENUMS.CAPTURE_DATADEST_NONE;
    private int nCapturedBlocks                             = 0;
    private int nCapturedBytes                              = 0;
    
    //-----------------------------------------------------------------------------------------------
    // PLAYBACK
    private AudioManager mAudioManager                  = null;
    
    //-----------------------------------------------------------------------------------------------
    // VAD
    private VADParams mVadParams                        = null;
    private final VADHandler mVadServiceHandler         = new VADHandler(this);
    private VADHandlerThread mVadHT                     = null;        

    //-----------------------------------------------------------------------------------------------
    // MFCC
    private MFCCParams mMfccParams                      = null;
    private final MFCCHandler mMfccServiceHandler       = new MFCCHandler(this);
    private MFCCHandlerThread mMfccHT                   = null;        
    private Handler mMfccHTLooper                       = null;     // looper of MFCCHandlerThread to send it messages 

    private boolean bIsCalculatingMFCC                  = false;              // do not calculate any mfcc score on startCatpure
    private int nMFCCProcessedFrames                    = 0;
    private int nMFCCProcessedBlocks                    = 0;
    private int nMFCCFrames2beProcessed                 = 0;
    private int nMFCCExpectedFrames                     = 0;        //updated by onCaptureData

    // what to do with MFCC data
    private int nMFCCDataDest                           = ENUMS.MFCC_DATADEST_NONE;        // send back to Web Layer or not  
    private boolean bTriggerAction                      = false;    // monitor nMFCCFrames2beProcessed zero-ing, when it goes to 0 and bTriggerAction=true => 
    
    //-----------------------------------------------------------------------------------------------
    // TF
    private final TFHandler mTfHandler                  = new TFHandler(this);
    private TFHandlerThread mTfHT                       = null;        
    //-----------------------------------------------------------------------------------------------
    
    //===============================================================================
    //binding
    public class LocalBinder extends Binder { SpeechRecognitionService getService() { return SpeechRecognitionService.this; } }

    @Override
    public IBinder onBind(Intent intent) { return mBinder; }
    
    @Override
    public void onDestroy()
    {
        closeService();
    }
    
    //===============================================================================
    //called after plugin got the service interface (ServiceConnection::onServiceConnected)
    public String initService()
    {
        try
        {
            // set PARTIAL_WAKE_LOCK to keep on using CPU resources also when the App is in background
            PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            cpuWeakLock     = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cpuWeakLock");              
            
            mAudioManager   = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE); 

            //start the VAD HandlerThread
            mVadHT          = new VADHandlerThread("VADHandlerThread", Process.THREAD_PRIORITY_MORE_FAVORABLE);
            mVadHT.start();   

            //start the MFCC HandlerThread
            mMfccHT         = new MFCCHandlerThread("MFCCHandlerThread", Process.THREAD_PRIORITY_MORE_FAVORABLE);
            mMfccHT.start();   

            //start the TF HandlerThread
            mTfHT           = new TFHandlerThread("TFHandlerThread", Process.THREAD_PRIORITY_MORE_FAVORABLE);
            mTfHT.start();   

            Log.d(LOG_TAG, "========> initService() <========="); 
            return "ok";
        }
        catch (Exception e) 
        {
            mVadHT  = null;
            mMfccHT = null;
            mTfHT   = null;
            e.printStackTrace();
            return e.toString();
        }            
    }
    //===============================================================================
    //called after plugin got the service interface (ServiceConnection::onServiceConnected)
    public void closeService()
    {
        try
        {
            unlockCPU();             
            
            if(mVadHT != null){
                mVadHT.quit();
                mVadHT.interrupt();
            }
            
            if(mMfccHT != null){
                mMfccHT.quit();
                mMfccHT.interrupt();
            }
            
            if(mTfHT != null){
                mTfHT.quit();
                mTfHT.interrupt();
            }
            Log.d(LOG_TAG, "========> unbindService() <========="); 
        }
        catch (Exception e) 
        {
            mVadHT             = null;
            mMfccHT            = null;
            mTfHT              = null;
            e.printStackTrace();
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
    public boolean startCapture(CaptureParams cfgParams, MFCCParams mfccParams, CallbackContext cb)
    {
        try 
        {
            callbackContext = cb;
            mCaptureParams  = cfgParams;
            
            // If I want to write the captured WAV, I create the buffer
            switch((int)mCaptureParams.nDataDest)
            {
                case ENUMS.CAPTURE_DATADEST_FILE:
                case ENUMS.CAPTURE_DATADEST_FILE_JS_RAW:
                case ENUMS.CAPTURE_DATADEST_FILE_JS_DB:
                case ENUMS.CAPTURE_DATADEST_FILE_JS_RAWDB:
                    int nMaxChunkLengthSamples  = VAD.getMaxSpeechLengthSamples(mCaptureParams.nChunkMaxLengthMS, mCaptureParams.nSampleRate, mCaptureParams.nBufferSize);                                                // 63*1024 = 64512
                    faCapturedChunk             = new float[nMaxChunkLengthSamples];             
                    break;
                default:
                    faCapturedChunk = null;
                    break;
            } 

            mMfccParams             = mfccParams; // it is alsway non-null if used by the js interface
            nMFCCDataDest           = mMfccParams.nDataDest;
            bIsCalculatingMFCC      = false;
            
            if(nMFCCDataDest > ENUMS.MFCC_DATADEST_NOCALC)
            {
                // I get the length (in samples) of the longest allowed speech chunk. 
                int nMaxSpeechLengthSample  = VAD.getMaxSpeechLengthSamples(mCaptureParams.nChunkMaxLengthMS, mCaptureParams.nSampleRate, mCaptureParams.nBufferSize);  

                // I pass to MFCC to let him allocate the proper cepstra buffer
                mMfccHT.init(mMfccParams, mMfccServiceHandler, mMfccServiceHandler, mMfccServiceHandler, nMaxSpeechLengthSample);       // MFCC send commands & results to TF, status here                
//                mMfccHT.initData();
                mMfccHTLooper       = mMfccHT.getHandlerLooper();   // get the mfcc looper   
                bIsCalculatingMFCC  = true;

                if(mMfccParams.sOutputPath != "" && mMfccParams.nDataDest >= ENUMS.MFCC_DATADEST_FILE)
                     FileUtilities.deleteExternalStorageFile(mMfccParams.sOutputPath + "_scores.dat");
            }            
            aicCapture                  = new AudioInputCapture(mCaptureParams, mAicServiceHandler, null, mAicServiceHandler);
            nCapturedDataDest           = mCaptureParams.nDataDest;
        }
        catch (Exception e) 
        {
            Messaging.sendErrorString2Web(callbackContext, e.toString(), ERRORS.SERVICE_INIT_CAPTURE, true);
            aicCapture      = null;
            return false;
        }

        try
        {
            resetDataCounters();
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
    
    public boolean startMicPlayback(CaptureParams cfgParams, CallbackContext cb)
    {
        try
        {
            callbackContext     = cb;
            mCaptureParams      = cfgParams;            
            aicCapture          = new AudioInputCapture(mCaptureParams, mAicServiceHandler, ENUMS.PLAYBACK_MODE); 
            aicCapture.setWlCb(callbackContext);
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
        nCapturedDataDest           = 0;     
    }    

    public void setPlayBackPercVol(int percvol, CallbackContext cb)
    {
        callbackContext             = cb;        
        aicCapture.setPlayBackPercVol(percvol);
    }    
        
    public boolean loadTFModel(TFParams tfParams, CallbackContext wlcb)
    {
        try 
        {        
            callbackContext             = wlcb;
            
            mTfHT.init(tfParams, mTfHandler, null, mTfHandler, callbackContext); 
            return mTfHT.loadModel();
        }
        catch (Exception e) 
        {
            onTFError(e.toString());
            return false;
        }            
    }
        
    public boolean startSpeechRecognition(CaptureParams cfgParams, VADParams vadParams, MFCCParams mfccParams, TFParams tfParams, CallbackContext wlcb)
    {
        try 
        {
            callbackContext             = wlcb;
            
            mCaptureParams              = cfgParams;
            mVadParams                  = vadParams;
            mMfccParams                 = mfccParams;
            
            Handler tfHTLooper          = mTfHT.getHandlerLooper();            // get the tf looper
            Handler vadHTLooper         = mVadHT.getHandlerLooper();           // get the mVadHT looper
            mMfccHTLooper               = mMfccHT.getHandlerLooper();          // get the mfcc looper   
            
            // MFCCParams params, Handler statuscb, Handler commandcb, Handler resultcb
            mVadHT.init(mVadParams, mCaptureParams, mVadServiceHandler, mMfccHTLooper, mVadServiceHandler, callbackContext);        // VAD send commands to MFCC, status here 

            // I get the length (first in samples, then in frames) of the max allowed speech chunk. 
            int nMaxSpeechLengthSample  = mVadHT.getMaxSpeechLengthSamples();  
            int nMaxSpeechLengthFrames  = Framing.getFrames(nMaxSpeechLengthSample, mMfccParams.nWindowLength, mMfccParams.nWindowDistance);
            int nParams                 = (mMfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? 3*mMfccParams.nNumberOfMFCCParameters : 3*mMfccParams.nNumberofFilters);

            // I pass to MFCC to let him allocate the proper cepstra buffer
            mMfccHT.init(mMfccParams, mMfccServiceHandler, tfHTLooper, tfHTLooper, nMaxSpeechLengthSample);       // MFCC send commands & results to TF, status here
            
            // A TF instance has been already created during the loadModel call. Thus, I only update the params in case something changed
            // TODO: I should check if the model file is the same
            mTfHT.setParams(tfParams);
            mTfHT.setWlCb(callbackContext);
            mTfHT.setCallbacks(mTfHandler, null, mTfHandler);
            mTfHT.setExtraParams(nMaxSpeechLengthFrames, nParams); // pass data to TFHT that allocates its proper internal cepstra buffer

            aicCapture                  = new AudioInputCapture(mCaptureParams, mAicServiceHandler, null, vadHTLooper); // CAPTURE send data to VAD, status here
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
        mVadHT.stopSpeechRecognition(callbackContext);
    }    
        
    public void getMFCC(MFCCParams mfccParams, String inputpathnoext, boolean overwrite, CallbackContext cb)
    {
        try 
        {
            callbackContext             = cb;   
            mMfccParams                 = mfccParams;           
            nMFCCDataDest               = mMfccParams.nDataDest;
            mMfccHT.init(mMfccParams, mMfccServiceHandler, callbackContext);       // MFCC send commands & results to TF, status here            
            mMfccHT.getMFCC(inputpathnoext, overwrite);   
        }
        catch (Exception e) 
        {
            onMFCCError(e.toString());
            callbackContext = null;
        }            
    }
    
    public void adjustVADThreshold(int newthreshold, CallbackContext cb)
    {
        callbackContext             = cb;   
        mVadHT.adjustVADThreshold(newthreshold);
    }
    
    public void recognizeCepstraFile(String cepstra_file_path, CallbackContext wlcb)
    {
        mTfHT.recognizeCepstraFile(cepstra_file_path, wlcb);
    }
            
    public boolean isCapturing() {
        return bIsCapturing;
    }    
    
    public boolean debugCall(JSONArray params) {
        return true;
    }    
  
    //==================================================================================================================================================================================
    //==================================================================================================================================================================================
    //==================================================================================================================================================================================
    // callback called by handlers 
    //==================================================================================================================================================================================
    //==================================================================================================================================================================================
    //==================================================================================================================================================================================
    
    //------------------------------------------------------------------------------------------------
    // CAPTURE
    //------------------------------------------------------------------------------------------------    
    // receive new audio data from AudioInputReceiver:
    // - start calculating MFCC
    // - return raw data to web layer if selected
    public void onCaptureData(Message msg)
    {
        Bundle b        = msg.getData(); 
        float[] data    = b.getFloatArray("data");  
        
        nCapturedBlocks++;  
        nCapturedBytes += data.length;

        if(bIsCalculatingMFCC)
        {   
            nMFCCExpectedFrames = Framing.getFrames(nCapturedBytes, mMfccParams.nWindowLength, mMfccParams.nWindowDistance) - mMfccParams.nDeltaWindow;
            Message newmsg      = Message.obtain(msg);  // get a copy of the original message
            newmsg.what         = ENUMS.CAPTURE_RESULT; // I rename the msg code in order to tell mfccHT that are captured data not to be sent to TF
            mMfccHTLooper.sendMessage(newmsg); 
        }
           
        // send raw to WEB and/or file ??
        if(mCaptureParams.nDataDest != ENUMS.CAPTURE_DATADEST_NONE)
        {
            try
            {
                // check FILE destination
                switch((int)mCaptureParams.nDataDest)
                {
                    case ENUMS.CAPTURE_DATADEST_FILE:
                    case ENUMS.CAPTURE_DATADEST_FILE_JS_RAW:
                    case ENUMS.CAPTURE_DATADEST_FILE_JS_DB:
                    case ENUMS.CAPTURE_DATADEST_FILE_JS_RAWDB:
                        System.arraycopy(data,0, faCapturedChunk, nCapturedBytes-data.length, data.length); // I already incremented nCapturedBytes, thus I append before
                        break;
                }                
                // check WEB destination
                float decibels, rms;
                String decoded;
                JSONObject info = new JSONObject(); 
                info.put("data_type", mCaptureParams.nDataDest); 
                info.put("type", ENUMS.CAPTURE_RESULT);  

                switch((int)mCaptureParams.nDataDest)
                {
                    case ENUMS.CAPTURE_DATADEST_JS_RAW:
                    case ENUMS.CAPTURE_DATADEST_FILE_JS_RAW:
                        decoded  = Arrays.toString(data);
                        info.put("data", decoded);
                        Messaging.sendUpdate2Web(callbackContext, info, true);
                        break;

                    case ENUMS.CAPTURE_DATADEST_JS_DB:
                    case ENUMS.CAPTURE_DATADEST_FILE_JS_DB:

                        rms       = AudioInputCapture.getAudioLevels(data);
                        decibels  = AudioInputCapture.getDecibelFromAmplitude(rms);
                        info.put("decibels", decibels);
                        Messaging.sendUpdate2Web(callbackContext, info, true);
                        break;

                    case ENUMS.CAPTURE_DATADEST_JS_RAWDB:
                    case ENUMS.CAPTURE_DATADEST_FILE_JS_RAWDB:
                        decoded  = Arrays.toString(data);
                        info.put("data", decoded);
                        rms       = AudioInputCapture.getAudioLevels(data);
                        decibels  = AudioInputCapture.getDecibelFromAmplitude(rms);
                        info.put("decibels", decibels);
                        Messaging.sendUpdate2Web(callbackContext, info, true);
                        break;
                }
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
    
    public void onCaptureStop(String totalReadBytes)
    {
        try
        {
            int ntotalReadBytes = Integer.parseInt(totalReadBytes);
            if(nCapturedBytes != ntotalReadBytes)    
                Log.w(LOG_TAG, "onCaptureStop: read by AIReceiver: " + totalReadBytes + "bytes, internal count " + Integer.toString(nCapturedBytes));            

            bTriggerAction  = true;

            // calculate how many frames must be still processed
            if(bIsCalculatingMFCC) 
            {
                nMFCCExpectedFrames = Framing.getFrames(ntotalReadBytes, mMfccParams.nWindowLength, mMfccParams.nWindowDistance) - mMfccParams.nDeltaWindow;
                nMFCCFrames2beProcessed = nMFCCExpectedFrames - nMFCCProcessedFrames;
            }     
            
            // do I have to save the speech wav ?
            switch((int)mCaptureParams.nDataDest)
            {
                case ENUMS.CAPTURE_DATADEST_FILE:
                case ENUMS.CAPTURE_DATADEST_FILE_JS_RAW:
                case ENUMS.CAPTURE_DATADEST_FILE_JS_DB:
                case ENUMS.CAPTURE_DATADEST_FILE_JS_RAWDB:
                    float[] recorded_data = new float[nCapturedBytes];
                    System.arraycopy(faCapturedChunk,0, recorded_data, 0, nCapturedBytes);

                    String path = Environment.getExternalStorageDirectory() + "/" + mCaptureParams.sOutputPath + ".wav";
//                    String cleanpath = mCaptureParams.sOutputPath.startsWith("file://") ? mCaptureParams.sOutputPath.split("file://")[1] : mCaptureParams.sOutputPath;
                    WavFile.createWavFile(path, mCaptureParams.nChannels, recorded_data, 16, (long)mCaptureParams.nSampleRate, true);                        
                    break;
            }
            sendStopEvent(totalReadBytes);
        }
        catch (Exception e)
        {
            sendStopEvent(totalReadBytes);
            onCaptureError(e.toString());
            e.printStackTrace();
        }
    }
    
    private void sendStopEvent(String totalReadBytes)
    {
        try
        {
            unlockCPU();
            bIsCapturing    = false;
            JSONObject info = new JSONObject();
            info.put("type", ENUMS.CAPTURE_STATUS_STOPPED);
            info.put("capturedblocks", nCapturedBlocks);        
            info.put("framesprocessed", nMFCCProcessedFrames);        
            info.put("frames2beprocessed", nMFCCFrames2beProcessed);        
            info.put("bytesread", totalReadBytes);        
            info.put("expectedframes", nMFCCExpectedFrames);        
            Messaging.sendUpdate2Web(callbackContext, info, true);    
        }
        catch (Exception e)
        {
            onCaptureError(e.toString());
            e.printStackTrace();
        }            
    }
    //------------------------------------------------------------------------------------------------
    // MFCC
    //------------------------------------------------------------------------------------------------
    // called by MFCC class when sending frames to be processed
    public void onMFCCStartProcessing(int nframes, int nmfccblocks)
    {
//        Log.d(LOG_TAG, "start to process: " + Integer.toString(nframes));
        nMFCCFrames2beProcessed += nframes;
        nMFCCProcessedBlocks    = nmfccblocks;
    }
    
    public void onMFCCData(float[][] cepstra, String source)
    {
        int nframes = cepstra.length;
        onMFCCProgress(nframes);        // here I increment nMFCCProcessedFrames

        // send MFCC data to Web Layer?
        JSONObject info = new JSONObject();
        try 
        {
            switch(nMFCCDataDest)
            {
                case ENUMS.MFCC_DATADEST_ALL:
                case ENUMS.MFCC_DATADEST_JSDATA:        //   "" + send progress(filename) + data(JSONArray) to WEB
                    info.put("type", ENUMS.MFCC_RESULT);
                    JSONArray data = new JSONArray(cepstra);
                    info.put("data", data);
                    info.put("progress", source);
                    Messaging.sendUpdate2Web(callbackContext, info, true);
                    break;

                case ENUMS.MFCC_DATADEST_JSPROGRESS:    //   "" + send progress(filename) to WEB
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
        nMFCCFrames2beProcessed = nMFCCExpectedFrames - nMFCCProcessedFrames;
        
        Log.d(LOG_TAG, "onMFCCProgress : expected frames : " + nMFCCExpectedFrames  + ", processed frames : " + Integer.toString(nMFCCProcessedFrames) +  ", still to be processed: " + Integer.toString(nMFCCFrames2beProcessed));
        Log.d(LOG_TAG, "onMFCCProgress : captured blocks: " + Integer.toString(nCapturedBlocks) + ", mfccprocessed blocks: " + Integer.toString(nMFCCProcessedBlocks));
        Log.d(LOG_TAG, "------");
        
        
        //check if this is the last cepstra packet (bTriggerAction=true has been set by onStopCapture)
        if(bTriggerAction && nMFCCExpectedFrames == nMFCCProcessedFrames)
        {
            Log.d(LOG_TAG, "@@@@@@@@@@@@@@@ F I N I S H E D   M F C C   C A L C @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            bTriggerAction      = false;
            bIsCalculatingMFCC  = false;
            Messaging.sendDataToHandler(mMfccHTLooper, ENUMS.MFCC_CMD_FINALIZEDATA);
            resetDataCounters();
        }          
    }
    //------------------------------------------------------------------------------------------------
    // ON ERRORS
    //------------------------------------------------------------------------------------------------
    public void onError(String message, int error_code)
    {
        try
        {
            JSONObject info = new JSONObject();
            info.put("type", error_code);
            info.put("message", message);        
            Messaging.sendError2Web(callbackContext, info, true);
        }
        catch (JSONException e){e.printStackTrace();}        
    }
    
    // presently redundant methods
    // if called by AudioInputReceiver....the thread already stopped/suspended itself
    public void onCaptureError(String message)
    {
        stopCapture(callbackContext);
        bIsCalculatingMFCC = false;
        onError(message, ERRORS.CAPTURE_ERROR);
    }    
    
    public void onMFCCError(String message)
    {
        onError(message, ERRORS.MFCC_ERROR);        
    }    
    
    public void onVADError(String message)
    {
        onError(message, ERRORS.VAD_ERROR);        
    }    
    
    public void onTFError(String message)
    {
        onError(message, ERRORS.TF_ERROR);        
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
                            service.onCaptureData(msg);
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
                            // da MFCC::exportData
                            service.onMFCCProgress(Integer.parseInt(b.getString("progress")));
                            break;
                            
                        case ENUMS.MFCC_RESULT:
                            
                            String source       = b.getString("source");
                            int nframes         = b.getInt("nframes");
                            int nparams         = b.getInt("nparams");
                            float[][] cepstra   = Messaging.deFlattenArray(b.getFloatArray("data"), nframes, nparams);

                            service.onMFCCData(cepstra, source);                            
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
                            // da MFCCHandlerThread::handleMessage::ENUMS.MFCC_CMD_GETQDATA or ENUMS.CAPTURE_RESULT
                            service.onMFCCStartProcessing(msg.arg1, msg.arg2);  //  "nframes" & "nops"
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
                    JSONObject info = new JSONObject();
                    Bundle b        = msg.getData();
                    switch((int)msg.what) //get message type
                    {
                        case ENUMS.TF_STATUS_PROCESS_STARTED:
                            break;                  
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
    private void resetDataCounters()
    {
        nCapturedBlocks         = 0;
        nCapturedBytes          = 0;
        nMFCCProcessedBlocks    = 0;    
        nMFCCProcessedFrames    = 0;
        nMFCCFrames2beProcessed = 0;        
    }
    
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
