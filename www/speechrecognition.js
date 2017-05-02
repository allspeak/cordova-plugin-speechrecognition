var argscheck   = require('cordova/argscheck'),
    utils       = require('cordova/utils'),
    exec        = require('cordova/exec'),
    channel     = require('cordova/channel');

var speechrecognition           = {};
speechrecognition.ENUM          = {};

speechrecognition.pluginName    = "SpeechRecognitionPlugin";

//=====================================================================================================
// PLUGIN RETURN VALUES
//=====================================================================================================
// MUST MAP a plugin's ENUMS.java subset (results & statuses, excluding internal CMD & enums)
speechrecognition.ENUM.RETURN   = 
{
    CAPTURE_STATUS_STARTED          : 11, 
    CAPTURE_RESULT                  : 12, 
    CAPTURE_STATUS_STOPPED          : 13, 
    
    SPEECH_STATUS_STARTED           : 20,
    SPEECH_STATUS_STOPPED           : 21,
    SPEECH_STATUS_SENTENCE          : 22,
    SPEECH_STATUS_MAX_LENGTH        : 23,
    SPEECH_STATUS_MIN_LENGTH        : 24,
    
    MFCC_RESULT                     : 40, //
    MFCC_STATUS_PROGRESS_DATA       : 31, //
    MFCC_STATUS_PROGRESS_FILE       : 32, //
    MFCC_STATUS_PROGRESS_FOLDER     : 33, //   suspended for a bug
    
    TF_STATUS_PROCESS_STARTED       : 50, //
    TF_RESULT                       : 52, //
    
    CAPTURE_DATADEST_NONE           : 200,
    CAPTURE_DATADEST_JS_RAW         : 201,
    CAPTURE_DATADEST_JS_DB          : 202    
}; 

// MUST MAP plugin's ERRORS.java
speechrecognition.ENUM.ERRORS   = 
{
    SERVICE_INIT                    : 100, 
    
    INVALID_PARAMETER               : 101, 
    MISSING_PARAMETER               : 102, 
    ENCODING_ERROR                  : 103, 
    
    CAPTURE_ERROR                   : 110, 
    PLUGIN_INIT_CAPTURE             : 111, 
    SERVICE_INIT_CAPTURE            : 112, 
    PLUGIN_INIT_PLAYBACK            : 113,    
    SERVICE_INIT_PLAYBACK           : 114,    
    CAPTURE_ALREADY_STARTED         : 115, 
    
    VAD_ERROR                       : 120,
    PLUGIN_INIT_RECOGNITION         : 121,
    SERVICE_INIT_RECOGNITION        : 122,
    
    MFCC_ERROR                      : 130, 
    PLUGIN_INIT_MFCC                : 131,
    SERVICE_INIT_MFCC               : 132,
    
    TF_ERROR                        : 150, 
   
    UNSPECIFIED                     : 999
}; 

//=====================================================================================================
// ENUMS
//=====================================================================================================
speechrecognition.ENUM.capture  = {};
speechrecognition.ENUM.mfcc     = {};
speechrecognition.ENUM.vad      = {};
speechrecognition.ENUM.tf       = {};

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

speechrecognition.ENUM.capture.DATADEST={
    NONE        : 200,
    JSDATA      : 201
};

speechrecognition.ENUM.capture.DATATYPE={
    RAW         : 202,
    DB          : 203
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
//-----------------------------------------------------------------------------------------------------
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
    NONE        : 0,
    JSPROGRESS  : 1,
    JSDATA      : 2,
    JSDATAWEB   : 3,    //   send progress(filename) + data(JSONArray) to WEB
    FILE        : 4,
    FILEWEB     : 5,    //   send progress(filename) + write data(String) to file
    ALL         : 6
};
//-----------------------------------------------------------------------------------------------------
speechrecognition.ENUM.vad.AUDIO_RESULT_TYPE = {
    WAV_BLOB: 1,
    WEBAUDIO_AUDIOBUFFER: 2,
    RAW_DATA: 3,
    DETECTION_ONLY: 4
};

speechrecognition.ENUM.vad.STATUS = {
    SPEECH_STATUS_STARTED      : 1,
    SPEECH_STATUS_STOPPED      : 2,
    VAD_ERROR        : 3,
//    CAPTURE_STATUS_STARTED     : 4,
//    CAPTURE_STATUS_STOPPED     : 5,
//    CAPTURE_ERROR       : 6,
    ENCODING_ERROR      : 7,
    SPEECH_STATUS_MAX_LENGTH   : 8,
    SPEECH_STATUS_MIN_LENGTH   : 9
};

//-----------------------------------------------------------------------------------------------------
speechrecognition.ENUM.tf = {};

//=========================================================================================
// DEFAULT
//=========================================================================================
// Default values
speechrecognition.ENUM.capture.DEFAULT = {
    SAMPLERATE              : speechrecognition.ENUM.capture.SAMPLERATE.TELEPHONE_8000Hz,
    BUFFER_SIZE             : 1024,
    CHANNELS                : speechrecognition.ENUM.capture.CHANNELS.MONO,
    FORMAT                  : speechrecognition.ENUM.capture.FORMAT.PCM_16BIT,
    NORMALIZE               : true,
    NORMALIZATION_FACTOR    : 32767.0,
    STREAM_TO_WEBAUDIO      : false,
    CONCATENATE_MAX_CHUNKS  : 10,
    AUDIOSOURCE_TYPE        : speechrecognition.ENUM.capture.AUDIOSOURCE_TYPE.DEFAULT,
    START_MFCC              : false,
    START_VAD               : false,
    DATA_DEST               : speechrecognition.ENUM.capture.DATADEST.JSDATA
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

speechrecognition.ENUM.tf.DEFAULT = {
    NET_LAYERS : 20
};
//=========================================================================================
// CHECK INPUT PARAMS
//=========================================================================================

speechrecognition.mfcc = {};
speechrecognition.mfcc.params = {};

speechrecognition.capture = {};
speechrecognition.capture.params = {};

speechrecognition.vad = {};
speechrecognition.vad.params = {};

speechrecognition.tf = {};
speechrecognition.tf.params = {};


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
    speechrecognition.capture.params.nSampleRate                = capture_params.nSampleRate            || speechrecognition.ENUM.capture.DEFAULT.SAMPLERATE;
    speechrecognition.capture.params.nBufferSize                = capture_params.nBufferSize            || speechrecognition.ENUM.capture.DEFAULT.BUFFER_SIZE;
    speechrecognition.capture.params.nChannels                  = capture_params.nChannels              || speechrecognition.ENUM.capture.DEFAULT.CHANNELS;
    speechrecognition.capture.params.sFormat                    = capture_params.sFormat                || speechrecognition.ENUM.capture.DEFAULT.FORMAT;
    speechrecognition.capture.params.nAudioSourceType           = capture_params.nAudioSourceType       || 0;
    speechrecognition.capture.params.nNormalize                 = typeof capture_params.nNormalize == 'boolean' ? speechrecognition.ENUM.capture.nNormalize : speechrecognition.ENUM.capture.DEFAULT.NORMALIZE;
    speechrecognition.capture.params.fNormalizationFactor       = capture_params.fNormalizationFactor   || speechrecognition.ENUM.capture.DEFAULT.NORMALIZATION_FACTOR;
    speechrecognition.capture.params.nConcatenateMaxChunks      = capture_params.nConcatenateMaxChunks  || speechrecognition.ENUM.capture.DEFAULT.CONCATENATE_MAX_CHUNKS;
//    speechrecognition.capture.params.AudioContext               = null;
//    speechrecognition.capture.params.bStreamToWebAudio          = capture_params.bStreamToWebAudio      || speechrecognition.ENUM.capture.DEFAULT.STREAM_TO_WEBAUDIO;
//    speechrecognition.capture.params.bStartMFCC                 = capture_params.bStartMFCC             || speechrecognition.ENUM.capture.DEFAULT.START_MFCC;
//    speechrecognition.capture.params.bStartVAD                  = capture_params.bStartVAD              || speechrecognition.ENUM.capture.DEFAULT.START_VAD;
    speechrecognition.capture.params.nDataDest                  = capture_params.nDataDest              || speechrecognition.ENUM.capture.DEFAULT.DATA_DEST;
    
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
    speechrecognition.vad.params.nSpeechDetectionThreshold      = vad_params.nSpeechDetectionThreshold       || speechrecognition.ENUM.vad.DEFAULT.SPEECH_DETECTION_THRESHOLD;
    speechrecognition.vad.params.nSpeechDetectionMinimum        = vad_params.nSpeechDetectionMinimum         || speechrecognition.ENUM.vad.DEFAULT.SPEECH_DETECTION_MIN_LENGTH;
    speechrecognition.vad.params.nSpeechDetectionMaximum        = vad_params.nSpeechDetectionMaximum         || speechrecognition.ENUM.vad.DEFAULT.SPEECH_DETECTION_MAX_LENGTH;
    speechrecognition.vad.params.nSpeechDetectionAllowedDelay   = vad_params.nSpeechDetectionAllowedDelay    || speechrecognition.ENUM.vad.DEFAULT.SPEECH_DETECTION_ALLOWED_DELAY;
    speechrecognition.vad.params.audioResultType                = vad_params.audioResultType                 || speechrecognition.ENUM.vad.DEFAULT.AUDIO_RESULT_TYPE;
    speechrecognition.vad.params.bCompressPauses                = vad_params.bCompressPauses                 || speechrecognition.ENUM.vad.DEFAULT.SPEECH_DETECTION_COMPRESS_PAUSES;
    speechrecognition.vad.params.nAnalysisChunkLength           = vad_params.nAnalysisChunkLength            || speechrecognition.ENUM.vad.DEFAULT.SPEECH_DETECTION_ANALYSIS_CHUNK_LENGTH;
    speechrecognition.vad.params.bDetectOnly                    = vad_params.bDetectOnly                     || speechrecognition.ENUM.vad.DEFAULT.DETECT_ONLY;

    if (speechrecognition.vad.params.detectOnly) {
        speechrecognition.vad.params.audioResultType = AUDIO_RESULT_TYPE.DETECTION_ONLY;
    }
    return JSON.stringify(speechrecognition.vad.params); 
};

speechrecognition.checkTfParams = function(tf_params)
{
    speechrecognition.tf.params.nNetLayers                      = tf_params.nNetLayers                      || speechrecognition.ENUM.tf.DEFAULT.NET_LAYERS;
   
    return JSON.stringify(speechrecognition.tf.params); 
};

//==================================================================================================================
speechrecognition._capturing = false;
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
speechrecognition.startCapture = function (captureCfg, mfccCfg) {
    if (!speechrecognition._capturing) 
    {
        if(captureCfg == null)  captureCfg = {};
        if(mfccCfg == null)     mfccCfg = {};
        
        // overwrite default params with exogenous ones
        var json_capture_params     = speechrecognition.checkCaptureParams(captureCfg);
        var json_mfcc_params        = speechrecognition.checkMfccParams(mfccCfg);
        
        exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "startCapture",
            [json_capture_params,
             json_mfcc_params]);
    }
    else {
        throw "Already capturing!";
    }
};

speechrecognition.startMicPlayback = function (captureCfg) {
    if (!speechrecognition._capturing) 
    {
        if(captureCfg == null)  captureCfg = {};
        var json_capture_params     = speechrecognition.checkCaptureParams(captureCfg);  // overwrite default params with exogenous ones
        exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "startMicPlayback", [json_capture_params]);
    }
    else {
        throw "Already capturing!";
    }
};

/**
 * Stop capturing audio
 */
speechrecognition.stopCapture = function () {
    if (speechrecognition._capturing) exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "stopCapture", []);
};

speechrecognition.setPlayBackPercVol = function (perc) {
    if (speechrecognition._capturing) 
        exec(null, speechrecognition._pluginError, speechrecognition.pluginName, "setPlayBackPercVol", [perc]);
};

//---------------------------------------------------------------
/**
 * Start calculating MFCC
 */
speechrecognition.startMFCC = function () {
    if (speechrecognition._capturing) 
        exec(null, speechrecognition._pluginError, speechrecognition.pluginName, "stopMFCC", [speechrecognition.mfcc.params]);
};

/**
 * Stop calculating MFCC
 */
speechrecognition.stopMFCC = function () {
    if (speechrecognition._capturing) 
        exec(null, speechrecognition._pluginError, speechrecognition.pluginName, "stopMFCC", []);
};
speechrecognition.getMFCC = function(mfcc_params, source, filepath_noext)
{
    var mfcc_json_params    = speechrecognition.checkMfccParams(mfcc_params);
    
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

//---------------------------------------------------------------

speechrecognition.startSpeechRecognition = function (captureCfg, vadCfg, mfccCfg, tfCfg) 
{
    if (!speechrecognition._capturing) 
    {
        if(captureCfg == null)  captureCfg = {};
        if(mfccCfg == null)     mfccCfg = {};
        if(vadCfg == null)      vadCfg = {};
        if(tfCfg == null)       tfCfg = {};
        
        // overwrite default params with exogenous ones
        var json_capture_params     = speechrecognition.checkCaptureParams(captureCfg);
        var json_mfcc_params        = speechrecognition.checkMfccParams(mfccCfg);
        var json_vad_params         = speechrecognition.checkVadParams(vadCfg);
        var json_tf_params          = speechrecognition.checkTfParams(tfCfg);
        
        exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "startSpeechRecognition",
            [json_capture_params,
             json_mfcc_params,
             json_vad_params,
             json_tf_params]);
    }
    else {
        throw "Already capturing!";
    }
};

/**
 * Stop capturing audio
 */
speechrecognition.stopSpeechRecognition = function () 
{
    if (speechrecognition._capturing) exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "stopSpeechRecognition", []);
};

//==================================================================================================================
// PLUGIN CALLBACKS (from JAVA => JS)
//==================================================================================================================
/**
 * Callback for audio input
 *
 * @param {Object} audioInputData     keys: data (PCM)
 */
speechrecognition._pluginEvent = function (data) {
    try {
        switch(data.type)
        {
            case speechrecognition.ENUM.RETURN.CAPTURE_RESULT:
                if(data.data_type == speechrecognition.ENUM.RETURN.CAPTURE_DATADEST_JS_RAW)
                {
                    if (data && data.data && data.data.length > 0) 
                    {
                        var audioData = JSON.parse(data.data);
                        cordova.fireWindowEvent("audioinput", {data: audioData});
                    }
                    else if (data && data.error) {
                        speechrecognition._captureErrorEvent(data.error);
                    }
                }
                else
                {
                    //mean decibel
                    cordova.fireWindowEvent("audiometer", {data: Math.round(JSON.parse(data.data))});
                }
                break;
            
            case speechrecognition.ENUM.RETURN.CAPTURE_STATUS_STARTED:
                console.log("audioInputMfcc._startaudioInputEvent");
                cordova.fireWindowEvent("capturestarted", {});
                speechrecognition._capturing = true;                   
                break;
                
            case speechrecognition.ENUM.RETURN.CAPTURE_STATUS_STOPPED:
                console.log("audioInputMfcc._stopaudioInputEvent: captured " + parseInt(data.bytesread) + "bytes, " + parseInt(data.datacaptured)*12 + " time windows, dataprocessed: " + parseInt(data.dataprocessed)*12);
                cordova.fireWindowEvent("capturestopped", {data: data});
                speechrecognition._capturing = false;                   
                break;
                
            case speechrecognition.ENUM.RETURN.MFCC_RESULT:
                cordova.fireWindowEvent("mfccdata", {data: data});
                break;
                
            case speechrecognition.ENUM.RETURN.MFCC_STATUS_PROGRESS_DATA:
                cordova.fireWindowEvent("mfccprogressdata", {data: data});
                break;
                
            case speechrecognition.ENUM.RETURN.MFCC_STATUS_PROGRESS_FILE:
                cordova.fireWindowEvent("mfccprogressfile", {data: data});
                break;
                
            case speechrecognition.ENUM.RETURN.SPEECH_STATUS_STARTED:
            case speechrecognition.ENUM.RETURN.SPEECH_STATUS_STOPPED:
            case speechrecognition.ENUM.RETURN.SPEECH_STATUS_SENTENCE:
            case speechrecognition.ENUM.RETURN.SPEECH_STATUS_MAX_LENGTH:
            case speechrecognition.ENUM.RETURN.SPEECH_STATUS_MIN_LENGTH:
                cordova.fireWindowEvent("speechstatus", {data: data.type});
                break;
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
// TODO : receive an error code from plugin
speechrecognition._pluginError = function (error) {
    speechrecognition._capturing = false;    
    cordova.fireWindowEvent("pluginError", {message: error.message, type:error.type});
};

//=========================================================================================
module.exports = speechrecognition;
