var argscheck = require('cordova/argscheck'),
    utils = require('cordova/utils'),
    exec = require('cordova/exec'),
    channel = require('cordova/channel');

var speechrecognition          = {};

speechrecognition.pluginName   = "SpeechRecognitionPlugin";

speechrecognition.mfcc = {};
speechrecognition.mfcc.params = {};

speechrecognition.capture = {};
speechrecognition.capture.params = {};

speechrecognition.vad = {};
speechrecognition.vad.params = {};

//=====================================================================================================
speechrecognition.ENUM         = {};
speechrecognition.ENUM.capture = {};
speechrecognition.ENUM.mfcc    = {};
speechrecognition.ENUM.vad     = {};

// Supported audio formats
speechrecognition.ENUM.capture.FORMAT = {
    PCM_16BIT           : 'PCM_16BIT',
    PCM_8BIT            : 'PCM_8BIT'
};

speechrecognition.ENUM.capture.CHANNELS = {
    MONO                : 1,
    STEREO              : 2
};

// Common Sampling rates
speechrecognition.ENUM.capture.SAMPLERATE = {
    TELEPHONE_8000Hz    : 8000,
    CD_QUARTER_11025Hz  : 11025,
    VOIP_16000Hz        : 16000,
    CD_HALF_22050Hz     : 22050,
    MINI_DV_32000Hz     : 32000,
    CD_XA_37800Hz       : 37800,
    NTSC_44056Hz        : 44056,
    CD_AUDIO_44100Hz    : 44100
};

// Audio Source types
speechrecognition.ENUM.capture.AUDIOSOURCE_TYPE = {
    DEFAULT             : 0,
    CAMCORDER           : 5,
    MIC                 : 1,
    UNPROCESSED         : 9,
    VOICE_COMMUNICATION : 7,
    VOICE_RECOGNITION   : 6
};

speechrecognition.ENUM.capture.ERROR_CODE = {
    NO_ERROR                    : 0,
    INVALID_PARAMETER           : 1,
    MISSING_PARAMETER           : 2,
    NO_WEB_AUDIO_SUPPORT        : 3,
    CAPTURE_ALREADY_STARTED     : 4,
    AUDIOINPUT_NOT_AVAILABLE    : 5,
    UNSPECIFIED                 : 999
};

speechrecognition.ENUM.mfcc.DATATYPE={
    NONE        : 0,
    FCC         : 1,
    MFFILTERS   : 2
};

speechrecognition.ENUM.mfcc.DATAORIGIN={
    JSONDATA    : 1,
    FILE        : 2,
    FOLDER      : 3,
    RAWDATA     : 4
};

speechrecognition.ENUM.mfcc.DATADEST={
    NONE    : 0,
    JS      : 1,
    FILE    : 2,
    BOTH    : 3
};

speechrecognition.ENUM.vad.AUDIO_RESULT_TYPE = {
    WAV_BLOB: 1,
    WEBAUDIO_AUDIOBUFFER: 2,
    RAW_DATA: 3,
    DETECTION_ONLY: 4
};

speechrecognition.ENUM.vad.STATUS = {
    SPEECH_STARTED      : 1,
    SPEECH_STOPPED      : 2,
    SPEECH_ERROR        : 3,
    CAPTURE_STARTED     : 4,
    CAPTURE_STOPPED     : 5,
    CAPTURE_ERROR       : 6,
    ENCODING_ERROR      : 7,
    SPEECH_MAX_LENGTH   : 8,
    SPEECH_MIN_LENGTH   : 9
};
//=========================================================================================
// DEFAULT
//=========================================================================================
speechrecognition.ENUM.capture.DEFAULT = {
    SAMPLERATE              : speechrecognition.ENUM.capture.SAMPLERATE.TELEPHONE_8000Hz,
    BUFFER_SIZE             : 1024,
    CHANNELS                : speechrecognition.ENUM.capture.CHANNELS.MONO,
    FORMAT                  : speechrecognition.ENUM.capture.FORMAT.PCM_16BIT,
    NORMALIZE               : true,
    NORMALIZATION_FACTOR    : 32767.0,
    STREAM_TO_WEBAUDIO      : false,
    CONCATENATE_MAX_CHUNKS  : 10,
    AUDIOSOURCE_TYPE        : speechrecognition.ENUM.capture.AUDIOSOURCE_TYPE.DEFAULT
};

speechrecognition.ENUM.mfcc.DEFAULT = {
    nNumberOfMFCCParameters : 12,
    dSamplingFrequency      : 8000.0,
    nNumberofFilters        : 24,
    nFftLength              : 256,
    bIsLifteringEnabled     : true,
    nLifteringCoefficient   : 22,
    bCalculate0ThCoeff      : true,
    nWindowDistance         : 80,
    nWindowLength           : 200,
    nDataType               : speechrecognition.ENUM.mfcc.DATATYPE.MFFILTERS,
    nDataDest               : speechrecognition.ENUM.mfcc.DATADEST.NONE,
    nDataOrig               : speechrecognition.ENUM.mfcc.DATAORIGIN.RAWDATA,
    sOutputPath             : ""
};

speechrecognition.ENUM.vad.DEFAULT = {
    SPEECH_DETECTION_THRESHOLD              : 15, // dB
    SPEECH_DETECTION_ALLOWED_DELAY          : 400, // mS
    SPEECH_DETECTION_MAX_LENGTH             : 10000, // mS
    SPEECH_DETECTION_MIN_LENGTH             : 500, // mS
    SPEECH_DETECTION_COMPRESS_PAUSES        : false,
    SPEECH_DETECTION_ANALYSIS_CHUNK_LENGTH  : 100, // mS
    AUDIO_RESULT_TYPE                       : 1,
    DETECT_ONLY                             : false
};

//=========================================================================================
// CHECK INPUT PARAMS
//=========================================================================================
speechrecognition.checkMfccParams = function(mfcc_params)
{
    speechrecognition.mfcc.params.nNumberOfMFCCParameters      = mfcc_params.nNumberOfMFCCParameters   || speechrecognition.ENUM.mfcc.DEFAULT.nNumberOfMFCCParameters; //without considering 0-th  
    speechrecognition.mfcc.params.dSamplingFrequency           = mfcc_params.dSamplingFrequency        || speechrecognition.ENUM.mfcc.DEFAULT.dSamplingFrequency;    
    speechrecognition.mfcc.params.nNumberofFilters             = mfcc_params.nNumberofFilters          || speechrecognition.ENUM.mfcc.DEFAULT.nNumberofFilters;    
    speechrecognition.mfcc.params.nFftLength                   = mfcc_params.nFftLength                || speechrecognition.ENUM.mfcc.DEFAULT.nFftLength;      
    speechrecognition.mfcc.params.bIsLifteringEnabled          = mfcc_params.bIsLifteringEnabled       || speechrecognition.ENUM.mfcc.DEFAULT.bIsLifteringEnabled ;    
    speechrecognition.mfcc.params.nLifteringCoefficient        = mfcc_params.nLifteringCoefficient     || speechrecognition.ENUM.mfcc.DEFAULT.nLifteringCoefficient;
    speechrecognition.mfcc.params.bCalculate0ThCoeff           = mfcc_params.bCalculate0ThCoeff        || speechrecognition.ENUM.mfcc.DEFAULT.bCalculate0ThCoeff;
    speechrecognition.mfcc.params.nWindowDistance              = mfcc_params.nWindowDistance           || speechrecognition.ENUM.mfcc.DEFAULT.nWindowDistance;
    speechrecognition.mfcc.params.nWindowLength                = mfcc_params.nWindowLength             || speechrecognition.ENUM.mfcc.DEFAULT.nWindowLength;          
    speechrecognition.mfcc.params.nDataType                    = mfcc_params.nDataType                 || speechrecognition.ENUM.mfcc.DEFAULT.nDataType;          
    speechrecognition.mfcc.params.nDataDest                    = mfcc_params.nDataDest                 || speechrecognition.ENUM.mfcc.DEFAULT.nDataDest;          
    speechrecognition.mfcc.params.nDataOrig                    = mfcc_params.nDataOrig                 || speechrecognition.ENUM.mfcc.DEFAULT.nDataOrig;          
    speechrecognition.mfcc.params.sOutputPath                  = mfcc_params.sOutputPath               || speechrecognition.ENUM.mfcc.DEFAULT.sOutputPath;          
    
    return JSON.stringify(speechrecognition.mfcc.params); 
};

speechrecognition.checkCaptureParams = function(capture_params)
{
    speechrecognition.capture.params.nSampleRate                 = capture_params.nSampleRate             || speechrecognition.ENUM.capture.DEFAULT.SAMPLERATE;
    speechrecognition.capture.params.nBufferSize                 = capture_params.nBufferSize             || speechrecognition.ENUM.capture.DEFAULT.BUFFER_SIZE;
    speechrecognition.capture.params.nChannels                   = capture_params.nChannels               || speechrecognition.ENUM.capture.DEFAULT.CHANNELS;
    speechrecognition.capture.params.sFormat                     = capture_params.sFormat                 || speechrecognition.ENUM.capture.DEFAULT.FORMAT;
    speechrecognition.capture.params.nAudioSourceType            = capture_params.nAudioSourceType        || 0;
    speechrecognition.capture.params.nNormalize                  = typeof capture_params.nNormalize == 'boolean' ? speechrecognition.ENUM.capture.nNormalize : speechrecognition.ENUM.capture.DEFAULT.NORMALIZE;
    speechrecognition.capture.params.fNormalizationFactor        = capture_params.fNormalizationFactor    || speechrecognition.ENUM.capture.DEFAULT.NORMALIZATION_FACTOR;
    speechrecognition.capture.params.nConcatenateMaxChunks       = capture_params.nConcatenateMaxChunks   || speechrecognition.ENUM.capture.DEFAULT.CONCATENATE_MAX_CHUNKS;
    speechrecognition.capture.params.AudioContext                = null;
    speechrecognition.capture.params.bStreamToWebAudio           = capture_params.bStreamToWebAudio        || speechrecognition.ENUM.capture.DEFAULT.STREAM_TO_WEBAUDIO;

    if (speechrecognition.capture.params.nChannels < 1 && speechrecognition.capture.params.nChannels > 2) {
        throw "Invalid number of channels (" + speechrecognition.capture.params.nChannels + "). Only mono (1) and stereo (2) is" +" supported.";
    }
    if (speechrecognition.capture.params.sFormat != "PCM_16BIT" && speechrecognition.capture.params.sFormat != "PCM_8BIT") {
        throw "Invalid format (" + speechrecognition.capture.params.sFormat + "). Only 'PCM_8BIT' and 'PCM_16BIT' is" + " supported.";
    }
    if (speechrecognition.capture.params.nBufferSize <= 0) {
        throw "Invalid bufferSize (" + speechrecognition.capture.params.nBufferSize + "). Must be greater than zero.";
    }
    if (speechrecognition.capture.params.nConcatenateMaxChunks <= 0) {
        throw "Invalid concatenateMaxChunks (" + speechrecognition.capture.params.nConcatenateMaxChunks + "). Must be greater than zero.";
    }
    
    return JSON.stringify(speechrecognition.capture.params); 
};

speechrecognition.checkVadParams = function(vad_params)
{
    speechmonitor.vad.params.speechDetectionThreshold       = vad_params.speechDetectionThreshold       || speechrecognition.ENUM.capture.DEFAULT.SPEECH_DETECTION_THRESHOLD;
    speechmonitor.vad.params.speechDetectionMinimum         = vad_params.speechDetectionMinimum         || speechrecognition.ENUM.capture.DEFAULT.SPEECH_DETECTION_MIN_LENGTH;
    speechmonitor.vad.params.speechDetectionMaximum         = vad_params.speechDetectionMaximum         || speechrecognition.ENUM.capture.DEFAULT.SPEECH_DETECTION_MAX_LENGTH;
    speechmonitor.vad.params.speechDetectionAllowedDelay    = vad_params.speechDetectionAllowedDelay    || speechrecognition.ENUM.capture.DEFAULT.SPEECH_DETECTION_ALLOWED_DELAY;
    speechmonitor.vad.params.audioResultType                = vad_params.audioResultType                || speechrecognition.ENUM.capture.DEFAULT.AUDIO_RESULT_TYPE;
    speechmonitor.vad.params.compressPauses                 = vad_params.compressPauses                 || speechrecognition.ENUM.capture.DEFAULT.SPEECH_DETECTION_COMPRESS_PAUSES;
    speechmonitor.vad.params.analysisChunkLength            = vad_params.analysisChunkLength            || speechrecognition.ENUM.capture.DEFAULT.SPEECH_DETECTION_ANALYSIS_CHUNK_LENGTH;
    speechmonitor.vad.params.detectOnly                     = vad_params.detectOnly                     || speechrecognition.ENUM.capture.DEFAULT.DETECT_ONLY;

    if (speechmonitor.vad.params.detectOnly) {
        speechmonitor.vad.params.audioResultType = AUDIO_RESULT_TYPE.DETECTION_ONLY;
    }
    return JSON.stringify(speechmonitor.vad.params); 
};

//==================================================================================================================
// PUBLIC
//==================================================================================================================
/**
 *
 * @returns {*}
 */
speechrecognition.getCfg = function () {
    return speechrecognition.capture.params;
};

/**
 *
 * @returns {boolean|Array}
 */
speechrecognition.isCapturing = function () {
    return speechrecognition._capturing;
};

//=========================================================================================
//=========================================================================================
// PLUGIN CALLS (from JS => JAVA)
//=========================================================================================
//=========================================================================================
/**
 * Start capture of Audio input
 *
 * @param {Object} cfg
 * keys:
 *  sampleRateInHz (44100),
 *  bufferSize (16384),
 *  channels (1 (mono) or 2 (stereo)),
 *  format ('PCM_8BIT' or 'PCM_16BIT'),
 *  normalize (true || false),
 *  normalizationFactor (create float data by dividing the audio data with this factor; default: 32767.0)
 *  streamToWebAudio (The plugin will handle all the conversion of raw data to audio)
 *  audioContext (If no audioContext is given, one will be created)
 *  concatenateMaxChunks (How many packets will be merged each time, low = low latency but can require more resources)
 *  audioSourceType (Use speechrecognition.AUDIOSOURCE_TYPE.)
 */
speechrecognition._capturing = false;

speechrecognition.startCapture = function (captureCfg, mfccCfg, vadCfg, eventCB, errorCB, speechStatusCB) 
{
    if (!speechrecognition._capturing) 
    {
        // check callbacks
        if (!eventCB) {
            _lastErrorCode = ERROR_CODE.MISSING_PARAMETER;
            throw "error: Mandatory parameter 'eventCB' is missing.";
        }
        else if (!(typeof eventCB === "function")) {
            _lastErrorCode = ERROR_CODE.INVALID_PARAMETER;
            throw "error: Parameter 'eventCB' must be of type function.";
        }
        else    speechrecognition._speechCapturedCB = speechCapturedCB;
            
        if (errorCB) {
            if (!(typeof errorCB === "function")) {
                _lastErrorCode = ERROR_CODE.INVALID_PARAMETER;
                throw "error: Parameter 'errorCB' must be of type function.";
            }
        }
        if (speechStatusCB) {
            if (!(typeof speechStatusCB === "function")) {
                _lastErrorCode = ERROR_CODE.INVALID_PARAMETER;
                throw "error: Parameter 'speechStatusCB' must be of type function.";
            }
        }
        speechrecognition._errorCB          = errorCB || null;
        speechrecognition._speechStatusCB   = speechStatusCB || null;
                
        
        // overwrite default params with exogenous ones
        var json_capture_params     = speechrecognition.checkCaptureParams(captureCfg);
        var json_mfcc_params        = speechrecognition.checkMfccParams(mfccCfg);
        var json_vad_params         = speechrecognition.checkVADParams(vadCfg);
        
        exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "startCapture",
            [json_capture_params,
             json_mfcc_params,
             json_vad_params]);

        speechrecognition._capturing = true;
    }
    else {
        throw "Already capturing!";
    }
};

/**
 * Stop capturing audio
 */
speechrecognition.stopCapture = function () 
{
    if (speechrecognition._capturing) {
        exec(null, speechrecognition._pluginError, speechrecognition.pluginName, "stopCapture", []);
        speechrecognition._capturing = false;    
    }
};

speechrecognition.getMFCC = function(successCB, errorCB, mfcc_params, source, filepath_noext)
{
    var mfcc_json_params    = speechrecognition.checkMfccParams(mfcc_params);
    
    speechrecognition.success      = successCB;
    speechrecognition.error        = errorCB;
    
    //check params consistency
    if(source == null || !source.length)
    {
        errorCB("ERROR in mfccCalculation: source is empty")
        return false;
    }
    else if(mfcc_params.nDataOrig == speechrecognition.ENUM.mfcc.DATAORIGIN.JSONDATA && source.constructor !== Array) 
    {
        errorCB("ERROR in mfccCalculation: you set data origin as data, but did not provide a data array");
        return false;
    }
    else if ((mfcc_params.nDataOrig == speechrecognition.ENUM.mfcc.DATAORIGIN.FILE || mfcc_params.nDataOrig == speechrecognition.ENUM.mfcc.DATAORIGIN.FOLDER) && source.constructor !== String)        
    {
        errorCB("ERROR in mfccCalculation: you set data origin as file/folder, but did not provide a string parameter");
        return false;
    }
    
    exec(speechrecognition.onMFCCSuccess, speechrecognition.onMFCCError, speechrecognition.pluginName, 'getMFCC', [mfcc_json_params, source, filepath_noext]);            
};

//startVAD
//stopVAD
//startMFCC
//stopMFCC
//
//==================================================================================================================
// PLUGIN CALLBACKS (from JAVA => JS)
//==================================================================================================================
/**
 * Callback for audio input
 *
 * @param {Object} audioInputData     keys: data (PCM)
 */
speechrecognition._pluginEvent = function (audioInputData) {
    try {
        if (audioInputData && audioInputData.data && audioInputData.data.length > 0) {
            var audioData = JSON.parse(audioInputData.data);
//            audioData = speechrecognition._normalizeAudio(audioData);

            if (speechrecognition.capture.params.bStreamToWebAudio && speechrecognition._capturing) {
                speechrecognition._enqueueAudioData(audioData);
            }
            else {
                cordova.fireWindowEvent("speechrecognition", {data: audioData});
            }
        }
        else if (audioInputData && audioInputData.error) {
            speechrecognition._pluginError(audioInputData.error);
        }
    }
    catch (ex) {
        speechrecognition._pluginError("speechrecognition._audioInputEvent ex: " + ex);
    }
};

/**
 * Error callback for AudioInputCapture start
 * @private
 */
speechrecognition._pluginError = function (e) {
    cordova.fireWindowEvent("speechrecognitionerror", {message: e});
};

/**
 * Callback for MFCC calculation
 *
 * @param {Object} mfccData     
 */
speechrecognition.onMFCCSuccess = function (mfccData) {
    speechrecognition.success(mfccData);
};

speechrecognition.onMFCCError = function (e) {
    speechrecognition.error(e);
};

//=========================================================================================
module.exports = speechrecognition;
