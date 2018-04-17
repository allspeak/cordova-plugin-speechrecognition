/**
 *
 * Copyright Edin Mujkanovic 2016
 *
 * Author: Edin Mujkanovic
 * Created: 2016-09-09
 *
 * Description:
 * Speech detection library used to detect and capture speech from an incoming audio stream of data.
 * Currently only supports cordova-plugin-audioinput as audio input, but I plan to also support MediaStreamSource in the future.
 *
 * License:
 * MIT
 *
 * Author: Alberto Inuggi
 * Created: 2017-01-20 
 * 
 * Java porting of the Speech detection library.

 */

package com.allspeak.audioprocessing.vad;

import android.os.Handler;
import android.util.Log;
import org.apache.cordova.CallbackContext;
//import com.allspeak.BuildConfig;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import com.allspeak.utility.Messaging;
import com.allspeak.audiocapture.AudioInputCapture;
import com.allspeak.audiocapture.CaptureParams;
import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.audioprocessing.vad.VADParams;
import com.allspeak.audiocapture.CaptureParams;
import com.allspeak.audioprocessing.WavFile;
import java.io.File;

public class VAD 
{
    private static final String LOG_TAG = "VAD";
    
    private VADParams mVadParams;
    private CaptureParams mCfgParams;
    private MFCCParams mMfccParams;
    
    private Handler mStatusCallback                     = null;     // Thread
    private Handler mCommandCallback                    = null;     // Thread
    private Handler mResultCallback                     = null;     // Thread
    private CallbackContext callbackContext             = null;    

    
    private int         nSilentIterations               = 50;
    private int         nOfEventsContinue               = 0;
    private int         nOfEventsStart                  = 0;
    private int         nOfEventsStop                   = 0;
    private int         nOfEventsMax                    = 0;
    private int         nOfEventsMin                    = 0;

    private boolean     bCaptureRunning                 = false;

    private int         nMaxSpeechLengthSamples         = 0; // determines faCurrentSpeech length
    private float[]     faCurrentSpeech;
    private int         nCurrentSpeechLength            = 0;  // incremented by appendSpeech, used as: if(nCurrentSpeechLength + 1 > nSpeechMaximumLengthChunks) maximumLengthSpeechEvent();
    private int         nCurrentSpeechSamples           = 0;  // incremented by appendSpeech when SAVE_DATA is requested

    private int         nAfterSpeechPeriod              = 0;  // used as following: if (nAfterSpeechPeriod > nSpeechAllowedDelayChunks) stopSpeechEvent(); 

    private float       fLastAudioLevel                 = -50; 
    private float       fCurrentThreshold               = 0;
    private float       fAmbientTotal                   = 0;
    private float       fAmbientAverageLevel            = 0;

    private int         nAnalysisBufferSize             = 0;
    private int         nOfAnalysisBuffersPerIteration  = 0;
    private float       fAnalysisBufferLengthInS        = 0;
    private float       fAnalysisBufferLengthInMs       = 0;
    
    private int         nSpeechAllowedDelayChunks       = 0;
    private int         nSpeechMinimumLengthChunks      = 0;
    private int         nSpeechMaximumLengthChunks      = 0;
    
    private boolean     bSpeakingRightNow               = false;
    private boolean     bProcessData                    = true;


    //================================================================================================================
    // I N I T
    //================================================================================================================    
    public VAD(VADParams params, CaptureParams captureparams, Handler scb, Handler ccb, Handler rcb)
    {
        setParams(params, captureparams);
        mStatusCallback     = scb;
        mCommandCallback    = ccb;
        mResultCallback     = rcb;
    }
    public VAD(VADParams params, CaptureParams captureparams, Handler cb)
    {
        this(params, captureparams, cb, cb, cb);
    }
    
    public void setParams(VADParams vadParams, CaptureParams captureparams)
    {
        mVadParams = vadParams;
        mCfgParams = captureparams;
        calculateTimePeriodsAnalysisBuffers(mCfgParams.nSampleRate, mCfgParams.nBufferSize);
        resetAll();       
    }
    public void setWlCb(CallbackContext wlcb)
    {
        callbackContext     = wlcb;
    } 
    public void setCallbacks(Handler scb, Handler ccb, Handler rcb)
    {
        mStatusCallback     = scb;
        mCommandCallback    = ccb;
        mResultCallback     = rcb;
    } 
    public void setCallbacks(Handler cb)
    {
        setCallbacks(cb, cb, cb);
    }        
    //====================================================================================================================
    // PUBLIC INTERFACE
    //====================================================================================================================
    // message from AudioInputCapture
    public void OnNewData(float[] audiodata)
    {
        bCaptureRunning = true;        
        try 
        {
            if (audiodata != null && audiodata.length > 0 && bProcessData)
                processData(audiodata);
        }
        catch (Exception e) 
        {
            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.VAD_ERROR, "error", "_getNextBuffer exception: " + e);
            callSpeechStatusCB(ERRORS.VAD_ERROR);
            resetAll();
        }        
    }
    
    // called by user
    public void stopCapture()
    {
//        // taken from  stop
//        if (faCurrentSpeech.size() > 0) {
//            handleAudioBufferCreation(faCurrentSpeech.toArray(new Float[faCurrentSpeech.size()]));
//        }        
        //taken from nextbuffer when not capturing
        if (bSpeakingRightNow) {
            stopSpeechEvent();
        } 
        bCaptureRunning = false;
    }
    
    // Returns the current decibel level of the captured audio.
    public float getCurrentVolume() {
        return fLastAudioLevel;
    }

    /**
     * Returns true if speech start event has occurred and is still in effect.
     * @returns {boolean}
     */
    public boolean isSpeakingRightNow(){
        return bSpeakingRightNow;
    }    

    public int getMaxSpeechLengthSamples()
    {
        return nMaxSpeechLengthSamples;
    }
    //====================================================================================================================
    // SPEECH DETECTION
    //====================================================================================================================
    /**
     * @param audioInputBuffer
     * @private
     */
    // called by OnNewData
    private void processData(float[] audioInputBuffer) 
    {
        int len = audioInputBuffer.length;

        // If buffer isn't of the expected size, recalculate everything based on the new length
        if(len != mCfgParams.nBufferSize)
            calculateTimePeriodsAnalysisBuffers(mCfgParams.nSampleRate, len);

        int startIdx, endIdx;
        for (int i = 0; i < nOfAnalysisBuffersPerIteration; i++)  // nOfAnalysisBuffersPerIteration=2, nAnalysisBufferSize=512
        {
            startIdx    = i * nAnalysisBufferSize;
            endIdx      = startIdx + nAnalysisBufferSize;

            if (endIdx > audioInputBuffer.length) endIdx    = audioInputBuffer.length;
            int data2take                                   = endIdx - startIdx;    // usually nAnalysisBufferSize=512
            float[] analysisBuffer                          = new float[data2take]; 
            System.arraycopy(audioInputBuffer, startIdx, analysisBuffer, 0, data2take);
            
            if (!monitor(analysisBuffer)) return; // Ignore more speech
        }
    }

    /**
     * @param audioBuffer
     * @private
     */
    private boolean monitor(float[] analysisBuffer)  // analysisBuffer.length = nAnalysisBufferSize = 512
    {
        // First: Has maximum length threshold occurred or continue?
        if (nCurrentSpeechLength + 1 > nSpeechMaximumLengthChunks) 
        {
            maximumLengthSpeechEvent();                                             // send event to WL, then call stopSpeechEvent => newSentenceEvent();
            return false;                                                           // don't consider new data
        }
        // Is somebody speaking?
        if (identifySpeech(analysisBuffer)) 
        {
            // Speech Started or continued?
            if (!bSpeakingRightNow) startSpeechEvent(analysisBuffer);               // send start event to WL, reset params, then call continueSpeechEvent
            else                    continueSpeechEvent(analysisBuffer, false);     // set nAfterSpeechPeriod=0, then appendSpeech (nCurrentSpeechLength++)
       }
        else 
        {
            // No speech was identified this time, was speech previously started?
            if (bSpeakingRightNow) 
            {
                nAfterSpeechPeriod++;                                               // periods of 0.064 sec
                // Was speech paused long enough to stop speech event?
                if (nAfterSpeechPeriod > nSpeechAllowedDelayChunks)
                    stopSpeechEvent();                                              // newSentenceEvent OR sendMinimumLengthSpeechEvent
                else 
                {
                    // no ... wait for other ms before closing the sentence
                    if (!mVadParams.bCompressPauses) continueSpeechEvent(analysisBuffer, true); // appendSpeech (nCurrentSpeechLength++)
                }
            }
            calculateAmbientAverageLevel(fLastAudioLevel);                          // Handle silence
        }
        return true;
    }

    /**
     * @param audioBuffer
     * @returns {boolean}
     * @private
     */
    private boolean identifySpeech(float[] analysisBuffer)  // analysisBuffer.length = nAnalysisBufferSize = 512
    {
        float currentLevel  = Float.NEGATIVE_INFINITY;
        float decibel       = Float.NEGATIVE_INFINITY;        
        if (analysisBuffer != null && analysisBuffer.length > 0) 
        {
            try
            {
                currentLevel    = AudioInputCapture.getAudioLevels(analysisBuffer);
                decibel         = AudioInputCapture.getDecibelFromAmplitude(currentLevel);
                
                if (decibel != Float.NEGATIVE_INFINITY) 
                {
                    fLastAudioLevel = decibel;
                    if (fLastAudioLevel > fCurrentThreshold) return true;
                }                
            }
            catch(Exception e)
            {
                Messaging.sendMessageToHandler(mStatusCallback, ERRORS.VAD_ERROR, "error", "_getAudioLevels exception: " + e);
                callSpeechStatusCB(ERRORS.VAD_ERROR);
                return false;
            }
        }
        return false;
    }

    //===================================================================
    // SEND STATUS TO mStatusHandler
    //===================================================================    
    private void callSpeechStatusCB(int eventType) 
    {
        Messaging.sendMessageToHandler(mStatusCallback, eventType, "", "");
    }      
    //-------------------------------------------------------------------
    /**
     * @private
     */
    private void sendStartSpeech()  {
        nOfEventsStart++;        
        bSpeakingRightNow = true;
        callSpeechStatusCB(ENUMS.SPEECH_STATUS_STARTED);
    } 
    
    /**
      * @private
     */
    private void sendStopSpeech() {
        nOfEventsStop++;           
        bSpeakingRightNow = false;
        callSpeechStatusCB(ENUMS.SPEECH_STATUS_STOPPED);
    }
        
    /**
     * @param speechData
     * @private
     */
    private void sendMaximumLengthSpeechEvent()  {
        nOfEventsMax++;
        callSpeechStatusCB(ENUMS.SPEECH_STATUS_MAX_LENGTH);
    }
        
    /**
     * @private
     */
    private void sendMinimumLengthSpeechEvent()  {
        nOfEventsMin++;
        callSpeechStatusCB(ENUMS.SPEECH_STATUS_MIN_LENGTH);
    }
        
    //===================================================================
    // PROCESS EVENTS
    //===================================================================
    /**
     * @param speechData
     * @private
     */
    private void startSpeechEvent(float[] analysisBuffer) // analysisBuffer.length = nAnalysisBufferSize = 512
    {
        sendStartSpeech();
        resetSpeechDetection();
        continueSpeechEvent(analysisBuffer, false); // set nAfterSpeechPeriod=0
    }
    
    /**
     * @param speechData
     * @param silent true if this continue event is considered silent
     * @private
     */
    private void continueSpeechEvent(float[] analysisBuffer, boolean silent) // analysisBuffer.length = nAnalysisBufferSize = 512
    {
        nOfEventsContinue++;
        appendSpeech(analysisBuffer);
        if (!silent) nAfterSpeechPeriod = 0;
    }
    
    /**
     * @private
     */
    private void stopSpeechEvent() 
    {
        sendStopSpeech();
        // Was the speech long enough to be considered a new sentence?
        if (nCurrentSpeechLength > nSpeechMinimumLengthChunks)  newSentenceEvent();
        else                                                    sendMinimumLengthSpeechEvent();
        resetSpeechDetection();
    }   
    
    /**
     * @param speechData
     * @private
     */
    private void maximumLengthSpeechEvent()  
    {
        sendMaximumLengthSpeechEvent();
        stopSpeechEvent();
    }    
    //===================================================================
    // SENTENCE MANAGEMENT
    //===================================================================
    /**
     * @param speechData
     * @private
     */
    private void appendSpeech(float[] analysisBuffer) 
    {
        int data2write      = analysisBuffer.length;
        
        if(mVadParams.nAudioResultType == ENUMS.VAD_RESULT_SAVE_SENTENCE || mVadParams.nAudioResultType == ENUMS.VAD_RESULT_PROCESS_DATA_SAVE_SENTENCE)
        {
            int final_length    = nCurrentSpeechSamples + data2write;
            if (final_length > faCurrentSpeech.length)
            {
                // write only those data that fit into the array
                data2write      = data2write - (final_length - faCurrentSpeech.length);
               Log.w(LOG_TAG, "appendSpeech: data needed to be truncated");
            }
            System.arraycopy(analysisBuffer,0, faCurrentSpeech, nCurrentSpeechSamples, data2write);
        }
        nCurrentSpeechSamples += data2write;
        if(mVadParams.nAudioResultType == ENUMS.VAD_RESULT_PROCESS_DATA || mVadParams.nAudioResultType == ENUMS.VAD_RESULT_PROCESS_DATA_SAVE_SENTENCE)
        {
            float [] data = new float[data2write];
            System.arraycopy(analysisBuffer,0, data, 0, data2write);
            Messaging.sendDataToHandler(mCommandCallback, ENUMS.MFCC_CMD_GETQDATA, "data", data);
        }
        nCurrentSpeechLength++;
    }

    /**
     * @param speechData
     * @private
     */
    private void newSentenceEvent()  
    {
        try
        {
            float[] data = null;
            String cleanpath;
            switch((int)mVadParams.nAudioResultType)
            {
                case ENUMS.VAD_RESULT_SAVE_SENTENCE:
                    data    = new float[nCurrentSpeechSamples];
                    System.arraycopy(faCurrentSpeech, 0, data, 0, nCurrentSpeechSamples);     

                    cleanpath = mVadParams.sDebugString.startsWith("file://") ? mVadParams.sDebugString.split("file://")[1] : mVadParams.sDebugString;
                    WavFile.createWavFile( mVadParams.sDebugString, mCfgParams.nChannels, data, 16, (long)mCfgParams.nSampleRate, true);
                    break;

                case ENUMS.VAD_RESULT_PROCESS_DATA_SAVE_SENTENCE:
                    data = new float[nCurrentSpeechSamples];
                    System.arraycopy(faCurrentSpeech, 0, data, 0, nCurrentSpeechSamples);    
                    
                    cleanpath = mVadParams.sDebugString.startsWith("file://") ? mVadParams.sDebugString.split("file://")[1] : mVadParams.sDebugString;
                    WavFile.createWavFile(cleanpath, mCfgParams.nChannels, data, 16, (long)mCfgParams.nSampleRate, true);

                    Messaging.sendDataToHandler(mCommandCallback, ENUMS.MFCC_CMD_SENDDATA, "info", nCurrentSpeechSamples);
                    break;
                    
                case ENUMS.VAD_RESULT_PROCESS_DATA:
                    Messaging.sendDataToHandler(mCommandCallback, ENUMS.MFCC_CMD_SENDDATA, "info", nCurrentSpeechSamples);
                    break;
            }
            if((boolean)mVadParams.bAutoPause)   bProcessData = false;
            callSpeechStatusCB(ENUMS.SPEECH_STATUS_SENTENCE);   
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }    
    
    public void resumeRecognition()
    {
        bProcessData = true;
    }
    //===================================================================
    // RESET PARAMS
    //===================================================================    
    /**
     * @private
     */
    public void resetAll() 
    {
        bSpeakingRightNow = false;

        resetSpeechDetection();
        resetAmbientLevels();

        nOfEventsContinue           = 0;
        nOfEventsStart              = 0;
        nOfEventsStop               = 0;
        nOfEventsMax                = 0;
        nOfEventsMin                = 0;

        fLastAudioLevel             = -50;
        fCurrentThreshold           = 0;
    }
    
    /**
     * @private
     */
    private void resetAmbientLevels() 
    {
        fAmbientTotal = 0;
        fAmbientAverageLevel = 0;
        nSilentIterations = 0;
    }

     /**
     * @private
     */
    private void resetSpeechDetection() 
    {
//        faCurrentSpeech = null;
        nCurrentSpeechLength    = 0;
        nCurrentSpeechSamples   = 0;
        nAfterSpeechPeriod      = 0;
        Messaging.sendDataToHandler(mCommandCallback, ENUMS.MFCC_CMD_CLEAR);
    }

    //===================================================================
    // PARAMETERS CALCULATION
    //===================================================================
    /**
     * @private
     */
    private void calculateAmbientAverageLevel(float audioLevel) 
    {
        nSilentIterations++;
        fAmbientTotal           += audioLevel;
        fAmbientAverageLevel    = fAmbientTotal / nSilentIterations;
        fCurrentThreshold       = fAmbientAverageLevel + mVadParams.nSpeechDetectionThreshold;
        fCurrentThreshold       = fCurrentThreshold > 0 ? 0 : fCurrentThreshold;
    }
        
    /**
     * @param sampleRate
     * @param bufferSize
     * @private
     */
    // nOfAnalysisBuffersPerIteration MUST BE an integer ! thus sampleRate, bufferSize & nAnalysisChunkLength must be linked
    // (bufferSize*1000)/(samplerate*nAnalysisChunkLength) = INTEGER
    // possible values are: with sr=8000, bs=512 => acl==64/128/256 etc...
    private void calculateTimePeriodsAnalysisBuffers(int sampleRate, int bufferSize) // mVadParams.nAnalysisChunkLength=64ms
    {
        try 
        {
            float fInputBufferSizeInMs      = (bufferSize*1000)/sampleRate;  
            float fInputBufferSizeInS       = fInputBufferSizeInMs/1000;  

            nOfAnalysisBuffersPerIteration  = (int)Math.ceil(fInputBufferSizeInMs / mVadParams.nAnalysisChunkLength);                // ceil(128/64) = 2
            nAnalysisBufferSize             = (int)Math.ceil(bufferSize / nOfAnalysisBuffersPerIteration);                           // 512
            fAnalysisBufferLengthInS        = fInputBufferSizeInS / nOfAnalysisBuffersPerIteration;                                  // 0.064
            fAnalysisBufferLengthInMs       = fAnalysisBufferLengthInS*1000; 
            
            nSpeechAllowedDelayChunks       = Math.round(mVadParams.nSpeechDetectionAllowedDelay / fAnalysisBufferLengthInMs);      // 400/64     = 6.25   => 6
            nSpeechMinimumLengthChunks      = Math.round(mVadParams.nSpeechDetectionMinimum      / fAnalysisBufferLengthInMs);      // 500/64     = 7.81   => 8
            nSpeechMaximumLengthChunks      = Math.round(mVadParams.nSpeechDetectionMaximum      / fAnalysisBufferLengthInMs);      // 10000/64   = 156,25 => 156

            // init of the array containing the longest allowed chunk data
            nMaxSpeechLengthSamples         = getMaxSpeechLengthSamples(mVadParams.nSpeechDetectionMaximum, sampleRate, bufferSize);                                                // 63*1024 = 64512
            faCurrentSpeech          = new float[nMaxSpeechLengthSamples];    
        }
        catch (Exception e) {
            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.VAD_ERROR, "error", "calculateTimePeriodsAnalysisBuffers exception: " + e);
        }
    }
    
    public static int getMaxSpeechLengthSamples(int maxlen_ms, int sampleRate, int bufferSize)
    {
        float maxLengthSecond           = (float)((maxlen_ms*1.0)/1000);                               // 8000 => 8.0
        float maxLengthSamples          = maxLengthSecond*sampleRate;                                                           // 8.0 x 8000 = 64000
        int max_segments                = (int) Math.ceil(maxLengthSamples/bufferSize);                                         // 64000/1024 = 62.25 => 63
        return max_segments*sampleRate;          
    }
    
    public float getCurrentThreshold()
    {
        return fCurrentThreshold;
    }
    
    public boolean adjustVADThreshold(int newthreshold)
    {
        // alternatively ....I call a function that calculate a new background level
        mVadParams.nSpeechDetectionThreshold = newthreshold;
        return true;
    }
    //======================================================================================    
}
