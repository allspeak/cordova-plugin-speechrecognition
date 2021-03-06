package com.allspeak;

public class ENUMS
{
    // map objects in the javascript interface
    public static final int CAPTURE_STATUS_STARTED      = 11;
    public static final int CAPTURE_RESULT              = 12;
    public static final int CAPTURE_STATUS_STOPPED      = 13;
    
    public static final int SPEECH_STATUS_STARTED       = 20;
    public static final int SPEECH_STATUS_STOPPED       = 21;
    public static final int SPEECH_STATUS_SENTENCE      = 22;
    public static final int SPEECH_STATUS_MAX_LENGTH    = 23;
    public static final int SPEECH_STATUS_MIN_LENGTH    = 24;
    
    public static final int MFCC_STATUS_PROCESS_STARTED = 30; //
    public static final int MFCC_STATUS_PROGRESS_DATA   = 31; //
    public static final int MFCC_STATUS_PROGRESS_FILE   = 32; //
    public static final int MFCC_STATUS_PROGRESS_FOLDER = 33; //
    public static final int MFCC_CMD_GETDATA            = 34; //  when you provide a float[] to be processed alone(for post MFCC calculation)
    public static final int MFCC_CMD_GETQDATA           = 35; //  when you provide a float[] to be processed with the existing queue (for realtime MFCC calculation)
    public static final int MFCC_CMD_GETFILE            = 36; //
    public static final int MFCC_CMD_GETFOLDER          = 37; //
    public static final int MFCC_CMD_CLEAR              = 38; //
    public static final int MFCC_CMD_SENDDATA           = 39; //
    public static final int MFCC_CMD_FINALIZEDATA       = 40; //  during capturing, onStopCapture => close file calculation, normalize data (& contexting?)
    public static final int MFCC_RESULT                 = 41; //
    public static final int MFCC_CMD_GETFILTEREDFOLDER  = 42; //
    
    
    
    public static final int TF_STATUS_MODEL_LOADED      = 50; // recognizing process started
    public static final int TF_STATUS_PROCESS_STARTED   = 51; // recognizing process started
    public static final int TF_CMD_LOADMODEL            = 52; // load a TensorFlow PB and labels file
    public static final int TF_CMD_CLEAR                = 53; // clear coincides with init....it creates the cepstra array in the thread space
    public static final int TF_CMD_RECOGNIZE            = 54; // recognize the sentence
    public static final int TF_CMD_NEWCEPSTRA           = 55; // new cepstra arrived      
    public static final int TF_RESULT                   = 56; //      
    public static final int TF_CMD_RECOGNIZE_FILE       = 57; //      
    public static final int TF_RESUME_RECOGNITION       = 58; //      
    
    public static final int VAD_CMD_ADJUST_THRESHOLD    = 60; //      
    
    public static final int CAPTURE_DATADEST_NONE           = 200;
    public static final int CAPTURE_DATADEST_JS_RAW         = 201;
    public static final int CAPTURE_DATADEST_JS_DB          = 202;
    public static final int CAPTURE_DATADEST_JS_RAWDB       = 203;
    public static final int CAPTURE_DATADEST_FILE           = 204;
    public static final int CAPTURE_DATADEST_FILE_JS_RAW    = 205;
    public static final int CAPTURE_DATADEST_FILE_JS_DB     = 206;
    public static final int CAPTURE_DATADEST_FILE_JS_RAWDB  = 207;
    
    public static final int CAPTURE_MODE                    = 211;
    public static final int PLAYBACK_MODE                   = 212;

    public static final int MFCC_DATADEST_NOCALC            = 230;      // DO NOT CALCULATE MFCC during capture
    public static final int MFCC_DATADEST_NONE              = 231;      // data(float[][])=> PLUGIN
    public static final int MFCC_DATADEST_JSPROGRESS        = 232;      // data=> PLUGIN + progress(filename)=> WEB
    public static final int MFCC_DATADEST_JSDATA            = 233;      // data=> PLUGIN + progress(filename) + data(JSONArray)=> WEB
    public static final int MFCC_DATADEST_JSDATAWEB         = 234;      // data(JSONArray)=> WEB
    public static final int MFCC_DATADEST_FILE              = 235;      // progress(filename)=> PLUGIN + data(String)=> FILE
    public static final int MFCC_DATADEST_FILEWEB           = 236;      // progress(filename)=> PLUGIN + data(String)=> FILE + data(JSONArray)=> WEB
    public static final int MFCC_DATADEST_ALL               = 237;      // data=> PLUGIN + data(String)=> FILE + data(JSONArray)=> WEB
    
    public static final int MFCC_DATAORIGIN_JSONDATA        = 240;
    public static final int MFCC_DATAORIGIN_FILE            = 241;
    public static final int MFCC_DATAORIGIN_FOLDER          = 242;
    public static final int MFCC_DATAORIGIN_RAWDATA         = 243;   
    
    public static final int MFCC_DATATYPE_MFPARAMETERS      = 250;
    public static final int MFCC_DATATYPE_MFFILTERS         = 251;  
    
    public static final int MFCC_PROCSCHEME_F_S         = 252;      // FilterBanks, Spectral derivatives, Contexting
    public static final int MFCC_PROCSCHEME_F_S_PP      = 253;      // FilterBanks, Spectral derivatives, Pre-processing, Contexting
    public static final int MFCC_PROCSCHEME_F_T         = 254;      // FilterBanks, Temporal derivatives, Contexting
    public static final int MFCC_PROCSCHEME_F_T_PP      = 255;      // FilterBanks, Temporal derivatives, Pre-processing, Contexting    
    public static final int MFCC_PROCSCHEME_F_S_NOTHR       = 256;      // FilterBanks, Spectral derivatives,                   DO NOT threshold frames with null cepstra
    public static final int MFCC_PROCSCHEME_F_S_PP_NOTHR    = 257;      // FilterBanks, Spectral derivatives, Pre-processing,   DO NOT threshold frames with null cepstra
    public static final int MFCC_PROCSCHEME_F_T_NOTHR       = 258;      // FilterBanks, Temporal derivatives,           ,       DO NOT threshold frames with null cepstra
    public static final int MFCC_PROCSCHEME_F_T_PP_NOTHR    = 259;      // FilterBanks, Temporal derivatives, Pre-processing,   DO NOT threshold frames with null cepstra
  
    public static final int MFCC_PROCSCHEME_F             = 260;      // FilterBanks,                                         Contexting
    public static final int MFCC_PROCSCHEME_F_PP          = 261;      // FilterBanks,                         Pre-processing, Contexting
    public static final int MFCC_PROCSCHEME_F_NOTHR       = 262;      // FilterBanks,                                           DO NOT threshold frames with null cepstra
    public static final int MFCC_PROCSCHEME_F_PP_NOTHR    = 263;      // FilterBanks,                         Pre-processing,   DO NOT threshold frames with null cepstra
    
    public static final int TF_DATADEST_MODEL               = 270;      // sentence's cepstra are sent to TF model only
    public static final int TF_DATADEST_FILEONLY            = 271;      // sentence's cepstra are written to a file only
    public static final int TF_DATADEST_MODEL_FILE          = 272;      // sentence's cepstra are sent to TF model and written to a file
    
    public static final int TF_MODELTYPE_COMMON             = 273;      // COMMON NET made with a general population
    public static final int TF_MODELTYPE_USER               = 274;      // PURE USER (PU) NET made only with user sentences (recordings must be ncommands x minrepetitions)
    public static final int TF_MODELTYPE_USER_ADAPTED       = 275;      // PURE USER ADAPTED (fine-tuned) NET made with user sentences (recordings must be ncommands x minrepetitions)   
    public static final int TF_MODELTYPE_COMMON_ADAPTED     = 276;      // COMMON ADAPTED (fine-tuned) NET made with user sentences (recordings must be ncommands x minrepetitions)   
    public static final int TF_MODELTYPE_USER_READAPTED     = 277;      // RE-ADAPTION of PUA NET made with user sentences (recordings are free)   
    public static final int TF_MODELTYPE_COMMON_READAPTED   = 278;      // RE-ADAPTION of CA NET made with user sentences (recordings are free)   
    
    public static final int TF_MODELCLASS_FF                = 280;      // use Feed Forward net
    public static final int TF_MODELCLASS_LSTM              = 281;      // use LSTM net

    public static final int VAD_RESULT_DETECTION_ONLY               = 290;      // just detect. no MFCC, no save, only callback to WL    
    public static final int VAD_RESULT_SAVE_SENTENCE                = 291;      // save (natively) and/or send data to WL  
    public static final int VAD_RESULT_PROCESS_DATA                 = 292;      // process sentence data (MFCC & TF)
    public static final int VAD_RESULT_PROCESS_DATA_SAVE_SENTENCE   = 293;      // process sentence data (MFCC & TF)
        
    
    public static final int TRAIN_DATA_ZIPPED               = 299; // 
    
    public static final int BLUETOOTH_INIT                  = 300; // 
    public static final int HEADSET_CONNECTED               = 301;
    public static final int HEADSET_DISCONNECTED            = 302; // 
    public static final int HEADSET_CONNECTING              = 303; // 
    public static final int HEADSET_DISCONNECTING           = 304; // 
    public static final int AUDIOSCO_CONNECTED              = 305; // 
    public static final int AUDIOSCO_DISCONNECTED           = 306; // 
    public static final int HEADSET_EXIST                   = 307; // 
    public static final int BLUETOOTH_STATUS                = 308; // 
    
    
}   
