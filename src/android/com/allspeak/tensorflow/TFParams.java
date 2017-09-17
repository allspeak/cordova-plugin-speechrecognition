package com.allspeak.tensorflow;

import android.content.res.AssetManager;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import com.allspeak.ENUMS;

public class TFParams
{
    // defined on config/json
    public int nInputParams             = DEFAULT.INPUT_PARAMS;
    public int nContextFrames           = DEFAULT.CONTEXT_FRAMES;   
    public int nItems2Recognize         = DEFAULT.ITEMS_TO_RECOGNIZE;   
    public String sModelFilePath        = DEFAULT.MODEL_FILENAME;   
    public String sLabelFilePath        = DEFAULT.MODEL_LABELNAME;   
    public String sInputNodeName        = DEFAULT.INPUT_NODENAME;   
    public String sOutputNodeName       = DEFAULT.OUTPUT_NODENAME; 
    public int nDataDest                = DEFAULT.OUTPUT_DATADEST; 
    public float fRecognitionThreshold  = DEFAULT.RECOGNITION_THRESHOLD; 
    
    // defined on runtime
    public AssetManager mAssetManager   = null;
    public String[] saAudioPath         = null;
    public boolean bLoaded              = DEFAULT.LOADED; 
    
    public TFParams(){}  
    
    public TFParams(JSONObject init)
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
                    case "nInputParams":
                        nInputParams        = init.getInt(field);
                        break;

                    case "nContextFrames":
                        nContextFrames      = init.getInt(field);
                        break;

                    case "nItems2Recognize":
                        nItems2Recognize    = init.getInt(field);
                        break;

                    case "sModelFilePath":
                        sModelFilePath      = init.getString(field);
                        break;
                  
                    case "sLabelFilePath":
                        sLabelFilePath      = init.getString(field);
                        break;
                  
                    case "sInputNodeName":
                        sInputNodeName      = init.getString(field);
                        break;
                  
                    case "sOutputNodeName":
                        sOutputNodeName     = init.getString(field);
                        break;
                  
                    case "nDataDest":
                        nDataDest           = init.getInt(field);
                        break;
                  
                    case "fRecognitionThreshold":
                        fRecognitionThreshold = (float)init.getDouble(field);
                        break;
                  
                    case "saAudioPath":
                        JSONArray arrJson   = init.getJSONArray("saAudioPath");
                        saAudioPath         = new String[arrJson.length()];
                        for(int s = 0; s < arrJson.length(); s++)
                            saAudioPath[s]  = arrJson.getString(s);                        
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
        public static int INPUT_PARAMS              = 792;        
        public static int CONTEXT_FRAMES            = 11;        
        public static int ITEMS_TO_RECOGNIZE        = 25;        
        public static String MODEL_FILENAME         = "trained";        
        public static String MODEL_LABELNAME        = "trained";        
        public static String INPUT_NODENAME         = "inputs/I";        
        public static String OUTPUT_NODENAME        = "O";        
        public static int OUTPUT_DATADEST           = ENUMS.TF_DATADEST_MODEL;        
        public static float RECOGNITION_THRESHOLD   = 0.1f;        
        public static boolean LOADED                = false;        
    }    
}
