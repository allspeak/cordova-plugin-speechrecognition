package com.allspeak.utility;

import java.util.List;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.util.Log;

/**
 * This is a utility to detect bluetooth headset connection and establish audio connection
 * for android API >= 11. 
 * 
 * @author Hoan Nguyen
 *
 */
public abstract class BluetoothHeadsetUtils
{
    protected  Context mContext;
    protected AudioManager mAudioManager;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothHeadset mBluetoothHeadset;
    protected BluetoothDevice mConnectedHeadset;

    private boolean mIsCountDownOn;
    protected boolean mIsOnHeadsetSco;          // indicates whether the connected hs is enabled
//    protected boolean mUseHeadsetConnected;     // indicates whether the connected hs is enabled
    protected boolean mExistHeadsetConnected;   //indicates whether exist a connected hs
    private boolean mIsStarted = false;
    
    protected boolean mAutoConnect = false;     // indicates whether enable (startRecognition) the connected device

    private static final String TAG = "BluetoothHeadsetUtils"; //$NON-NLS-1$

    /**
     * Constructor
     * @param context
     */
    public BluetoothHeadsetUtils(Context context)
    {
        mContext            = context;
        mBluetoothAdapter   = BluetoothAdapter.getDefaultAdapter();
        mAudioManager       = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Call this to start BluetoothHeadsetUtils functionalities.
     * Register a headset profile listener
     * @return false    if device does not support bluetooth or current platform does not supports
     *                  use of SCO for off call or error in getting profile proxy.
     * @return          true otherwise
     */
    public boolean start()
    {
        Log.d(TAG, "startBluetooth"); //$NON-NLS-1$
        if (mIsStarted) return true;
        if (mBluetoothAdapter == null || !mAudioManager.isBluetoothScoAvailableOffCall()) return false;

        mAudioManager.setMode(AudioManager.MODE_IN_CALL);
        
        mIsStarted = mBluetoothAdapter.getProfileProxy(mContext, mHeadsetProfileListener, BluetoothProfile.HEADSET);                 // All the detection and audio connection are done in mHeadsetProfileListener
        return mIsStarted;
    }

    /**
     * Should call this on onResume or onDestroy.
     * Unregister broadcast receivers and stop Sco audio connection
     * and cancel count down.
     */
    public void stop()
    {
        if (mIsStarted)
        {
            mIsStarted = false;
            Log.d(TAG, "stopBluetooth"); //$NON-NLS-1$

            if (mIsCountDownOn)
            {
                mIsCountDownOn = false;
                mConnectHeadset.cancel();
            }

            if (mBluetoothHeadset != null)
            {
                // Need to call stopVoiceRecognition here when the app
                // change orientation or close with headset still turns on.
                mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
                mContext.unregisterReceiver(mHeadsetBroadcastReceiver);
                mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
                mBluetoothHeadset = null;
                
//                mUseHeadsetConnected = false;
            }
        }
    }

    /**
     * 
     * @return  true if audio is connected through headset.
     */
    public boolean isOnHeadsetSco()
    {
        return mIsOnHeadsetSco;
    }

    public abstract void onExistHeadset(BluetoothDevice device);
    public abstract void onHeadsetDisconnected(BluetoothDevice device);
    public abstract void onHeadsetConnecting(BluetoothDevice device);
    public abstract void onScoAudioDisconnected(BluetoothDevice device);
    public abstract void onScoAudioConnected(BluetoothDevice device);

    
    public boolean enableHeadSet(boolean doenable)
    {
        if(doenable)
        {
            mAudioManager.setSpeakerphoneOn(false);
            mAudioManager.setBluetoothScoOn(true);            
            mAudioManager.startBluetoothSco();
            return mBluetoothHeadset.startVoiceRecognition(mConnectedHeadset);
        }
        else
        {
            mAudioManager.stopBluetoothSco();
            mAudioManager.setBluetoothScoOn(false);
            mAudioManager.setSpeakerphoneOn(true);
            return mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
//            return true;
        }
    }

    /**
     * Check for already connected headset and if so start audio connection.
     * Register for broadcast of headset and Sco audio connection states.
     */
    private BluetoothProfile.ServiceListener mHeadsetProfileListener = new BluetoothProfile.ServiceListener()
    {
        /**
         * This method is never called, even when we closeProfileProxy on onPause.
         * When or will it ever be called???
         */
        @Override
        public void onServiceDisconnected(int profile)
        {
            Log.d(TAG, "Profile listener onServiceDisconnected"); //$NON-NLS-1$
            stop();
        }

        @SuppressWarnings("synthetic-access")
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy)
        {
            Log.d(TAG, "Profile listener onServiceConnected"); //$NON-NLS-1$

            // mBluetoothHeadset is just a headset profile, does not represent a headset device.
            mBluetoothHeadset = (BluetoothHeadset) proxy;

            // If a headset is connected before this application starts, ACTION_CONNECTION_STATE_CHANGED will not be broadcast. 
            // ==> So we need to check for already connected headset.
            List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();
            if (devices.size() > 0)
            {
                // Only one headset can be connected at a time, so the connected headset is at index 0.
                mConnectedHeadset       = devices.get(0);
                mExistHeadsetConnected  = true;
                onExistHeadset(mConnectedHeadset);
                Log.d(TAG, "connected headset: " + mConnectedHeadset.getName()); //$NON-NLS-1$
                
                if(mAutoConnect)
                {
                    Log.d(TAG, "attempting to enable headset: " + mConnectedHeadset.getName()); //$NON-NLS-1$
                    // Should not need count down timer, but just in case. See comment below in mHeadsetBroadcastReceiver onReceive()
                    mIsCountDownOn = true;
                    mConnectHeadset.start();
                }
            }
            else 
            {
                mExistHeadsetConnected  = false;
                mConnectedHeadset       = null;
            }

            // During the active life time of the app, a user may turn on and off the headset.
            // => register for broadcast of connection states.
            mContext.registerReceiver(mHeadsetBroadcastReceiver, new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED));
            // Calling startVoiceRecognition does not result in immediate audio connection.
            // So register for broadcast of audio connection states. This broadcast will only be sent if startVoiceRecognition returns true.
            mContext.registerReceiver(mHeadsetBroadcastReceiver, new IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED));
        }
    };

    /**
     *  Handle headset and Sco audio connection states.
     */
    private BroadcastReceiver mHeadsetBroadcastReceiver = new BroadcastReceiver()
    {

        @SuppressWarnings("synthetic-access")
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            int state;
            if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED))
            {
                state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
                Log.d(TAG, "\nAction = " + action + "\nState = " + state); //$NON-NLS-1$ //$NON-NLS-2$
                if (state == BluetoothHeadset.STATE_CONNECTED)
                {
                    mConnectedHeadset       = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    mExistHeadsetConnected  = true;                        Log.d(TAG, "connected headset: " + mConnectedHeadset.getName()); //$NON-NLS-1$

                    onExistHeadset(mConnectedHeadset);
                    Log.d(TAG, "connected headset: " + mConnectedHeadset.getName()); //$NON-NLS-1$

                    if(mAutoConnect)
                    {
                        Log.d(TAG, "attempting to enable headset: " + mConnectedHeadset.getName()); //$NON-NLS-1$
                        // Calling startVoiceRecognition always returns false here, 
                        // that why a count down timer is implemented to call
                        // startVoiceRecognition in the onTick.
                        mIsCountDownOn = true;
                        mConnectHeadset.start();
                    }
                }
                else if (state == BluetoothHeadset.STATE_DISCONNECTED)
                {
                    // Calling stopVoiceRecognition always returns false here
                    // as it should since the headset is no longer connected.
                    if (mIsCountDownOn)
                    {
                        mIsCountDownOn = false;
                        mConnectHeadset.cancel();
                    }
                    Log.d(TAG, "disconnected Headset " + mConnectedHeadset.getName()); //$NON-NLS-1$
                    mExistHeadsetConnected  = false;
                    mConnectedHeadset       = null;
                    onHeadsetDisconnected(null);
                }
            }
            else // audio
            {
                state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                Log.d(TAG, "\nAction = " + action + "\nState = " + state); //$NON-NLS-1$ //$NON-NLS-2$
                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED)
                {
                    if (mIsCountDownOn)
                    {
                        mIsCountDownOn = false;
                        mConnectHeadset.cancel();
                    }
                    mIsOnHeadsetSco = true;
                    onScoAudioConnected(mConnectedHeadset);
                    Log.d(TAG, "\nHeadset SCOAUDIO connected");  //$NON-NLS-1$
                }
                else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED)
                {
                    mIsOnHeadsetSco = false;
                    // The headset audio is disconnected, but calling stopVoiceRecognition always returns true here. //mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
                    onScoAudioDisconnected(mConnectedHeadset);
                    Log.d(TAG, "Headset SCOAUDIO disconnected"); //$NON-NLS-1$
                }
            }   
        }
    };

    /**
     * Try to connect to audio headset in onTick.
     */
    private CountDownTimer mConnectHeadset = new CountDownTimer(10000, 1000)
    {
        @SuppressWarnings("synthetic-access")
        @Override
        public void onTick(long millisUntilFinished)
        {
            // First stick calls always returns false. The second stick
            // always returns true if the countDownInterval is set to 1000.
            // It is somewhere in between 500 to a 1000.
//        	mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
            if(enableHeadSet(true))
            
//            if(mBluetoothHeadset.startVoiceRecognition(mConnectedHeadset)) 
            	System.out.println("Started voice recognition");
            else
            	System.out.println("Could not start recognition");
            
            System.out.println("headset: " + mConnectedHeadset.getName());
//            mAudioManager.setBluetoothScoOn(true);
//            mAudioManager.startBluetoothSco();
//            mUseHeadsetConnected     = true;
            Log.d(TAG, "onTick startVoiceRecognition"); //$NON-NLS-1$
        }

        @SuppressWarnings("synthetic-access")
        @Override
        public void onFinish()
        {
            // Calls to startVoiceRecognition in onStick are not successful.
            // Should implement something to inform user of this failure
            mIsCountDownOn = false;
            Log.d(TAG, "\nonFinish fail to connect to headset audio"); //$NON-NLS-1$
        }
    };

}  
