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

package com.allspeak.audioprocessing;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.Collections;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import com.allspeak.STATUS;
import java.util.Arrays;
import java.util.List;

public class VAD 
{
    private static final String TAG = "VAD";

    /******************************************************************************************************************/
    // PRIVATE/INTERNAL 
    
    private VADParams vadParams;
    private CFGParams cfgParams;
    
    
    private int         nGetNextBufferDuration = 50;
    private int         nAnalyzeIterations = 0;
    private int         nSilentIterations = 50;
    private int         nOfEventsContinue = 0;
    private int         nOfEventsStart = 0;
    private int         nOfEventsStop = 0;
    private int         nOfEventsMax = 0;
    private int         nOfEventsMin = 0;
    private int         nTotalNumOfSpeechChunks = 0;

    private boolean     bCaptureRunning = false;

    private float []    faAudioDataQueue;
    private int         nAudioInputDataTotal = 0;
    private float []    faFullAudioSpeechData; // = new Float32Array(0);

    ArrayList<Float>    faCurrentSpeechHistory = new ArrayList<Float>();
//    private float []    faCurrentSpeechHistory;
    private int         nCurrentSpeechLength = 0;
    private float       fLastAudioLevel = -50;
    private int         fCurrentThreshold = 0;
    private int         nSpeechPeriod = 0;
    private float       fAmbientTotal = 0;
    private float       fAmbientAverageLevel = 0;

    private int         nAnalysisBufferSize = 0;
    private int         nOfAnalysisBuffersPerIteration = 0;
    private float       fAudioInputFrequency = 0;
    private float       fBufferLengthInSeconds = 0;

    private int         nSpeechAllowedDelayChunks = 0;
    private int         nSpeechMinimumLengthChunks = 0;
    private int         nSpeechMaximumLengthChunks = 0;

    private int         nGetNextBufferIterations = 0;
    private int         nAudioInputEvents = 0;
    private float       fAnalysisBufferLengthInS = 0;
    private boolean     bSpeakingRightNow = false;

    private int         nLastErrorCode = ERRORS.NO_ERROR;


    private Handler handler;
    private Message message;
    private Bundle messageBundle = new Bundle();
    
    //================================================================================================================
    public VAD(VADParams params, CFGParams captureparams, Handler _handler)
    {
        vadParams   = params;
        cfgParams   = captureparams;
        handler     = _handler;
        
        calculateTimePeriods(cfgParams.nSampleRate, cfgParams.nBufferSize);
        resetAll();
    }
        

    public void OnNewData(float[] audiodata)
    {
        try 
        {
            nGetNextBufferIterations++;
            if (audiodata != null && audiodata.length > 0)
                iteratedAndMonitorInputBuffer(audiodata);
        }
        catch (Exception e) {
            sendMessageToHandler("error", "_getNextBuffer exception: " + e);
            callSpeechStatusCB(STATUS.SPEECH_ERROR);
            resetAll();
        }        
    }
    
    
    public void stopCapture()
    {
        if (bSpeakingRightNow) {
            stopSpeechEvent(faCurrentSpeechHistory);
        }
    }
    

    /**
     *
     * @param audioInputBuffer
     * @private
     */
    private void iteratedAndMonitorInputBuffer(float[] audioInputBuffer) {
        try {
            int len = audioInputBuffer.length;

            // If buffer isn't of the expected size, recalculate everything based on the new length
            if(audioInputBuffer.length != cfgParams.nBufferSize) {
                calculateTimePeriods(cfgParams.nSampleRate, audioInputBuffer.length);
            }
            int startIdx, endIdx;
            for (int i = 0; i < nOfAnalysisBuffersPerIteration; i++) {
                startIdx    = i * nAnalysisBufferSize;
                endIdx      = startIdx + nAnalysisBufferSize;

                if (endIdx > audioInputBuffer.length) {
                    endIdx = audioInputBuffer.length;
                }

                float[] buf = audioInputBuffer.slice(startIdx, endIdx);

                if (!monitor(buf)) {
                    return; // Ignore more speech
                }
            }
        }
        catch (Exception e) {
            sendMessageToHandler("error", "_iteratedAndMonitorInputBuffer exception: " + e);
            callSpeechStatusCB(STATUS.SPEECH_ERROR);
        }
    }
    //======================================================================================    
    // NOTIFICATIONS TO PLUGIN MAIN
    //======================================================================================    
    private void sendMessageToHandler(String field, String info)
    {
        message = handler.obtainMessage();
        messageBundle.putString(field, info);
        message.setData(messageBundle);
        handler.sendMessage(message);        
    }    
    
    private void sendDataToHandler(String field, float[] audiodata)
    {
        message = handler.obtainMessage();
        messageBundle.putFloatArray(field, audiodata);
        message.setData(messageBundle);
        handler.sendMessage(message);        
    } 


    /**
     *
     * @private
     */
    private void resetAll() 
    {
        bSpeakingRightNow = false;

        resetAudioInputQueue();
        resetSpeechDetection();
        resetAmbientLevels();

        nOfEventsContinue           = 0;
        nOfEventsStart              = 0;
        nOfEventsStop               = 0;
        nOfEventsMax                = 0;
        nOfEventsMin                = 0;
        nTotalNumOfSpeechChunks     = 0;

        nAudioInputEvents           = 0;
        nAnalyzeIterations          = 0;
        nGetNextBufferIterations    = 0;
        fLastAudioLevel             = -50;
        fCurrentThreshold           = 0;

        nAudioInputDataTotal        = 0;
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
    private void resetAudioInputQueue() {
        faAudioDataQueue = null;
    }

    /**
     * @private
     */
    private void resetSpeechDetection() {
        faCurrentSpeechHistory.clear();
        nCurrentSpeechLength = 0;
        nSpeechPeriod = 0;
    }


    /**
     *
     * @param audioBuffer
     * @private
     */
    private boolean monitor(float[] audioBuffer) {
        try {

            // First: Has maximum length threshold occurred or continue?
            if (nCurrentSpeechLength + 1 > nSpeechMaximumLengthChunks) {
                maximumLengthSpeechEvent(faCurrentSpeechHistory);
                return false;
            }

            // Is somebody speaking?
            if (identifySpeech(audioBuffer)) {
                // Speech Started or continued?
                if (!bSpeakingRightNow) {
                    startSpeechEvent(audioBuffer);
                }
                else {
                    continueSpeechEvent(audioBuffer, false);
                }
            }
            else {
                // No speech was identified this time, was speech previously started?
                if (bSpeakingRightNow) {
                    nSpeechPeriod++;

                    // Was speech paused long enough to stop speech event?
                    if (nSpeechPeriod > nSpeechAllowedDelayChunks) {
                        stopSpeechEvent(faCurrentSpeechHistory);
                    }
                    else {
                        if (!vadParams.bCompressPauses) {
                            continueSpeechEvent(audioBuffer, true);
                        }
                    }
                }

                // Handle silence
                calculateAmbientAverageLevel(fLastAudioLevel);
            }

            return true;
        }
        catch (Exception e) {
            sendMessageToHandler("error", "_monitor exception: " + e);
            callSpeechStatusCB(STATUS.SPEECH_ERROR);
            resetAll();
            return false;
        }
    };


    /**
     *
     * @param audioBuffer
     * @returns {boolean}
     * @private
     */
    private boolean identifySpeech(float[] audioBuffer) {
        try {

            if (audioBuffer != null && audioBuffer.length > 0) {
                nAnalyzeIterations++;

                float currentLevel = getAudioLevels(audioBuffer);

                if (currentLevel != Float.NEGATIVE_INFINITY) {

                    fLastAudioLevel = currentLevel;

                    if (fLastAudioLevel > fCurrentThreshold) {
                        nTotalNumOfSpeechChunks++;
                        return true;
                    }
                }
            }
        }
        catch (Exception e) {
            sendMessageToHandler("error", "_identifySpeech exception: " + e);
            callSpeechStatusCB(STATUS.SPEECH_ERROR);
        }

        return false;
    }


    /**
     *
     * @param audioBuffer
     *
     * @private
     * @returns {*}
     */
    private float getAudioLevels(float[] audioBuffer) {
        try {

            int total = 0;
            int length = audioBuffer.length;
            float decibel;
            float rms;
            float absFreq;

            for (int i = 0; i < length; i++) {
                absFreq = Math.abs(audioBuffer[i]);
                total += ( absFreq * absFreq );
            }

            rms = (float)Math.sqrt(total / length);
            decibel = getDecibelFromAmplitude(rms);


            return decibel;
        }
        catch (Exception e) {
            sendMessageToHandler("error", "_getAudioLevels exception: " + e);
            callSpeechStatusCB(STATUS.SPEECH_ERROR);
            return Float.NaN;
        }
    }



    /**
     *
     * @private
     */
    private void calculateAmbientAverageLevel(float audioLevel) {
        nSilentIterations++;
        fAmbientTotal           = fAmbientTotal + audioLevel;
        fAmbientAverageLevel    = fAmbientTotal / nSilentIterations;
        fCurrentThreshold       = fAmbientAverageLevel + vadParams.nSpeechDetectionThreshold;
        fCurrentThreshold       = fCurrentThreshold > 0 ? 0 : fCurrentThreshold;
    }

    
    /**
     * Convert amplitude to decibel
     *
     * @param amplitudeLevel
     * @returns {number}
     * @private
     */
    private float getDecibelFromAmplitude(float amplitudeLevel) {
        return (float) (20 * ( Math.log(amplitudeLevel) / Math.log(10) ));
    }
    

    /**
     *
     * @param speechData
     * @private
     */
    private void maximumLengthSpeechEvent(float[] speechData) {
        nOfEventsMax++;
        stopSpeechEvent(speechData);
        callSpeechStatusCB(STATUS.SPEECH_MAX_LENGTH);
    };


    
    
    /**
     *
     * @private
     */
    private void startSpeech() {
        bSpeakingRightNow = true;
        callSpeechStatusCB(STATUS.SPEECH_STARTED);
    }


    /**
     *
     * @param speechData
     * @private
     */
    private void startSpeechEvent(float[] speechData) {
        nOfEventsStart++;
        startSpeech();
        resetSpeechDetection();
        continueSpeechEvent(speechData, false);
    }

        /**
     *
     * @param speechData
     * @param silent true if this continue event is considered silent
     * @private
     */
    private void continueSpeechEvent(float[] speechData, boolean silent) {
        nOfEventsContinue++;
        appendSpeechToHistory(speechData);
        if (!silent) {
            nSpeechPeriod = 0;
        }
    }
    
    
    /**
     *
     * @param speechData
     * @private
     */
    private void appendSpeechToHistory(Float[] speechData) {
        if (!vadParams.bDetectOnly) {
//             List<Float> sd = Arrays.asList(speechData);
            
            Collections.addAll(faCurrentSpeechHistory, speechData);
        }
        nCurrentSpeechLength++;
    };

    

    /**
     *
     * @private
     */
    private void captureStarted() {
        callSpeechStatusCB(STATUS.CAPTURE_STARTED);
    }
    
    /**
     *
     * @private
     */
    private void stopSpeech() {
        bSpeakingRightNow = false;
        callSpeechStatusCB(STATUS.SPEECH_STOPPED);
    };

    


    /**
     *
     * @param sampleRate
     * @param bufferSize
     * @private
     */
    private void calculateTimePeriods(int sampleRate, int bufferSize) 
    {
        try 
        {
//            _cfg.bufferSize = bufferSize;
            fAudioInputFrequency    = sampleRate / bufferSize;
            fBufferLengthInSeconds  = 1 / fAudioInputFrequency;

            /*mon = {
                sampleRate: sampleRate,
                bufferSize: bufferSize,
                _audioInputFrequency: _audioInputFrequency,
                _bufferLengthInSeconds: _bufferLengthInSeconds
            };

            alert("_calculateTimePeriods: " + JSON.stringify(mon));*/

            calculateAnalysisBuffers(bufferSize, fBufferLengthInSeconds, vadParams.nAnalysisChunkLength);
        }
        catch (Exception ex) {
            sendMessageToHandler("error", "_calculateTimePeriods exception: " + ex);
        }
    }


    /**
     *
     * @param bufferSize
     * @param bufferLengthInSeconds
     * @param analysisChunkLength
     * @private
     */
    private void calculateAnalysisBuffers(int bufferSize, float bufferLengthInSeconds, int analysisChunkLength) 
    {
        try 
        {
            float fInputBufferSizeInMs      = bufferLengthInSeconds * 1000;
            nOfAnalysisBuffersPerIteration  = (int)Math.ceil(fInputBufferSizeInMs / analysisChunkLength);
            nAnalysisBufferSize             = (int)Math.ceil(bufferSize / nOfAnalysisBuffersPerIteration);
            fAnalysisBufferLengthInS        = bufferLengthInSeconds / nOfAnalysisBuffersPerIteration;

            nSpeechAllowedDelayChunks       = Math.round(vadParams.nSpeechDetectionAllowedDelay / analysisChunkLength);
            nSpeechMinimumLengthChunks      = Math.round(vadParams.nSpeechDetectionMinimum / analysisChunkLength);
            nSpeechMaximumLengthChunks      = Math.round(vadParams.nSpeechDetectionMaximum / analysisChunkLength);

            nGetNextBufferDuration = analysisChunkLength;

            /*mon = {
                bufferSize: bufferSize,
                bufferLengthInSeconds: bufferLengthInSeconds,
                analysisChunkLength: analysisChunkLength,
                inputBufferSizeInMs: inputBufferSizeInMs,
                _noOfAnalysisBuffersPerIteration: _noOfAnalysisBuffersPerIteration,
                _analysisBufferSize: _analysisBufferSize,
                _analysisBufferLengthInS: _analysisBufferLengthInS,
                _speechAllowedDelayChunks: _speechAllowedDelayChunks,
                _speechMinimumLengthChunks: _speechMinimumLengthChunks,
                _speechMaximumLengthChunks: _speechMaximumLengthChunks,
                _getNextBufferDuration: _getNextBufferDuration
            };

            alert("_calculateAnalysisBuffers: " + JSON.stringify(mon));*/
        }
        catch (Exception ex) {
            sendMessageToHandler("error", "_calculateAnalysisBuffers exception: " + ex);
        }
    };
    

    /**
     *
     * @param speechData
     * @private
     */
    private void callSpeechCapturedCB(float[] speechData) {
    
        sendDataToHandler("speech", speechData);
    };

    
    
    /**
     *
     * @param speechData
     * @private
     */
    private void stopSpeechEvent(float[] speechData) {
        nOfEventsStop++;
        handleAudioBufferCreation(speechData);
        stopSpeech();
        resetSpeechDetection();
    };

    
    /**
     * Stops monitoring.
     */
    public void stop() 
    {
        if (faCurrentSpeechHistory.length > 0) {
            handleAudioBufferCreation(faCurrentSpeechHistory);
        }
        captureStopped();
        resetAll();
    }


    /**
     * Returns true if speech start event has occurred and is still in effect.
     *
     * @returns {boolean}
     */
    public boolean isSpeakingRightNow() 
    {
        return bSpeakingRightNow;
    }

    /**
     * Returns the current configuration.
     *
     * @returns {*}
     */
    public boolean getCfg() {
        return vadParams;
    }

    /**
     * Returns the current decibel level of the captured audio.
     *
     * @returns {number|*}
     */
    public float getCurrentVolume()
    {
        return fLastAudioLevel;
    }


    /**
     *
     * @returns {number}
     */
    public int getLastErrorCode()
    {
        return nLastErrorCode;
    }

    

    /**
     *
     * @private
     */
    private void captureStopped() {
            callSpeechStatusCB(STATUS.CAPTURE_STOPPED);
        }
    };



    /**
     *
     * @returns {*|null}
     */
    public  getFullSpeechWavData()
    {
        return _createWAVAudioBuffer(_fullAudioSpeechData);
    };

    /**
     *
     * @returns {*|null}
     */
    private void getFullSpeechRawData() {
        return _fullAudioSpeechData;
    }





    /**
     *
     * @param eventType
     * @private
     */
    private void callSpeechStatusCB(int eventType) {
        if (_cfg.speechStatusCB) {
            _cfg.speechStatusCB(eventType);
        }
        else {
            sendMessageToHandler("error", "_callSpeechStatusCB: No callback defined!");
        }
    };

    /**
     * Gets new audio data from the audio input queue.
     *
     * @private
     */
    private void consumeFromAudioInputQueue() {

        audioInputData = new Float32Array(0),
            chunk = null;

        if (_audioDataQueue.length > 0) {
            for (i = 0; i < _cfg.concatenateMaxChunks; i++) {
                if (_audioDataQueue.length === 0) {
                    break;
                }

                chunk = _audioDataQueue.shift();
                audioInputData = audioInputData.concat(chunk);
                _audioInputDataTotal += chunk.length;
            }
        }

        return audioInputData;
    }









    /**
     *
     * @param audioDataBuffer
     * @private
     */
    private void createWAVAudioBuffer(audioDataBuffer) {
        try {
            wavData = wavEncoder.encode(audioDataBuffer, _cfg.sampleRate, _cfg.channels);

            return new Blob([wavData], {
                type: 'audio/wav'
            });
        }
        catch (Exception e) {
            sendMessageToHandler("error", "_createWAVAudioBuffer exception: " + e);
            callSpeechStatusCB(STATUS.ENCODING_ERROR);
            return null;
        }
    };



    
    /**
     *
     * @param speechData
     * @private
     */
    private void handleAudioBufferCreation(float[] speechData) {

        // Was the speech long enough to create an audio buffer?
        if (nCurrentSpeechLength > nSpeechMinimumLengthChunks) { // speechData && speechData.length > 0 &&
            audioResult = null;
            preEncodingBuffer = speechData.slice(0);

            switch (_cfg.audioResultType) {
                case AUDIO_RESULT_TYPE.WEBAUDIO_AUDIOBUFFER:
                    audioResult = _createWebAudioBuffer(preEncodingBuffer);
                    break;
                case AUDIO_RESULT_TYPE.RAW_DATA:
                    audioResult = preEncodingBuffer;
                    break;
                case AUDIO_RESULT_TYPE.DETECTION_ONLY:
                    audioResult = null;
                    break;
                default:
                case AUDIO_RESULT_TYPE.WAV_BLOB:
                    audioResult = _createWAVAudioBuffer(preEncodingBuffer);
                    break;
            }

            callSpeechCapturedCB(audioResult);
        }
        else {
            nOfEventsMin++;
            callSpeechStatusCB(STATUS.SPEECH_MIN_LENGTH);
        }
    };
    
    
}