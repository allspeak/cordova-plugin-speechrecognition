package com.allspeak.tensorflow;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Bundle;

import org.apache.cordova.CallbackContext;
import android.util.Log;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;

// not necessary
import com.allspeak.tensorflow.TFParams;
//import com.allspeak.tensorflow.TF;


/*
it's a layer which call the MFCC functions on a new thread
sends the following messages to Plugin Activity:
- data
- progress_file
- progress_folder
- error
*/

public class TFHandlerThread extends HandlerThread implements Handler.Callback
{
    private static final String LOG_TAG = "TFHandlerThread";
    
    private Handler mInternalHandler    = null;   // manage internal messages
    private Handler mStatusCallback     = null;   // destination handler of status messages
    private Handler mResultCallback     = null;   // destination handler of data result
    private Handler mCommandCallback    = null;   // destination handler of output command
    private CallbackContext mWlCb       = null;   // access to web layer 
    
    private TFParams tfParams           = null;
    
    //================================================================================================================
    public TFHandlerThread(String name)
    {
        super(name);
    }
    public TFHandlerThread(String name, int priority)
    {
        super(name, priority);
    }
    //===============================================================================================
    // PUBLIC
    //===============================================================================================
    public void setParams(TFParams params)
    {
        tfParams  = params;
    }
    public void setWlCb(CallbackContext wlcb)
    {
        mWlCb = wlcb;        
    }
    public void setCallbacks(Handler cb)
    {
        mStatusCallback     = cb;        
        mCommandCallback    = cb;        
        mResultCallback     = cb;       
    }
    public void setCallbacks(Handler scb, Handler ccb, Handler rcb)
    {
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;      
    }
//--------------------------------------------------------------------------------------------------    
    public void init(TFParams params, Handler cb)
    {
        tfParams            = params;
        mStatusCallback     = cb;        
        mCommandCallback    = cb;        
        mResultCallback     = cb;  
        init(params, cb);
    }    
    public void init(TFParams params, Handler cb, CallbackContext wlcb)
    {
        mWlCb       = wlcb; 
        init(params, cb);
    }    
    public void init(TFParams params, Handler scb, Handler ccb, Handler rcb)
    {
        tfParams            = params;
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;   
    }
    public void init(TFParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcb)
    {
        mWlCb       = wlcb;
        init(params, scb, ccb, rcb);
    }        
    //===============================================================================================
    // wrapper to thread execution 
    //===============================================================================================
    // GET FROM folder or a file
    public void recognize(float[] data)
    {
        Bundle bundle  = new Bundle();
        Message message;
                
        bundle.putFloatArray("data", data);
        message = mInternalHandler.obtainMessage();
        message.setData(bundle);
        message.what    = ENUMS.TF_CMD_RECOGNIZE;
        mInternalHandler.sendMessage(message);
    }    
    //===============================================================================================
    // INTERNAL PROCESSING
    //===============================================================================================
    private void doRecognize(float[] data)
    {

    }
    //================================================================================================================
    @Override
    public boolean handleMessage(Message msg) 
    {
        Bundle bundle = msg.getData();
        float[] data;
        switch((int)msg.what)
        {
            case ENUMS.TF_CMD_RECOGNIZE:  
                    
                data            = bundle.getFloatArray("data");
                mStatusCallback.sendMessage(Message.obtain(null, ENUMS.TF_STATUS_PROCESS_STARTED));
                doRecognize(data);
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
        if(mInternalHandler == null)   Log.w(LOG_TAG, "TFHandlerThread mInternalHandler is NULL !!!!!!!!!!!!!!!!!");
        return mInternalHandler;
    }  
    //================================================================================================================
    
}

//
//
//    public TFHandlerThread(TFParams params, Handler cb, String name)
//    {
//        super(name);
//        mStatusCallback     = cb;        
//        mCommandCallback    = cb;        
//        mResultCallback     = cb;
//        tfParams            = params;
//    }
//    public TFHandlerThread(TFParams params, Handler cb, String name, int priority)
//    {
//        super(name, priority);
//        mStatusCallback     = cb;        
//        mCommandCallback    = cb;        
//        mResultCallback     = cb;
//        tfParams            = params;
//    }
//    public TFHandlerThread(TFParams params, Handler cb, CallbackContext wlcb, String name, int priority)
//    {
//        super(name, priority);
//        mStatusCallback     = cb;        
//        mCommandCallback    = cb;        
//        mResultCallback     = cb;
//        tfParams            = params;
//        mWlCb               = wlcb;
//    }