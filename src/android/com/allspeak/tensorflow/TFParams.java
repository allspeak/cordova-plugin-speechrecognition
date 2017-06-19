package com.allspeak.tensorflow;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class TFParams
{
    public int nInputParams         = DEFAULT.INPUT_PARAMS;
    public int nContextFrames       = DEFAULT.CONTEXT_FRAMES;   
    public int nItems2Recognize     = DEFAULT.ITEMS_TO_RECOGNIZE;   
    public String sModelFileName    = DEFAULT.MODEL_FILENAME;   
    public String sLabelFileName    = DEFAULT.MODEL_LABELNAME;   
    public String sInputNodeName    = DEFAULT.INPUT_NODENAME;   
    public String sOutputNodeName   = DEFAULT.OUTPUT_NODENAME;   
    
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

                    case "sModelFileName":
                        sModelFileName    = init.getString(field);
                        break;
                  
                    case "sLabelFileName":
                        sLabelFileName    = init.getString(field);
                        break;
                  
                    case "sInputNodeName":
                        sInputNodeName    = init.getString(field);
                        break;
                  
                    case "sOutputNodeName":
                        sOutputNodeName    = init.getString(field);
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
        public static int INPUT_PARAMS          = 792;        
        public static int CONTEXT_FRAMES        = 11;        
        public static int ITEMS_TO_RECOGNIZE    = 25;        
        public static String MODEL_FILENAME     = "";        
        public static String MODEL_LABELNAME    = "";        
        public static String INPUT_NODENAME     = "I";        
        public static String OUTPUT_NODENAME    = "O";        
    }    
}
