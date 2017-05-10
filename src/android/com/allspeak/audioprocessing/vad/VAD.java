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

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import com.allspeak.utility.Messaging;
import com.allspeak.audiocapture.AudioInputCapture;
import com.allspeak.audiocapture.CFGParams;
import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.audioprocessing.vad.VADParams;
import com.allspeak.audiocapture.CFGParams;
import com.allspeak.audioprocessing.WavFile;
import java.io.File;

public class VAD 
{
    private static final String LOG_TAG = "VAD";
    
    private VADParams mVadParams;
    private CFGParams mCfgParams;
    private MFCCParams mMfccParams;
    
    private Handler mStatusCallback             = null;     // Thread
    private Handler mCommandCallback            = null;     // Thread
    private Handler mResultCallback             = null;     // Thread
    private CallbackContext callbackContext     = null;    

    
    private int         nSilentIterations = 50;
    private int         nOfEventsContinue = 0;
    private int         nOfEventsStart = 0;
    private int         nOfEventsStop = 0;
    private int         nOfEventsMax = 0;
    private int         nOfEventsMin = 0;

    private boolean     bCaptureRunning = false;

    private float[]     faCurrentSpeechHistory;
    private int         nCurrentSpeechLength = 0;  // incremented by appendSpeechToHistory, used as: if(nCurrentSpeechLength + 1 > nSpeechMaximumLengthChunks) maximumLengthSpeechEvent();

    private int         nAfterSpeechPeriod = 0;  // used as following: if (nAfterSpeechPeriod > nSpeechAllowedDelayChunks) stopSpeechEvent(); 

    private float       fLastAudioLevel = -50; 
    private float       fCurrentThreshold = 0;
    private float       fAmbientTotal = 0;
    private float       fAmbientAverageLevel = 0;

    private int         nAnalysisBufferSize = 0;
    private int         nOfAnalysisBuffersPerIteration = 0;
    private float       fAudioInputFrequency = 0;
    private float       fBufferLengthInSeconds = 0;
    private float       fAnalysisBufferLengthInS = 0;
    private float       fAnalysisBufferLengthInMs = 0;
    
    private int         nSpeechAllowedDelayChunks = 0;
    private int         nSpeechMinimumLengthChunks = 0;
    private int         nSpeechMaximumLengthChunks = 0;
    
    private boolean     bSpeakingRightNow = false;


    //================================================================================================================
    // I N I T
    //================================================================================================================    
    public VAD(VADParams params, CFGParams captureparams, Handler scb, Handler ccb, Handler rcb)
    {
        setParams(params, captureparams);
        mStatusCallback     = scb;
        mCommandCallback    = ccb;
        mResultCallback     = rcb;
    }
    public VAD(VADParams params, CFGParams captureparams, Handler cb)
    {
        setParams(params, captureparams);
        mStatusCallback     = cb;
        mCommandCallback    = cb;
        mResultCallback     = cb;
    }
    
    public void setParams(VADParams vadParams, CFGParams captureparams)
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
    
    //====================================================================================================================
    // PUBLIC INTERFACE
    //====================================================================================================================
    // message from AudioInputCapture
    public void OnNewData(float[] audiodata)
    {
        bCaptureRunning = true;        
        try 
        {
            if (audiodata != null && audiodata.length > 0)
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
//        if (faCurrentSpeechHistory.size() > 0) {
//            handleAudioBufferCreation(faCurrentSpeechHistory.toArray(new Float[faCurrentSpeechHistory.size()]));
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
            return false;
        }
        // Is somebody speaking?
        if (identifySpeech(analysisBuffer)) 
        {
            // Speech Started or continued?
            if (!bSpeakingRightNow) startSpeechEvent(analysisBuffer);               // send start event to WL, reset params, then call continueSpeechEvent
            else                    continueSpeechEvent(analysisBuffer, false);     // set nAfterSpeechPeriod=0, then appendSpeechToHistory (nCurrentSpeechLength++)
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
                    if (!mVadParams.bCompressPauses) continueSpeechEvent(analysisBuffer, true); // appendSpeechToHistory (nCurrentSpeechLength++)
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
        appendSpeechToHistory(analysisBuffer);
        if (!silent) nAfterSpeechPeriod = 0;
    }
    
    /**
     * @private
     */
    private void stopSpeechEvent() {
        // Was the speech long enough to be considered a new sentence?
        if (nCurrentSpeechLength > nSpeechMinimumLengthChunks)  newSentenceEvent();
        else                                                    sendMinimumLengthSpeechEvent();
        
        sendStopSpeech();
        resetSpeechDetection();
    }   
    
    /**
     * @param speechData
     * @private
     */
    private void maximumLengthSpeechEvent()  {
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
    private void appendSpeechToHistory(float[] analysisBuffer) 
    {
        if (!mVadParams.bDetectOnly) 
        {
            int data2write      = analysisBuffer.length;
            int final_length    = nCurrentSpeechLength*mCfgParams.nBufferSize + data2write;
            
            if (final_length > faCurrentSpeechHistory.length)
            {
                // write only those data that fit into the array
                data2write      = data2write - (final_length - faCurrentSpeechHistory.length);
                Log.w(LOG_TAG, "appendSpeechToHistory: data needed to be truncated");
            }
            System.arraycopy(analysisBuffer,0, faCurrentSpeechHistory, nCurrentSpeechLength*mCfgParams.nBufferSize, data2write);
            Messaging.sendDataToHandler(mCommandCallback, ENUMS.MFCC_CMD_GETQDATA, "data", analysisBuffer);
        }
        nCurrentSpeechLength++;
    }

    /**
     * @param speechData
     * @private
     */
    private void newSentenceEvent()  {
        
        int data2write  = nCurrentSpeechLength*mCfgParams.nBufferSize;
        float[] data    = new float[data2write];
        System.arraycopy(faCurrentSpeechHistory, 0, data, 0, data2write);

//        WavFile.createWavFile( file, mCfgParams.nChannels, data, 16, (long)mCfgParams.nSampleRate);
        callSpeechStatusCB(ENUMS.SPEECH_STATUS_SENTENCE);   
        Messaging.sendDataToHandler(mCommandCallback, ENUMS.MFCC_CMD_SENDDATA, "info", ENUMS.SPEECH_STATUS_MAX_LENGTH);
    }    
    //===================================================================
    // RESET PARAMS
    //===================================================================    
    /**
     * @private
     */
    private void resetAll() {
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
    private void resetAmbientLevels() {
        fAmbientTotal = 0;
        fAmbientAverageLevel = 0;
        nSilentIterations = 0;
    }

     /**
     * @private
     */
    private void resetSpeechDetection() {
//        faCurrentSpeechHistory = null;
        nCurrentSpeechLength = 0;
        nAfterSpeechPeriod = 0;
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
            fAudioInputFrequency            = sampleRate / bufferSize;                                                                  // 7.8125
            fBufferLengthInSeconds          = 1 / fAudioInputFrequency;                                                                 // 0.128 sec

            float fInputBufferSizeInMs      = fBufferLengthInSeconds * 1000;                                                            // 128 ms
            nOfAnalysisBuffersPerIteration  = (int)Math.ceil(fInputBufferSizeInMs / mVadParams.nAnalysisChunkLength);                   // ceil(128/64) = 2
            nAnalysisBufferSize             = (int)Math.ceil(bufferSize / nOfAnalysisBuffersPerIteration);                              // 512
            fAnalysisBufferLengthInS        = fBufferLengthInSeconds / nOfAnalysisBuffersPerIteration;                                  // 0.064
            fAnalysisBufferLengthInMs       = fAnalysisBufferLengthInS*1000;                                                            // 64 ms

            nSpeechAllowedDelayChunks       = Math.round(mVadParams.nSpeechDetectionAllowedDelay / fAnalysisBufferLengthInMs);          // 400/64     = 6.25   => 6
            nSpeechMinimumLengthChunks      = Math.round(mVadParams.nSpeechDetectionMinimum      / fAnalysisBufferLengthInMs);               // 500/64     = 7.81   => 8
            nSpeechMaximumLengthChunks      = Math.round(mVadParams.nSpeechDetectionMaximum      / fAnalysisBufferLengthInMs);               // 10000/64   = 156,25 => 156

            // init of the array containing the longest allowed chunk data
            int nLongestSentenceSamples     = (int)Math.ceil((double)(mVadParams.nSpeechDetectionMaximum*1.0)/1000);                    // 80000
            faCurrentSpeechHistory          = new float[nLongestSentenceSamples*sampleRate];    
        }
        catch (Exception e) {
            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.VAD_ERROR, "error", "calculateTimePeriodsAnalysisBuffers exception: " + e);
        }
    }
    //======================================================================================    
}



//            nSpeechAllowedDelayChunks       = Math.round(mVadParams.nSpeechDetectionAllowedDelay / mVadParams.nAnalysisChunkLength);  // 4
//            nSpeechMinimumLengthChunks      = Math.round(mVadParams.nSpeechDetectionMinimum / mVadParams.nAnalysisChunkLength);       // 5
//            nSpeechMaximumLengthChunks      = Math.round(mVadParams.nSpeechDetectionMaximum / mVadParams.nAnalysisChunkLength);       // 100            
            // max sentence length=10s, one chunk is (1024/8000=0.128s), num chunks to store 10s is 78.xxx => 79, I add nSpeechAllowedDelayChunks more to be surer
//            int nMaxChunks                  = (int)Math.ceil((double)((mVadParams.nSpeechDetectionMaximum*1.0)/1000)/((mCfgParams.nBufferSize*1.0)/mCfgParams.nSampleRate))+nSpeechAllowedDelayChunks; 
//            faCurrentSpeechHistory          = new float[nMaxChunks*mCfgParams.nBufferSize]; 