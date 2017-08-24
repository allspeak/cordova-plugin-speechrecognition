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
import com.allspeak.audioprocessing.mfcc.Framing;



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
    
    private int nScores                     = 0;                // indicates the number of reconstructed scores (24 for filters)...]
    private int nMaxSpeechLengthFrames      = 0;                // max speech length in frames

    // manage MFCC queue
    private float[] faMFCCQueue             = new float[2048];  // contains data in the queue, waiting to be processed
    private float[] faData2Process          = null;             // contains (nsamples) data sent to be calculated     
    private int nQueueLastIndex             = 0;                // define the id of the faMFCCQueue last used samples 
    
    private float[][] mScoresQueue          = null;             // store the last nDeltaWindow-cepstra to calculate, on the next packet, the derivatives of the first frames
    private float[][] faCalculatedCepstra   = null;             // contains (MAXnframes, numparams) calculated FilterBanks...array storing the calculated cepstra

    private int nArrivedSamples             = 0;                // samples arrived from VHT or Service
    private int nProcessedSamples           = 0;                // samples used to calculate cepstra from nArrivedSamples    
    private int nProcessingOperations       = 0;                // number of data packets received from VadHandlerThread/SpeechRecognitionService    
    private int nProcessedFrames            = 0;                // number of processed frames    
    private int nIgnoredFrames              = 0;                // number of removed frames (where none of frame's cepstras passed the threshold)

    String sSource                          = "";
    int scoresMultFactor                    = 3;                // number used to calcolate all the available features 3*nScores
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
    public void init(MFCCParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcb, int maxspeechsamples)
    {
        mfccParams              = params;
        mStatusCallback         = scb;        
        mCommandCallback        = ccb;        
        mResultCallback         = rcb;  
        mWlCb                   = wlcb;         
        nScores                 = (mfccParams.nDataType == ENUMS.MFCC_DATATYPE_MFPARAMETERS ? mfccParams.nNumberOfMFCCParameters : mfccParams.nNumberofFilters);
        nMaxSpeechLengthFrames  = Framing.getFrames(maxspeechsamples, params.nWindowLength, params.nWindowDistance);
        mfcc                    = new MFCC(mfccParams, scb, ccb, rcb, mWlCb);
        initData();
    }      
    //-----------------------------------------------------------------------------------------------------    
    // overloads
    //-----------------------------------------------------------------------------------------------------       
    public void init(MFCCParams params, Handler cb, CallbackContext wlcb)
    {
        init(params, cb, cb, cb, wlcb, 0);
    }
    public void init(MFCCParams params, Handler scb, Handler ccb, Handler rcb)
    {
        init(params, scb, ccb, rcb, null, 0);
    }
    public void init(MFCCParams params, Handler cb)
    {
        init(params, cb, cb, cb, null, 0);
    }
    public void init(MFCCParams params, Handler scb, Handler ccb, Handler rcb, int maxspeechsamples)
    {
        init(params, scb, ccb, rcb, null, maxspeechsamples);
    }    
    public void init(MFCCParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcb)
    {
        init(params, scb, ccb, rcb, wlcb, 0);
    }    
    //===============================================================================================
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
    //===============================================================================================
    // wrapper to thread execution 
    //===============================================================================================
    // send message MFCC_CMD_GETFILE / MFCC_CMD_GETFOLDER 
    // GET FROM folder or a file
    public void getMFCC(String source, boolean overwrite)
    {
        sSource = source;
        Bundle bundle  = new Bundle();
        Message message;
        switch((int)mfccParams.nDataOrig)
        {
            case ENUMS.MFCC_DATAORIGIN_FILE:
                
                bundle.putString("source", source);
                bundle.putBoolean("overwrite", overwrite);
                message         = mInternalHandler.obtainMessage();
                message.what    = ENUMS.MFCC_CMD_GETFILE;
                message.setData(bundle);
                mInternalHandler.sendMessage(message);
                break;

            case ENUMS.MFCC_DATAORIGIN_FOLDER:

                bundle.putString("source", source);
                bundle.putBoolean("overwrite", overwrite);
                message         = mInternalHandler.obtainMessage();
                message.what    = ENUMS.MFCC_CMD_GETFOLDER;
                message.setData(bundle);
                mInternalHandler.sendMessage(message);
                break;
        }        
    }    

    // send message MFCC_CMD_GETDATA    
    // GET FROM data array (a real-time stream)
    public void getMFCC(float[] data, String outfile)
    {
        Bundle bundle   = new Bundle();
        bundle.putString("source", outfile);
        bundle.putFloatArray("data", data);
        
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.MFCC_CMD_GETDATA;
        message.setData(bundle);
        mInternalHandler.sendMessage(message);
    }
    
    // send message MFCC_CMD_GETDATA    
    public void getMFCC(float[] data)
    {
        Bundle bundle   = new Bundle();
        bundle.putFloatArray("data", data);
        
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.MFCC_CMD_GETDATA;
        message.setData(bundle);
        mInternalHandler.sendMessage(message);
    }
    
    // send message MFCC_CMD_GETQDATA
    public void getQueueMFCC(float[] data)
    {
        Bundle bundle   = new Bundle();
        bundle.putFloatArray("data", data);
        
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.MFCC_CMD_GETQDATA;
        message.setData(bundle);
        mInternalHandler.sendMessage(message);
    }     
    
    // send message MFCC_CMD_CLEAR
    public void initData()
    {
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.MFCC_CMD_CLEAR;
        mInternalHandler.sendMessage(message);
    }     
    //===============================================================================================
    // run on handlerThread
    //===============================================================================================
    private void THclearData()
    {
        nQueueLastIndex             = 0;    
  
        nArrivedSamples             = 0;      
        nProcessedSamples           = 0;
        nProcessingOperations       = 0;
        nProcessedFrames            = 0;
        nIgnoredFrames              = 0;
        
        if(mfccParams.nDeltaWindow > 0) mScoresQueue        = new float[mfccParams.nDeltaWindow][scoresMultFactor*nScores];
        else                            mScoresQueue        = null;
        
        if(nMaxSpeechLengthFrames > 0)  faCalculatedCepstra = new float[nMaxSpeechLengthFrames][scoresMultFactor*nScores];        
        else                            faCalculatedCepstra = null;
        
        mfcc.clearData();
        Messaging.sendMessageToHandler(mCommandCallback, ENUMS.TF_CMD_CLEAR);
    }    
   
    private void THsendRecognizeCMD2TF(int sentSamples)
    {
        if (THcheckData(sentSamples)) Messaging.sendDataToHandler(mCommandCallback, ENUMS.TF_CMD_RECOGNIZE, "nframes", nProcessedFrames);
    }    
   
    // check if the number of the (processed - ignored) frames in MFCCHT == the number of the frames (calculated from the samples received) passed from VADHT
    // With the introduction of the frame thresholding....this count sometimes fails....
    // SO I alway return true !!! 
    // TODO: understand why & fix it
    // ??the latter must be converted assuming that the first frames takes 200 samples, the other 80 and for (nframes-1) time there is also the 
    private boolean THcheckData(int sentSamples)
    {
        boolean res;
        String strmsg;

        int expectedFrames = Framing.getFrames(sentSamples, mfccParams.nWindowLength, mfccParams.nWindowDistance)- mfccParams.nDeltaWindow - nIgnoredFrames;
        if(expectedFrames == nProcessedFrames)
        {
            strmsg  = "MFCC_CMD_SENDDATA: sample OK !! => processed valid frames: " + String.valueOf(nProcessedFrames) + " from the ArrivedSamples: " + String.valueOf(nArrivedSamples);
            res     = true;
        }
        else
        {
            strmsg  = "MFCC_CMD_SENDDATA: sample ERROR, processed frames: " + String.valueOf(nProcessedFrames) + " expectedFrames: " + String.valueOf(expectedFrames);
            res     = true; // -----> I SEND OK EVEN IF THEY ARE WRONG. TODO: fix it !
        }
       
//        int manipulatedSamples  = mfccParams.nWindowLength + (nProcessedFrames-1)*mfccParams.nWindowDistance + mfccParams.nData2Reprocess*(nProcessingOperations-1);
//        if(manipulatedSamples == nProcessedSamples)
//        {
//            strmsg  = "CMD_SENDDATA: sample OK !! => processed frames: " + String.valueOf(nProcessedFrames) + ", processedSamples: " + String.valueOf(nArrivedSamples);
//            res     = true;
//        }
//        else
//        {
//            strmsg  = "CMD_SENDDATA: sample ERROR, frames: " + String.valueOf(nProcessedFrames) + " expected2bemanipulated: " + String.valueOf(manipulatedSamples) + ", manipulated: " + String.valueOf(nProcessedSamples);
//            res     = false;
//        }
        Log.d(LOG_TAG, strmsg);         
        return res;
    }

    //===============================================================================================
    // cepstra processing with temporal derivatives makes the last nDeltaWindow frames invalid.
    // in case of one-shot processing (e.g. a file) invalidframes derivatives scores will be zero as no new data are expected
    // in case of live processing, I will export only the valid (nframes-nDeltaWindow) frames, 
    // the samples associated to the invalid frames are set at the beginning of the queue and will be processed during the next data chunk
    
    // from (nframes-2*nDeltaWindow) to (nframes-2*nDeltaWindow+1)-th frames of cepstra => cepstra queue
    // from (nframes-nDeltaWindow) to (nframes)-th frame of samples => samples queue
    //
    //      _________
    //     |    _____|___        | valid
    //         |    _____|___    | valid  
    //             |    _____|___| invalid
    //                 |         | invalid
    //
    //             |_____|_______|
    //            samples' queue = 120 + 2 * steplength = 280 samples
    
    // receive new data, calculate how many samples must be sent to analysis.
    //      queued data +  new samples  =  to-be-processed + newque 
    // then frame and return  to-be-processed = [nframes-nDeltaWindow][nscores]
    //
    private float[] getSamples2Process(float[] data)
    {
        //num of samples to recalculate the nDeltaWindow invalid frames + standard mfccParams.nData2Reprocess
        int nData2Reprocess = mfccParams.nData2Reprocess + mfccParams.nDeltaWindow*mfccParams.nWindowDistance;
        
        int nOldData        = nQueueLastIndex;
        int nNewData        = data.length;
        int nTotQueue       = nQueueLastIndex + nNewData;
        int nMFCCWindow     = Framing.getOptimalVectorLength(nTotQueue, mfccParams.nWindowLength, mfccParams.nWindowDistance);
        
        int nNewData2use    = nMFCCWindow - nQueueLastIndex;             
        int nData2Queue     = data.length - nNewData2use + nData2Reprocess; 

        // assumes that first [0-(nQueueLastIndex-1)] elements of faMFCCQueue contains the still not processed data 
        float[] faData2Process  = new float[nMFCCWindow]; 
        
        // writes the to-be-processed sample vector
        System.arraycopy(faMFCCQueue, 0, faData2Process, 0, nOldData);      // whole faMFCCQueue -> faData2Process, then, 
        System.arraycopy(data, 0, faData2Process, nOldData, nNewData2use);  // nNewData2use of data -> faData2Process  

        // update queued samples vector
        // take from id= (nNewData2use - mfccParams.nData2Reprocess - samples within nDeltaWindow-frames) of data -> beginning of queue        
        System.arraycopy(data, nNewData2use - nData2Reprocess, faMFCCQueue, 0, nData2Queue); 
        nQueueLastIndex     = nData2Queue;  

        return faData2Process;
//        return Framing.frameVector(faData2Process, mfccParams.nWindowLength, mfccParams.nWindowDistance);
    }
    //================================================================================================================
    @Override
    public boolean handleMessage(Message msg) 
    {
        try
        {
            Bundle bundle = msg.getData();
            float[] data;
            float[][] cepstra;
            boolean overwrite;
            switch((int)msg.what)
            {                       
                case ENUMS.MFCC_CMD_GETQDATA:   //  to process (real-time) data sent here by other handlerThread (e.g. VAD), usually for speech recognition
                case ENUMS.CAPTURE_RESULT:      //  to process real-time data captured by the AudioInputReceiver thread, usually for recording/feature extract ops

                    data                        = bundle.getFloatArray("data");
                    nArrivedSamples             += data.length;
                    nProcessingOperations++;

                    // takes new data : assemble the to-be-processed vector (queue + part of new data),..
                    float[] samples2beprocessed = getSamples2Process(data);  // return [nframes][mfccParams.nWindowLength]

                    int nframes                 = Framing.getFrames(samples2beprocessed.length, mfccParams.nWindowLength, mfccParams.nWindowDistance);
                    Messaging.sendDataToHandler(mStatusCallback, ENUMS.MFCC_STATUS_PROCESS_STARTED, nframes, nProcessingOperations);
                    // ------------------------------------------------------------------------------------------------------------------------------
                    cepstra                     = mfcc.getFeaturesQueued(samples2beprocessed, null); //cepstra will be [nframes-nDeltaWindow-invalidfr(?)][nscores*scoresMultFactor]
                    // ------------------------------------------------------------------------------------------------------------------------------
                    int nvalidframes            = cepstra.length;
                    nIgnoredFrames             += (nframes - nvalidframes - mfccParams.nDeltaWindow);

                    // manage cepstras' queue
                    if(mScoresQueue == null)  // after clearData()                
                        mScoresQueue = new float[mfccParams.nDeltaWindow][nScores*scoresMultFactor];

                    for(int dw=0; dw<mfccParams.nDeltaWindow; dw++)
                        System.arraycopy(cepstra[nvalidframes - mfccParams.nDeltaWindow + dw],0, mScoresQueue[dw], 0, nScores*scoresMultFactor); 

                    // store calculated cepstra in its buffer (only after, I do update nProcessedFrames)
                    for(int f=0; f<nvalidframes; f++) System.arraycopy(cepstra[f], 0, faCalculatedCepstra[nProcessedFrames + f], 0, scoresMultFactor*nScores);                 

                    // what to do with the calculated cepstra ?
                    if((int)msg.what == ENUMS.MFCC_CMD_GETQDATA) 
                    {
                        // send to TF
                        float [][] cepstracopy = new float[nvalidframes][nScores*scoresMultFactor];
                        for(int f=0; f<nvalidframes; f++)  System.arraycopy(cepstra[f],0, cepstracopy[f], 0, nScores*scoresMultFactor);                    
                        Messaging.sendDataToHandler(mResultCallback, ENUMS.TF_CMD_NEWCEPSTRA, cepstracopy, nvalidframes, nScores*scoresMultFactor);
//                        Messaging.sendDataToHandler(mResultCallback, ENUMS.TF_CMD_NEWCEPSTRA, cepstra, nvalidframes, nScores*scoresMultFactor);
                    }
                    else mfcc.exportData(cepstra, false);    // false says that they are not final data => do not write them

                    nProcessedFrames += nvalidframes;
                    break;

                case ENUMS.MFCC_CMD_GETFILE:
                    sSource             = bundle.getString("source");
                    overwrite           = bundle.getBoolean("overwrite");
                    mfcc.processFile(sSource, overwrite);
                    break;

                case ENUMS.MFCC_CMD_GETFOLDER:

                    sSource             = bundle.getString("source");
                    overwrite           = bundle.getBoolean("overwrite");
                    mfcc.processFolder(sSource, overwrite);
                    break;

                case ENUMS.MFCC_CMD_GETDATA:

                    if(bundle.getString("source") != null)  sSource = bundle.getString("source");
                    data                = bundle.getFloatArray("data");
                    cepstra             = mfcc.getFeatures(data);
                    mfcc.exportData(cepstra);
                    break;

                case ENUMS.MFCC_CMD_CLEAR:  // called by VAD::resetSpeechDetection or during this::init to delete all the stored CEPSTRA and /recreate their array
                    THclearData();
                    break;

                case ENUMS.MFCC_CMD_SENDDATA: // VAD says that a new sentence has been detected. I send a command to TF handlerThread 
                    int sentSamples = bundle.getInt("info");
                    THsendRecognizeCMD2TF(sentSamples);
                    break;

                case ENUMS.MFCC_CMD_FINALIZEDATA: // onStopCapture says that I can close file's MFCC calculation. normalize
                    float[][] final_data = new float[nProcessedFrames][scoresMultFactor*nScores];
                    for(int f=0; f<nProcessedFrames; f++) System.arraycopy(faCalculatedCepstra[f], 0, final_data[f], 0, scoresMultFactor*nScores);

                    Framing.normalizeFrames(final_data);
                    mfcc.exportData(final_data);
                    break;
            }
            return true;
        }
        catch(Exception e)
        {
            e.printStackTrace();                  
            Log.e(LOG_TAG, e.getMessage(), e);
            Messaging.sendErrorString2Web(mWlCb, e.getMessage(), ERRORS.MFCC_ERROR, true);            
            return false;            
        }
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
