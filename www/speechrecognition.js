var argscheck   = require('cordova/argscheck'),
    utils       = require('cordova/utils'),
    exec        = require('cordova/exec'),
    channel     = require('cordova/channel');

var speechrecognition           = {};

speechrecognition.ver = "0.4.1";
speechrecognition.ENUM          = {};

speechrecognition.pluginName    = "SpeechRecognitionPlugin";

//=====================================================================================================
// PLUGIN RETURN VALUES
//=====================================================================================================
// MUST MAP a plugin's ENUMS.java subset (results & statuses, excluding internal CMD & enums)
speechrecognition.ENUM.PLUGIN   = 
{
    CAPTURE_STATUS_STARTED          : 11, 
    CAPTURE_RESULT                  : 12, 
    CAPTURE_STATUS_STOPPED          : 13, 
    
    SPEECH_STATUS_STARTED           : 20,
    SPEECH_STATUS_STOPPED           : 21,
    SPEECH_STATUS_SENTENCE          : 22,
    SPEECH_STATUS_MAX_LENGTH        : 23,
    SPEECH_STATUS_MIN_LENGTH        : 24,
    
    MFCC_RESULT                     : 41, //
    MFCC_STATUS_PROGRESS_DATA       : 31, //
    MFCC_STATUS_PROGRESS_FILE       : 32, //
    MFCC_STATUS_PROGRESS_FOLDER     : 33, //   suspended for a bug
    
    TF_STATUS_MODEL_LOADED          : 50, //
    TF_STATUS_PROCESS_STARTED       : 51, //
    TF_RESULT                       : 56, //
    TF_CMD_RECOGNIZE_FILE           : 57, //
    TF_RESUME_RECOGNITION           : 58, //
    
    CAPTURE_DATADEST_NONE           : 200,
    CAPTURE_DATADEST_JS_RAW         : 201,
    CAPTURE_DATADEST_JS_DB          : 202,    
    CAPTURE_DATADEST_JS_RAWDB       : 203,    
    CAPTURE_DATADEST_FILE           : 204,    
    CAPTURE_DATADEST_FILE_JS_RAW    : 205,    
    CAPTURE_DATADEST_FILE_JS_DB     : 206,    
    CAPTURE_DATADEST_FILE_JS_RAWDB  : 207,    
    
    MFCC_DATADEST_NOCALC            : 230,    // DO NOT CALCULATE MFCC during capture
    MFCC_DATADEST_NONE              : 231,    // data(float[][])    => SERVICE
    MFCC_DATADEST_JSPROGRESS        : 232,    // data               => SERVICE + progress(filename) => WEB
    MFCC_DATADEST_JSDATA            : 233,    // data               => SERVICE + progress(filename) + data(JSONArray) => WEB
    MFCC_DATADEST_JSDATAWEB         : 234,    // data(JSONArray)    => WEB
    MFCC_DATADEST_FILE              : 235,    // progress(filename) => SERVICE + data(String) => FILE
    MFCC_DATADEST_FILEWEB           : 236,    // progress(filename) => SERVICE + data(String) => FILE + data(JSONArray) => WEB
    MFCC_DATADEST_ALL               : 237,    // data               => SERVICE + data(String) => FILE + data(JSONArray) => WEB
    
    MFCC_DATAORIGIN_JSONDATA        : 240,
    MFCC_DATAORIGIN_FILE            : 241,
    MFCC_DATAORIGIN_FOLDER          : 242,
    MFCC_DATAORIGIN_RAWDATA         : 243,   
    
    MFCC_DATATYPE_MFPARAMETERS      : 250,
    MFCC_DATATYPE_MFFILTERS         : 251,
    MFCC_PROCSCHEME_F_S             : 252,      // FilterBanks, Spectral derivatives, Contexting
    MFCC_PROCSCHEME_F_S_PP          : 253,      // FilterBanks, Spectral derivatives, Pre-processing, Contexting
    MFCC_PROCSCHEME_F_T             : 254,      // FilterBanks, Temporal derivatives, Contexting
    MFCC_PROCSCHEME_F_T_PP          : 255,      // FilterBanks, Temporal derivatives, Pre-processing, Contexting      
    MFCC_PROCSCHEME_F_S_NOTHR       : 256,      // FilterBanks, Spectral derivatives,                   DO NOT threshold frames with null cepstra
    MFCC_PROCSCHEME_F_S_PP_NOTHR    : 257,      // FilterBanks, Spectral derivatives, Pre-processing,   DO NOT threshold frames with null cepstra
    MFCC_PROCSCHEME_F_T_NOTHR       : 258,      // FilterBanks, Temporal derivatives,           ,       DO NOT threshold frames with null cepstra
    MFCC_PROCSCHEME_F_T_PP_NOTHR    : 259,      // FilterBanks, Temporal derivatives, Pre-processing,   DO NOT threshold frames with null cepstra    
    
    MFCC_PROCSCHEME_F               : 260,      // FilterBanks,                                         Contexting
    MFCC_PROCSCHEME_F_PP            : 261,      // FilterBanks,                         Pre-processing, Contexting
    MFCC_PROCSCHEME_F_NOTHR         : 262,      // FilterBanks,                                           DO NOT threshold frames with null cepstra
    MFCC_PROCSCHEME_F_PP_NOTHR      : 263,      // FilterBanks,                         Pre-processing,   DO NOT threshold frames with null cepstra
    
    TF_DATADEST_MODEL               : 270,      // sentence's cepstra are sent to TF model only
    TF_DATADEST_FILEONLY            : 271,      // sentence's cepstra are written to a file only
    TF_DATADEST_MODEL_FILE          : 272,      // sentence's cepstra are sent to TF model and written to a file    
    
    TF_MODELTYPE_COMMON             : 273,      // COMMON NET made with a general population
    TF_MODELTYPE_USER               : 274,      // PURE USER (PU) NET made only with user sentences (recordings must be ncommands x minrepetitions)
    TF_MODELTYPE_USER_ADAPTED       : 275,      // PURE USER ADAPTED (fine-tuned) NET made with user sentences (recordings must be ncommands x minrepetitions)   
    TF_MODELTYPE_COMMON_ADAPTED     : 276,      // COMMON ADAPTED (fine-tuned) NET made with user sentences (recordings must be ncommands x minrepetitions)   
    TF_MODELTYPE_USER_READAPTED     : 277,      // RE-ADAPTION of PUA NET made with user sentences (recordings are free)   
    TF_MODELTYPE_COMMON_READAPTED   : 278,      // RE-ADAPTION of CA NET made with user sentences (recordings are free)   
    
    TF_MODELCLASS_FF                : 280,      // use Feed Forward net
    TF_MODELCLASS_LSTM              : 281,      // use LSTM net

    VAD_RESULT_DETECTION_ONLY               : 290,      // just detect. no MFCC, no save, only callback to WL    
    VAD_RESULT_SAVE_SENTENCE                : 291,      // save (natively) and/or send data to WL  
    VAD_RESULT_PROCESS_DATA                 : 292,      // process sentence data (MFCC & TF)
    VAD_RESULT_PROCESS_DATA_SAVE_SENTENCE   : 293,      // process sentence data (MFCC & TF)    
    
    TRAIN_DATA_ZIPPED               : 299,
        
    BLUETOOTH_INIT                  : 300, 
    HEADSET_CONNECTED               : 301,
    HEADSET_DISCONNECTED            : 302, 
    HEADSET_CONNECTING              : 303,     
    HEADSET_DISCONNECTING           : 304,     
    AUDIOSCO_CONNECTED              : 305,     
    AUDIOSCO_DISCONNECTED           : 306,
    HEADSET_EXIST                   : 307,
    BLUETOOTH_STATUS                : 308
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
    SERVICE_INIT_TF_MODEL           : 115,    
    CAPTURE_ALREADY_STARTED         : 116, 
    
    VAD_ERROR                       : 120,
    PLUGIN_INIT_RECOGNITION         : 121,
    SERVICE_INIT_RECOGNITION        : 122,
    
    MFCC_ERROR                      : 130, 
    PLUGIN_INIT_MFCC                : 131,
    SERVICE_INIT_MFCC               : 132,
    
    TF_ERROR                        : 150, 
    
    HEADSET_ERROR                   : 160, 
   
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
    VOIP_16000Hz        : 16000
};

// Buffer Size
speechrecognition.ENUM.capture.BUFFER_SIZES = {
    BS_256              : 256,
    BS_512              : 512,
    BS_1024             : 1024,
    BS_2048             : 2048,
    BS_4096             : 4096
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
//-----------------------------------------------------------------------------------------------------

speechrecognition.ENUM.vad.MIN_ACL_MS = 32;
speechrecognition.ENUM.vad.MIN_MXL_MS = 8000;   // minimun SPEECH_DETECTION_MAX_LENGTH
speechrecognition.ENUM.vad.MIN_MIL_MS = 300;    // minimum SPEECH_DETECTION_MIN_LENGTH
speechrecognition.ENUM.vad.MAX_MIL_MS = 1000;   // maximum SPEECH_DETECTION_MIN_LENGTH

//=========================================================================================

speechrecognition.ENUM.mfcc.processingTypes = [
    {"label": "Filt-Spec-Thr"       , "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F_S},
    {"label": "Filt-Spectral-PP-Thr", "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F_S_PP},
    {"label": "Filt-Temp-Thr"       , "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F_T},
    {"label": "Filt-Temp-PP-Thr"    , "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F_T_PP},
    {"label": "Filt-Spect"          , "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F_S_NOTHR},
    {"label": "Filt-Spect-PP"       , "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F_S_PP_NOTHR},
    {"label": "Filt-Temp"           , "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F_T_NOTHR},
    {"label": "Filt-Temp-PP"        , "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F_T_PP_NOTHR},
    {"label": "Filt-Thr"            , "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F},
    {"label": "Filt-PP-Thr"         , "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F_PP},
    {"label": "Filt"                , "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F_NOTHR},
    {"label": "Filt-PP"             , "value": speechrecognition.ENUM.PLUGIN.MFCC_PROCSCHEME_F_PP_NOTHR}    
];
//=========================================================================================
// DEFAULT
//=========================================================================================
// Default values
speechrecognition.ENUM.capture.DEFAULT = {
    nSampleRate             : speechrecognition.ENUM.capture.SAMPLERATE.TELEPHONE_8000Hz,
    nBufferSize             : 1024,
    nChannels               : speechrecognition.ENUM.capture.CHANNELS.MONO,
    sFormat                 : speechrecognition.ENUM.capture.FORMAT.PCM_16BIT,
    nNormalize              : true,
    fNormalizationFactor    : 32767.0,
    nConcatenateMaxChunks   : 10,
    nAudioSourceType        : speechrecognition.ENUM.capture.AUDIOSOURCE_TYPE.DEFAULT,
    nDataDest               : speechrecognition.ENUM.PLUGIN.CAPTURE_DATADEST_JS_RAW,
    nChunkMaxLengthMS       : 20000,
    sOutputPath             : ""
};

speechrecognition.ENUM.mfcc.DEFAULT = {
    nNumberOfMFCCParameters : 13,
    dSamplingFrequency      : 8000.0,
    nNumberofFilters        : 24,
    nFftLength              : 256,
    bIsLifteringEnabled     : true,
    nLifteringCoefficient   : 22,
    bCalculate0ThCoeff      : true,
    nWindowDistance         : 80,
    nWindowLength           : 200,
    nDataType               : speechrecognition.ENUM.PLUGIN.MFCC_DATATYPE_MFFILTERS,
    nDataDest               : speechrecognition.ENUM.PLUGIN.MFCC_DATADEST_NONE,
    nDataOrig               : speechrecognition.ENUM.PLUGIN.MFCC_DATAORIGIN_RAWDATA,
    sOutputPath             : "",
    nDeltaWindow            : 2,
    nProcessingScheme       : 252
};

speechrecognition.ENUM.vad.DEFAULT = {
    nSpeechDetectionThreshold       : 15, // dB
    nSpeechDetectionAllowedDelay    : 384, // mS
    nSpeechDetectionMaximum         : 10000, // mS
    nSpeechDetectionMinimum         : 512, // mS
    bCompressPauses                 : false,
    nAnalysisChunkLength            : 64, // mS
    nAudioResultType                : speechrecognition.ENUM.PLUGIN.VAD_RESULT_PROCESS_DATA,
    bAutoPause                      : true
};

speechrecognition.ENUM.tf.DEFAULT = {
    sLabel                  : "",        
    nModelClass             : speechrecognition.ENUM.PLUGIN.TF_MODELCLASS_FF,        
    nModelType              : speechrecognition.ENUM.PLUGIN.TF_MODELTYPE_COMMON,        
    nInputParams            : 792,        
    nContextFrames          : 5,        
    nItems2Recognize        : 25,
    sModelFilePath          : "",         
    saInputNodeName         : ["inputs/I"],          
    sOutputNodeName         : "SMO",      
    nDataDest               : speechrecognition.ENUM.PLUGIN.TF_DATADEST_MODEL,      
    fRecognitionThreshold   : 0.1,      
    sCreationTime           : "",      
    nProcessingScheme       : 252,      
    sModelFileName          : "controls_fsc.pb",         
    sLocalFolder            : "default"
};
//=========================================================================================
// CHECK INPUT PARAMS
//=========================================================================================

speechrecognition.mfcc              = {};
speechrecognition.mfcc.params       = {};

speechrecognition.capture           = {};
speechrecognition.capture.params    = {};

speechrecognition.vad               = {};
speechrecognition.vad.params        = {};

speechrecognition.tf                = {};
speechrecognition.tf.params         = {};

speechrecognition.checkCaptureParams = function(capture_params)
{
    speechrecognition.capture.params.nSampleRate                = capture_params.nSampleRate            || speechrecognition.ENUM.capture.DEFAULT.nSampleRate;
    speechrecognition.capture.params.nBufferSize                = capture_params.nBufferSize            || speechrecognition.ENUM.capture.DEFAULT.nBufferSize;
    speechrecognition.capture.params.nChannels                  = capture_params.nChannels              || speechrecognition.ENUM.capture.DEFAULT.nChannels;
    speechrecognition.capture.params.sFormat                    = capture_params.sFormat                || speechrecognition.ENUM.capture.DEFAULT.sFormat;
    speechrecognition.capture.params.nAudioSourceType           = capture_params.nAudioSourceType       || 0;
    speechrecognition.capture.params.nNormalize                 = capture_params.nNormalize             || speechrecognition.ENUM.capture.DEFAULT.nNormalize;
    speechrecognition.capture.params.fNormalizationFactor       = capture_params.fNormalizationFactor   || speechrecognition.ENUM.capture.DEFAULT.fNormalizationFactor;
    speechrecognition.capture.params.nConcatenateMaxChunks      = capture_params.nConcatenateMaxChunks  || speechrecognition.ENUM.capture.DEFAULT.nConcatenateMaxChunks;
    speechrecognition.capture.params.nDataDest                  = capture_params.nDataDest              || speechrecognition.ENUM.capture.DEFAULT.nDataDest;
    speechrecognition.capture.params.nChunkMaxLengthMS          = capture_params.nChunkMaxLengthMS      || speechrecognition.ENUM.capture.DEFAULT.nChunkMaxLengthMS;
    speechrecognition.capture.params.sOutputPath                = capture_params.sOutputPath            || speechrecognition.ENUM.capture.DEFAULT.sOutputPath;
    
    if (speechrecognition.capture.params.nChannels < 1 && speechrecognition.capture.params.nChannels > 2) 
        throw "Invalid number of channels (" + speechrecognition.capture.params.nChannels + "). Only mono (1) and stereo (2) is" +" supported.";
    
    if (speechrecognition.capture.params.sFormat != "PCM_16BIT" && speechrecognition.capture.params.sFormat != "PCM_8BIT") 
        throw "Invalid format (" + speechrecognition.capture.params.sFormat + "). Only 'PCM_8BIT' and 'PCM_16BIT' is" + " supported.";
    
    if (speechrecognition.capture.params.nBufferSize <= 0) 
        throw "Invalid bufferSize (" + speechrecognition.capture.params.nBufferSize + "). Must be greater than zero.";
    
    if (speechrecognition.capture.params.nConcatenateMaxChunks <= 0)
        throw "Invalid concatenateMaxChunks (" + speechrecognition.capture.params.nConcatenateMaxChunks + "). Must be greater than zero.";
    
    return JSON.stringify(speechrecognition.capture.params); 
};

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
    speechrecognition.mfcc.params.nDeltaWindow                 = mfcc_params.nDeltaWindow              || speechrecognition.ENUM.mfcc.DEFAULT.nDeltaWindow;          
    speechrecognition.mfcc.params.nProcessingScheme            = mfcc_params.nProcessingScheme         || speechrecognition.ENUM.mfcc.DEFAULT.nProcessingScheme;          
    
    return JSON.stringify(speechrecognition.mfcc.params); 
};

speechrecognition.checkVadParams = function(vad_params)
{
    speechrecognition.vad.params.nSpeechDetectionThreshold      = vad_params.nSpeechDetectionThreshold       || speechrecognition.ENUM.vad.DEFAULT.nSpeechDetectionThreshold;
    speechrecognition.vad.params.nSpeechDetectionMinimum        = vad_params.nSpeechDetectionMinimum         || speechrecognition.ENUM.vad.DEFAULT.nSpeechDetectionMinimum;
    speechrecognition.vad.params.nSpeechDetectionMaximum        = vad_params.nSpeechDetectionMaximum         || speechrecognition.ENUM.vad.DEFAULT.nSpeechDetectionMaximum;
    speechrecognition.vad.params.nSpeechDetectionAllowedDelay   = vad_params.nSpeechDetectionAllowedDelay    || speechrecognition.ENUM.vad.DEFAULT.nSpeechDetectionAllowedDelay;
    speechrecognition.vad.params.nAudioResultType               = vad_params.nAudioResultType                || speechrecognition.ENUM.vad.DEFAULT.nAudioResultType;
    speechrecognition.vad.params.bCompressPauses                = vad_params.bCompressPauses                 || speechrecognition.ENUM.vad.DEFAULT.bCompressPauses;
    speechrecognition.vad.params.nAnalysisChunkLength           = vad_params.nAnalysisChunkLength            || speechrecognition.ENUM.vad.DEFAULT.nAnalysisChunkLength;
    speechrecognition.vad.params.sDebugString                   = vad_params.sDebugString                    || "";
    speechrecognition.vad.params.bAutoPause                     = vad_params.bAutoPause                      || speechrecognition.ENUM.vad.DEFAULT.bAutoPause;

    return JSON.stringify(speechrecognition.vad.params); 
};

speechrecognition.checkTfParams = function(tf_params)
{
    speechrecognition.tf.params.sLabel                          = tf_params.sLabel                      || "";          
    speechrecognition.tf.params.nModelClass                     = tf_params.nModelClass                 || speechrecognition.ENUM.tf.DEFAULT.nModelClass;          
    speechrecognition.tf.params.nModelType                      = tf_params.nModelType                  || speechrecognition.ENUM.tf.DEFAULT.nModelType;          
    speechrecognition.tf.params.nInputParams                    = tf_params.nInputParams                || speechrecognition.ENUM.tf.DEFAULT.nInputParams;          
    speechrecognition.tf.params.nContextFrames                  = tf_params.nContextFrames              || speechrecognition.ENUM.tf.DEFAULT.nContextFrames;          
    speechrecognition.tf.params.nItems2Recognize                = tf_params.nItems2Recognize            || speechrecognition.ENUM.tf.DEFAULT.nItems2Recognize;          
    speechrecognition.tf.params.sModelFilePath                  = tf_params.sModelFilePath              || "";          
    speechrecognition.tf.params.saInputNodeName                 = tf_params.saInputNodeName             || [speechrecognition.ENUM.tf.DEFAULT.sInputNodeName];          
    speechrecognition.tf.params.sOutputNodeName                 = tf_params.sOutputNodeName             || speechrecognition.ENUM.tf.DEFAULT.sOutputNodeName;          
    speechrecognition.tf.params.fRecognitionThreshold           = tf_params.fRecognitionThreshold       || speechrecognition.ENUM.tf.DEFAULT.fRecognitionThreshold;
    speechrecognition.tf.params.sCreationTime                   = tf_params.sCreationTime               || "";
    speechrecognition.tf.params.nProcessingScheme               = tf_params.nProcessingScheme           || speechrecognition.ENUM.tf.DEFAULT.nProcessingScheme;    
    speechrecognition.tf.params.sModelFileName                  = tf_params.sModelFileName              || speechrecognition.ENUM.tf.DEFAULT.sModelFileName;    
    speechrecognition.tf.params.sLocalFolder                    = tf_params.sLocalFolder                || speechrecognition.ENUM.tf.DEFAULT.sLocalFolder;    
    speechrecognition.tf.params.commands                        = tf_params.commands                    || [];
    speechrecognition.tf.params.nDataDest                       = tf_params.nDataDest                   || speechrecognition.ENUM.tf.DEFAULT.nDataDest;
    speechrecognition.tf.params.bLoaded                         = false;
       
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
//
//=========================================================================================
// PROMISES
//=========================================================================================
// doesn't return to _pluginEvent
speechrecognition.getAudioDevices = function () 
{
    var promise = new Promise(function(resolve, reject) {
        successCallback = resolve;
        errorCallback = reject;
    });
    exec(successCallback, errorCallback, speechrecognition.pluginName, "getAudioDevices", []);
    return promise;  
};

speechrecognition.getBluetoothStatus = function () 
{
    var promise = new Promise(function(resolve, reject) {
        successCallback = resolve;
        errorCallback = reject;
    });    
    exec(successCallback, errorCallback, speechrecognition.pluginName, "getBluetoothStatus", []);
    return promise;  
};

speechrecognition.loadTFNet = function (mTfCfg) 
{
    var promise = new Promise(function(resolve, reject) {
        successCallback = resolve;
        errorCallback = reject;
    });
    
    if(mTfCfg == null)     mTfCfg = {};
        
    // overwrite default params with exogenous ones
    var json_tf_params = speechrecognition.checkTfParams(mTfCfg);    
    exec(successCallback, errorCallback, speechrecognition.pluginName, "loadTFModel", [json_tf_params]);
    
    return promise;
};

/**
 * adjustVADThreshold
 * set a new VAD threshold or ask for threshold reset
 */
speechrecognition.adjustVADThreshold = function (threshold) 
{
//    if (speechrecognition._capturing) 
    var promise = new Promise(function(resolve, reject) {
        successCallback = resolve;
        errorCallback = reject;
    });
    
   exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "adjustVADThreshold", [parseInt(threshold)]);
   return promise;    
};

//=========================================================================================
// UNIFIED _PLUGINEVENT CALLBACK
//=========================================================================================
speechrecognition.enableHeadSet = function(enable)
{
    if(enable != true && enable != false) return Promise.reject({"message": "enable param of enableHeadSet is not valid: " + enable.toString()});
    exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "enableHeadSet", [enable]);
};

// init BluetoothHeadsetProfile and its listeners...do be done only once !
speechrecognition.initBluetooth = function(bltsrv_callback)
{
    speechrecognition.bluetoothSrvCallback = bltsrv_callback;
    
    exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "initBluetooth", []);
};


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

speechrecognition.getMFCC = function(mfcc_params, source, dest, overwrite, filefilters)
{
    if(overwrite == null)   overwrite   = true;
    if(dest == null)        dest        = source;
        
    var mfcc_json_params    = speechrecognition.checkMfccParams(mfcc_params);
    
    //check params consistency
    if(source == null || !source.length)
    {
        alert("ERROR in mfccCalculation: source is empty")
        return false;
    }
    else if(mfcc_params.nDataOrig == speechrecognition.ENUM.PLUGIN.MFCC_DATAORIGIN_JSONDATA && source.constructor !== Array) 
    {
        alert("ERROR in mfccCalculation: you set data origin as data, but did not provide a data array");
        return false;
    }
    else if ((mfcc_params.nDataOrig == speechrecognition.ENUM.PLUGIN.MFCC_DATAORIGIN_FILE || mfcc_params.nDataOrig == speechrecognition.ENUM.PLUGIN.MFCC_DATAORIGIN_FOLDER) && source.constructor !== String)        
    {
        alert("ERROR in mfccCalculation: you set data origin as file/folder, but did not provide a string parameter");
        return false;
    }
    
    exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, 'getMFCC', [mfcc_json_params, source, dest, overwrite, filefilters]);            
    return true;
};

speechrecognition.startSpeechRecognition = function (captureCfg, vadCfg, mfccCfg, tfCfg) 
{
    if (!speechrecognition._capturing) 
    {
        if(captureCfg == null)  captureCfg = {};
        if(vadCfg == null)      vadCfg = {};
        if(mfccCfg == null)     mfccCfg = {};
        if(tfCfg == null)       tfCfg = {};
        
        // overwrite default params with exogenous ones
        var json_capture_params     = speechrecognition.checkCaptureParams(captureCfg);
        var json_vad_params         = speechrecognition.checkVadParams(vadCfg);
        var json_mfcc_params        = speechrecognition.checkMfccParams(mfccCfg);
        var json_tf_params          = speechrecognition.checkTfParams(tfCfg);
        
        exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "startSpeechRecognition",
            [json_capture_params,
             json_vad_params,
             json_mfcc_params,
             json_tf_params]);
    }
    else {
        throw "Already capturing!";
    }
};

/**
 * Stop recognizing speech
 */
speechrecognition.stopSpeechRecognition = function () 
{
    if (speechrecognition._capturing) exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "stopSpeechRecognition", []);
};

/**
 * Resume recognizing speech
 * after recognized sentence has been playback, resume recognition (stopped internally in the VAD module
 */
speechrecognition.resumeSpeechRecognition = function () 
{
    if (speechrecognition._capturing) exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "resumeSpeechRecognition", []);
};

/**
 * zipFolder
 * 
 */
speechrecognition.zipFolder = function (infolder, outzippath, list_ext) 
{
    exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "zipFolder", [infolder, outzippath, list_ext]);
};

speechrecognition.debugCall = function (obj) 
{
    var json_params = JSON.stringify(obj); 
    exec(speechrecognition._pluginEvent, speechrecognition._pluginError, speechrecognition.pluginName, "debugCall", [json_params]);
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
            case speechrecognition.ENUM.PLUGIN.CAPTURE_RESULT:
                switch(data.data_type)
                {
                    case speechrecognition.ENUM.PLUGIN.CAPTURE_DATADEST_JS_RAW:
                    case speechrecognition.ENUM.PLUGIN.CAPTURE_DATADEST_FILE_JS_RAW:
                        if (data && data.data && data.data.length > 0) 
                        {
                            var audioData = JSON.parse(data.data);
                            cordova.fireWindowEvent("audioinput", {data: audioData});
                        }
                        else if (data && data.error)
                            speechrecognition._pluginError({message: "error in _pluginEvent: " + data.error.toString()});

                        break;
                        
                    case speechrecognition.ENUM.PLUGIN.CAPTURE_DATADEST_JS_DB:
                    case speechrecognition.ENUM.PLUGIN.CAPTURE_DATADEST_FILE_JS_DB:
                        
                        if(data.decibels == "-Infinity")    data.decibels = -1000;
                        if(data.threshold != null)          
                            cordova.fireWindowEvent("audiometer", {decibels: Math.round(JSON.parse(data.decibels)), threshold: Math.round(JSON.parse(data.threshold))});   //mean decibel & threshold
                        else
                            cordova.fireWindowEvent("audiometer", {decibels: Math.round(JSON.parse(data.decibels))});   //mean decibel & threshold
                        break;
                    
                    case speechrecognition.ENUM.PLUGIN.CAPTURE_DATADEST_JS_RAWDB:
                    case speechrecognition.ENUM.PLUGIN.CAPTURE_DATADEST_FILE_JS_RAWDB:
  
                        if(data.decibels == "-Infinity")    data.decibels = -1000;    
                        
                        if(data.threshold != null)          
                            cordova.fireWindowEvent("audiometer", {decibels: Math.round(JSON.parse(data.decibels)), threshold: Math.round(JSON.parse(data.threshold))});   //mean decibel & threshold
                        else
                            cordova.fireWindowEvent("audiometer", {decibels: Math.round(JSON.parse(data.decibels))});   //mean decibel & threshold
                    
                        if (data && data.data && data.data.length > 0) 
                        {
                            var audioData = JSON.parse(data.data);
                            cordova.fireWindowEvent("audioinput", {data: audioData});
                        }
                        else if (data && data.error) 
                            speechrecognition._pluginError({message: "error in _pluginEvent: " + data.error.toString()});
                        break;
                }
                break;
            
            case speechrecognition.ENUM.PLUGIN.CAPTURE_STATUS_STARTED:
                console.log("audioInputMfcc._startaudioInputEvent");
                cordova.fireWindowEvent("capturestarted", {});
                speechrecognition._capturing = true;                   
                break;
                
            case speechrecognition.ENUM.PLUGIN.CAPTURE_STATUS_STOPPED:
                console.log("audioInputMfcc._stopaudioInputEvent: captured " + parseInt(data.bytesread) + " bytes, " + parseInt(data.framesprocessed) + " processed frames, still " + parseInt(data.frames2beprocessed) + " to be processed, expected frames: " + parseInt(data.expectedframes));
                cordova.fireWindowEvent("capturestopped", {data: data});
                speechrecognition._capturing = false;                   
                break;
                
            case speechrecognition.ENUM.PLUGIN.MFCC_RESULT:
                cordova.fireWindowEvent("mfccdata", {data: data});
                break;onMFCCSuccess
                
            case speechrecognition.ENUM.PLUGIN.MFCC_STATUS_PROGRESS_DATA:
                cordova.fireWindowEvent("mfccprogressdata", {data: data});
                break;
                
            case speechrecognition.ENUM.PLUGIN.MFCC_STATUS_PROGRESS_FILE:
                cordova.fireWindowEvent("mfccprogressfile", {data: data});
                break;
                
            case speechrecognition.ENUM.PLUGIN.MFCC_STATUS_PROGRESS_FOLDER:
                cordova.fireWindowEvent("mfccprogressfolder", {data: data});
                break;
                
            case speechrecognition.ENUM.PLUGIN.SPEECH_STATUS_STARTED:
            case speechrecognition.ENUM.PLUGIN.SPEECH_STATUS_STOPPED:
            case speechrecognition.ENUM.PLUGIN.SPEECH_STATUS_SENTENCE:
            case speechrecognition.ENUM.PLUGIN.SPEECH_STATUS_MAX_LENGTH:
            case speechrecognition.ENUM.PLUGIN.SPEECH_STATUS_MIN_LENGTH:
                cordova.fireWindowEvent("speechstatus", {datatype: data.type});
                break;
                
//            case speechrecognition.ENUM.PLUGIN.HEADSET_CONNECTED:
            case speechrecognition.ENUM.PLUGIN.HEADSET_DISCONNECTED:
            case speechrecognition.ENUM.PLUGIN.HEADSET_CONNECTING:
            case speechrecognition.ENUM.PLUGIN.AUDIOSCO_CONNECTED:
            case speechrecognition.ENUM.PLUGIN.AUDIOSCO_DISCONNECTED:
            case speechrecognition.ENUM.PLUGIN.HEADSET_EXIST:
                cordova.fireWindowEvent("headsetstatus", {data: data});
                break;
                
            case speechrecognition.ENUM.PLUGIN.TRAIN_DATA_ZIPPED:
                cordova.fireWindowEvent("traindataready", {items:data.items});
                break;
                
            case speechrecognition.ENUM.PLUGIN.TF_RESULT:
                cordova.fireWindowEvent("recognitionresult", {items:data.items});
                break;
                
            case speechrecognition.ENUM.PLUGIN.TF_RESUME_RECOGNITION:
                speechrecognition.resumeSpeechRecognition();
                break;
                
            case speechrecognition.ENUM.PLUGIN.BLUETOOTH_INIT:
                speechrecognition.bluetoothSrvCallback(data.data);  // inform BluetoothSrv if bluetooth headset profile has been enabled
                break;
        }
    }
    catch (ex) {
        speechrecognition._pluginError({message: "error in _pluginEvent" + ex.message + ": data is" + data.toString()});
    }
};

/**
 * Error callback for AudioInputCapture start
 * @private
 */
// TODO : receive an error code from plugin
speechrecognition._pluginError = function (error) 
{
    if(speechrecognition._capturing)       speechrecognition.stopCapture();
    cordova.fireWindowEvent("pluginError", {message: error.message, type:error.type});
};

//=========================================================================================
module.exports = speechrecognition;
