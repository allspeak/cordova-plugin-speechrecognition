package com.allspeak.audioprocessing.mfcc;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Bundle;

import org.apache.cordova.CallbackContext;
import android.util.Log;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;

import com.allspeak.utility.Messaging;

// not necessary
import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.audioprocessing.mfcc.MFCC;
import com.allspeak.audioprocessing.mfcc.MFCCCalcJAudio;


/*
it's a layer which call the MFCC functions on a new thread
sends the following messages to Plugin Activity:
- data
- processing started
- progress_file
- progress_folder
- error
*/

public class MFCCHandlerThread extends HandlerThread implements Handler.Callback
{
    private static final String LOG_TAG = "MFCCHandlerThread";
    
    private Handler mInternalHandler        = null;   // manage internal messages
    private Handler mStatusCallback         = null;   // destination handler of status messages
    private Handler mResultCallback         = null;   // destination handler of data result
    private Handler mCommandCallback        = null;   // destination handler of output command
    private CallbackContext mWlCb           = null;   // access to web layer 
    
    private MFCC mfcc                       = null;
    private MFCCParams mfccParams           = null;
    
    private int nScores                     = 0;                // indicates the number of reconstructed scores (22 for filters)...]
    private int nMaxSpeechLengthFrames      = 0;                // max speech length in frames

    // manage MFCC queue
    private float[] faMFCCQueue             = new float[2048];  // contains data in the queue, waiting to be processed
    private float[] faData2Process          = null;             // contains (nsamples) data sent to be calculated     
    private int nQueueLastIndex             = 0;                // define the id of the faMFCCQueue last used samples 
    
    private int nProcessingOperations       = 0;                // number of data packets received from VadHandlerThread    
    private int nArrivedSamples             = 0;                // samples arrived from VadHandlerThread
    private int nManipulatedSamples         = 0;                // samples used to calculate cepstra from nArrivedSamples    
    private int nProcessedFrames            = 0;                // number of calculated frames    

    
    String sSource                          = "";
    
    Bundle bundle;
    //================================================================================================================
    public MFCCHandlerThread(String name)
    {
        super(name);
    }
    public MFCCHandlerThread(String name, int priority)
    {
        super(name, priority);
    }
    //===============================================================================================
    public void setParams(MFCCParams params)
    {
        mfccParams  = params;
        nScores     = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccParams.nNumberOfMFCCParameters : mfccParams.nNumberofFilters);
        mfcc.setParams(params);
    }
    public void setWlCb(CallbackContext wlcb)
    {
        mWlCb = wlcb;        
        mfcc.setWlCb(wlcb);
    }
    public void setCallbacks(Handler cb)
    {
        mStatusCallback = cb;        
        mCommandCallback = cb;        
        mResultCallback = cb;        
        mfcc.setCallbacks(cb);
    }
    public void setCallbacks(Handler scb, Handler ccb, Handler rcb)
    {
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;        
        mfcc.setCallbacks(mStatusCallback, mCommandCallback, mResultCallback);
    }
//--------------------------------------------------------------------------------------------------
    public void init(MFCCParams params, Handler cb)
    {
        mfccParams          = params;
        mStatusCallback     = cb;        
        mCommandCallback    = cb;        
        mResultCallback     = cb;  
        nScores             = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccParams.nNumberOfMFCCParameters : mfccParams.nNumberofFilters);
        mfcc                = new MFCC(mfccParams, cb, mWlCb);
    }
    public void init(MFCCParams params, Handler scb, Handler ccb, Handler rcb)
    {
        mfccParams          = params;
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;   
        nScores             = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccParams.nNumberOfMFCCParameters : mfccParams.nNumberofFilters);
        mfcc                = new MFCC(mfccParams, scb, ccb, rcb, mWlCb);
    }
    public void init(MFCCParams params, Handler scb, Handler ccb, Handler rcb, int maxspeechsamples)
    {
        init(params, scb, ccb, rcb);
        nMaxSpeechLengthFrames  = MFCCCalcJAudio.getFrames(maxspeechsamples, params.nWindowLength, params.nWindowDistance);
    }    
    public void init(MFCCParams params, Handler cb, CallbackContext wlcb)
    {
        init(params, cb);
        mWlCb       = wlcb; 
    }
    public void init(MFCCParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcb)
    {
        init(params, scb, ccb, rcb);
        mWlCb       = wlcb;
    }    
    public void init(MFCCParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcb, int maxspeechsamples)
    {
        init(params, scb, ccb, rcb, wlcb);
        nMaxSpeechLengthFrames  = MFCCCalcJAudio.getFrames(maxspeechsamples, params.nWindowLength, params.nWindowDistance);
    }    
    //===============================================================================================
    // wrapper to thread execution 
    //===============================================================================================
    // GET FROM folder or a file
    public void getMFCC(String source)
    {
        sSource = source;
        bundle  = new Bundle();
        Message message;
        switch((int)mfccParams.nDataOrig)
        {
            case ENUMS.MFCC_DATAORIGIN_FILE:
                
                bundle.putString("source", source);
                message         = mInternalHandler.obtainMessage();
                message.what    = ENUMS.MFCC_CMD_GETFILE;
                message.setData(bundle);
                mInternalHandler.sendMessage(message);
                break;

            case ENUMS.MFCC_DATAORIGIN_FOLDER:

                bundle.putString("source", source);
                message         = mInternalHandler.obtainMessage();
                message.what    = ENUMS.MFCC_CMD_GETFOLDER;
                message.setData(bundle);
                mInternalHandler.sendMessage(message);
                break;
        }        
    }    

    // GET FROM data array (a real-time stream)
    public void getMFCC(float[] data, String outfile)
    {
        bundle          = new Bundle();
        bundle.putString("source", outfile);
        bundle.putFloatArray("data", data);
        
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.MFCC_CMD_GETDATA;
        message.setData(bundle);
        mInternalHandler.sendMessage(message);
    }
    
    public void getMFCC(float[] data)
    {
        bundle          = new Bundle();
        bundle.putFloatArray("data", data);
        
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.MFCC_CMD_GETDATA;
        message.setData(bundle);
        mInternalHandler.sendMessage(message);
    }
    
    public void getQueueMFCC(float[] data)
    {
        bundle          = new Bundle();
        bundle.putFloatArray("data", data);
        
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.MFCC_CMD_GETQDATA;
        message.setData(bundle);
        mInternalHandler.sendMessage(message);
    }     
    
    //===============================================================================================
    // receive new data, calculate how many samples must be processed and send them to analysis. 
    // copy the last 120 samples of these to-be-processed data plus the remaining still-to-be-processed ones to the queue array
    private int arrangeDataInQueue(float[] data)
    {
        int nOldData        = nQueueLastIndex;
        int nNewData        = data.length;
        int nTotQueue       = nQueueLastIndex + nNewData;
        int nMFCCWindow     = MFCCCalcJAudio.getOptimalVectorLength(nTotQueue, mfccParams.nWindowLength, mfccParams.nWindowDistance);
        int nData2take      = nMFCCWindow - nQueueLastIndex;             
        int nData2Store     = data.length - nData2take + mfccParams.nData2Reprocess; 

        // assumes that first [0-(nQueueLastIndex-1)] elements of faMFCCQueue contains the still not processed data 
        faData2Process      = new float[nMFCCWindow]; 
        
//        Log.d(LOG_TAG, "start to process # samples: " + Integer.toString(nMFCCWindow));

        // writes the to-be-processed vector
        System.arraycopy(faMFCCQueue, 0, faData2Process, 0, nOldData);  // whole faMFCCQueue => mfccvector, then, 
        System.arraycopy(data, 0, faData2Process, nOldData, nData2take);// first nData2Take of data => mfccvector  

        // update queue vector
        // take from id= (nData2take - mfccParams.nData2Reprocess) of data => beginning of queue        
        System.arraycopy(data, nData2take - mfccParams.nData2Reprocess, faMFCCQueue, 0, nData2Store); 
        nQueueLastIndex = nData2Store;  

        return MFCCCalcJAudio.getFrames(nMFCCWindow, mfccParams.nWindowLength, mfccParams.nWindowDistance);
    }
    
    private void clearData()
    {
        nQueueLastIndex             = 0;    
        nProcessedFrames            = 0;      
        nArrivedSamples             = 0;      
        nManipulatedSamples         = 0;
        nProcessingOperations       = 0;
        mfcc.clearData();
        Messaging.sendMessageToHandler(mCommandCallback, ENUMS.TF_CMD_CLEAR);
    }    
   
    private void sendRecognizeCMD2TF(int sentSamples)
    {
        if (checkData(sentSamples)) Messaging.sendDataToHandler(mCommandCallback, ENUMS.TF_CMD_RECOGNIZE, "nframes", nProcessedFrames);
    }    
   
    // check if the number of the processed samples in MFCCHT coincides with the number of the samples passed from VADHT
    // the latter must be converted assuming that the first frames takes 200 samples, the other 80 and for (nframes-1) time there is also the 
    private boolean checkData(int sentSamples)
    {
        boolean res;
        String strmsg;

        int expectedFrames = MFCCCalcJAudio.getFrames(sentSamples, mfccParams.nWindowLength, mfccParams.nWindowDistance);
        if(expectedFrames == nProcessedFrames)
        {
            strmsg  = "MFCC_CMD_SENDDATA: sample OK !! => processed frames: " + String.valueOf(nProcessedFrames) + ", processedSamples: " + String.valueOf(nArrivedSamples);
            res     = true;
        }
        else
        {
            strmsg  = "MFCC_CMD_SENDDATA: sample ERROR, processed frames: " + String.valueOf(nProcessedFrames) + " expectedFrames: " + String.valueOf(expectedFrames);
            res     = false;
        }
       
        int manipulatedSamples  = mfccParams.nWindowLength + (nProcessedFrames-1)*mfccParams.nWindowDistance + mfccParams.nData2Reprocess*(nProcessingOperations-1);

//        if(manipulatedSamples == nManipulatedSamples)
//        {
//            strmsg  = "CMD_SENDDATA: sample OK !! => processed frames: " + String.valueOf(nProcessedFrames) + ", processedSamples: " + String.valueOf(nArrivedSamples);
//            res     = true;
//        }
//        else
//        {
//            strmsg  = "CMD_SENDDATA: sample ERROR, frames: " + String.valueOf(nProcessedFrames) + " expected2bemanipulated: " + String.valueOf(manipulatedSamples) + ", manipulated: " + String.valueOf(nManipulatedSamples);
//            res     = false;
//        }
        Log.d(LOG_TAG, strmsg);         
        return res;
    }
    //================================================================================================================
    @Override
    public boolean handleMessage(Message msg) 
    {
        Bundle bundle = msg.getData();
        float[] data;
        switch((int)msg.what)
        {                       

            case ENUMS.MFCC_CMD_GETFILE:
                sSource         = bundle.getString("source");
                mfcc.processFile(sSource);
                break;

            case ENUMS.MFCC_CMD_GETFOLDER:
                
                sSource             = bundle.getString("source");
                mfcc.processFolder(sSource);
                break;

            case ENUMS.MFCC_CMD_GETDATA:
                
                data                = bundle.getFloatArray("data");
                if(bundle.getString("source") != null)
                    sSource         = bundle.getString("source");
                mfcc.processRawData(data);
                break;

            case ENUMS.MFCC_CMD_GETQDATA:  
            case ENUMS.CAPTURE_RESULT:  //  to process real-time data captured by the AudioInputReceiver thread
                    
                data                = bundle.getFloatArray("data");
                nArrivedSamples     += data.length;
                nProcessingOperations++;
                
                int nframes         = arrangeDataInQueue(data); // takes new data and copy to faMFCCQueue & faData2Process ..return number of calculated frames
                
//                Messaging.sendDataToHandler(mStatusCallback, ENUMS.MFCC_STATUS_PROCESS_STARTED,"nframes", nframes);
                float[][] cepstra   = mfcc.processRawData(faData2Process);
                int newframes       = cepstra.length;
                if(nframes != newframes)
                {
                    Messaging.sendMessageToHandler(mStatusCallback, ERRORS.MFCC_ERROR, "error", "MFCCHandlerThread::handleMessage::MFCC_CMD_GETQDATA");
                    return false;
                }
                    
                nManipulatedSamples += faData2Process.length;
                nProcessedFrames    += newframes;
                
                Messaging.sendDataToHandler(mResultCallback, ENUMS.TF_CMD_NEWCEPSTRA, cepstra, newframes, nScores*3);
                break;
                
            case ENUMS.MFCC_CMD_CLEAR:  // called by VAD or during this::init to delete all the stored CEPSTRA
                clearData();
                break;
                
            case ENUMS.MFCC_CMD_SENDDATA: // VAD says that a new sentence has been detected. I send a command to TF handlerThread 
                int sentSamples = bundle.getInt("info");
                sendRecognizeCMD2TF(sentSamples);
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
        if(mInternalHandler == null)   Log.w(LOG_TAG, "MFCCHandlerThread mInternalHandler is NULL !!!!!!!!!!!!!!!!!");
        return mInternalHandler;
    }        
    //================================================================================================================
}
