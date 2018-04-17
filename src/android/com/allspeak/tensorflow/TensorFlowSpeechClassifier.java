/* Copyright 2016 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.allspeak.tensorflow;

import android.content.res.AssetManager;
import android.os.Trace;
import android.util.Log;
//import com.allspeak.BuildConfig;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Vector;
import org.tensorflow.Operation;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import com.allspeak.utility.FileUtilities;


/** A classifier specialized to label speech chunks using TensorFlow. */
public class TensorFlowSpeechClassifier implements Classifier 
{
    private static final String TAG = "TensorFlowSpeechClassifier";
    
    private final int nReturnedElements = 3;

    // Config values.
    private String inputName;
    private String outputName;
    private int inputSize;      // nodes' number of the input layer
    private int nFrames;        // varies speech by speech
    private int nOutputClasses; // trained items within the net 

    // Pre-allocated buffers.
    private Vector<String> labels = new Vector<String>();
    private float[][] inputValues;      // it cannot be initialized in the create() function as the number of frames varies 
    private float[] outputs;
    private String[] outputNames;

    private TensorFlowInferenceInterface inferenceInterface;

    private TensorFlowSpeechClassifier() {}

    /**
     * Initializes a native TensorFlow session for classifying Speech's frames.
     *
     * @param assetManager  The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     * @param inputSize     The input size. 24 filters x 3 derivatives orders x 11 context frames = 792 float.
     * @param inputName     The label of the image input node.
     * @param outputName    The label of the output node.
     * @throws IOException
     */
//    public static Classifier createold(AssetManager assetManager,
//                                    String modelFilename,
//                                    String labelFilename,
//                                    int inputSize,
//                                    String inputName,
//                                    String outputName) 
//    {
//        TensorFlowSpeechClassifier c    = new TensorFlowSpeechClassifier();
//        c.inputName                     = inputName;
//        c.outputName                    = outputName;
//        c.labels                        = readLabels(assetManager, labelFilename);
//        
//        // Ideally, inputSize could have been retrieved from the shape of the input operation.  
//        // Alas, the placeholder node for input in the graphdef typically used does not specify a shape, so it must be passed in as a parameter. 
//        c.inputSize                     = inputSize;
//        c.inferenceInterface            = new TensorFlowInferenceInterface();
//        
//        modelFilename = labelFilename.startsWith("file://") ? modelFilename.split("file://")[1] : modelFilename;                
//        if (c.inferenceInterface.initializeTensorFlow(assetManager, modelFilename) != 0) throw new RuntimeException("TF initialization failed");
//    
//        // The shape of the output is [N, NUM_CLASSES], where N is the batch size.
//        final Operation operation       = c.inferenceInterface.graph().operation(outputName);
//        if (operation == null)          throw new RuntimeException("Node '" + outputName + "' does not exist in model '" + modelFilename + "'");
//    
//        c.nOutputClasses                = (int)operation.output(0).shape().size(1);
//       Log.i(TAG, "Read " + c.labels.size() + " labels, output layer size is " + c.nOutputClasses);
//
//        // Pre-allocate buffers.
//        c.outputNames   = new String[] {outputName};
//
//        return c;
//    }
    
    public static Classifier create(AssetManager assetManager,
                                    String modelFilename,
                                    int inputSize,
                                    String inputName,
                                    String outputName,
                                    Vector<String> titles) 
    {
        TensorFlowSpeechClassifier c    = new TensorFlowSpeechClassifier();
        c.inputName                     = inputName;
        c.outputName                    = outputName;
        c.labels                        = titles;
        
        // Ideally, inputSize could have been retrieved from the shape of the input operation.  
        // Alas, the placeholder node for input in the graphdef typically used does not specify a shape, so it must be passed in as a parameter. 
        c.inputSize                     = inputSize;
//        c.inferenceInterface            = new TensorFlowInferenceInterface();
////        modelFilename = labelFilename.startsWith("file://") ? modelFilename.split("file://")[1] : modelFilename;                
//        if (c.inferenceInterface.initializeTensorFlow(assetManager, modelFilename) != 0) throw new RuntimeException("TF initialization failed");

        c.inferenceInterface            = new TensorFlowInferenceInterface(assetManager, modelFilename);



        // The shape of the output is [N, NUM_CLASSES], where N is the batch size.
        final Operation operation       = c.inferenceInterface.graph().operation(outputName);
        if (operation == null)          throw new RuntimeException("Node '" + outputName + "' does not exist in model '" + modelFilename + "'");
    
        c.nOutputClasses                = (int)operation.output(0).shape().size(1);
       Log.i(TAG, "Read " + c.labels.size() + " labels, output layer size is " + c.nOutputClasses);

        // Pre-allocate buffers.
        c.outputNames   = new String[] {outputName};

        return c;
    }

//    private static Vector<String> readLabels(AssetManager assetManager, String labelFilename)
//    {
//        Vector<String> labels = new Vector<String>();
//        // Read the label names into memory.
//        // TODO(andrewharp): make this handle non-assets.
//        boolean hasAssetPrefix = labelFilename.startsWith("file:///android_asset/");
//        String actualFilename = hasAssetPrefix ? labelFilename.split("file:///android_asset/")[1] : labelFilename;
//       Log.i(TAG, "Reading labels from: " + actualFilename);
//        BufferedReader br = null;
//        try 
//        {
//            br = new BufferedReader(new InputStreamReader(assetManager.open(actualFilename)));
//            String line;
//            while ((line = br.readLine()) != null) labels.add(line);
//            br.close();
//        }
//        catch (IOException e) 
//        {
//            if (hasAssetPrefix) throw new RuntimeException("Problem reading TF model label file!" , e);
//            try 
//            {
//                actualFilename  = labelFilename.startsWith("file://") ? labelFilename.split("file://")[1] : labelFilename;                
//                br              = new BufferedReader(new InputStreamReader(new FileInputStream(actualFilename)));
//                String line;
//                while ((line = br.readLine()) != null) labels.add(line);
//                br.close();
//            }
//            catch (IOException e2) { throw new RuntimeException("Problem reading TF model label file!" , e);  }
//        }        
//        return labels;
//    }
//    
    // return a list of (nOutputClasses+1) elements
    // considering only those frames where at least one command's prob is over threshold. 
    // At position 0 u find the most probable element.
    @Override
    public List<Recognition> recognizeSpeech(final float[][] framesCepstra) 
    {
        return recognizeSpeech(framesCepstra, 0); 
    }
    
    @Override
    public List<Recognition> recognizeSpeech(final float[][] framesCepstra, float threshold) 
    {
        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeSpeech"); 
        
        nFrames = framesCepstra.length;
        outputs = new float[nOutputClasses];
        float[] confidences;
        
//        String outfile      = "AllSpeak/audiofiles/temp/probabilities.dat";
//        try
//        {
            for(int f=0; f<nFrames; f++)
            {
                confidences = recognizeFrame(framesCepstra[f]);
//                FileUtilities.writeArrayToFile(confidences, outfile, "%.4f", true);

                // check threshold...if at least one element is over threshold, include the frame
                boolean validframe = false;
                for(int c = 0; c < nOutputClasses; c++)
                {
                    if(confidences[c] >= threshold)
                    {
                        validframe = true;
                        break;
                    }
                }
                if(validframe) for(int c = 0; c < nOutputClasses; c++) outputs[c] += confidences[c];
            }

//        }
//        catch(IOException e)
//        {
//            e.printStackTrace();
//        }         

       
        // Find the best classifications.
        PriorityQueue<Recognition> pq =
            new PriorityQueue<Recognition>(
                3,
                new Comparator<Recognition>() {
                    @Override
                    public int compare(Recognition lhs, Recognition rhs) {
                        // Intentionally reversed to put high confidence at the head of the queue.
                        return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                    }
                });
        
        final ArrayList<Recognition> recognitions = new ArrayList<Recognition>();
        float max = 0;
        int item = 0;
        for(int c = 0; c < nOutputClasses; c++)
        {
            outputs[c] /= nFrames;
            if(outputs[c] > max)
            {
                max     = outputs[c];
                item    = c; 
            }
            pq.add(new Recognition("" + c, labels.size() > c ? labels.get(c) : "unknown", outputs[c]));
        }
        for (int i = 0; i < nReturnedElements; ++i) recognitions.add(pq.poll());
        
        Trace.endSection(); // "recognizeSpeech"
        return recognitions;        
    }
    
    public float[] recognizeFrame(float[] frameCepstra) 
    {
        float[] confidences = new float[nOutputClasses];
        // if(BuildConfig.DEBUG) Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeFrame");

        // Copy the input data into TensorFlow.
        Trace.beginSection("fillNodeFloat");
//        inferenceInterface.fillNodeFloat(inputName, new int[] {1, inputSize}, frameCepstra);  //   << ===========  ????????????????????????
        inferenceInterface.feed(inputName, frameCepstra, new long[] {1, inputSize});        
        Trace.endSection();

        // Run the inference call.
        Trace.beginSection("runInference");
//        inferenceInterface.runInference(outputNames);
        inferenceInterface.run(outputNames);
        Trace.endSection();

        // Copy the output Tensor back into the output array.
        Trace.beginSection("readNodeFloat");
//        inferenceInterface.readNodeFloat(outputName, confidences);
        inferenceInterface.fetch(outputName, confidences);
        Trace.endSection();

        Trace.endSection(); // "recognizeFrame"

//        softmax(confidences);
        return confidences;
    }
 
    @Override
    public void enableStatLogging(boolean debug) {
//        inferenceInterface.enableStatLogging(debug);
        inferenceInterface.run(outputNames, debug);
    }

    @Override
    public String getStatString() {
        return inferenceInterface.getStatString();
    }

    @Override
    public void close() {
        inferenceInterface.close();
    }
}



//    private void softmax(float[] vals) 
//    {
//        float max = Float.NEGATIVE_INFINITY;
//        for (final float val : vals) max = Math.max(max, val);
//        float sum = 0.0f;
//        for (int i = 0; i < vals.length; ++i) 
//        {
//            vals[i] = (float) Math.exp(vals[i] - max);
//            sum += vals[i];
//        }
//        for (int i = 0; i < vals.length; ++i) vals[i] = vals[i] / sum;
//    }   
