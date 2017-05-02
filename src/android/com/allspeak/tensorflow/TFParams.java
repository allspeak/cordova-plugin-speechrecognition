package com.allspeak.tensorflow;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class TFParams
{
    public int nNetLayers = DEFAULT.NET_LAYERS;
    
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
                    case "nNetLayers":
                        nNetLayers            = init.getInt(field);
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
        public static int NET_LAYERS                = 20;        
    }    
}
