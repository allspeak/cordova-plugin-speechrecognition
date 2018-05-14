/**Calculates temporal or spectral derivatives as static methods.
 */
package com.allspeak.audioprocessing.mfcc;

public class MFCCDerivatives 
{
    private static final String TAG = "MFCCDerivatives";
    
    // =======================================================================================================
    // 1-st & 2-nd order derivatives of cepstra data
    // =======================================================================================================
    // USED to process a single file. 
    // it duplicates the first and last nDeltaWindow frames
    // INPUT  data represent (nframes X 3*nfilters), 
    // OUTPUT data represent (nframes X 3*nfilters)
    static public void addTemporalDerivatives(float[][] cepstra, int nDeltaWindow)
    {
        int nscores         = cepstra[0].length/3; // num scores
        int nframes         = cepstra.length;      // num time windows
        float[] tempData;
        
        int nDerivDenom = 0;
        for(int dw=1; dw<=nDeltaWindow; dw++)
            nDerivDenom = nDerivDenom + 2*(dw*dw);          
        
        //-------------------------------------------------------------------
        // adjust input vector creating past and future data
        //-------------------------------------------------------------------
        // copy first nDeltaWindow scores to pastData        
        float[][] pastData  = new float[nDeltaWindow][nscores];
        for (int q = 0; q < nDeltaWindow; q++)
            System.arraycopy(cepstra[0], 0, pastData[q], 0, nscores); 
        
        // copy last nDeltaWindow scores to futureData
        float[][] futureData    = new float[nDeltaWindow][nscores];
        for (int q=0; q<nDeltaWindow; q++)
            System.arraycopy(cepstra[nframes-1], 0, futureData[q], 0, nscores);
        
        //-------------------------------------------------------------------
        // first derivative
        //-------------------------------------------------------------------
        for(int tw = 0; tw < nframes; tw++)
        {
            if(tw >= nDeltaWindow && tw < (nframes-nDeltaWindow))
            {
                for(int sc = 0; sc < nscores; sc++)
                {
                    for(int r=1; r<=nDeltaWindow; r++)
                        cepstra[tw][nscores + sc] = r*(cepstra[tw+r][sc] - cepstra[tw-r][sc]);
                    cepstra[tw][nscores + sc] /= nDerivDenom;
                }
            }
            else if(tw < nDeltaWindow)
            {
                //first nDeltaWindow frames
                for(int sc = 0; sc < nscores; sc++)
                {
                    for(int r=1; r <= nDeltaWindow; r++)
                    {
                        tempData = pastData[nDeltaWindow-r];
                        cepstra[tw][nscores + sc] = r*(cepstra[tw+r][sc] - tempData[sc]);
                    }
                    cepstra[tw][nscores + sc] /= nDerivDenom;
                }                
            }
            else if(tw >= (nframes-nDeltaWindow))
            {
                //last nDeltaWindow frames
                for(int sc = 0; sc < nscores; sc++)
                {
                    for(int r=1; r <= nDeltaWindow; r++)
                    {
                        tempData = futureData[r-1];
                        cepstra[tw][nscores + sc] = r*(tempData[sc] - cepstra[tw-r][sc]);
                    }
                    cepstra[tw][nscores + sc] /= nDerivDenom;
                }                 
            }            
        }
        //-------------------------------------------------------------------
        // second derivative
        //-------------------------------------------------------------------
        // I copy the first derivative of borders frames in 0->(nscores-1) positions
        // copy first nDeltaWindow 1-st deriv scores to pastData
        // copy last nDeltaWindow 1-st deriv scores to futureData
        for (int q=0; q<nDeltaWindow; q++)
        {
            System.arraycopy(cepstra[0], nscores, pastData[q], 0, nscores); 
            System.arraycopy(cepstra[nframes-1], nscores, futureData[q], 0, nscores); 
        }        
       
        for(int tw = 0; tw < nframes; tw++)
        {
            if(tw >= nDeltaWindow && tw < (nframes-nDeltaWindow))
            {
                for(int sc = 0; sc < nscores; sc++)
                {
                    for(int r=1; r<=nDeltaWindow; r++)
                        cepstra[tw][2*nscores + sc] = r*(cepstra[tw+r][sc+nscores] - cepstra[tw-r][sc+nscores]);
                    cepstra[tw][2*nscores + sc] /= nDerivDenom;
                }
            }
            else if(tw < nDeltaWindow)
            {
                for(int sc = 0; sc < nscores; sc++)
                {
                    for(int r=1; r <= nDeltaWindow; r++)
                    {
                        tempData = pastData[nDeltaWindow-r];
                        cepstra[tw][2*nscores + sc] = r*(cepstra[tw+r][sc+nscores] - tempData[sc]);  // tempData = [nDeltaWindow][nscores]
                    }
                    cepstra[tw][2*nscores + sc] /= nDerivDenom;
                }                
            }
            else if(tw >= (nframes-nDeltaWindow))
            {
                //last nDeltaWindow frames
                for(int sc = 0; sc < nscores; sc++)
                {
                    for(int r=1; r <= nDeltaWindow; r++)
                    {
                        tempData = futureData[r-1];
                        cepstra[tw][2*nscores + sc] = r*(tempData[sc] - cepstra[tw-r][sc+nscores]);
                    }
                    cepstra[tw][2*nscores + sc] /= nDerivDenom;
                }                 
            }              
        }
    }
    //----------------------------------------------------------------------------------------------------------------
    // USED to process a live stream. 
    // it gets the nDeltaWindow previous cestra's frames and calculate derivatives only for the valid frames (nframes-nDeltaWindow)
    // INPUT  data represent (nframes X 3*nfilters), the last nDeltaWindow previous frames, to be used to calculate the temporal derivative
    // OUTPUT data represent (nframes-nDeltaWindow X 3*nfilters)    
    static public void addTemporalDerivatives(float[][] cepstra, float[][] pastData, int nDeltaWindow)
    {
        int nscores             = cepstra[0].length/3;   // num scores
        int nframes                 = cepstra.length;      // num time windows
        float[] tempData;
        
        int nindices1;
        int[] indices1 ;
        int nindices2;
        int[] indices2 ;
        int nindicesout;
        int[] indicesout;
    
        nindices1           = nscores + nDeltaWindow*2;
        indices1            = new int[nindices1];
        for(int r=0; r<nindices1; r++)
            indices1[r]     = nDeltaWindow + r;

        nindices2           = nscores;
        indices2            = new int[nindices2];
        for(int r=0; r<nindices2; r++)
            indices2[r]     = 2*nDeltaWindow + r;
        
        nindicesout        = nscores;
        indicesout         = new int[nindicesout];
        for(int r=0; r<nindicesout; r++)
            indicesout[r]  = 2*nDeltaWindow + r;     
        
        int nDerivDenom = 0;
        for(int dw=1; dw<=nDeltaWindow; dw++)
            nDerivDenom = nDerivDenom + 2*(dw*dw); 

        if(pastData == null)
        {
            pastData = new float[nDeltaWindow][nscores];
            // copy first nDeltaWindow nscores-vectors to pastData
            for (int q = 0; q < nDeltaWindow; q++)
                System.arraycopy(cepstra[0], 0, pastData[q], 0, nscores); 
        }
        //-------------------------------------------------------------------
        // first derivative
        //-------------------------------------------------------------------
        for(int tw = 0; tw < nframes; tw++)
        {
            if(tw >= nDeltaWindow && tw < (nframes-nDeltaWindow))
            {
                for(int sc = 0; sc < nscores; sc++)
                {
                    for(int r=1; r<=nDeltaWindow; r++)
                        cepstra[tw][nscores + sc] = r*(cepstra[tw+r][sc] - cepstra[tw-r][sc]);
                    cepstra[tw][nscores + sc] /= nDerivDenom;
                }
            }
            else if(tw < nDeltaWindow)
            {
                //first nDeltaWindow frames
                for(int sc = 0; sc < nscores; sc++)
                {
                    for(int r=1; r <= nDeltaWindow; r++)
                    {
                        tempData = pastData[nDeltaWindow-r];
                        cepstra[tw][nscores + sc] = r*(cepstra[tw+r][sc] - tempData[sc]);
                    }
                    cepstra[tw][nscores + sc] /= nDerivDenom;
                }                
            }
        }
        //-------------------------------------------------------------------
        // second derivative
        //-------------------------------------------------------------------
        // I copy the first derivative of borders frames in 0->(nscores-1) positions
        // copy first nDeltaWindow 1-st deriv scores to pastData
        // copy last nDeltaWindow 1-st deriv scores to futureData
        for (int q=0; q<nDeltaWindow; q++)
            System.arraycopy(cepstra[0], nscores, pastData[q], 0, nscores); 
       
        for(int tw = 0; tw < nframes; tw++)
        {
            if(tw >= nDeltaWindow && tw < (nframes-nDeltaWindow))
            {
                for(int sc = 0; sc < nscores; sc++)
                {
                    for(int r=1; r<=nDeltaWindow; r++)
                        cepstra[tw][2*nscores + sc] = r*(cepstra[tw+r][sc+nscores] - cepstra[tw-r][sc+nscores]);
                    cepstra[tw][2*nscores + sc] /= nDerivDenom;
                }
            }
            else if(tw < nDeltaWindow)
            {
                for(int sc = 0; sc < nscores; sc++)
                {
                    for(int r=1; r <= nDeltaWindow; r++)
                    {
                        tempData = pastData[nDeltaWindow-r];
                        cepstra[tw][2*nscores + sc] = r*(cepstra[tw+r][sc+nscores] - tempData[sc]);  // tempData = [nDeltaWindow][nscores]
                    }
                    cepstra[tw][2*nscores + sc] /= nDerivDenom;
                }                
            }
        }
//        denominator = 2 * sum([i**2 for i in range(1, N+1)])
//        delta_feat = numpy.empty_like(feat)
//        padded = numpy.pad(feat, ((N, N), (0, 0)), mode='edge')   # padded version of feat
//
//        for fr in range(NUMFRAMES):
//            delta_feat[fr] = [0 for _ in range(NUMSCORES)]
//            for d in xrange(1, N+1):
//                delta_feat[fr] = map(add, delta_feat[fr], d*(padded[fr+N+d] - padded[fr+N-d]))
//            delta_feat[fr] /= denominator        

    }

    // =======================================================================================================
    // INPUT  data represent (ntimewindows X nfilters), the last two previous frames, to be used to calculate the temporal derivative
    // OUTPUT data represent (ntimewindows X 3*nfilters)
    static public void addSpectralDerivatives(float[][] data, int nDeltaWindow)
    {
       
        int nscores             = data[0].length/3;   // num scores
        int nframes                 = data.length;      // num time windows
//        float[][] res           = new float[nframes][nscores*3];

        int nindices1;
        int[] indices1 ;
        int nindices2;
        int[] indices2 ;
        int nindicesout;
        int[] indicesout;
        int nDerivDenom; 

        nindices1           = nscores + nDeltaWindow*2;
        indices1            = new int[nindices1];
        for(int r=0; r<nindices1; r++)
            indices1[r]     = nDeltaWindow + r;

        nindices2           = nscores;
        indices2            = new int[nindices2];
        for(int r=0; r<nindices2; r++)
            indices2[r]     = 2*nDeltaWindow + r;
        
        nindicesout        = nscores;
        indicesout         = new int[nindicesout];
        for(int r=0; r<nindicesout; r++)
            indicesout[r]  = 2*nDeltaWindow + r;     
        
        nDerivDenom = 0;
        for(int dw=1; dw<=nDeltaWindow; dw++)
            nDerivDenom = nDerivDenom + 2*(dw*dw);   

        int borderColumnsWidth  = 2*nDeltaWindow;
        int finalColumns        = 2*borderColumnsWidth + nscores;
        
        // with the first and last column (score), I have to emulate the following Python code : 
        // column vector [nframes] => [nframes,1] => [nframes, 2*deltawindow]
        
        float[][] appendedVec   = new float[nframes][finalColumns];
        float[][] deltaVec      = new float[nframes][finalColumns];
        float[][] deltadeltaVec = new float[nframes][finalColumns];
        
        for(int c=0; c<finalColumns; c++)
        {
            if(c<borderColumnsWidth)
                for(int tw=0; tw<nframes; tw++)
                    appendedVec[tw][c] = data[tw][0];
            else if (c>=borderColumnsWidth && c<(borderColumnsWidth+nscores))
                for(int tw=0; tw<nframes; tw++)
                    appendedVec[tw][c] = data[tw][c-borderColumnsWidth];
            else
                for(int tw=0; tw<nframes; tw++)
                    appendedVec[tw][c] = data[tw][nscores-1];
        }
        //-------------------------------------------------------------------
        // first derivative
        int offset = nscores;
        float[][] deltaVecCur       = new float[nframes][nindices1];

        for(int dw=1; dw<=nDeltaWindow; dw++)
        {
            for(int r=0; r<nindices1; r++)
                for(int tw=0; tw<nframes; tw++)
                    deltaVecCur[tw][r] = appendedVec[tw][indices1[r]+dw] - appendedVec[tw][indices1[r]-dw];
            
            for(int r=0; r<nindices1; r++)
                for(int tw=0; tw<nframes; tw++)
                    deltaVec[tw][indices1[r]] = deltaVec[tw][indices1[r]] + deltaVecCur[tw][r]*dw;
        }
        // final extraction: [nframes][2*dw + nscores + 2*dw] => [nframes][nscores]
        for(int sc=0; sc<nscores; sc++)
            for(int tw=0; tw<nframes; tw++)
                data[tw][sc+offset] = deltaVec[tw][sc+2*nDeltaWindow]/nDerivDenom;
        
        //-------------------------------------------------------------------
        // second derivative
        offset = 2*nscores;        
        float[][] deltadeltaVecCur = new float[nframes][nindices2];        
        
        for(int dw=1; dw<=nDeltaWindow; dw++)
        {
            for(int r=0; r<nindices2; r++)
                for(int tw=0; tw<nframes; tw++)
                    deltadeltaVecCur[tw][r] = deltaVec[tw][indices2[r]+dw] - deltaVec[tw][indices2[r]-dw];
            
            for(int r=0; r<nindices2; r++)
                for(int tw=0; tw<nframes; tw++)
                    deltadeltaVec[tw][indices2[r]] = deltadeltaVec[tw][indices2[r]] + deltadeltaVecCur[tw][r]*dw;
        }
        
        // final extraction: [nframes][nscores + 4*dw] => [nframes][nscores]
        for(int sc=0; sc<nscores; sc++)
            for(int tw=0; tw<nframes; tw++)
                data[tw][sc+offset] = deltadeltaVec[tw][sc+2*nDeltaWindow]/nDerivDenom;
        
    }
    // =======================================================================================================
}

/*    
    // input data represent (ntimewindows X nfilters)
    private float[][][] getSpectralDerivatives(float[][] data)
    {
        //int[][] data = new int[][]{{11,12,13,14},{21,22,23,24},{31,32,33,34}}; // DEBUG
        
        int nscores             = data[0].length;   // num scores
        int nframes                 = data.length;      // num time windows
        float[][][] res         = new float[2][nframes][nscores];
        
        int borderColumnsWidth  = 2*nDeltaWindow;
        int finalColumns        = 2*borderColumnsWidth + nscores;
        
        // with the first and last column (score), I have to emulate the following Python code : 
        // column vector [nframes] => [nframes,1] => [nframes, 2*deltawindow]
        
        float[][] appendedVec   = new float[nframes][finalColumns];
        float[][] deltaVec      = new float[nframes][finalColumns];
        float[][] deltadeltaVec = new float[nframes][finalColumns];
        
        for(int c=0; c<finalColumns; c++)
        {
            if(c<borderColumnsWidth)
                for(int tw=0; tw<nframes; tw++)
                    appendedVec[tw][c] = data[tw][0];
            else if (c>=borderColumnsWidth && c<(borderColumnsWidth+nscores))
                for(int tw=0; tw<nframes; tw++)
                    appendedVec[tw][c] = data[tw][c-borderColumnsWidth];
            else
                for(int tw=0; tw<nframes; tw++)
                    appendedVec[tw][c] = data[tw][nscores-1];
        }
        //-------------------------------------------------------------------
        // first derivative
        float[][] deltaVecCur       = new float[nframes][nindices1];

        for(int dw=1; dw<=nDeltaWindow; dw++)
        {
            for(int r=0; r<nindices1; r++)
                for(int tw=0; tw<nframes; tw++)
                    deltaVecCur[tw][r] = appendedVec[tw][indices1[r]+dw] - appendedVec[tw][indices1[r]-dw];
            
            for(int r=0; r<nindices1; r++)
                for(int tw=0; tw<nframes; tw++)
                    deltaVec[tw][indices1[r]] = deltaVec[tw][indices1[r]] + deltaVecCur[tw][r]*dw;
        }
        // final extraction: [nframes][2*dw + nscores + 2*dw] => [nframes][nscores]
        for(int sc=0; sc<nscores; sc++)
            for(int tw=0; tw<nframes; tw++)
                res[0][tw][sc] = deltaVec[tw][sc+2*nDeltaWindow]/nDerivDenom;
        
        //-------------------------------------------------------------------
        // second derivative
        float[][] deltadeltaVecCur = new float[nframes][nindices2];        
        
        for(int dw=1; dw<=nDeltaWindow; dw++)
        {
            for(int r=0; r<nindices2; r++)
                for(int tw=0; tw<nframes; tw++)
                    deltadeltaVecCur[tw][r] = deltaVec[tw][indices2[r]+dw] - deltaVec[tw][indices2[r]-dw];
            
            for(int r=0; r<nindices2; r++)
                for(int tw=0; tw<nframes; tw++)
                    deltadeltaVec[tw][indices2[r]] = deltadeltaVec[tw][indices2[r]] + deltadeltaVecCur[tw][r]*dw;
        }
        // final extraction: [nframes][nscores + 4*dw] => [nframes][nscores]
        for(int sc=0; sc<nscores; sc++)
            for(int tw=0; tw<nframes; tw++)
                res[1][tw][sc] = deltadeltaVec[tw][sc+2*nDeltaWindow]/nDerivDenom;
        
        //-------------------------------------------------------------------
        return res;
    }
*/