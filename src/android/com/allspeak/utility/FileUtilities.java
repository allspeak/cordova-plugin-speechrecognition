/**
 */
package com.allspeak.utility;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.LineNumberReader;

import android.os.Environment;


public class FileUtilities 
{
    private static final String TAG = "FileUtilities";
    
    // overwrite or append in case of existing file
    public static boolean writeStringToFile(String filename, String data, boolean append) throws Exception
    {
        try 
        {
            File f = new File(Environment.getExternalStorageDirectory(), filename);
            if (f.exists() && !append)  f.delete();
            if (!f.exists()) f.createNewFile();
            
            FileWriter writer = new FileWriter(f, append);
            writer.write(data);
            writer.close();            
            return true;
        }
        catch (Exception e) 
        {
            throw (e);
        }	
    }

    public static boolean writeArrayToFile(float[] scores, String filename, String precision, boolean append) throws IOException
    {
        try
        {
            int col                     = scores.length;
            BufferedWriter outputWriter = null;

            File f = new File(Environment.getExternalStorageDirectory(), filename);
            if (f.exists() && !append)  f.delete();
  

            outputWriter = new BufferedWriter(new FileWriter(Environment.getExternalStorageDirectory() + "/" + filename, append));
            for(int j = 0; j < col; j++)//for each column
                outputWriter.write(String.format(precision, scores[j]) + " ");
            
            outputWriter.newLine();
            outputWriter.flush();  
            outputWriter.close();  
            return true;
        }
        catch(Exception e)
        {
            throw (e);
        }
            
    }

    public static boolean write2DArrayToFile(float[][] scores, int rows, String filename, String precision, boolean append) throws Exception
    {
        try
        {
            int col = scores[0].length;
            BufferedWriter outputWriter = null;

            File f = new File(Environment.getExternalStorageDirectory(), filename);
            if (f.exists() && !append)  f.delete();
  

            outputWriter = new BufferedWriter(new FileWriter(Environment.getExternalStorageDirectory() + "/" + filename, append));
            for (int i = 0; i < rows; i++) 
            {
                for(int j = 0; j < col; j++)//for each column
                {            
//                    outputWriter.write(Float.toString(scores[i][j])+ " ");
                    outputWriter.write(String.format(precision, scores[i][j]) + " ");
                }
                outputWriter.newLine();
                outputWriter.flush();  
            }
            outputWriter.close();  
            return true;
        }
        catch(Exception e)
        {
            throw (e);
        }
            
    }

    // assumes every row have the same number of columns,
    // so it first reada the number of lines then calculate #columns while parsing the first row
    public static float[][] read2DArrayFromFile(String filename) throws IOException
    {
        String fullpath     = Environment.getExternalStorageDirectory() + "/" + filename;
        int nlines          = countLines(new File(fullpath));
        float[][] matrix    = null;
        int size            = -1;
        try
        {
            BufferedReader buffer = new BufferedReader(new FileReader(fullpath));
            String line;
            int row = 0;

            while ((line = buffer.readLine()) != null) 
            {
                String[] vals = line.trim().split("\\s+");

                // Lazy instantiation.
                if (matrix == null) 
                {
                    size = vals.length;
                    matrix = new float[nlines][size];
                }

                for (int col = 0; col < size; col++) matrix[row][col] = Float.parseFloat(vals[col]);
                row++;
            }
            return matrix;
        }
        catch(IOException e)
        {
            throw (e);
        }   
    }

    
    public static int countLines(File aFile) throws IOException 
    {
        LineNumberReader reader = null;
        try {
            reader = new LineNumberReader(new FileReader(aFile));
            while ((reader.readLine()) != null);
            return reader.getLineNumber();
        } catch (Exception ex) {
            return -1;
        } finally { 
            if(reader != null) 
                reader.close();
        }
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