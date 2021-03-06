/**Calculates the mel-based cepstra coefficients for one frame of speech.
 * Based on the original MFCC implementation described in:
 * [1] Davis & Mermelstein - IEEE Transactions on ASSP, August 1980.
 * Additional references are:
 * [2] Joseph Picone, Proceedings of the IEEE, Sep. 1993.
 * [3] Jankowski et al. IEEE Trans. on Speech and Audio Processing. July, 1995.
 * [4] Cardin et al, ICASSP'93 - pp. II-243
 *
 * Notice that there are several different implementations of the mel filter
 * bank. For example, the log is usually implementated after having the filter
 * outputs calculated, but could be implemented before filtering. Besides, there are
 * differences in the specification of the filter frequencies. [1]
 * suggested linear scale until 1000 Hz and logarithm scale afterwards.
 * This implementation uses the equation (10) in [2]:
 *      mel frequency = 2595 log(1 + (f/700)), where log is base 10
 * to find the filter bank center frequencies.
 *
 * @author Aldebaro Klautau
 * @version 2.0 - March 07, 2001
 * @see MFCCPatternGenerator
*/
package com.allspeak.audioprocessing.mfcc;

import com.allspeak.audioprocessing.FFT;
import com.allspeak.audioprocessing.Framing;

// if m_bCalculate0ThCoeff  is true,
// this class decrements m_nnumberOfParameters by 1 and
// adds the 0-th coefficient to complete a vector with
// the number of MFCC's specified by the user.
public class MFCCCalcJAudio 
{

    private static final String TAG = "MFCCCalcJAudio";
    
    // parameter USEPOWER in HTK, where default is false
    private static final boolean m_ousePowerInsteadOfMagnitude = false;

    // Number of MFCCs per speech frame.
    private final int m_nnumberOfParameters;
    
    // Sampling frequency.
    private final double m_dSamplingFrequency;
    
    // Number of filter in mel filter bank.
    private final int m_nnumberOfFilters;

    // Number of FFT points.
    private final int m_nFFTLength;

    /**Coefficient of filtering performing in cepstral domain
     * (called 'liftering' operation). It is not used if
     * m_bIsLifteringEnabled  is false.*/
    private final int m_nLifteringCoefficient;
    
    //True enables liftering.
    private final boolean m_bIsLifteringEnabled ;
    
    /**Minimum value of filter output, otherwise the log is not calculated
     * and m_dlogFilterOutputFloor is adopted.
     * ISIP implementation assumes m_dminimumFilterOutput = 1 and this value is used
     * here. */
    private final double m_dminimumFilterOutput = 1.0;

    //True if the zero'th MFCC should be calculated.
    private final boolean m_bCalculate0ThCoeff ;

    /**Floor value for filter output in log domain.
     * ISIP implementation assumes m_dlogFilterOutputFloor = 0 and this value is used
     * here.  */
    private final double m_dlogFilterOutputFloor = 0.0;
    
    private FFT m_fft;

    private double[] m_ffilterOutput;
    private final double[] m_nlifteringMultiplicationFactor;

    //things to be calculated just once:
    private final double m_dscalingFactor;
    private double[][] m_ddCTMatrix;
    private double[][] m_dweights;
    private int[][] m_nboundariesDFTBins;
    
    //-------------------------------------------------------------------------
    // NEW SECTION: manage framing, default cepstra calculated and spectral derivatives
    //-------------------------------------------------------------------------
    private int m_nWindowDistance;
    private int m_nWindowLength;
    private int m_nFrames;
    
    private int m_nDefaultDataType;     // indicates if default parameters are Parameters or Filters
    private int m_nDefaultScoreLength;  // indicates either m_nnumberOfParameters or m_nnumberOfFilters
    private int m_nDeltaWindow;         // indicates either m_nnumberOfParameters or m_nnumberOfFilters
    
//    private int nindices1;
//    private int[] indices1 ;
//    private int nindices2;
//    private int[] indices2 ;
//    private int nindicesout;
//    private int[] indicesout;
//    private int nDerivDenom;    
    
    
    /**The 0-th coefficient is included in nnumberOfParameters.
     * So, if one wants 12 MFCC's and additionally the 0-th
     * coefficient, one should call the constructor with
     * nnumberOfParameters = 13 and
     * bCalculate0ThCoeff  = true
     */
    public MFCCCalcJAudio(int nnumberOfParameters,
                double dSamplingFrequency,
                int nNumberofFilters,
                int nFFTLength,
                boolean bIsLifteringEnabled ,
                int nLifteringCoefficient,
                boolean bCalculate0ThCoeff,
                int nWindowDistance,
                int nWindowLength 
                ) 
    {
        this(nnumberOfParameters, dSamplingFrequency, nNumberofFilters, nFFTLength, bIsLifteringEnabled, nLifteringCoefficient, bCalculate0ThCoeff);
        m_nWindowDistance = nWindowDistance;
        m_nWindowLength = nWindowLength;
    }
    
    public MFCCCalcJAudio(int nnumberOfParameters,
                double dSamplingFrequency,
                int nNumberofFilters,
                int nFFTLength,
                boolean bIsLifteringEnabled ,
                int nLifteringCoefficient,
                boolean bCalculate0ThCoeff,
                int nWindowDistance,
                int nWindowLength,
                int nDefaultDataType,
                int nDefaultScoresLength,
                int nDeltaWindow) 
    {
        this(nnumberOfParameters, dSamplingFrequency, nNumberofFilters, nFFTLength, bIsLifteringEnabled, nLifteringCoefficient, bCalculate0ThCoeff, nWindowDistance, nWindowLength);
        m_nDefaultDataType      = nDefaultDataType;
        m_nDefaultScoreLength   = nDefaultScoresLength;
        m_nDeltaWindow          = nDeltaWindow;
        
//        mDerivativesQueue        = new float[m_nDeltaWindow][m_nDefaultScoreLength];
        
//        initSpectralDerivativeIndices(m_nDeltaWindow, m_nDefaultScoreLength);
    }
    
    
    public MFCCCalcJAudio(int nnumberOfParameters,
                double dSamplingFrequency,
                int nNumberofFilters,
                int nFFTLength,
                boolean bIsLifteringEnabled ,
                int nLifteringCoefficient,
                boolean bCalculate0ThCoeff ) {

        m_bCalculate0ThCoeff  = bCalculate0ThCoeff ;
        if (m_bCalculate0ThCoeff ) {
            //the user shouldn't notice that nnumberOfParameters was
            //decremented internally
            m_nnumberOfParameters = nnumberOfParameters - 1;
        } else {
            m_nnumberOfParameters = nnumberOfParameters;
        }

        m_dSamplingFrequency = dSamplingFrequency;
        m_nnumberOfFilters = nNumberofFilters;
        m_nFFTLength = nFFTLength;

        //the filter bank weights, FFT's cosines and sines
        //and DCT matrix are initialized once to save computations.

        //initializes the mel-based filter bank structure
        calculateMelBasedFilterBank(dSamplingFrequency,
                                    nNumberofFilters,
                                    nFFTLength);
        m_fft = new FFT(m_nFFTLength); //initialize FFT
        initializeDCTMatrix();
        m_nLifteringCoefficient = nLifteringCoefficient;
        m_bIsLifteringEnabled  = bIsLifteringEnabled ;

        //avoid allocating RAM space repeatedly, m_ffilterOutput is
        //going to be used in method getParameters()
        m_ffilterOutput = new double[m_nnumberOfFilters];

        //needed in method getParameters()
        //m_dscalingFactor shouldn't be necessary because it's only
        //a scaling factor, but I'll implement it
        //for the sake of getting the same numbers ISIP gets
        m_dscalingFactor = Math.sqrt(2.0 / m_nnumberOfFilters);

        //for liftering method
        if (m_bIsLifteringEnabled ) {
            //note that:
            int nnumberOfCoefficientsToLift = m_nnumberOfParameters;
            //even when m_bCalculate0ThCoeff  is true
            //because if 0-th cepstral coefficient is included,
            //it is not liftered
            m_nlifteringMultiplicationFactor = new double[m_nLifteringCoefficient];
            double dfactor = m_nLifteringCoefficient / 2.0;
            double dfactor2 = Math.PI / m_nLifteringCoefficient;
            for (int i=0; i<m_nLifteringCoefficient; i++) {
                m_nlifteringMultiplicationFactor[i] = 1.0 + dfactor * Math.sin(dfactor2*(i+1));
            }
            if (m_nnumberOfParameters > m_nLifteringCoefficient) {
                new Error("Liftering is enabled and the number " +
                "of parameters = " + m_nnumberOfParameters + ", while " +
                "the liftering coefficient is " + m_nLifteringCoefficient +
                ". In this case some cepstrum coefficients would be made " +
                "equal to zero due to liftering, what does not make much " +
                "sense in a speech recognition system. You may want to " +
                "increase the liftering coefficient or decrease the number " +
                "of MFCC parameters.");
            }
        } else {
            m_nlifteringMultiplicationFactor = null;
        }
    }

    /**Initializes the DCT matrix.*/
    private void initializeDCTMatrix() {
        m_ddCTMatrix = new double[m_nnumberOfParameters][m_nnumberOfFilters];
        for(int i=0;i<m_nnumberOfParameters;i++) {
            for(int j=0;j<m_nnumberOfFilters;j++) {
                m_ddCTMatrix[i][j] = Math.cos((i+1.0)*(j+1.0-0.5)*(Math.PI/m_nnumberOfFilters));
            }
        }
    }

    /**Calculates triangular filters.
     */
    private void calculateMelBasedFilterBank(double dSamplingFrequency,
                                             int nNumberofFilters,
                                             int nfftLength) {

        //frequencies for each triangular filter
        double[][] dfrequenciesInMelScale = new double[nNumberofFilters][3];
        //the +1 below is due to the sample of frequency pi (or fs/2)
        double[] dfftFrequenciesInHz = new double[nfftLength/2 + 1];
        //compute the frequency of each FFT sample (in Hz):
        double ddeltaFrequency = dSamplingFrequency / nfftLength;
        for (int i=0; i<dfftFrequenciesInHz.length; i++) {
            dfftFrequenciesInHz[i] = i * ddeltaFrequency;
        }
        //convert Hz to Mel
        double[] dfftFrequenciesInMel = this.convertHzToMel(dfftFrequenciesInHz,dSamplingFrequency);

        //compute the center frequencies. Notice that 2 filters are
        //"artificially" created in the endpoints of the frequency
        //scale, correspondent to 0 and fs/2 Hz.
        double[] dfilterCenterFrequencies = new double[nNumberofFilters + 2];
        //implicitly: dfilterCenterFrequencies[0] = 0.0;
        ddeltaFrequency = dfftFrequenciesInMel[dfftFrequenciesInMel.length-1] / (nNumberofFilters+1);
        for (int i = 1; i < dfilterCenterFrequencies.length; i++) {
            dfilterCenterFrequencies[i] = i * ddeltaFrequency;
        }

        //initialize member variables
        m_nboundariesDFTBins= new int[m_nnumberOfFilters][2];
        m_dweights = new double[m_nnumberOfFilters][];

        //notice the loop starts from the filter i=1 because i=0 is the one centered at DC
        for (int i=1; i<= nNumberofFilters; i++) {
            m_nboundariesDFTBins[i-1][0] = Integer.MAX_VALUE;
            //notice the loop below doesn't include the first and last FFT samples
            for (int j=1; j<dfftFrequenciesInMel.length-1; j++) {
                //see if frequency j is inside the bandwidth of filter i
                if ( (dfftFrequenciesInMel[j] >= dfilterCenterFrequencies[i-1]) &
                     (dfftFrequenciesInMel[j] <= dfilterCenterFrequencies[i+1]) ) {
                    //the i-1 below is due to the fact that we discard the first filter i=0
                    //look for the first DFT sample for this filter
                    if (j < m_nboundariesDFTBins[i-1][0]) {
                        m_nboundariesDFTBins[i-1][0] = j;
                    }
                    //look for the last DFT sample for this filter
                    if (j > m_nboundariesDFTBins[i-1][1]) {
                        m_nboundariesDFTBins[i-1][1] = j;
                    }
                }
            }
        }
        //check for consistency. The problem below would happen just
        //in case of a big number of MFCC parameters for a small DFT length.
        for (int i=0; i < nNumberofFilters; i++) {
            if(m_nboundariesDFTBins[i][0]==m_nboundariesDFTBins[i][1]) {
                                new Error("Error in MFCC filter bank. In filter "+i+" the first sample is equal to the last sample !" +
                " Try changing some parameters, for example, decreasing the number of filters.");
            }
        }

        //allocate space
        for(int i=0;i<nNumberofFilters;i++) {
            m_dweights[i] = new double[m_nboundariesDFTBins[i][1]-m_nboundariesDFTBins[i][0]+1];
        }

        //calculate the weights
        for(int i=1;i<=nNumberofFilters;i++) {
            for(int j=m_nboundariesDFTBins[i-1][0],k=0;j<=m_nboundariesDFTBins[i-1][1];j++,k++) {
                if (dfftFrequenciesInMel[j] < dfilterCenterFrequencies[i]) {
                    m_dweights[i-1][k] = (dfftFrequenciesInMel[j]-dfilterCenterFrequencies[i-1]) /
                                         (dfilterCenterFrequencies[i] - dfilterCenterFrequencies[i-1]);
                } else {
                    m_dweights[i-1][k] = 1.0 -( (dfftFrequenciesInMel[j]-dfilterCenterFrequencies[i]) /
                                         (dfilterCenterFrequencies[i+1] - dfilterCenterFrequencies[i]) );
                }
            }
        }
    }
   
    // =======================================================================================================
    // =======================================================================================================
    // =======================================================================================================
    // Returns the MF-FILTERS for ONE speech frame.    
    public float[] getFilterBankOutputs(float[] fspeechFrame) {
        //use mel filter bank
        float ffilterOutput[] = new float[m_nnumberOfFilters];
        
        
        for(int i=0; i < m_nnumberOfFilters; i++) {
            //Notice that the FFT samples at 0 (DC) and fs/2 are not considered on this calculation
            if (m_ousePowerInsteadOfMagnitude) {
                double[] fpowerSpectrum = m_fft.calculateFFTPower(fspeechFrame);
                for(int j=m_nboundariesDFTBins[i][0], k=0;j<=m_nboundariesDFTBins[i][1];j++,k++) {
                    ffilterOutput[i] += fpowerSpectrum[j] * m_dweights[i][k];
                }
            } else {
                double[] fmagnitudeSpectrum = m_fft.calculateFFTMagnitude(fspeechFrame);
                for(int j=m_nboundariesDFTBins[i][0], k=0;j<=m_nboundariesDFTBins[i][1];j++,k++) {
                    ffilterOutput[i] += fmagnitudeSpectrum[j] * m_dweights[i][k];
                }
            }

            //ISIP (Mississipi univ.) implementation
            if (ffilterOutput[i] > m_dminimumFilterOutput) {//floor power to avoid log(0)
                ffilterOutput[i] = (float)Math.log(ffilterOutput[i]); //using ln
            } else {
                ffilterOutput[i] = (float)m_dlogFilterOutputFloor;
            }
        }
        return ffilterOutput;
    }
    
    public float[] getParameters(float[] fspeechFrame) {

        //use mel filter bank
        for(int i=0; i < m_nnumberOfFilters; i++) {
            m_ffilterOutput[i] = 0.0;
            //Notice that the FFT samples at 0 (DC) and fs/2 are not considered on this calculation
            if (m_ousePowerInsteadOfMagnitude) {
                double[] fpowerSpectrum = m_fft.calculateFFTPower(fspeechFrame);
                for(int j=m_nboundariesDFTBins[i][0], k=0;j<=m_nboundariesDFTBins[i][1];j++,k++) {
                    m_ffilterOutput[i] += fpowerSpectrum[j] * m_dweights[i][k];
                }
            } else {
                double[] fmagnitudeSpectrum = m_fft.calculateFFTMagnitude(fspeechFrame);
                for(int j=m_nboundariesDFTBins[i][0], k=0;j<=m_nboundariesDFTBins[i][1];j++,k++) {
                    m_ffilterOutput[i] += fmagnitudeSpectrum[j] * m_dweights[i][k];
                }
            }

            //ISIP (Mississipi univ.) implementation
            if (m_ffilterOutput[i] > m_dminimumFilterOutput) {//floor power to avoid log(0)
                m_ffilterOutput[i] = Math.log(m_ffilterOutput[i]); //using ln
            } else {
                m_ffilterOutput[i] = m_dlogFilterOutputFloor;
            }
        }

        //need to allocate space for output array because it allows the user to call this method
        //many times, without having to do a deep copy of the output vector
        float[] fMFCCParameters = null;
        if (m_bCalculate0ThCoeff ) {
            fMFCCParameters = new float[m_nnumberOfParameters + 1];
            //calculates zero'th cepstral coefficient and pack it
            //after the MFCC parameters of each frame for the sake
            //of compatibility with HTK
            double dzeroThCepstralCoefficient = 0.0;
            for(int j=0;j<m_nnumberOfFilters;j++) {
                dzeroThCepstralCoefficient += m_ffilterOutput[j];
            }
            dzeroThCepstralCoefficient *= m_dscalingFactor;
            fMFCCParameters[fMFCCParameters.length-1] = (float)dzeroThCepstralCoefficient;
        } else {
            //allocate space
            fMFCCParameters = new float[m_nnumberOfParameters];
        }

        //cosine transform
        for(int i=0;i<m_nnumberOfParameters;i++) {
            for(int j=0;j<m_nnumberOfFilters;j++) {
                fMFCCParameters[i] += (float)(m_ffilterOutput[j]*m_ddCTMatrix[i][j]);
                //the original equations have the first index as 1
            }
            //could potentially incorporate liftering factor and factor below to save multiplications, but will not
            //do it for the sake of clarity
            fMFCCParameters[i] *= (float)m_dscalingFactor;
        }

        //debugging purposes
        //System.out.println("Windowed speech");        //IO.DisplayVector(fspeechFrame);
        //System.out.println("FFT spectrum");           //IO.DisplayVector(fspectrumMagnitude);
        //System.out.println("Filter output in dB");    //IO.DisplayVector(ffilterOutput);
        //System.out.println("DCT matrix");             //IO.DisplayMatrix(m_ddCTMatrix);
        //System.out.println("MFCC before liftering");  //IO.DisplayVector(dMFCCParameters);

        if (m_bIsLifteringEnabled ) {
            // Implements liftering to smooth the cepstral coefficients according to
            // [1] Rabiner, Juang, Fundamentals of Speech Recognition, pp. 169,
            // [2] The HTK Book, pp 68 and
            // [3] ISIP package - Mississipi Univ. Picone's group.
            //if 0-th coefficient is included, it is not liftered
            for (int i=0; i<m_nnumberOfParameters; i++) {
                fMFCCParameters[i] *= (float)m_nlifteringMultiplicationFactor[i];
            }
        }

        return fMFCCParameters;
    } //end method
    // =======================================================================================================
    // =======================================================================================================
    // =======================================================================================================    

    /**Converts frequencies in Hz to mel scale according to
     * mel frequency = 2595 log(1 + (f/700)), where log is base 10
     * and f is the frequency in Hz.
     */
    public static double[] convertHzToMel(double[] dhzFrequencies, double dSamplingFrequency) {
        double[] dmelFrequencies = new double[dhzFrequencies.length];
        for (int k=0; k<dhzFrequencies.length; k++) {
            dmelFrequencies[k] = 2595.0*( Math.log(1.0 + (dhzFrequencies[k] / 700.0) ) / Math.log(10) );
        }
        return dmelFrequencies;
    }    
    
    /**Returns the sampling frequency.
     */
    public double getSamplingFrequency() {
        return this.m_dSamplingFrequency;
    }

    /**Returns the number of points of the Fast Fourier
     * Transform (FFT) used in the calculation of this MFCC.
     */
    public int getFFTLength() {
        return m_nFFTLength;
    }

    /**Returns the number of MFCC coefficients,
     * including the 0-th if required by user in the object construction.
     */
    public int getNumberOfCoefficients() {
        return (m_bCalculate0ThCoeff  ? (m_nnumberOfParameters + 1) : m_nnumberOfParameters);
    }

    /**Return a string with all important parameters of this object.
     */
    public String toString() {
        return
            "MFCC.nnumberOfParameters = " + (m_bCalculate0ThCoeff  ? (m_nnumberOfParameters + 1) : m_nnumberOfParameters) +
            "\n" + "MFCC.nnumberOfFilters = " + m_nnumberOfFilters +
            "\n" + "MFCC.nFFTLength = " + m_nFFTLength +
            "\n" + "MFCC.dSamplingFrequency = " + m_dSamplingFrequency +
            "\n" + "MFCC.nLifteringCoefficient = " + m_nLifteringCoefficient +
            "\n" + "MFCC.bIsLifteringEnabled  = " + m_bIsLifteringEnabled  +
            "\n" + "MFCC.bCalculate0ThCoeff  = " + m_bCalculate0ThCoeff ;
    }

    // =======================================================================================================
    // cepstra calculation (parameters, filters) calls
    // =======================================================================================================    
    // PARAMETERS : returns [nframes][m_nnumberOfParameters]
    public float[][] getMFCC(float[][] frames)
    {
        try
        {
            m_nFrames           = frames.length;
            float[][] faMFCC    = new float[m_nFrames][m_nnumberOfParameters];        
            float[] temp;
            for(int f=0; f<m_nFrames; f++)
            {
                temp = getParameters(frames[f]);
                int len = temp.length;
                System.arraycopy(temp, 0, faMFCC[f], 0, len);
            }
            return faMFCC;
        }
        catch(Exception e) 
        {
            e.printStackTrace();
            return null;
        }  
    }
    
    //------------------------------------------------------------------------------------------------
    // FILTERS : returns [nframes][m_nnumberOfFilters]
    public float[][] getMFFilters(float[][] frames)
    {
        try
        {
            m_nFrames           = frames.length;
            float[][] faMFCC    = new float[m_nFrames][m_nnumberOfFilters];  
            float[] temp;
            for(int f=0; f<m_nFrames; f++)
            {
                temp = getFilterBankOutputs(frames[f]);
                int len = temp.length;
                System.arraycopy(temp, 0, faMFCC[f], 0, len);
            }
            return faMFCC;
        }
        catch(Exception e) 
        {
            e.printStackTrace();
            return null;
        }             
    }

//    // =======================================================================================================
//    // 1-st & 2-nd order derivatives of cepstra data
//    // =======================================================================================================
//    // USED to process a single file. 
//    // it duplicates the first and last m_nDeltaWindow frames
//    // INPUT  data represent (nframes X 3*nfilters), 
//    // OUTPUT data represent (nframes X 3*nfilters)
//    public void addTemporalDerivatives(float[][] cepstra)
//    {
//        int nscores         = cepstra[0].length/3;   // num scores
//        int ntw             = cepstra.length;      // num time windows
//        float[] tempData;
//        
//        // copy first m_nDeltaWindow scores to pastData        
//        float[][] pastData  = new float[m_nDeltaWindow][nscores];
//        for (int q = 0; q < m_nDeltaWindow; q++)
//            System.arraycopy(cepstra[0], 0, pastData[q], 0, nscores); 
//        
//        // copy last m_nDeltaWindow scores to futureData
//        float[][] futureData    = new float[m_nDeltaWindow][nscores];
//        for (int q=0; q<m_nDeltaWindow; q++)
//            System.arraycopy(cepstra[ntw-1], 0, futureData[q], 0, nscores);
//        
//        //-------------------------------------------------------------------
//        // first derivative
//        //-------------------------------------------------------------------
//        for(int tw = 0; tw < ntw; tw++)
//        {
//            if(tw >= m_nDeltaWindow && tw < (ntw-m_nDeltaWindow))
//            {
//                for(int sc = 0; sc < nscores; sc++)
//                {
//                    for(int r=1; r<=m_nDeltaWindow; r++)
//                        cepstra[tw][nscores + sc] = r*(cepstra[tw+r][sc] - cepstra[tw-r][sc]);
//                    cepstra[tw][nscores + sc] /= nDerivDenom;
//                }
//            }
//            else if(tw < m_nDeltaWindow)
//            {
//                //first m_nDeltaWindow frames
//                for(int sc = 0; sc < nscores; sc++)
//                {
//                    for(int r=1; r <= m_nDeltaWindow; r++)
//                    {
//                        tempData = pastData[m_nDeltaWindow-r];
//                        cepstra[tw][nscores + sc] = r*(cepstra[tw+r][sc] - tempData[sc]);
//                    }
//                    cepstra[tw][nscores + sc] /= nDerivDenom;
//                }                
//            }
//            else if(tw >= (ntw-m_nDeltaWindow))
//            {
//                //last m_nDeltaWindow frames
//                for(int sc = 0; sc < nscores; sc++)
//                {
//                    for(int r=1; r <= m_nDeltaWindow; r++)
//                    {
//                        tempData = futureData[r-1];
//                        cepstra[tw][nscores + sc] = r*(tempData[sc] - cepstra[tw-r][sc]);
//                    }
//                    cepstra[tw][nscores + sc] /= nDerivDenom;
//                }                 
//            }            
//        }
//        //-------------------------------------------------------------------
//        // second derivative
//        //-------------------------------------------------------------------
//        // I copy the first derivative of borders frames in 0->(nscores-1) positions
//        // copy first m_nDeltaWindow 1-st deriv scores to pastData
//        // copy last m_nDeltaWindow 1-st deriv scores to futureData
//        for (int q=0; q<m_nDeltaWindow; q++)
//        {
//            System.arraycopy(cepstra[0], nscores, pastData[q], 0, nscores); 
//            System.arraycopy(cepstra[ntw-1], nscores, futureData[q], 0, nscores); 
//        }        
//       
//        for(int tw = 0; tw < ntw; tw++)
//        {
//            if(tw >= m_nDeltaWindow && tw < (ntw-m_nDeltaWindow))
//            {
//                for(int sc = 0; sc < nscores; sc++)
//                {
//                    for(int r=1; r<=m_nDeltaWindow; r++)
//                        cepstra[tw][2*nscores + sc] = r*(cepstra[tw+r][sc+nscores] - cepstra[tw-r][sc+nscores]);
//                    cepstra[tw][2*nscores + sc] /= nDerivDenom;
//                }
//            }
//            else if(tw < m_nDeltaWindow)
//            {
//                for(int sc = 0; sc < nscores; sc++)
//                {
//                    for(int r=1; r <= m_nDeltaWindow; r++)
//                    {
//                        tempData = pastData[m_nDeltaWindow-r];
//                        cepstra[tw][2*nscores + sc] = r*(cepstra[tw+r][sc+nscores] - tempData[sc]);  // tempData = [m_nDeltaWindow][nscores]
//                    }
//                    cepstra[tw][2*nscores + sc] /= nDerivDenom;
//                }                
//            }
//            else if(tw >= (ntw-m_nDeltaWindow))
//            {
//                //last m_nDeltaWindow frames
//                for(int sc = 0; sc < nscores; sc++)
//                {
//                    for(int r=1; r <= m_nDeltaWindow; r++)
//                    {
//                        tempData = futureData[r-1];
//                        cepstra[tw][2*nscores + sc] = r*(tempData[sc] - cepstra[tw-r][sc+nscores]);
//                    }
//                    cepstra[tw][2*nscores + sc] /= nDerivDenom;
//                }                 
//            }              
//        }
//    }
//    //----------------------------------------------------------------------------------------------------------------
//    // USED to process a live stream. 
//    // it gets the m_nDeltaWindow previous cestra's frames and calculate derivatives only for the valid frames (nframes-m_nDeltaWindow)
//    // INPUT  data represent (nframes X 3*nfilters), the last m_nDeltaWindow previous frames, to be used to calculate the temporal derivative
//    // OUTPUT data represent (nframes-m_nDeltaWindow X 3*nfilters)    
//    public void addTemporalDerivatives(float[][] cepstra, float[][] pastData)
//    {
//        int nscores             = cepstra[0].length/3;   // num scores
//        int ntw                 = cepstra.length;      // num time windows
//        float[] tempData;
//        
//        if(pastData == null)
//        {
//            pastData = new float[m_nDeltaWindow][nscores];
//            // copy first m_nDeltaWindow nscores-vectors to pastData
//            for (int q = 0; q < m_nDeltaWindow; q++)
//                System.arraycopy(cepstra[0], 0, pastData[q], 0, nscores); 
//        }
//        //-------------------------------------------------------------------
//        // first derivative
//        //-------------------------------------------------------------------
//        for(int tw = 0; tw < ntw; tw++)
//        {
//            if(tw >= m_nDeltaWindow && tw < (ntw-m_nDeltaWindow))
//            {
//                for(int sc = 0; sc < nscores; sc++)
//                {
//                    for(int r=1; r<=m_nDeltaWindow; r++)
//                        cepstra[tw][nscores + sc] = r*(cepstra[tw+r][sc] - cepstra[tw-r][sc]);
//                    cepstra[tw][nscores + sc] /= nDerivDenom;
//                }
//            }
//            else if(tw < m_nDeltaWindow)
//            {
//                //first m_nDeltaWindow frames
//                for(int sc = 0; sc < nscores; sc++)
//                {
//                    for(int r=1; r <= m_nDeltaWindow; r++)
//                    {
//                        tempData = pastData[m_nDeltaWindow-r];
//                        cepstra[tw][nscores + sc] = r*(cepstra[tw+r][sc] - tempData[sc]);
//                    }
//                    cepstra[tw][nscores + sc] /= nDerivDenom;
//                }                
//            }
//        }
//        //-------------------------------------------------------------------
//        // second derivative
//        //-------------------------------------------------------------------
//        // I copy the first derivative of borders frames in 0->(nscores-1) positions
//        // copy first m_nDeltaWindow 1-st deriv scores to pastData
//        // copy last m_nDeltaWindow 1-st deriv scores to futureData
//        for (int q=0; q<m_nDeltaWindow; q++)
//            System.arraycopy(cepstra[0], nscores, pastData[q], 0, nscores); 
//       
//        for(int tw = 0; tw < ntw; tw++)
//        {
//            if(tw >= m_nDeltaWindow && tw < (ntw-m_nDeltaWindow))
//            {
//                for(int sc = 0; sc < nscores; sc++)
//                {
//                    for(int r=1; r<=m_nDeltaWindow; r++)
//                        cepstra[tw][2*nscores + sc] = r*(cepstra[tw+r][sc+nscores] - cepstra[tw-r][sc+nscores]);
//                    cepstra[tw][2*nscores + sc] /= nDerivDenom;
//                }
//            }
//            else if(tw < m_nDeltaWindow)
//            {
//                for(int sc = 0; sc < nscores; sc++)
//                {
//                    for(int r=1; r <= m_nDeltaWindow; r++)
//                    {
//                        tempData = pastData[m_nDeltaWindow-r];
//                        cepstra[tw][2*nscores + sc] = r*(cepstra[tw+r][sc+nscores] - tempData[sc]);  // tempData = [m_nDeltaWindow][nscores]
//                    }
//                    cepstra[tw][2*nscores + sc] /= nDerivDenom;
//                }                
//            }
//        }
////        denominator = 2 * sum([i**2 for i in range(1, N+1)])
////        delta_feat = numpy.empty_like(feat)
////        padded = numpy.pad(feat, ((N, N), (0, 0)), mode='edge')   # padded version of feat
////
////        for fr in range(NUMFRAMES):
////            delta_feat[fr] = [0 for _ in range(NUMSCORES)]
////            for d in xrange(1, N+1):
////                delta_feat[fr] = map(add, delta_feat[fr], d*(padded[fr+N+d] - padded[fr+N-d]))
////            delta_feat[fr] /= denominator        
//
//    }
//
//    // =======================================================================================================
//    // INPUT  data represent (ntimewindows X nfilters), the last two previous frames, to be used to calculate the temporal derivative
//    // OUTPUT data represent (ntimewindows X 3*nfilters)
//    public void addSpectralDerivatives(float[][] data)
//    {
//       
//        int nscores             = data[0].length/3;   // num scores
//        int ntw                 = data.length;      // num time windows
////        float[][] res           = new float[ntw][nscores*3];
//        
//        int borderColumnsWidth  = 2*m_nDeltaWindow;
//        int finalColumns        = 2*borderColumnsWidth + nscores;
//        
//        // with the first and last column (score), I have to emulate the following Python code : 
//        // column vector [ntw] => [ntw,1] => [ntw, 2*deltawindow]
//        
//        float[][] appendedVec   = new float[ntw][finalColumns];
//        float[][] deltaVec      = new float[ntw][finalColumns];
//        float[][] deltadeltaVec = new float[ntw][finalColumns];
//        
//        for(int c=0; c<finalColumns; c++)
//        {
//            if(c<borderColumnsWidth)
//                for(int tw=0; tw<ntw; tw++)
//                    appendedVec[tw][c] = data[tw][0];
//            else if (c>=borderColumnsWidth && c<(borderColumnsWidth+nscores))
//                for(int tw=0; tw<ntw; tw++)
//                    appendedVec[tw][c] = data[tw][c-borderColumnsWidth];
//            else
//                for(int tw=0; tw<ntw; tw++)
//                    appendedVec[tw][c] = data[tw][nscores-1];
//        }
//        //-------------------------------------------------------------------
//        // first derivative
//        int offset = nscores;
//        float[][] deltaVecCur       = new float[ntw][nindices1];
//
//        for(int dw=1; dw<=m_nDeltaWindow; dw++)
//        {
//            for(int r=0; r<nindices1; r++)
//                for(int tw=0; tw<ntw; tw++)
//                    deltaVecCur[tw][r] = appendedVec[tw][indices1[r]+dw] - appendedVec[tw][indices1[r]-dw];
//            
//            for(int r=0; r<nindices1; r++)
//                for(int tw=0; tw<ntw; tw++)
//                    deltaVec[tw][indices1[r]] = deltaVec[tw][indices1[r]] + deltaVecCur[tw][r]*dw;
//        }
//        // final extraction: [ntw][2*dw + nscores + 2*dw] => [ntw][nscores]
//        for(int sc=0; sc<nscores; sc++)
//            for(int tw=0; tw<ntw; tw++)
//                data[tw][sc+offset] = deltaVec[tw][sc+2*m_nDeltaWindow]/nDerivDenom;
//        
//        //-------------------------------------------------------------------
//        // second derivative
//        offset = 2*nscores;        
//        float[][] deltadeltaVecCur = new float[ntw][nindices2];        
//        
//        for(int dw=1; dw<=m_nDeltaWindow; dw++)
//        {
//            for(int r=0; r<nindices2; r++)
//                for(int tw=0; tw<ntw; tw++)
//                    deltadeltaVecCur[tw][r] = deltaVec[tw][indices2[r]+dw] - deltaVec[tw][indices2[r]-dw];
//            
//            for(int r=0; r<nindices2; r++)
//                for(int tw=0; tw<ntw; tw++)
//                    deltadeltaVec[tw][indices2[r]] = deltadeltaVec[tw][indices2[r]] + deltadeltaVecCur[tw][r]*dw;
//        }
//        
//        // final extraction: [ntw][nscores + 4*dw] => [ntw][nscores]
//        for(int sc=0; sc<nscores; sc++)
//            for(int tw=0; tw<ntw; tw++)
//                data[tw][sc+offset] = deltadeltaVec[tw][sc+2*m_nDeltaWindow]/nDerivDenom;
//        
//        //-------------------------------------------------------------------
//    }
//     
//    private void initSpectralDerivativeIndices(int ndw, int nscores)
//    {
//        nindices1           = nscores + ndw*2;
//        indices1            = new int[nindices1];
//        for(int r=0; r<nindices1; r++)
//            indices1[r]     = ndw + r;
//
//        nindices2           = nscores;
//        indices2            = new int[nindices2];
//        for(int r=0; r<nindices2; r++)
//            indices2[r]     = 2*ndw + r;
//        
//        nindicesout        = nscores;
//        indicesout         = new int[nindicesout];
//        for(int r=0; r<nindicesout; r++)
//            indicesout[r]  = 2*ndw + r;     
//        
//        nDerivDenom = 0;
//        for(int dw=1; dw<=ndw; dw++)
//            nDerivDenom = nDerivDenom + 2*(dw*dw);        
//    }      
    // =======================================================================================================
}



/*
public static void main(String[] args) {
    int nNumberofFilters = 24;
    int nLifteringCoefficient = 22;
    boolean bIsLifteringEnabled  = true;
    boolean bCalculate0ThCoeff  = false;
    int nNumberOfMFCCParameters = 12; //without considering 0-th
    double dSamplingFrequency = 8000.0;
    int nFFTLength = 512;
    if (bCalculate0ThCoeff ) {
      //take in account the zero-th MFCC
      nNumberOfMFCCParameters = nNumberOfMFCCParameters + 1;
    }
    else {
      nNumberOfMFCCParameters = nNumberOfMFCCParameters;
    }
    MFCC mfcc = new MFCC(nNumberOfMFCCParameters,
                         dSamplingFrequency,
                         nNumberofFilters,
                         nFFTLength,
                         bIsLifteringEnabled ,
                         nLifteringCoefficient,
                         bCalculate0ThCoeff );
    System.out.println(mfcc.toString());
    //simulate a frame of speech
    double[] x = new double[160];
    x[2]=10; x[4]=14;
    double[] dparameters = mfcc.getParameters(x);
    System.out.println("MFCC parameters:");
    for (int i = 0; i < dparameters.length; i++) {
      System.out.print(" " + dparameters[i]);
    }
  }
*/




/*
    //--------------------------------------------------------------------------------------------------------
    // first divide a speech chunk in frames (m_nWindowLength long, every m_nWindowDistance) 
    // and returns the MF-FILTERS as [nframes, nscores].    
    public float[][] getMFCC(float[] audiodata)
    {
        // divide the input stream in multiple frames of length nWindowLength and starting every nWindowDistance samples 
        int inlen           = audiodata.length;
        m_nFrames           = Framing.getFrames(inlen, m_nWindowLength, m_nWindowDistance);
        float[][] faMFCC   = new float[m_nFrames][m_nnumberOfParameters];
        try
        {
            float [] buffer    = new float[m_nWindowLength];
            for (int f = 0; f < m_nFrames-1; f++)
            {
                for (int j = 0; j < m_nWindowLength; j++)
                    buffer[j]  = audiodata[j + f*m_nWindowDistance];

                faMFCC[f]           = getParameters(buffer);
            }
            // during the last frame i may fill with blanks
            for (int j = 0; j < m_nWindowLength; j++)
            {
                int id = j + (m_nFrames-1)*m_nWindowDistance;
                if(id < inlen)
                    buffer[j]       = audiodata[id];
                else
                    buffer[j]       = 0;
            }      
            faMFCC[m_nFrames-1]        = getParameters(buffer); 
            return faMFCC;
        }
        catch(Exception e) 
        {
            e.printStackTrace();
            return null;
        }
    }

    private float[][] getMFCC(float[] audiodata, int WindowDistance, int WindowLength)
    {
        m_nWindowDistance = WindowDistance;
        m_nWindowLength = WindowLength;
        return getMFCC(audiodata);
    }    

    public float[][] getMFFilters(float[] audiodata)
    {
        // divide the input stream in multiple frames of length nWindowLength and starting every nWindowDistance samples 
        int inlen           = audiodata.length;
        m_nFrames           = Framing.getFrames(inlen, m_nWindowLength, m_nWindowDistance);
        float[][] faMFCC    = new float[m_nFrames][m_nnumberOfFilters];
        try
        {
            float [] buffer    = new float[m_nWindowLength];
            for (int f = 0; f < m_nFrames-1; f++)
            {
                for (int j = 0; j < m_nWindowLength; j++)
                    buffer[j]  = audiodata[j + f*m_nWindowDistance];

                faMFCC[f]           = getFilterBankOutputs(buffer);
            }
            // during the last frame i may fill with blanks
            for (int j = 0; j < m_nWindowLength; j++)
            {
                int id = j + (m_nFrames-1)*m_nWindowDistance;
                if(id < inlen)
                    buffer[j]       = audiodata[id];
                else
                    buffer[j]       = 0;
            }      
            faMFCC[m_nFrames-1]        = getFilterBankOutputs(buffer); 
            return faMFCC;
        }
        catch(Exception e) 
        {
            e.printStackTrace();
            return null;            
        }
        
    }

    private float[][] getMFFilters(float[] audiodata, int WindowDistance, int WindowLength)
    {
        m_nWindowDistance = WindowDistance;
        m_nWindowLength = WindowLength;
        return getMFFilters(audiodata);
    }    
    
    // input data represent (ntimewindows X nfilters)
    private float[][][] getSpectralDerivatives(float[][] data)
    {
        //int[][] data = new int[][]{{11,12,13,14},{21,22,23,24},{31,32,33,34}}; // DEBUG
        
        int nscores             = data[0].length;   // num scores
        int ntw                 = data.length;      // num time windows
        float[][][] res         = new float[2][ntw][nscores];
        
        int borderColumnsWidth  = 2*m_nDeltaWindow;
        int finalColumns        = 2*borderColumnsWidth + nscores;
        
        // with the first and last column (score), I have to emulate the following Python code : 
        // column vector [ntw] => [ntw,1] => [ntw, 2*deltawindow]
        
        float[][] appendedVec   = new float[ntw][finalColumns];
        float[][] deltaVec      = new float[ntw][finalColumns];
        float[][] deltadeltaVec = new float[ntw][finalColumns];
        
        for(int c=0; c<finalColumns; c++)
        {
            if(c<borderColumnsWidth)
                for(int tw=0; tw<ntw; tw++)
                    appendedVec[tw][c] = data[tw][0];
            else if (c>=borderColumnsWidth && c<(borderColumnsWidth+nscores))
                for(int tw=0; tw<ntw; tw++)
                    appendedVec[tw][c] = data[tw][c-borderColumnsWidth];
            else
                for(int tw=0; tw<ntw; tw++)
                    appendedVec[tw][c] = data[tw][nscores-1];
        }
        //-------------------------------------------------------------------
        // first derivative
        float[][] deltaVecCur       = new float[ntw][nindices1];

        for(int dw=1; dw<=m_nDeltaWindow; dw++)
        {
            for(int r=0; r<nindices1; r++)
                for(int tw=0; tw<ntw; tw++)
                    deltaVecCur[tw][r] = appendedVec[tw][indices1[r]+dw] - appendedVec[tw][indices1[r]-dw];
            
            for(int r=0; r<nindices1; r++)
                for(int tw=0; tw<ntw; tw++)
                    deltaVec[tw][indices1[r]] = deltaVec[tw][indices1[r]] + deltaVecCur[tw][r]*dw;
        }
        // final extraction: [ntw][2*dw + nscores + 2*dw] => [ntw][nscores]
        for(int sc=0; sc<nscores; sc++)
            for(int tw=0; tw<ntw; tw++)
                res[0][tw][sc] = deltaVec[tw][sc+2*m_nDeltaWindow]/nDerivDenom;
        
        //-------------------------------------------------------------------
        // second derivative
        float[][] deltadeltaVecCur = new float[ntw][nindices2];        
        
        for(int dw=1; dw<=m_nDeltaWindow; dw++)
        {
            for(int r=0; r<nindices2; r++)
                for(int tw=0; tw<ntw; tw++)
                    deltadeltaVecCur[tw][r] = deltaVec[tw][indices2[r]+dw] - deltaVec[tw][indices2[r]-dw];
            
            for(int r=0; r<nindices2; r++)
                for(int tw=0; tw<ntw; tw++)
                    deltadeltaVec[tw][indices2[r]] = deltadeltaVec[tw][indices2[r]] + deltadeltaVecCur[tw][r]*dw;
        }
        // final extraction: [ntw][nscores + 4*dw] => [ntw][nscores]
        for(int sc=0; sc<nscores; sc++)
            for(int tw=0; tw<ntw; tw++)
                res[1][tw][sc] = deltadeltaVec[tw][sc+2*m_nDeltaWindow]/nDerivDenom;
        
        //-------------------------------------------------------------------
        return res;
    }

*/