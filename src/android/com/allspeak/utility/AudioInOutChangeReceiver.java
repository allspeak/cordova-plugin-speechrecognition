package com.allspeak.utility;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.text.TextUtils;
import android.util.Log;
import java.lang.StringBuilder;

//import com.test.example.BirdersApplication;
//import com.test.example.controller.AudioHeadSetController;
//import com.test.example.controller.BluetoothHeadsetManager;
//import com.test.example.controller.BluetoothHeadsetManager.onBluetoothHeadSetListener;
//import com.test.example.controller.DeviceAudioOutputManager;
//import com.test.example.interfce.ILogger;
//import com.test.example.model.HeadSetModel;
//import com.test.example.util.StorageManager;

public class AudioInOutChangeReceiver extends BroadcastReceiver {

	private static AudioInOutChangeReceiver mInstance;

//	private AudioHeadSetController mAudioController;

	private static final String ACTION_HEADSET_PLUG_STATE = "android.intent.action.HEADSET_PLUG";

	private AudioManager mAudioManager;

	private BluetoothHeadsetManager mBTHeadSetManager;

	private Context mContext;

	private BluetoothHeadset mBluetoothHeadset;

	@Override
	public String getTAG()
	{
            return AudioInOutChangeReceiver.class.getCanonicalName();
	}

	private AudioInOutChangeReceiver(Context aContext)
	{
		if(BuildConfig.DEBUG) Log.d(getTAG(), "AudioInOutChangeReceiver()");

		if (mContext == null)
			mContext = aContext;

		initiBTHeadSetConfiguration();
	}

	private AudioInOutChangeReceiver()
	{

	}

	public static AudioInOutChangeReceiver getInstance(Context aContext)
	{
		if(mInstance == null)
		{
			mInstance = new AudioInOutChangeReceiver(aContext);
		}
		return mInstance;
	}

	public static void voidInstance()
	{
		mInstance = null;
	}

	public Context getActiveContext()
	{
		return mContext;
	}

//	public AudioInOutChangeReceiver addAudioController(AudioHeadSetController aController)
//	{
//		if(mAudioController == null)
//		this.mAudioController = aController;
//		return mInstance;
//	}

//	public boolean hasController()
//	{
//		return mAudioController != null ? true : false;
//	}

//	public AudioInOutChangeReceiver removeAudioController()
//	{
//
//		if(BuildConfig.DEBUG) Log.d(getTAG(), "removeAudioController()");
//
//		// reset BT Head set Configuration
//		resetBTHeadSetConfiguration();
//
//		if(this.mAudioController != null)
//		{
//			this.mAudioController  = null;
//		}
//		return mInstance;
//	}

	private void initiBTHeadSetConfiguration()
	{
		if (mBTHeadSetManager == null)
			mBTHeadSetManager = new BluetoothHeadsetManager(mContext);

		mBTHeadSetManager.addListener(mHeadsetCallback);

		// if BT is turn on then we move next step
		if(mBTHeadSetManager.hasEnableBluetooth())
		{
			mBTHeadSetManager.connectionToProxy();
		}
	}

	private void resetBTHeadSetConfiguration()
	{
		mBTHeadSetManager.disconnectProxy();
	}


	@Override
	public void onReceive(Context context, Intent intent)
	{
//		if(BirdersApplication.getInstance().getDebug_mode())
//			StorageManager.dumpIntent(getTAG(),intent);

		// initial audio manager
		initiAudioManager(context);

		final String action = intent.getAction();
		// check action should not empty
		if(TextUtils.isEmpty(action))return;

		 //Broadcast Action: Wired Headset plugged in or unplugged.
		 if (action.equals(ACTION_HEADSET_PLUG_STATE))
		 {
			 updateHeadSetDeviceInfo(context, intent);

		} else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
				|| BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)
				|| BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))
		 {

			updateBTHeadSetDeviceInfo(context, intent);
		 }

	}

	public void updateHeadSetDeviceInfo(Context context, Intent intent)
	{
		 //0 for unplugged, 1 for plugged.
		 int state = intent.getIntExtra("state", -1);

		 StringBuilder stateName = null;

		 //Headset type, human readable string
		 String name = intent.getStringExtra("name");

		 // - 1 if headset has a microphone, 0 otherwise, 1 mean h2w
		 int microPhone = intent.getIntExtra("microphone", -1);

		 switch (state)
		 {
                    case 0:
                        stateName = new StringBuilder("Headset is unplugged");
                       Log.d(getTAG(),stateName.toString());
                        break;
                    case 1:
                        stateName = new StringBuilder("Headset is plugged");
                       Log.d(getTAG(),stateName.toString());
                        break;
                    default:
                       Log.d(getTAG(), "I have no idea what the headset state is");
		 }

//	 	HeadSetModel tempModel = new HeadSetModel().setState(state).setStateName(stateName.toString()).setHandSetName(name).setMicroPhone(microPhone);
//
//	 	// update the prefrence key 'KEY_HEADSET_MODEL'  for headset info
//		DeviceAudioOutputManager.getInstance(context).setHeadsetInfo(tempModel);
//
//		if(hasController())
//		{
//			mAudioController.updateHeadSetInfo(tempModel.getState(), tempModel);
//		}
	}

	public void updateBTHeadSetDeviceInfo(Context context, Intent intent)
	{
		if(intent != null)
		{
			BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			if(device != null)
			{
				verifyHeadSetState(true,device);
			}
		}
	}
	public onBluetoothHeadSetListener mHeadsetCallback = new onBluetoothHeadSetListener() {

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

	private void verifyHeadSetState(boolean flag,BluetoothDevice device)
	{

		//TODO Wait for other wise result getting wrong
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		int state = 0;
		HeadSetModel btheasetdmodel = null;
		if(flag)
		{
			// STATE_DISCONNECTED	0
			// STATE_CONNECTED 		2

			// STATE_CONNECTING		1
			// STATE_DISCONNECTING	3

			state = mBluetoothHeadset.getConnectionState(device);
			if(BuildConfig.DEBUG) Log.d(getTAG(), "State :" + state);
		}
		else
		{
			AudioManager am = getAudioManager();
			state = (am.isBluetoothA2dpOn() == true) ? 1 : 0;
		}

		switch (state)
		{
			case 0:
			{
				if(BuildConfig.DEBUG) Log.d(getTAG(), "isBluetoothA2dpOff()");
//				btheasetdmodel= new HeadSetModel().setState(0).setStateName("BluetoothA2dpOff");
				break;
			}
			case 1:
			{
				if(BuildConfig.DEBUG) Log.d(getTAG(), "isBluetoothA2dpOn()");
//				btheasetdmodel= new HeadSetModel().setState(1).setStateName("BluetoothA2dpOn");
				break;
			}
			case 2:
			{
				if(BuildConfig.DEBUG) Log.d(getTAG(), "isBluetoothA2dpOn()");
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

	private AudioManager initiAudioManager(Context aContext)
	{
		if(mAudioManager == null)
		mAudioManager = (AudioManager) aContext.getSystemService(Context.AUDIO_SERVICE);
		return mAudioManager;
	}

	public AudioManager getAudioManager()
	{
		return mAudioManager != null ? mAudioManager : initiAudioManager(getActiveContext()) ;
	}

	public void queryAudioManagerForRouted()
	{
		AudioManager am = getAudioManager();
		if (am.isBluetoothA2dpOn())
		{
		    // Adjust output for Bluetooth.
			if(BuildConfig.DEBUG) Log.d(getTAG(), "isBluetoothA2dpOn()");

		} else if (am.isSpeakerphoneOn())
		{
		    // Adjust output for Speakerphone.
			if(BuildConfig.DEBUG) Log.d(getTAG(), "isSpeakerphoneOn()");

		} else if (am.isWiredHeadsetOn())
		{
		    // Adjust output for headsets
			if(BuildConfig.DEBUG) Log.d(getTAG(), "isWiredHeadsetOn()");

		}
		else
		{

		}
	}

}
