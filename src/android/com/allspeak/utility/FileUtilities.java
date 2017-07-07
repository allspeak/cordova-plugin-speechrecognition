/**
 */
package com.allspeak.utility;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import android.os.Environment;


public class FileUtilities 
{
    private static final String TAG = "FileUtilities";
    

    public static boolean writeStringToFile(String filename, String data) throws Exception
    {
        try 
        {
            File f = new File(Environment.getExternalStorageDirectory(), filename);
            if (!f.exists()) {
                f.createNewFile();
            }
            FileWriter writer = new FileWriter(f, true);
            writer.write(data);
            writer.close();            
            return true;
        }
        catch (Exception e) 
        {
            throw (e);
        }	
    }

    public static boolean write2DArrayToFile(float[][] scores, int rows, String filename, String precision) throws Exception
    {
        int col = scores[0].length;
        BufferedWriter outputWriter = null;
        outputWriter = new BufferedWriter(new FileWriter(Environment.getExternalStorageDirectory() + "/" + filename));
        for (int i = 0; i < rows; i++) 
        {
            for(int j = 0; j < col; j++)//for each column
            {            
                outputWriter.write(Float.toString(scores[i][j])+ " ");
            }
            outputWriter.newLine();
            outputWriter.flush();  
        }
        outputWriter.close();  
        return true;
    }

    public static boolean deleteExternalStorageFile(String output_file)
    {
        File f = new File(Environment.getExternalStorageDirectory(), output_file);
        if (f.exists()) return f.delete();
        return true;
    }    

    public static boolean existFile(String filefullpath)
    {
        File f = new File(filefullpath);
        return f.exists();
    }    

    public static boolean existFile(String parentfolder, String name)
    {
        File f = new File(Environment.getExternalStorageDirectory(), name);
        return f.exists();
    }    

}

//
//    public static boolean write2DArrayToFile2(float[][] scores, int rows, String filename, String precision) throws Exception
//    {
//        try 
//        {
//            int col = scores[0].length;
//            StringBuilder builder = new StringBuilder();
//            for(int i = 0; i < rows; i++)//for each row
//            {
//               for(int j = 0; j < col; j++)//for each column
//               {
//                  builder.append(scores[i][j]+"");//append to the output string
//                  if(j < scores.length - 1)//if this is not the last row element
//                     builder.append(" ");//then add comma (if you don't like commas you can use spaces)
//               }
//               builder.append(System.getProperty("line.separator"));//append new line at the end of the row
//            }
//            File f = new File(Environment.getExternalStorageDirectory(), filename);
//            if (f.exists()) f.delete();            
//            
//            BufferedWriter writer = new BufferedWriter(new FileWriter(Environment.getExternalStorageDirectory() + "/" + filename));
//            writer.write(builder.toString());//save the string representation of the board
//            writer.close();
//            return true;
//        }
//        catch (Exception e) 
//        {
//            throw (e);
//        }	
//    }
//    