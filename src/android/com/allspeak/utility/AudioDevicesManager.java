package com.allspeak.utility;

import android.util.Log;
//import com.allspeak.BuildConfig;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;

import android.bluetooth.BluetoothDevice;

import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import com.allspeak.utility.Messaging;

import org.apache.cordova.CallbackContext;

import com.allspeak.utility.ArrayList2d;
import com.allspeak.utility.BluetoothHeadsetUtils;
import android.util.Log;

// INHERITED
//    private  Context mContext;
//    private AudioManager mAudioManager;
    
//    private BluetoothAdapter mBluetoothAdapter;
//    private BluetoothHeadset mBluetoothHeadset;
//    private BluetoothDevice mConnectedHeadset;
//
//    private boolean mIsCountDownOn;
//    private boolean mIsStarting;
//    private boolean mIsOnHeadsetSco;
//    private boolean mIsStarted; 
//    private boolean mAutoConnect

public class AudioDevicesManager extends BluetoothHeadsetUtils
{
    private static final String LOG_TAG         = "AudioDevicesManager";
    private CallbackContext callbackContext     = null;
  
    ArrayList2d<AudioDeviceInfo> mAd            = new ArrayList2d<AudioDeviceInfo>();
    
    //=========================================================================================================================
    public AudioDevicesManager(Context context)
    {
        super(context);
        mAd = getAudioDevices();
    }
    
    public void setCallback(CallbackContext clb)
    {
        callbackContext = clb;
    }
    
    //=========================================================================================================================
    // CALLS FROM WL
    //=========================================================================================================================
    public JSONObject getBluetoothStatus()
    {
        return getBluetoothStatus(mConnectedHeadset, ENUMS.BLUETOOTH_STATUS);
    }    
    
    public JSONObject getBluetoothStatus(BluetoothDevice device, int event_type)
    {
        try
        {
            JSONObject res = new JSONObject();
            
            res.put("type"                  , event_type);
            res.put("mIsOnHeadsetSco"       , mIsOnHeadsetSco);
            res.put("mExistHeadsetConnected", mExistHeadsetConnected);
            res.put("mAutoConnect"          , mAutoConnect);
            
            if(device != null)
            {
                res.put("mActiveHeadSetName"    , device.getName());
                res.put("mActiveHeadSetAddress" , device.getAddress());
            }
            else
            {
                res.put("mActiveHeadSetName"    , "");
                res.put("mActiveHeadSetAddress" , "");
            }
                
            return res;
        }
        catch(JSONException e)
        {
            e.printStackTrace(); 
            return null;
        }        
    }
    
    public JSONObject exportAudioDevicesJSON() throws JSONException
    {
        try
        {
            JSONObject info = new JSONObject();
            JSONArray input = new JSONArray();
            JSONArray output = new JSONArray();

            int lin     = mAd.getNumCols(0);
            int lout    = mAd.getNumCols(1);
            int max     = Math.max(lin, lout);

            JSONObject elem;
            for(int d=0; d<lin; d++)
            {
                AudioDeviceInfo adinfo = mAd.get(0,d);
                elem            = new JSONObject();
                elem.put("name", adinfo.getProductName().toString());
                elem.put("type", adinfo.getType());
                elem.put("channels", adinfo.getChannelCounts());

                input.put(elem);
            }
            for(int d=0; d<lout; d++)
            {
                AudioDeviceInfo adinfo = mAd.get(1,d);
                elem            = new JSONObject();
                elem.put("name", adinfo.getProductName().toString());
                elem.put("type", adinfo.getType());
                elem.put("channels", adinfo.getChannelCounts());

                output.put(elem);
            }

            info.put("input", input);
            info.put("output", output);  
            return info;
        }
        catch(Exception e)
        {
            e.printStackTrace(); 
            throw (e);
        }
    }    
        
    //==============================================================================================
    // CALLBACK TO WL
    //==============================================================================================
    public void onHeadsetDisconnected(BluetoothDevice device)
    {
        Log.i(LOG_TAG, "onHeadsetDisconnected");
        Messaging.sendUpdate2Web(callbackContext, getBluetoothStatus(device, ENUMS.HEADSET_DISCONNECTED), true);
    };

    public void onHeadsetConnecting(BluetoothDevice device)
    {
        Log.i(LOG_TAG, "onHeadsetConnecting");
        Messaging.sendUpdate2Web(callbackContext, getBluetoothStatus(device, ENUMS.HEADSET_CONNECTING), true);
    };

    public void onExistHeadset(BluetoothDevice device)
    {
        Log.i(LOG_TAG, "onExistHeadset");
        Messaging.sendUpdate2Web(callbackContext, getBluetoothStatus(device, ENUMS.HEADSET_EXIST), true);
    };
    
    public void onScoAudioDisconnected(BluetoothDevice device)
    {
        Log.i(LOG_TAG, "onScoAudioDisconnected");
        Messaging.sendUpdate2Web(callbackContext, getBluetoothStatus(device, ENUMS.AUDIOSCO_DISCONNECTED), true);
    };

    public void onScoAudioConnected(BluetoothDevice device)
    {
        Log.i(LOG_TAG, "onScoAudioConnected");
        Messaging.sendUpdate2Web(callbackContext, getBluetoothStatus(device, ENUMS.AUDIOSCO_CONNECTED), true);
    };   
    //=========================================================================================================================
    public ArrayList2d<AudioDeviceInfo> getAudioDevices()
    {
        AudioDeviceInfo[] inad = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        int l = inad.length;
        for(int d=0; d<l; d++) mAd.Add(inad[d], 0);
        
        AudioDeviceInfo[] outad = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        l = outad.length;
        for(int d=0; d<l; d++) mAd.Add(outad[d], 1);
       
        return mAd;
    }    

    //=========================================================================================================================
    // CALLS FROM APP MAIN EVENTS
    //=========================================================================================================================    
    public void onDestroy()
    {
        stop();
    }
    //=================================================================================================
    //=================================================================================================
}
