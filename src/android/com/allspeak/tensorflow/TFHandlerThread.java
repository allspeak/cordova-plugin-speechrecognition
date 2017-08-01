package com.allspeak.tensorflow;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Bundle;

import org.apache.cordova.CallbackContext;
import android.util.Log;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;

import com.allspeak.utility.Messaging;
import com.allspeak.utility.FileUtilities;

// not necessary
import com.allspeak.tensorflow.TFParams;
import com.allspeak.tensorflow.TF;
import java.io.IOException;


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
    private static final String LOG_TAG     = "TFHandlerThread";
    
    private Handler mInternalHandler        = null;   // manage internal messages
    private Handler mStatusCallback         = null;   // destination handler of status messages
    private Handler mResultCallback         = null;   // destination handler of data result
    private Handler mCommandCallback        = null;   // destination handler of output command
    private CallbackContext mWlCb           = null;   // access to web layer 
    
    private TFParams tfParams               = null;
    private TF tf                           = null;
    
    // data to store calculated CEPSTRA: float[nMaxSpeechLengthFrames][nScores*3]
    private int nColumns                    = 0;    // indicates the number of received params (24*3 for filters)...]
    private int nMaxSpeechLengthFrames      = 0;    // max speech length in frames
    private float[][] faCalculatedCepstra   = null;   // contains (MAXnframes, numparams) calculated FilterBanks...array storing the calculated cepstra
    
    private int nProcessedFrames            = 0;      

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
        tf.setParams(params);
    }
    public void setWlCb(CallbackContext wlcb)
    {
        mWlCb = wlcb;   
        tf.setWlCb(wlcb);
    }
    public void setCallbacks(Handler cb)
    {
        mStatusCallback     = cb;        
        mCommandCallback    = cb;        
        mResultCallback     = cb;  
        tf.setCallbacks(cb);
    }
    public void setCallbacks(Handler scb, Handler ccb, Handler rcb)
    {
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;  
        tf.setCallbacks(scb, ccb, rcb);
    }
    public void setExtraParams(int maxspeechframes, int ncolumns)
    {
        nMaxSpeechLengthFrames  = maxspeechframes;
        nColumns                = ncolumns;
        Messaging.sendDataToHandler(mInternalHandler, ENUMS.TF_CMD_CLEAR, nMaxSpeechLengthFrames, nColumns);
    }
//--------------------------------------------------------------------------------------------------    
    public void init(TFParams params, Handler cb)
    {
        tfParams            = params;
        mStatusCallback     = cb;        
        mCommandCallback    = cb;        
        mResultCallback     = cb;  
        init(params, cb, cb, cb);
    }    
    public void init(TFParams params, Handler cb, CallbackContext wlcb)
    {
        mWlCb       = wlcb; 
        init(params, cb, wlcb);
    }    
    public void init(TFParams params, Handler scb, Handler ccb, Handler rcb)
    {
        tfParams            = params;
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;   
        tf                  = new TF(tfParams, scb, ccb, rcb);
    }
    public void init(TFParams params, Handler scb, Handler ccb, Handler rcb, int maxspeechframes, int ncolumns)
    {
        init(params, scb, ccb, rcb);
        nMaxSpeechLengthFrames  = maxspeechframes;
        nColumns                = ncolumns;
        Messaging.sendDataToHandler(mInternalHandler, ENUMS.TF_CMD_CLEAR, nMaxSpeechLengthFrames, nColumns);
    }
    public void init(TFParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcb)
    {
        mWlCb               = wlcb;
        tfParams            = params;
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;   
        tf                  = new TF(tfParams, scb, ccb, rcb, wlcb);
    }        
    public void init(TFParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcb, int maxspeechframes, int ncolumns)
    {
        init(params, scb, ccb, rcb, wlcb);
        nMaxSpeechLengthFrames  = maxspeechframes;
        nColumns                = ncolumns;
        Messaging.sendDataToHandler(mInternalHandler, ENUMS.TF_CMD_CLEAR, nMaxSpeechLengthFrames, nColumns);
    }        
    //===============================================================================================
    // wrapper to thread execution 
    //===============================================================================================
    // GET FROM folder or a file
    public void recognize(float[] data)
    {
        Bundle bundle  = new Bundle();
        bundle.putFloatArray("data", data);

        Message message = mInternalHandler.obtainMessage();
        message.setData(bundle);
        message.what    = ENUMS.TF_CMD_RECOGNIZE;
        mInternalHandler.sendMessage(message);
    }    
  
    // load model
    public boolean loadModel()
    {
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.TF_CMD_LOADMODEL;
        mInternalHandler.sendMessage(message);
        return true;
    }    
  
    // recognize from a file with contexted frames cepstras
    public boolean recognizeCepstraFile(String cepstra_file_path, CallbackContext wlcb)
    {
        tf.setWlCb(wlcb);
        Bundle bundle  = new Bundle();        
        bundle.putString("file", cepstra_file_path);    
        
        Message message = mInternalHandler.obtainMessage();
        message.setData(bundle);        
        message.what    = ENUMS.TF_CMD_RECOGNIZE_FILE;
        mInternalHandler.sendMessage(message);
        return true;        
    }    
    //===============================================================================================
    // INTERNAL PROCESSING
    //===============================================================================================

    private void clearData(int row, int col)
    {
        nProcessedFrames    = 0;
        faCalculatedCepstra = new float[row][col];
    }    

    // check if the number of the here stored frames coincides with the number of frames passed from MFCCHT
    private boolean checkData(int expectedFrames)
    {
        boolean res;
        String strmsg;
        if(expectedFrames == nProcessedFrames)
        {
            strmsg  = "TF_CMD_RECOGNIZE: sample OK !! => processed frames: " + String.valueOf(nProcessedFrames) + ", expected frames: " + String.valueOf(expectedFrames);
            res     = true;
        }
        else
        {
            strmsg  = "TF_CMD_RECOGNIZE: sample ERROR, frames: " + String.valueOf(nProcessedFrames) + " expected frames: " + String.valueOf(expectedFrames);
            res     = false;
        }
        Log.d(LOG_TAG, strmsg);         
        return res;
    }    
    //================================================================================================================
    @Override
    public boolean handleMessage(Message msg) 
    {
        Bundle bundle = msg.getData();
        int nframes;
        float[] data = null;
        float[][] cepstra = null;
        switch((int)msg.what)
        {
            case ENUMS.TF_CMD_CLEAR:  
                int row,col;
                if(msg.arg1 != 0 && msg.arg2 != 0)
                {
                    row = msg.arg1;
                    col = msg.arg2;
                }
                else
                {
                    row = nMaxSpeechLengthFrames;
                    col = nColumns;                    
                }    
                clearData(row, col);
                break;
                
            case ENUMS.TF_CMD_RECOGNIZE:  
                nframes             = bundle.getInt("nframes");
                boolean res         = checkData(nframes);
                if(true)            tf.doRecognize(faCalculatedCepstra, nProcessedFrames);// TODO: decide what to do whether the frames do not correspond
                break;
                
            case ENUMS.TF_CMD_RECOGNIZE_FILE:  
                String cepstra_file = bundle.getString("file");
                try
                {
                    cepstra             = FileUtilities.read2DArrayFromFile(cepstra_file);
                    if(true)            tf.doRecognize(cepstra, cepstra.length);// TODO: decide what to do whether the frames do not correspond                    
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }  
                break;
                
            case ENUMS.TF_CMD_LOADMODEL:  
                tf.loadModel();
                break;
                
            case ENUMS.TF_CMD_NEWCEPSTRA:  
                    
                data                = bundle.getFloatArray("data");
                nframes             = bundle.getInt("nframes");
                int nparams         = bundle.getInt("nparams");
                cepstra             = Messaging.deFlattenArray(data, nframes, nparams);
                
                // store calculated cepstra in its buffer
                for(int f=0; f<nframes; f++) System.arraycopy(cepstra[f], 0, faCalculatedCepstra[nProcessedFrames+f], 0, nparams);   

//                try{
//                String outfile      = "AllSpeak/audiofiles/temp/cepstra.dat";
//                FileUtilities.write2DArrayToFile(cepstra, nframes, outfile, "%.4f", true);
//                }catch(Exception e){e.printStackTrace();};
                
                    nProcessedFrames += nframes;
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