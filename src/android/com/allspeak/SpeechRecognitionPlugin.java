package com.allspeak;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PermissionHelper;


import android.media.AudioDeviceInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.CountDownTimer;
import android.widget.Toast;


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
import com.allspeak.tensorflow.TFParams;
import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.audioprocessing.vad.VADParams;
import com.allspeak.audiocapture.CFGParams;
import com.allspeak.audiocapture.*;
import java.util.ArrayList;
import java.util.Arrays;


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
    
    
    boolean isCapturing                         = false;
    boolean isHeadSetConnected                  = false;
    //-----------------------------------------------------------------------------------------------
    
    private SpeechRecognitionService mService   = null;
    private boolean mBound                      = false;
    
    private CFGParams mCfgParams                = null;
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
            initBTConnection();
            boolean conn = bindService();
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
     *          startCapture
     *          startMicPlayback
     *          setPlayBackPercVol
     *          stopCapture
     *          loadTFModel
     *          startSpeechRecognition
     *          stopSpeechRecognition
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
        if (action.equals("init")) 
        {
            // used to just set the callback;
            Messaging.sendNoResult2Web(callbackContext);
            return true;
        }
        else if (action.equals("startCapture")) 
        {
            if(mService.isCapturing())
            {
                Messaging.sendErrorString2Web(callbackContext, "SpeechRecognitionPlugin : plugin is already capturing.", ERRORS.CAPTURE_ALREADY_STARTED, true);
                return true;
            }
            try 
            {
                mCfgParams                       = new CFGParams(new JSONObject((String)args.get(0))); 
                if(!args.isNull(1))  mMfccParams = new MFCCParams(new JSONObject((String)args.get(1))); 
                
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
            if(mService.isCapturing())
            {
                Messaging.sendErrorString2Web(callbackContext, "SpeechRecognitionPlugin : plugin is already capturing.", ERRORS.CAPTURE_ALREADY_STARTED, true);
                return true;
            }
            try 
            {
                mCfgParams = new CFGParams(new JSONObject((String)args.get(0))); 
                
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
                
                mService.loadTFModel(mTfParams, callbackContext);
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
        else if (action.equals("startSpeechRecognition")) 
        {
            if(mService.isCapturing())
            {
                Messaging.sendErrorString2Web(callbackContext, "SpeechRecognitionPlugin : plugin is already capturing.", ERRORS.CAPTURE_ALREADY_STARTED, true);
                return true;
            }
            try 
            {
                mCfgParams                      = new CFGParams(new JSONObject((String)args.get(0))); 
                mVadParams                      = new VADParams(new JSONObject((String)args.get(1))); 
                mMfccParams                     = new MFCCParams(new JSONObject((String)args.get(2))); 
                mTfParams                       = new TFParams(new JSONObject((String)args.get(3))); 
                
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
        else if(action.equals("getMFCC")) 
        {            
            try 
            {               
                // JS interface call params:     mfcc_json_params, source;  params have been validated in the js interface
                // should have a nDataDest > 0  web,file,both
                mMfccParams             = new MFCCParams(new JSONObject((String)args.get(0)));
                String inputpathnoext   = args.getString(1); 
                
                mService.getMFCC(mMfccParams, inputpathnoext, callbackContext);
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
        mContext.unregisterReceiver(mBluetoothScoReceiver);
        mAudioManager.stopBluetoothSco();        
    }
    
    @Override
    public void onResume(boolean multitasking)
    {
        initBTConnection();
    }
        
//    @Override
//    public void onNewIntent(Intent intent) {
//    }
    //=================================================================================================
    // BLUETOOTH HEADSET STATE CHANGES
    //================================================================================================= 
    private void initBTConnection()
    {
        IntentFilter intentFilterSCO = new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        IntentFilter intentFilterBTHSCONN = new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothScoReceiver, intentFilterSCO);
        mContext.registerReceiver(mBTHSConnReceiver, intentFilterBTHSCONN);

        
        AudioDeviceInfo[] adIN  = new AudioDeviceInfo[10];
        AudioDeviceInfo[] adOUT = new AudioDeviceInfo[10];
        adIN                    = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        adOUT                   = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
      
        
//        mAudioManager.startBluetoothSco();          
    }

    //called when headset is connected/disconnected
    private BroadcastReceiver mBTHSConnReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
            int state               = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            BluetoothDevice device  = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String deviceName       = device.getName();
            
            try
            {
                isHeadSetConnected  = false;
                JSONObject info     = new JSONObject(); 
                
                switch (state)
                {
                    case BluetoothProfile.STATE_CONNECTED:
                        info.put("type", ENUMS.HEADSET_CONNECTED);  
                        isHeadSetConnected = true;
                        Log.d(LOG_TAG, "BT headset " + deviceName + " connected");
                        break;

                    case BluetoothProfile.STATE_DISCONNECTED:
                        info.put("type", ENUMS.HEADSET_DISCONNECTED);  
                        isHeadSetConnected = false;
                        Log.d(LOG_TAG, "BT headset " + deviceName + " disconnected");
                        break;

                    case BluetoothProfile.STATE_CONNECTING:
                        info.put("type", ENUMS.HEADSET_CONNECTING);  
                        isHeadSetConnected = false;
                        Log.d(LOG_TAG, "BT headset " + deviceName + " connecting");
                        break;

                    case BluetoothProfile.STATE_DISCONNECTING:
                        info.put("type", ENUMS.HEADSET_DISCONNECTING);  
                        isHeadSetConnected = false;
                        Log.d(LOG_TAG, "BT headset " + deviceName + " disconnecting");
                        break;
                }

                Messaging.sendUpdate2Web(callbackContext, info, true);
            }
            catch (JSONException e){e.printStackTrace();}              
        }        
    };

    private BroadcastReceiver mBluetoothScoReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
            
            try
            {
                isHeadSetConnected  = false;
                JSONObject info     = new JSONObject(); 
                
                if(state == AudioManager.SCO_AUDIO_STATE_ERROR)
                    Messaging.sendErrorString2Web(callbackContext, "headset connection error", ERRORS.HEADSET_ERROR, true);
                else
                {
                    switch (state)
                    {
                        case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                            info.put("type", ENUMS.HEADSET_CONNECTED);  
                            isHeadSetConnected = true;
                            break;

                        case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                            info.put("type", ENUMS.HEADSET_DISCONNECTED);  
                            isHeadSetConnected = false;
                            break;

                        case AudioManager.SCO_AUDIO_STATE_CONNECTING:
                            info.put("type", ENUMS.HEADSET_CONNECTING);  
                            isHeadSetConnected = false;
                            break;
                    }

                    Messaging.sendUpdate2Web(callbackContext, info, true);
                }
            }
            catch (JSONException e){e.printStackTrace();}              
        }
    };      
    
    
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

