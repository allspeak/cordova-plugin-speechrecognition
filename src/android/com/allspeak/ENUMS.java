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
    public static final int MFCC_RESULT                 = 40; //
    
    public static final int TF_STATUS_PROCESS_STARTED   = 50; //
    public static final int TF_CMD_RECOGNIZE            = 51; //
    public static final int TF_RESULT                   = 52; //      
    
    public static final int CAPTURE_DATADEST_NONE       = 200;
    public static final int CAPTURE_DATADEST_JS_RAW     = 201;
    public static final int CAPTURE_DATADEST_JS_DB      = 202;
    
    public static final int CAPTURE_MODE                = 211;
    public static final int PLAYBACK_MODE               = 212;
  
}   
