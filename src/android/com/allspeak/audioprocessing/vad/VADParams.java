package com.allspeak.audioprocessing.vad;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

// only the classic parameters used by MFCCCalcJAudio
public class VADParams
{
    // parameters
    public int nSpeechDetectionThreshold    = DEFAULT.SPEECH_DETECTION_THRESHOLD;
    public int nSpeechDetectionMinimum      = DEFAULT.SPEECH_DETECTION_MIN_LENGTH;
    public int nSpeechDetectionMaximum      = DEFAULT.SPEECH_DETECTION_MAX_LENGTH;
    public int nSpeechDetectionAllowedDelay = DEFAULT.SPEECH_DETECTION_ALLOWED_DELAY;
    public int audioResultType              = DEFAULT.AUDIO_RESULT_TYPE;
    public boolean bCompressPauses          = DEFAULT.SPEECH_DETECTION_COMPRESS_PAUSES;
    public int nAnalysisChunkLength         = DEFAULT.SPEECH_DETECTION_ANALYSIS_CHUNK_LENGTH;
    public boolean bDetectOnly              = DEFAULT.DETECT_ONLY;

    public VADParams(JSONObject init)
    {
        try
        {
            JSONArray labels = init.names();
            int len_labels   = labels.length();        

            for(int i=0; i<len_labels; i++)
            {
                String field = labels.getString(i);
                switch(field)
                {
                    case "nSpeechDetectionThreshold":
                        nSpeechDetectionThreshold       = init.getInt(field);
                        break;
                    case "nSpeechDetectionMinimum":
                        nSpeechDetectionMinimum         = init.getInt(field);
                        break;
                    case "nSpeechDetectionMaximum":
                        nSpeechDetectionMaximum         = init.getInt(field);
                        break;
                    case "nSpeechDetectionAllowedDelay":
                        nSpeechDetectionAllowedDelay    = init.getInt(field);
                        break;
                    case "audioResultType":
                        audioResultType                 = init.getInt(field);
                        break;
                    case "bCompressPauses":
                        bCompressPauses                 = init.getBoolean(field);
                        break;
                    case "nAnalysisChunkLength":
                        nAnalysisChunkLength            = init.getInt(field);
                        break;
                    case "bDetectOnly":
                        bDetectOnly                     = init.getBoolean(field);
                        break;
                }
            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
    }
    private static class DEFAULT
    {
        public static final int SPEECH_DETECTION_THRESHOLD              = 15; // dB
        public static final int SPEECH_DETECTION_ALLOWED_DELAY          = 400; // mS
        public static final int SPEECH_DETECTION_MAX_LENGTH             = 10000; // mS
        public static final int SPEECH_DETECTION_MIN_LENGTH             = 500; // mS
        public static final boolean SPEECH_DETECTION_COMPRESS_PAUSES    = false;
        public static final int SPEECH_DETECTION_ANALYSIS_CHUNK_LENGTH  = 64; // mS
        public static final int AUDIO_RESULT_TYPE                       = 1;
        public static final boolean DETECT_ONLY                         = false;
    } 
}
