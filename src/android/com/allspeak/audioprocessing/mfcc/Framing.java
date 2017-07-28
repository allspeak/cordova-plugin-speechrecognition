package com.allspeak.audioprocessing.mfcc;


public class Framing 
{

    private static final String TAG = "Framing";
    
    // =======================================================================================================
    // DATA FRAMING 
    // =======================================================================================================
    // returns the number of frames you can divide a vector into
    // first frame : 0=>windowLength, i+1 frame: then I need N steps to reach the remaining diff (inlen-windowLength) with steps of windowDistance
    public static int getFrames(int inlen, int windowLength, int windowDistance)
    {
        return (1 + (int) Math.ceil((inlen-windowLength)/windowDistance));
    }
    
    // returns how many samples constitutes n frames
    public static int getFramesWidth(int nframes, int windowLength, int windowDistance)
    {
        if(nframes == 0)    return 0;
        return (windowLength + windowDistance*(nframes-1));
    }

    // determines the maximum number of samples you can provide to MFCC analysis to get a clean number of frames
    // assuming I have 1024 samples, I can process 11 frames, consuming 1000 samples => I return 1000
    public static int getOptimalVectorLength(int inlen, int wlength, int wdist)
    {
        int nframes = (1 + (int) Math.floor((inlen-wlength)/wdist));
        return getFramesWidth(nframes, wlength, wdist);
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
    // in real-time (queued) calculation it always receive an optimal vector (using all the samples)
    // when processing a file, the input vector is NOT optimal. 
    public static float[][] frameVector(float[] data, int fwidth, int fdistance)
    {
        int inlen               = data.length;
        int nframes             = getFrames(inlen, fwidth, fdistance);
        float[][] frames        = new float[nframes][fwidth];
        int remaining_samples   = isOptimalVectorLength(inlen, fwidth, fdistance);   
        
        int f;
        if(remaining_samples == 0)
        {
            //OPTIMAL VECTOR
            for (f = 0; f < nframes; f++)  System.arraycopy(data, f*fdistance, frames[f], 0, fwidth);
            return frames;
        }
        else
        {
            //NON-OPTIMAL VECTOR..first all the nframes-1 complete vectors, then the last incomplete one.
            for (f = 0; f < nframes-1; f++)  
                System.arraycopy(data, f*fdistance, frames[f], 0, fwidth);
            
               System.arraycopy(data, f*fdistance, frames[f], 0, remaining_samples);   // during the last frame i fill with (fwidth-remaining_samples) zeros 
            return frames;
        }
    }    
    // =======================================================================================================
}