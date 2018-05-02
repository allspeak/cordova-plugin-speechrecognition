package com.allspeak.utility;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import android.util.Log;
//import com.allspeak.BuildConfig;

import java.util.concurrent.ExecutorService;
import org.apache.cordova.CallbackContext;

import android.os.Environment;
import com.allspeak.utility.StringUtilities;
import com.allspeak.utility.Messaging;
import com.allspeak.ERRORS;
import com.allspeak.ENUMS;


public class ZipManager 
{
    private static final int BUFFER = 80000;

    public static void zipFolder(String infolder, final String outfilename, String[] validext, ExecutorService execServ, final CallbackContext wlcb)
    {
        String inpath           = Environment.getExternalStorageDirectory().toString() + "/" + infolder;    // Log.d("Files", "Path: " + inpath);
        final String outzip     = Environment.getExternalStorageDirectory().toString() + "/" + outfilename;    // Log.d("Files", "Path: " + inpath);
        
        File directory          = new File(inpath);
        File[] files            = directory.listFiles();   //Log.d("Files", "Size: "+ files.length);
        int norigfiles          = files.length;
        String[] validfiles     = null;
        if(validext != null)
        {
            // filter by extension
            String[] fileinfolder   = new String[norigfiles];
            int nvalidfiles         = 0;            
            for (int i = 0; i < norigfiles; i++)
            {
                String filename = files[i].getName();
               Log.d("Files", "FileName:" + files[i].getName());
                String ext = StringUtilities.getExtension(filename);
                for (int e = 0; e < validext.length; e++)
                {                
                    if(ext.equals(validext[e]))
                    {
                        fileinfolder[nvalidfiles] = inpath + "/" + filename;
                        nvalidfiles++;
                    }
                }
            }   
            validfiles = new String[nvalidfiles];
            for(int f=0; f<nvalidfiles; f++) validfiles[f] = fileinfolder[f];                
        }
        else
        {
           validfiles  = new String[norigfiles];
           for(int f=0; f<norigfiles; f++) validfiles[f] = inpath + "/" + files[f].getName(); 
        }
        
        final String[] finallist = validfiles;
        execServ.execute(new Runnable() 
        {
            public void run() 
            {        
               dozip(finallist, outzip, wlcb);
            }
        });
    }    
    
    
    public static boolean dozip(String[] _files, String zipFileName, CallbackContext wlcb) 
    {
        try 
        {
            BufferedInputStream origin  = null;
            FileOutputStream dest       = new FileOutputStream(zipFileName);
            ZipOutputStream out         = new ZipOutputStream(new BufferedOutputStream(dest));
            byte data[]                 = new byte[BUFFER];

            for (int i = 0; i < _files.length; i++) 
            {
               Log.v("Compress", "Adding: " + _files[i]);
                FileInputStream fi = new FileInputStream(_files[i]);
                origin = new BufferedInputStream(fi, BUFFER);

                ZipEntry entry = new ZipEntry(_files[i].substring(_files[i].lastIndexOf("/") + 1));
                out.putNextEntry(entry);
                int count;

                while ((count = origin.read(data, 0, BUFFER)) != -1) out.write(data, 0, count);
                origin.close();
            }
            out.close();
            Messaging.sendCode2Web(wlcb, ENUMS.TRAIN_DATA_ZIPPED, true);            
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            Messaging.sendErrorString2Web(wlcb, e.getMessage(), ERRORS.ZIP_ERROR, true);
            return false;
        }
    }

    public static void unzip(final String _zipFile, final String _targetLocation, ExecutorService execServ, final CallbackContext wlcb) 
    {
        execServ.execute(new Runnable() 
        {
            public void run() 
            {        
               dounzip(_zipFile, _targetLocation, wlcb);
            }
        });        
    }
    
    public static boolean dounzip(String _zipFile, String _targetLocation, CallbackContext wlcb) 
    {
        dirChecker(_targetLocation);    //create target location folder if not exist
        try 
        {
            FileInputStream fin = new FileInputStream(_zipFile);
            ZipInputStream zin = new ZipInputStream(fin);
            ZipEntry ze = null;
            while ((ze = zin.getNextEntry()) != null) 
            {
                //create dir if required while unzipping
                if (ze.isDirectory()) {
                        dirChecker(ze.getName());
                } 
                else 
                {
                    FileOutputStream fout = new FileOutputStream(_targetLocation + ze.getName());
                    for (int c = zin.read(); c != -1; c = zin.read()) { fout.write(c); }
                    zin.closeEntry();
                    fout.close();
                }
            }
            zin.close();
            Messaging.sendCode2Web(wlcb, ENUMS.TRAIN_DATA_ZIPPED, true);            
            return true;            
        }
        catch (Exception e) 
        {
            System.out.println(e);
            Messaging.sendErrorString2Web(wlcb, e.getMessage(), ERRORS.ZIP_ERROR, true);
            return false;
        }
    }

    private static void dirChecker(String dir) 
    {
        File f = new File(dir);
        if (!f.isDirectory()) f.mkdirs();
    }
}
