package com.allspeak.audioprocessing;

import java.util.ArrayList;
import java.util.List;

public class Framing 
{
    private static final String TAG = "Framing";
    
    // =======================================================================================================
    // SAMPLES FRAMING 
    // =======================================================================================================
    // returns the number of frames you can divide a vector into
    // first frame : 0=>windowLength, i+1 frame: then I need N steps to reach the remaining diff (inlen-windowLength) with steps of windowDistance
    public static int getFrames(int inlen, int windowLength, int windowDistance)
    {
        if(inlen == 0)  return 0;
        else            return (1 + (int) Math.ceil((inlen-windowLength)/windowDistance));
    }
    
    // returns how many samples constitutes n frames
    public static int getFramesWidth(int nframes, int windowLength, int windowDistance)
    {
        if(nframes == 0)    return 0;
        else                return (windowLength + windowDistance*(nframes-1));
    }

    // determines the maximum number of samples you can provide to MFCC analysis to get a clean number of frames
    // assuming I have 1024 samples, I can process 11 frames, consuming 1000 samples => I return 1000
    public static int getOptimalVectorLength(int inlen, int wlength, int wdist)
    {
        if(inlen == 0)  return 0;
        
        int nframes = (1 + (int) Math.floor((inlen-wlength)/wdist));
        return getFramesWidth(nframes, wlength, wdist);
    }    
    
    // check if the provided vector can be optimally divided in frames
    // optimal      => return 0 
    // not optimal  => return remaining samples
    public static int isOptimalVectorLength(int inlen, int wlength, int wdist)
    {
        if(inlen == 0)  return 0;
        
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
    
    
    /**
     * performs Hamming Window<br>
     * calls: none<br>
     * called by: featureExtraction
     * @param frame A frame
     * @return Processed frame with hamming window applied to it
     */
    public static void hammingWindow(float[][] frames, float[] hammWnd)
    {
        int frameLength = frames[0].length; 
        for (int f = 0; f < frames.length; f++){
            for (int s = 0; s < frameLength; s++){
                frames[f][s] *= hammWnd[s];
            }
        }
    }
    
    public static float[] initHamming(int frame_length)
    {
        float w[] = new float[frame_length];
        for (int n = 0; n < frame_length; n++)   w[n] = (float)(0.54 - 0.46 * Math.cos((2*Math.PI*n)/(frame_length - 1)));
        return w;
    }
        
    // =======================================================================================================
    // SAMPLES PROCESSING
    // =======================================================================================================

    public static float[][] samplesProcessing(float[] inputSignal, int fwidth, int fdistance, float alpha, float[] hammWnd)
    {
        float[]             data    = inputSignal;
        if(alpha > 0)       data    = preEmphasis(inputSignal, alpha);
        float[][]           frames  = frameVector(data, fwidth, fdistance);
        if(hammWnd != null) hammingWindow(frames, hammWnd);
        return frames;
    }
    
    /**
     * perform pre-emphasis to equalize amplitude of high and low frequency<br>
     * calls: none<br>
     * called by: featureExtraction
     * @param inputSignal Speech Signal (16 bit integer data)
     * @return Speech signal after pre-emphasis (16 bit integer data)
     */
    public static float[] preEmphasis(float inputSignal[], float alpha)
     {
        float outputSignal[] = new float[inputSignal.length];
        
        // apply pre-emphasis to each sample
        for (int n = 1; n < inputSignal.length; n++)  outputSignal[n] = inputSignal[n] - alpha*inputSignal[n - 1];
        return outputSignal;
    }    

    // =======================================================================================================
    // CEPSTRA PROCESSING
    // =======================================================================================================    
    
    public static float[][] getSuprathresholdFrames(float[][] cepstra, float threshold)
    {
        List<float[]> validcepstra = new ArrayList<float[]>();

        int len = cepstra.length;
        int col = cepstra[0].length;
        for(int f = 0; f < len; f++)
        {
            boolean invalid = true;
            for(int s = 0; s < col; s++)
            {
                // whether at least one score is > threshold, I add that frame
                if(cepstra[f][s] > threshold)
                {
                    validcepstra.add(cepstra[f]);
                    break;
                }
            }
        }
        // create return array
        int lenvalid = validcepstra.size();
        float[][] validframes = new float[lenvalid][col];
        for(int f = 0; f < lenvalid; f++)  validframes[f] = validcepstra.get(f).clone();
        
        return validframes;
    }
    
    public static void normalizeFrames(float[][] cepstra)
    {
        normalizeFrames(cepstra, cepstra.length);
    }
    
    public static void normalizeFrames(float[][] cepstra, int frames2recognize)
    {
        int nscores         = cepstra[0].length;
        float[] means       = new float[nscores];
        float[] sd          = new float[nscores];
        
        // means
        for(int sc=0; sc<nscores; sc++)
        {
            for(int f=0; f<frames2recognize; f++)
                means[sc] += cepstra[f][sc]; 
            means[sc] /= frames2recognize;
        }

        // standard Deviations
        float variance=0;
        for(int sc=0; sc<nscores; sc++)
        {
            variance=0;
            for(int f=0; f<frames2recognize; f++)
                variance += (cepstra[f][sc] - means[sc])*(cepstra[f][sc] - means[sc]); 
            variance   /= frames2recognize;
            sd[sc]      = (float)Math.sqrt(variance);
        }        
        
        // normalize data
        for(int sc=0; sc<nscores; sc++)
        {
            if(sd[sc] == 0)
                for(int f=0; f<frames2recognize; f++)
                    cepstra[f][sc] = 0;                 
            else
                for(int f=0; f<frames2recognize; f++)
                    cepstra[f][sc] = (cepstra[f][sc] - means[sc])/sd[sc]; 
        }
    }  
    
    public static float[][] getContextedFrames(float[][] cepstra, int nctxframes, int noutparams)    // cepstra = [:][72] => [:][792] 
    {
        return getContextedFrames(cepstra, nctxframes, noutparams, cepstra.length);
    }

    // cepstra can be only partially filled, thus I process up to frames2recognize
    public static float[][] getContextedFrames(float[][] cepstra, int nctxframes, int noutparams, int frames2recognize)    // cepstra = [:][72] => [:][792] 
    {
        int ncepstra                    = cepstra[0].length;
        float[][] contextedCepstra      = new float[frames2recognize][noutparams];
        int startId, endId, cnt,corr_pf = 0;
        
        int preFrames  = (int)Math.floor((double)(nctxframes*1.0)/2);
        // append Context frames (from 72 => 792 = tfParams.nInputParam)
        for (int f=0; f<frames2recognize; f++)
        {
            startId = f - preFrames;
            endId   = f + preFrames + 1; // won't be included in the for...
            cnt     = 0;
            
            if(f < (frames2recognize-preFrames))
            {
                // from frames = [0 : frames2recognize-preFrames-1]
                for(int pf=startId; pf<endId; pf++)
                {
                    if(pf < 0)  corr_pf = 0;
                    else        corr_pf = pf;
                    for(int ff=0; ff<ncepstra; ff++)
                    {
                        contextedCepstra[f][cnt] = cepstra[corr_pf][ff];                
                        cnt++;
                    }
                }                
            }
            else
            {
                // from frames = [frames2recognize-preFrames : frames2recognize-1]
                for(int pf=startId; pf<endId; pf++)
                {
                    if(pf > (frames2recognize-1))   corr_pf = frames2recognize-1;
                    else                            corr_pf = pf;
                    
                    for(int ff=0; ff<ncepstra; ff++)
                    {
                        contextedCepstra[f][cnt] = cepstra[corr_pf][ff];                
                        cnt++;
                    }
                }                
            }
        }
        return contextedCepstra;
    }      
    // =======================================================================================================
}