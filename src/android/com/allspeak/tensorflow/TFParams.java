package com.allspeak.tensorflow;

import android.content.res.AssetManager;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import com.allspeak.ENUMS;
import java.util.Vector;

public class TFParams
{
    // defined in config.json
    public String sLabel                = "";
    public int nModelType               = DEFAULT.MODEL_TYPE;
    public int nInputParams             = DEFAULT.INPUT_PARAMS;
    public int nContextFrames           = DEFAULT.CONTEXT_FRAMES;   
    public int nItems2Recognize         = DEFAULT.ITEMS_TO_RECOGNIZE;   
    public String sModelFilePath        = "";   
    public String sInputNodeName        = DEFAULT.INPUT_NODENAME;   
    public String sOutputNodeName       = DEFAULT.OUTPUT_NODENAME; 
    public float fRecognitionThreshold  = DEFAULT.RECOGNITION_THRESHOLD; 
    public int nProcessingScheme        = DEFAULT.PROCESSING_SCHEME; 
    public String sCreationTime         = "";
    public String sModelFileName        = DEFAULT.MODEL_FILENAME;   
    public String sLocalFolder          = DEFAULT.LOCAL_FOLDER;   
    
    public item[] commands            = null;

    // def in config.json, not in the model.json
    public int nDataDest                = DEFAULT.OUTPUT_DATADEST; 
    
    // defined on runtime
    public boolean bLoaded              = DEFAULT.LOADED;
    
    public AssetManager mAssetManager   = null;
    
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
                    case "sLabel":
                        sLabel                  = init.getString(field);
                        break;
                    case "nModelType":
                        nModelType              = init.getInt(field);
                        break;
                    case "nInputParams":
                        nInputParams            = init.getInt(field);
                        break;
                    case "nContextFrames":
                        nContextFrames          = init.getInt(field);
                        break;
                    case "nItems2Recognize":
                        nItems2Recognize        = init.getInt(field);
                        break;
                    case "sModelFilePath":
                        sModelFilePath          = init.getString(field);
                        break;
                    case "sInputNodeName":
                        sInputNodeName          = init.getString(field);
                        break;
                    case "sOutputNodeName":
                        sOutputNodeName         = init.getString(field);
                        break;
                    case "nDataDest":
                        nDataDest               = init.getInt(field);
                        break;
                    case "fRecognitionThreshold":
                        fRecognitionThreshold   = (float)init.getDouble(field);
                        break;
                    case "nProcessingScheme":
                        nProcessingScheme       = init.getInt(field);
                        break;
                    case "sModelFileName":
                        sModelFileName          = init.getString(field);
                        break;
                    case "sLocalFolder":
                        sLocalFolder            = init.getString(field);
                        break;
                    case "commands":
                        JSONArray arrJson       = init.getJSONArray("commands");
                        commands              = new item[arrJson.length()];
                        for(int s = 0; s < arrJson.length(); s++)
                        {
                            JSONObject jitem    = arrJson.getJSONObject(s);
                            commands[s]       = new item(jitem.getString("title"), jitem.getInt("id"));                        
                        }
                       break;
                }
//                if(commands.length != "nItems2Recognize")
            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
    }
    
    public Vector<String> getTitles()
    {
        Vector<String> labels = new Vector<String>();
        for(int t=0; t<nItems2Recognize; t++)   labels.add(commands[t].title);
        return labels;
    }
    
    public String getTitle(int index)
    {
        return commands[index].title;
    }
    
    public int[] getIDs()
    {
        int[] ids = new int[nItems2Recognize];
        for(int t=0; t<nItems2Recognize; t++)   ids[t] = commands[t].id;
        return ids;
    }
    
    public int getID(int index)
    {
        return commands[index].id;
    }

    private class item
    {
        public String title;
        public int id;
        
        public item(String tit, int id)
        {
            title = tit;
            this.id = id;
        }
    }
    
    private static class DEFAULT
    {
        public static int MODEL_TYPE                = ENUMS.TF_MODELTYPE_COMMON;        
        public static int INPUT_PARAMS              = 792;        
        public static int CONTEXT_FRAMES            = 5;        
        public static int ITEMS_TO_RECOGNIZE        = 25;        
        public static String MODEL_FILENAME         = "controls_fsc.pb";        
        public static String INPUT_NODENAME         = "inputs/I";        
        public static String OUTPUT_NODENAME        = "SMO";        
        public static int OUTPUT_DATADEST           = ENUMS.TF_DATADEST_MODEL;        
        public static float RECOGNITION_THRESHOLD   = 0.1f;        
        public static boolean LOADED                = false;        
        public static int PROCESSING_SCHEME         = ENUMS.MFCC_PROCSCHEME_F_S_CTX;        
        public static String LOCAL_FOLDER           = "default";        
    }    
}
