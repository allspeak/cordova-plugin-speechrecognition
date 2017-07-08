package com.allspeak.audioprocessing.mfcc;


public class Framing 
{

    private static final String TAG = "Framing";
    
    // =======================================================================================================
    // DATA FRAMING 
    // =======================================================================================================
    // returns the number of frames you can divide a vector into
    public static int getFrames(int inlen, int windowLength, int windowDistance)
    {
        return (1 + (int) Math.ceil((inlen-windowLength)/windowDistance));
    }

    // determines the maximum number of samples you can provide to MFCC analysis to get a clean number of frames
    // assuming I have 1024 samples, I can process 11 frames, consuming 1000 samples => I return it
    public static int getOptimalVectorLength(int inlen, int wlength, int wdist)
    {
        int nframes = (1 + (int) Math.floor((inlen-wlength)/wdist));
        return  (wlength + wdist*(nframes-1));
    }    
    
    // check if the provided vector can be optimally divided in frames
    // optimal      => return 0 
    // not optimal  => return remaining samples
    public static int isOptimalVectorLength(int inlen, int wlength, int wdist)
    {
        int optimal_vlen = getOptimalVectorLength(inlen, wlength, wdist);
        return (optimal_vlen == inlen ? 0 : inlen-optimal_vlen);
    }    
    
    // frames a vector into fwidth-length frames divided by fdistance samples.
    // it SHOULD always receive an optimal vector (using all the samples)
    // if the input vector is NOT optimal. it fills with zero the last, incomplete frame
    public static float[][] frameVector(float[] data, int fwidth, int fdistance)
    {
        int inlen               = data.length;
        int remaining_samples   = isOptimalVectorLength(inlen, fwidth, fdistance);   

        int f;
        if(remaining_samples == 0)
        {
            //OPTIMAL VECTOR
            int nframes             = getFrames(inlen, fwidth, fdistance);
            float[][] frames        = new float[nframes][fwidth];
            for (f = 0; f < nframes; f++)  System.arraycopy(data, f*fdistance, frames[f], 0, fwidth);
            return frames;
        }
        else
        {
            //NON-OPTIMAL VECTOR
            int nframes             = getFrames(inlen, fwidth, fdistance) + 1;
            float[][] frames        = new float[nframes][fwidth];            
            for (f = 0; f < nframes-1; f++)  System.arraycopy(data, f*fdistance, frames[f], 0, fwidth);

            // during the last frame i fill with (fwidth-remaining_samples) blanks 
            System.arraycopy(data, f*fdistance, frames[f], 0, remaining_samples);
            for (int j = 0; j < (fwidth-remaining_samples); j++)
                frames[nframes-1][j+remaining_samples]    = 0;
            
            return frames;
        }
    }    
    // =======================================================================================================
}