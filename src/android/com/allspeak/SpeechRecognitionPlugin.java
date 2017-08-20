package com.allspeak;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;


import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import android.util.Log;
import android.content.pm.PackageManager;
import android.Manifest;

import com.allspeak.ERRORS;
import com.allspeak.ENUMS;
import com.allspeak.SpeechRecognitionService;
import com.allspeak.SpeechRecognitionService.LocalBinder;
import com.allspeak.utility.Messaging;
import com.allspeak.utility.AudioDevicesManager;
import com.allspeak.tensorflow.TFParams;
import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.audioprocessing.vad.VADParams;
import com.allspeak.audiocapture.CaptureParams;
import com.allspeak.audiocapture.*;
import java.util.ArrayList;
import java.util.Arrays;

//import com.example.HelloCJni;


import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.content.BroadcastReceiver;
import android.media.AudioDeviceInfo;

public class SpeechRecognitionPlugin extends CordovaPlugin
{
    private static final String LOG_TAG         = "SpeechRecognitionPlugin";
    
    private Context mContext                    = null;
    private AudioManager mAudioManager          = null;
    
    //cordova stuffs
    private CallbackContext callbackContext     = null;
    private CordovaInterface cordovaInterface   = null;
    
    //record permissions
    public static String[]  permissions         = { Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE };
    public static int       RECORD_AUDIO        = 0;
    public static final int PERMISSION_DENIED_ERROR = 20;    
    boolean isCapturingAllowed                  = false;
    
    AudioDevicesManager mAudioDevicesManager    = null;
    
    boolean isCapturing                         = false;
    boolean bUseHeadSet                         = false;
    boolean isHeadSetConnected                         = false;
    //-----------------------------------------------------------------------------------------------
    
    private SpeechRecognitionService mService   = null;
    private boolean mBound                      = false;
    
    private CaptureParams mCfgParams                = null;
    private MFCCParams mMfccParams              = null;
    private VADParams mVadParams                = null;
    private TFParams mTfParams                  = null;

    //======================================================================================================================
    public void initialize(CordovaInterface cordova, CordovaWebView webView) 
    {
        super.initialize(cordova, webView);
        Log.d(LOG_TAG, "Initializing SpeechRecognitionPlugin");

        //get plugin context
        cordovaInterface        = cordova;
        mContext                = cordovaInterface.getActivity();
        
        mAudioManager           = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        try
        {
//            initBTConnection();
            mAudioDevicesManager = new AudioDevicesManager(mContext, mAudioManager);
            boolean conn = bindService();
            
//            int jniOutput = HelloCJni.calculate(2,5);
//            int res = 2 + jniOutput;
//            promptForDangerousPermissions();

        }
        catch(Exception e)
        {
            e.printStackTrace();                    
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        
    }
    //======================================================================================================================
    //get Service interface    
    
    private boolean bindService()
    {
        // bind service
        Intent bindIntent   = new Intent(mContext, SpeechRecognitionService.class);  // Binding.this instead of mContext in the official sample.
        boolean res         = mContext.bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);        
        if(!res)            mContext.unbindService(mConnection);
        return res;
    }
    
    private ServiceConnection mConnection = new ServiceConnection() 
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) 
        {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder  = (LocalBinder) service;
            mService            = binder.getService();
            String res          = mService.initService();  // if(res != "ok") dont' know how to inform web layer
            mBound              = true;
            Log.d(LOG_TAG, "========> Service Bounded <=========");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) 
        {
            mService    = null;
            mBound      = false;
        }
    };    
    
    /**
     * @param action
     *          getAudioDevices
     *          startCapture
     *          startMicPlayback
     *          setPlayBackPercVol
     *          stopCapture
     *          loadTFModel
     *          startSpeechRecognition
     *          stopSpeechRecognition
     *          adjustVADThreshold
     *          getMFCC
     * 
     * @param args
     * @param _callbackContext
     * @return
     * @throws JSONException 
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext _callbackContext) throws JSONException 
    {
        callbackContext = _callbackContext;
        
        if(mService == null)
        {
            Messaging.sendErrorString2Web(callbackContext, "Service not binded. FATAL ERROR", ERRORS.SERVICE_INIT, true);
            return true;
        }
        
        if (action.equals("getAudioDevices")) 
        {
            try
            {
                mAudioDevicesManager.setCallback(callbackContext);
                JSONObject audiodevicesinfo = mAudioDevicesManager.exportAudioDevicesJSON();
                Messaging.sendUpdate2Web(callbackContext, audiodevicesinfo, true);
                return true;
            }
            catch (Exception e)
            {
                Messaging.sendErrorString2Web(callbackContext, e.toString(), ERRORS.AUDIODEVICES_RETRIEVE, true);
                callbackContext = null;
                return true;                        
            }
        }
        else if (action.equals("startCapture")) 
        {
            try 
            {
                if(mService.isCapturing())
                {
                    Messaging.sendErrorString2Web(callbackContext, "SpeechRecognitionPlugin : plugin is already capturing.", ERRORS.CAPTURE_ALREADY_STARTED, true);
                    return true;
                }                
                mCfgParams                       = new CaptureParams(new JSONObject((String)args.get(0))); 
                if(!args.isNull(1))  mMfccParams = new MFCCParams(new JSONObject((String)args.get(1))); 
                else                 mMfccParams = new MFCCParams();    
                
                mService.startCapture(mCfgParams, mMfccParams, callbackContext);
                Messaging.sendNoResult2Web(callbackContext);
                return true;
            }
            catch (Exception e) 
            {
                Messaging.sendErrorString2Web(callbackContext, e.toString(), ERRORS.PLUGIN_INIT_CAPTURE, true);
                callbackContext = null;
                return true;
            }
        }
        else if (action.equals("startMicPlayback")) 
        {
            try 
            {
                if(mService.isCapturing())
                {
                    Messaging.sendErrorString2Web(callbackContext, "SpeechRecognitionPlugin : plugin is already capturing.", ERRORS.CAPTURE_ALREADY_STARTED, true);
                    return true;
                }                
                mCfgParams = new CaptureParams(new JSONObject((String)args.get(0))); 
                
                mService.startMicPlayback(mCfgParams, callbackContext);
                Messaging.sendNoResult2Web(callbackContext);
                return true;
            }
            catch (Exception e) 
            {
                Messaging.sendErrorString2Web(callbackContext, e.toString(), ERRORS.PLUGIN_INIT_PLAYBACK ,true);
                callbackContext = null;
                return true;
            }            
        }  
        else if (action.equals("setPlayBackPercVol")) 
        {
            if(mService.isCapturing())
            {
                int newperc;
                if(!args.isNull(0)) newperc = args.getInt(0);
                else                newperc = 0;
                
                mService.setPlayBackPercVol(newperc, callbackContext);
            }   
            Messaging.sendNoResult2Web(callbackContext);
            return true;
        }  
        else if (action.equals("stopCapture")) 
        {
            // an interrupt command is sent to audioreceiver, when it exits from its last cycle, it sends an event here
            mService.stopCapture(callbackContext);
            Messaging.sendNoResult2Web(callbackContext);
            return true;
        }        
        else if (action.equals("loadTFModel")) 
        {
            try 
            {
                mTfParams                       = new TFParams(new JSONObject((String)args.get(0))); 
                mTfParams.mAssetManager         = mContext.getAssets();
                
                boolean loaded = mService.loadTFModel(mTfParams, callbackContext);
                Messaging.sendNoResult2Web(callbackContext);

//                callbackContext.success();
                return true;
            }
            catch (Exception e) 
            {
                Messaging.sendErrorString2Web(callbackContext, e.toString(), ERRORS.PLUGIN_INIT_RECOGNITION, true);
                callbackContext = null;
                return true;
            }
        }
        else if (action.equals("startSpeechRecognition")) 
        {
            if(mService.isCapturing())
            {
                Messaging.sendErrorString2Web(callbackContext, "SpeechRecognitionPlugin : plugin is already capturing.", ERRORS.CAPTURE_ALREADY_STARTED, true);
                return true;
            }
            try 
            {
                mCfgParams                      = new CaptureParams(new JSONObject((String)args.get(0))); 
                mVadParams                      = new VADParams(new JSONObject((String)args.get(1))); 
                mMfccParams                     = new MFCCParams(new JSONObject((String)args.get(2))); 
                mTfParams                       = new TFParams(new JSONObject((String)args.get(3))); 
                
                mTfParams.mAssetManager         = mContext.getAssets();                
                mService.startSpeechRecognition(mCfgParams, mVadParams, mMfccParams, mTfParams, callbackContext);
                Messaging.sendNoResult2Web(callbackContext);
                return true;
            }
            catch (Exception e) 
            {
                Messaging.sendErrorString2Web(callbackContext, e.toString(), ERRORS.PLUGIN_INIT_RECOGNITION, true);
                callbackContext = null;
                return true;
            }
        }        
        else if (action.equals("stopSpeechRecognition")) 
        {
            // an interrupt command is sent to audioreceiver, when it exits from its last cycle, it sends an event here
            mService.stopSpeechRecognition(callbackContext);
            Messaging.sendNoResult2Web(callbackContext);
            return true;
        } 
        else if (action.equals("adjustVADThreshold")) 
        {
            // an interrupt command is sent to audioreceiver, when it exits from its last cycle, it sends an event here
            int newthreshold   = args.getInt(0); 
            
            mService.adjustVADThreshold(newthreshold, callbackContext);
            Messaging.sendNoResult2Web(callbackContext);
            return true;
        } 
        else if (action.equals("startSCOConnection")) 
        {
            bUseHeadSet = (boolean)args.get(0);
            
            mAudioDevicesManager.startBTHSConnection(bUseHeadSet);
            Messaging.sendNoResult2Web(callbackContext);
            return true;
        } 
        else if(action.equals("getMFCC")) 
        {            
            // fcc_json_params, source (inputpathnoext or dataarray), overwrite, inputpathnoext (in case of dataarray)
            try 
            {               
                // JS interface call params:     mfcc_json_params, source;  params have been validated in the js interface
                // should have a nDataDest > 0  web,file,both
                mMfccParams             = new MFCCParams(new JSONObject((String)args.get(0)));
                boolean overwrite       = true;
                if(args.get(2) != null)
                    overwrite = args.getBoolean(2); 
                
                String inputpathnoext = "";
                if(mMfccParams.nDataOrig == ENUMS.MFCC_DATAORIGIN_JSONDATA)
                {
                    Messaging.sendErrorString2Web(callbackContext, "getMFCC from a data array still not supported", ERRORS.PLUGIN_INIT_MFCC, true);
                    return true;
                }
                else    inputpathnoext   = args.getString(1); 

                mService.getMFCC(mMfccParams, inputpathnoext, overwrite, callbackContext);
                Messaging.sendNoResult2Web(callbackContext);
                return true;
            }
            catch (Exception e) 
            {
                Messaging.sendErrorString2Web(callbackContext, e.toString(), ERRORS.PLUGIN_INIT_MFCC, true);
                callbackContext = null;
                return true;
            }            
        }    
        else if(action.equals("recognizeCepstraFile")) 
        {  
            String cepstra_file_path = args.getString(0);
            mService.recognizeCepstraFile(cepstra_file_path, callbackContext);
            Messaging.sendNoResult2Web(callbackContext);
            return true;
        }    
        else if(action.equals("debugCall")) 
        {  
//            if(args.get(0) != null)
            mService.debugCall(args);     
            return true;
        }        
        return false;
    }
    
    @Override
    public void onDestroy() 
    {
        if (mBound) 
        {
            if(mService.isCapturing())  mService.stopCapture(callbackContext);
            mContext.unbindService(mConnection);
            mBound = false;
        }
    }
    
    @Override
    public void onReset() 
    {
//        if (mBound) 
//        {
//            if(mService.isCapturing())  mService.stopCapture(callbackContext);
//            mContext.unbindService(mConnection);
//            mBound = false;
//        }        
    }
    
    @Override
    public void onPause(boolean multitasking)
    {
        mAudioDevicesManager.onPause();
//                mAudioManager.stopBluetoothSco();        

    }
    
    @Override
    public void onResume(boolean multitasking)
    {
        mAudioDevicesManager.onResume();
//        initBTConnection();
    }
        
//    @Override
//    public void onNewIntent(Intent intent) {
//    }

    //=================================================================================================
    // GET RECORDING PERMISSIONS
    //=================================================================================================        
     //Ensure that we have gotten record audio permission
    private void promptForDangerousPermissions() 
    {
        ArrayList<String> permissions2Ask = new ArrayList<String>();
    
        for(int p=0; p < permissions.length; p++)
            if(!PermissionHelper.hasPermission(this, permissions[p])) permissions2Ask.add(permissions[p]);
        
        if(!permissions2Ask.isEmpty())
        {
            Object[] objPerms = permissions2Ask.toArray();
            String[] strPerms = Arrays.copyOf(objPerms, objPerms.length, String[].class);            
            PermissionHelper.requestPermissions(this, RECORD_AUDIO, strPerms);
        }
    }

    // Handle request permission result
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
    {
        for(int r:grantResults) 
        {
            if(r == PackageManager.PERMISSION_DENIED) 
            {
                Messaging.sendErrorString2Web(callbackContext, "RECORDING_PERMISSION_DENIED_ERROR", true);
                isCapturingAllowed = false;
                return;
            }
        }
        isCapturingAllowed = true;
    }     

    //=================================================================================================
}

