package com.allspeak.utility;

import android.util.Log;
import com.allspeak.BuildConfig;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;

import android.media.AudioDeviceInfo;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
 
import android.bluetooth.BluetoothAdapter;

import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import com.allspeak.utility.Messaging;
import org.apache.cordova.CallbackContext;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import com.allspeak.utility.ArrayList2d;
import com.allspeak.utility.BluetoothHeadsetManager;
import com.allspeak.utility.BluetoothHeadsetManager.onBluetoothHeadSetListener;

public class AudioDevicesManager 
{
    private static final String LOG_TAG         = "AudioDevicesManager";
    
    Context mContext                            = null;
    AudioManager mAudioManager                  = null;
    boolean isHeadSetConnected                  = false;
    boolean isSCOEnabled                        = false;
    private CallbackContext callbackContext     = null;
    
    private BluetoothHeadset mBluetoothHeadset;
    private BluetoothAdapter mBluetoothAdapter          = null;
    private BluetoothHeadsetManager mBTHeadSetManager;
   
    IntentFilter intentFilterSCO                = new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
    IntentFilter intentFilterBTHSCONN           = new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);    
    
    ArrayList2d<AudioDeviceInfo> mAd            = new ArrayList2d<AudioDeviceInfo>();
    
    private myAudioDeviceCallback mAudioDeviceCallback  = new myAudioDeviceCallback();
    
    //=========================================================================================================================
    public AudioDevicesManager(Context context, AudioManager audiomanager)
    {
        mContext            = context;
        mAudioManager       = audiomanager;

        mAd                 = getAudioDevices();
        registerReceivers(true);
        mAudioManager.startBluetoothSco();  

//        if (mBTHeadSetManager == null)
//                mBTHeadSetManager = new BluetoothHeadsetManager(mContext);
//
//        mBTHeadSetManager.addListener(mHeadsetCallback);
//
//        // if BT is turn on then we move next step
//        if(mBTHeadSetManager.hasEnableBluetooth())
//        {
//                mBTHeadSetManager.connectionToProxy();
//        }        

    }
    
    public onBluetoothHeadSetListener mHeadsetCallback = new onBluetoothHeadSetListener() 
    {

        @Override
        public void disconnectedToProxy()
        {
            verifyHeadSetState(false,null);
        }

        @Override
        public void connectedToProxy(BluetoothHeadset aBluetoothHeadset)
        {
            mBluetoothHeadset = aBluetoothHeadset;
            if(mBluetoothHeadset != null)
            {
                verifyHeadSetState(false,null);
            }
        }
    };    
    
    private void verifyHeadSetState(boolean flag, BluetoothDevice device)
    {
        //TODO Wait for other wise result getting wrong
        try {
                Thread.sleep(500);
        } catch (InterruptedException e) {
                e.printStackTrace();
        }

        int state = 0;
//        HeadSetModel btheasetdmodel = null;
        if(flag)
        {
            // STATE_DISCONNECTED	0
            // STATE_CONNECTED 		2

            // STATE_CONNECTING		1
            // STATE_DISCONNECTING	3

            state = mBluetoothHeadset.getConnectionState(device);
            if(BuildConfig.DEBUG) Log.d(AudioDevicesManager.class.getCanonicalName(), "State :" + state);
        }
        else
        {
//            AudioManager am = getAudioManager();
            state = (mAudioManager.isBluetoothA2dpOn() == true) ? 1 : 0;
        }

        switch (state)
        {
            case 0:
            {
                if(BuildConfig.DEBUG) Log.d(AudioDevicesManager.class.getCanonicalName(), "isBluetoothA2dpOff()");
//				btheasetdmodel= new HeadSetModel().setState(0).setStateName("BluetoothA2dpOff");
                break;
            }
            case 1:
            {
                if(BuildConfig.DEBUG) Log.d(AudioDevicesManager.class.getCanonicalName(), "isBluetoothA2dpOn()");
//				btheasetdmodel= new HeadSetModel().setState(1).setStateName("BluetoothA2dpOn");
                break;
            }
            case 2:
            {
                if(BuildConfig.DEBUG) Log.d(AudioDevicesManager.class.getCanonicalName(), "isBluetoothA2dpOn()");
//				btheasetdmodel= new HeadSetModel().setState(1).setStateName("BluetoothA2dpOn");
                break;
            }
            default:
                break;
        }

        // TODO Please call setBTHeadsetInfo instead of setHeadsetInfo
//		DeviceAudioOutputManager.getInstance(getActiveContext()).setBTHeadsetInfo(btheasetdmodel);
//
//		if(hasController())
//		{
//			mAudioController.updateHeadSetInfo(btheasetdmodel.getState(), btheasetdmodel);
//		}

    }    
    
    
    public AudioDevicesManager(Context context, AudioManager audiomanager, CallbackContext callbackcontext)
    {
        this(context, audiomanager);
        callbackContext = callbackcontext;
    }
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
    
    public String[][] exportAudioDevicesNames()
    {
        int lin     = mAd.getNumCols(0);
        int lout    = mAd.getNumCols(1);
        int max     = Math.max(lin, lout);
        
        String[][] str_ad = new String[2][max];
        
        for(int d=0; d<lin; d++)    str_ad[0][d] = mAd.get(0,d).getProductName().toString();
        for(int d=0; d<lout; d++)   str_ad[1][d] = mAd.get(1,d).getProductName().toString();
        
        return str_ad;
    }
    
    public JSONObject exportAudioDevicesJSON() throws JSONException
    {
        try
        {
            JSONObject info = new JSONObject();
            info.put("type", ENUMS.AUDIODEVICES_INFO);

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
    
    public AudioDeviceInfo[][] exportAudioDevice()
    {
        int lin     = mAd.getNumCols(0);
        int lout    = mAd.getNumCols(1);
        int max     = Math.max(lin, lout);

        AudioDeviceInfo[][] ad = new AudioDeviceInfo[2][max];
        
        for(int d=0; d<lin; d++)    ad[0][d] = mAd.get(0,d);
        for(int d=0; d<lout; d++)   ad[1][d] = mAd.get(1,d);        
        
        return ad;
    }

    public boolean isBTHSconnected()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) 
        {
            // Device does not support Bluetooth
            
        }        
//        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        return true;
    }
    
    public void startBTHSConnection(boolean start)
    {
        if(start)
        {
            mAudioManager.setSpeakerphoneOn(false);
            mAudioManager.setBluetoothScoOn(true);            
            mAudioManager.startBluetoothSco();
        }
        else
        {
            mAudioManager.stopBluetoothSco();
            mAudioManager.setBluetoothScoOn(false);
            mAudioManager.setSpeakerphoneOn(true);
        }
    }
    
    public void setCallback(CallbackContext callbackcontext)
    {
        callbackContext = callbackcontext;
    }

    public void onPause()
    {
        registerReceivers(false);
    }
    
    public void onResume()
    {
        registerReceivers(true);
    }
    //=================================================================================================
    //=================================================================================================
    //=================================================================================================
    //=================================================================================================
    //=================================================================================================
    private void registerReceivers(boolean reg)
    {
        if(reg)
        {
            mContext.registerReceiver(mBluetoothScoReceiver, intentFilterSCO);
//            mContext.registerReceiver(mBTHSConnReceiver, intentFilterBTHSCONN);  
//            mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, null);
        }
        else
        {
            mContext.unregisterReceiver(mBluetoothScoReceiver);
//            mContext.unregisterReceiver(mBTHSConnReceiver);  
//            mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);            
        }
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
                        if(BuildConfig.DEBUG) Log.d(LOG_TAG, "BT headset " + deviceName + " connected");
                        break;

                    case BluetoothProfile.STATE_DISCONNECTED:
                        info.put("type", ENUMS.HEADSET_DISCONNECTED);  
                        isHeadSetConnected = false;
                        if(BuildConfig.DEBUG) Log.d(LOG_TAG, "BT headset " + deviceName + " disconnected");
                        break;

                    case BluetoothProfile.STATE_CONNECTING:
                        info.put("type", ENUMS.HEADSET_CONNECTING);  
                        isHeadSetConnected = false;
                        if(BuildConfig.DEBUG) Log.d(LOG_TAG, "BT headset " + deviceName + " connecting");
                        break;

                    case BluetoothProfile.STATE_DISCONNECTING:
                        info.put("type", ENUMS.HEADSET_DISCONNECTING);  
                        isHeadSetConnected = false;
                        if(BuildConfig.DEBUG) Log.d(LOG_TAG, "BT headset " + deviceName + " disconnecting");
                        break;
                }

                Messaging.sendUpdate2Web(callbackContext, info, true);
            }
            catch (JSONException e){e.printStackTrace();}              
        }        
    };

    //called when a SCO connection has changed
    private BroadcastReceiver mBluetoothScoReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
            
            try
            {
                isSCOEnabled  = false;
                JSONObject info     = new JSONObject(); 
                
                if(state == AudioManager.SCO_AUDIO_STATE_ERROR)
                    Messaging.sendErrorString2Web(callbackContext, "headset connection error", ERRORS.HEADSET_ERROR, true);
                else
                {
                    switch (state)
                    {
                        case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                            info.put("type", ENUMS.HEADSET_CONNECTED);  
                            isSCOEnabled = true;
                            if(BuildConfig.DEBUG) Log.d(LOG_TAG, "SCO connected");
                            break;

                        case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                            info.put("type", ENUMS.HEADSET_DISCONNECTED);  
                            isSCOEnabled = false;
                            if(BuildConfig.DEBUG) Log.d(LOG_TAG, "SCO disconnected");
                            
                            break;

                        case AudioManager.SCO_AUDIO_STATE_CONNECTING:
                            info.put("type", ENUMS.HEADSET_CONNECTING);  
                            isSCOEnabled = false;
                            break;
                    }

                    Messaging.sendUpdate2Web(callbackContext, info, true);
                }
            }
            catch (JSONException e){e.printStackTrace();}              
        }
    };      
    
    // AUDIO DEVICES LISTENER
    private class myAudioDeviceCallback extends AudioDeviceCallback 
    {
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) 
        {
            for (int d=0; d<addedDevices.length; d++)
            {
                AudioDeviceInfo ad  = addedDevices[d];
                if(ad.isSink()) mAd.Add(ad, 1);
                else            mAd.Add(ad, 0);
                
                if(BuildConfig.DEBUG) Log.d(LOG_TAG, "added audio devices: " + addedDevices[d].getProductName().toString());
            }
        }

        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) 
        {
            for (int d=0; d<removedDevices.length; d++)
            {
                AudioDeviceInfo ad  = removedDevices[d];
                String name         = ad.getProductName().toString();
                int col;
                if(ad.isSink()) col = 1;
                else            col = 0;
                
                for(int dd=0; dd<mAd.getNumCols(col); dd++)
                {
                    AudioDeviceInfo thisad  = mAd.get(col,dd);
                    String thisname         = thisad.getProductName().toString();                        
                    if(name == thisname)    mAd.remove(col, dd);
                }
                
                if(BuildConfig.DEBUG) Log.d(LOG_TAG, "removed audio devices: " + name);
            }
        }
    }  
    //=================================================================================================
}
