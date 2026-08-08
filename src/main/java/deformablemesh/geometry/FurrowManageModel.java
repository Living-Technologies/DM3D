/*-
 * #%L
 * Triangulated surface for deforming in 3D.
 * %%
 * Copyright (C) 2013 - 2023 University College London
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package deformablemesh.geometry;

import deformablemesh.MeshImageStack;
import deformablemesh.util.Vector3DOps;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * This used to be a way to initialize a contractile ring. It is becoming a tool for
 * reviewing segmentations.
 *
 * It stores a time series of planes.
 *
 * Created by msmith on 1/20/14.
 */
public class FurrowManageModel implements Iterable<Integer>{
    MeshImageStack stack;
    ImageProcessor currentSlice, currentBinary;

    int frames;
    int slices;
    int height;
    int width;

    int frame;

    double threshold;
    Map<Integer, Furrow3D> furrows;
    private Furrow3D furrow;

    public FurrowManageModel(){
        furrow = new Furrow3D(new double[3], Vector3DOps.zhat);
        furrows = new TreeMap<>();
        stack = MeshImageStack.getEmptyStack();

    }
    public void setImageStack(MeshImageStack stack){
        this.stack = stack;
        frames = stack.getNFrames();
        slices = stack.getNSlices();
        height = stack.getHeightPx();
        width = stack.getWidthPx();

    }


    public void setThresh(double t){
        threshold=t;
    }

    public void createFurrowSlice(int frame){
        Furrow3D f;
        if(furrows.containsKey(frame)){
            f = furrows.get(frame);
        } else {
            f = furrow;
        }
        if(f == null) return;
        FurrowTransformer transformer = new FurrowTransformer(f, stack);

        int xcounts = transformer.getXCounts();
        int ycounts = transformer.getYCounts();
        if(xcounts<1){xcounts =1;}
        if(ycounts<1){ycounts =1;}
        ImageProcessor proc = new FloatProcessor(xcounts, ycounts);
        double[] pt = new double[2];

        for (int i = 0; i < xcounts; i++) {
            for (int j = 0; j < ycounts; j++) {

                pt[0] = i;
                pt[1] = j;
                double v = stack.getInterpolatedValue(transformer.getVolumeCoordinates(pt));
                try {
                    proc.setf(i, j, (float) v);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
        currentSlice=proc;

    }
    public ImageProcessor getFurrowSlice(){

        return currentSlice;
    }

    public ImageProcessor createBinarySlice(){
        if(currentSlice==null) return null;
        ImageProcessor proc = currentSlice;

        ImageProcessor binary = new ByteProcessor(proc.getWidth(), proc.getHeight());
        threshAndCenter(proc, binary);
        currentBinary = binary;
        return binary;
    }


    /**
     * Apply the current threshold to the image.
     *
     * @param input input intensity values
     * @param output where the output will be drawn.
     */
    void threshAndCenter(ImageProcessor input, ImageProcessor output){
        int w = input.getWidth();
        int h = input.getHeight();
        int n = w*h;
        double count = 0;
        for(int i = 0; i<n; i++){
            float f = input.getf(i);
            if(f>threshold){
                output.set(i, 255);
            } else{
                output.set(i, 0);
            }
        }
    }



    public Iterator<Integer> iterator(){
        return furrows.keySet().iterator();
    }

    public Furrow3D getFurrow(int i){
        Furrow3D f = furrows.get(i);
        if(f == null){
            return furrow;
        }
        return f;
    }

    public Furrow3D getFurrow(){
        return getFurrow(frame);
    }

    public void setFrame(int frame){
        this.frame = frame;
        if(furrows.containsKey(frame)){
            furrow = furrows.get(frame);
            createFurrowSlice(frame);
        } else if(furrow != null) {
            createFurrowSlice(frame);
        }
    }

    public void putFurrow(int frame, Furrow3D furrow){
        furrows.put(frame, furrow);
        this.furrow = furrow;
    }


    public Map<Integer, Furrow3D> getFurrows() {
        return furrows;
    }
}
