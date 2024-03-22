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
package deformablemesh;

import deformablemesh.geometry.Box3D;
import deformablemesh.geometry.Furrow3D;
import deformablemesh.ringdetection.FurrowTransformer;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;

import static deformablemesh.geometry.DeformableMesh3D.ORIGIN;

/**
 *
 * This will setup normalized coordinates for an image.
 *
 * User: msmith
 * Date: 7/3/13
 */
public class MeshImageStack {
    public static class ImageRegion3D{
        final int lx, ly, lz, hx, hy, hz;
        ImageRegion3D (int lx, int ly, int lz, int hx, int hy, int hz){
            this.lx = lx;
            this.ly = ly;
            this.lz = lz;
            this.hx = hx;
            this.hy = hy;
            this.hz = hz;
        }
    };

    public double[][][] data;

    public double SCALE;
    public double[] scale_values;
    public double[] offsets;
    public double[] pixel_dimensions;
    int[] max_dex;
    ImagePlus original;
    int channel = 0;
    public int CURRENT = 0;
    int FRAMES,SLICES, CHANNELS;

    public double MIN_VALUE;
    public double MAX_VALUE;
    final private double PX;

    /**
     * Creates a null mesh image stack.
     */
    public MeshImageStack(){
        SCALE=1;
        CHANNELS = 1;
        scale_values=new double[]{1,1,1};
        offsets=new double[]{0,0,0};
        pixel_dimensions=new double[]{1,1,1};
        PX=1;
        data = new double[1][1][1];
        max_dex = new int[3];
        FRAMES = 999;
    }

    /**
     * Creates a mesh image stack and sets the backing data to the corresponding time frame and channel.
     * @param original image needs to be viewable as a hyperstack in image.
     * @param frame 0-th index time frame eg 2 time points valid values are 0, 1.
     * @param channel 0 index channel. eg 3 channels values 0, 1, 2 are valid.
     */
    public MeshImageStack(ImagePlus original, int frame, int channel){
        this.original=original;
        SLICES = original.getNSlices();
        FRAMES = original.getNFrames();
        CHANNELS = original.getNChannels();

        CURRENT=frame;
        this.channel = channel;
        int py = original.getHeight();
        int px = original.getWidth();

        data = new double[SLICES][py][px];

        max_dex = new int[]{px-1, py-1, SLICES-1};

        FileInfo info = original.getFileInfo();

        double[] dim3d = new double[]{
                info.pixelWidth*px,
                info.pixelHeight*py,
                info.pixelDepth*SLICES
        };


        pixel_dimensions = new double[]{
                info.pixelWidth,
                info.pixelHeight,
                info.pixelDepth
        };

        SCALE = dim3d[0];

        for(int i = 1; i<=2; i++){
            if(dim3d[i]>SCALE){
                SCALE = dim3d[i];
            }
        }

        scale_values = new double[]{
                1/info.pixelWidth,
                1/info.pixelHeight,
                1/info.pixelDepth
        };

        offsets = new double[3];

        for(int i = 0; i<3; i++){
            offsets[i] = 0.5*dim3d[i]/SCALE;
        }

        //px should be the smallest pixel in normalized coordinates.
        double[] nPx = scaleToNormalizedLength(new double[]{1,1,1});
        PX = nPx[0] < nPx[1] ?
                nPx[0] < nPx[2] ? nPx[0] : nPx[2] :
                nPx[1] < nPx[2] ? nPx[1] : nPx[2];

        copyValues();
    }

    public MeshImageStack(ImagePlus original){
        this(original, 0, 0);
    }

    /**
     * Creates an ImagePlus from the provided path.
     * @param path a local of an image to load.
     */
    public MeshImageStack(Path path) {
        this( new ImagePlus( path.toAbsolutePath().toString()));
    }

    public int getNFrames(){
        return FRAMES;
    }

    public int getNSlices(){
        return SLICES;
    }

    public int getWidthPx(){
        if(original==null){
            return 0;
        }
        return original.getWidth();
    }

    public int getHeightPx(){
        if(original==null){
            return 0;
        }
        return original.getHeight();
    }

    public String getUnits(){
        String unit;
        if(original == null){
            unit = "NA";
        } else{
            unit = original.getFileInfo().unit;
        }
        return unit;
    }

    /**
     * given a length in px, px, slices this scales the length to normalized
     * coordinate values.
     * @param l px, px, slices (x,y,z lengths in image slices)
     * @return scaled value.
     */
    public double[] scaleToNormalizedLength(double[] l){

        return new double[]{
                l[0]*pixel_dimensions[0]/SCALE,
                l[1]*pixel_dimensions[1]/SCALE,
                l[2]*pixel_dimensions[2]/SCALE
        };
    }

    /**
     * Finds the position in normalized coordinates using.
     *
     * @param r location in px,px,slice
     * @return normalized coordinates where center of image is at 0 and the longest axis in real units is -0.5 0.5.
     */
    public double[] getNormalizedCoordinate(double[] r){
        double[] ret = new double[3];


        for(int i = 0; i<3; i++){
            ret[i] = r[i]*pixel_dimensions[i]/SCALE - offsets[i];
        }

        return ret;
    }

    public void nextFrame(){
        if(CURRENT+1<FRAMES){
            CURRENT+=1;
            copyValues();
        }
    }

    public void previousFrame(){
        if(CURRENT>0){
            CURRENT--;
            copyValues();
        }
    }

    public void setFrame(int i){
        if(i!=CURRENT&&i<FRAMES&&i>=0){
            CURRENT=i;
            copyValues();
        }
    }

    public void setChannel(int c){
        if(c >= 0 && c < CHANNELS ){
            if(channel != c){
                channel = c;
                copyValues();
            }
        } else{
            System.out.println("Channel " + c + " specified is out of range [0, " + ( CHANNELS - 1 ) +" ]");
        }
    }

    /**
     * Copies the image data from the image stack to the double[][] backing the
     * image data that is used for obtaining values.
     *
     */
    public void copyValues(){
        if(original == null){
            return;
        }
        int slices = original.getNSlices();
        int py = original.getHeight();
        int px = original.getWidth();

        MIN_VALUE=Double.MAX_VALUE;
        MAX_VALUE=-MIN_VALUE;

        for(int i = 0;i<slices; i++){
            //int N = z*channels + i * channels * slices + c;
            int n = i * CHANNELS + CURRENT*CHANNELS*slices + channel + 1;
            ImageProcessor proc = original.getStack().getProcessor( n );
            for(int j = 0; j<py; j++){
                for(int k = 0; k<px; k++){
                    double v = proc.getPixelValue(k,j);
                    if(v<MIN_VALUE) MIN_VALUE=v;
                    else if(v>MAX_VALUE) MAX_VALUE=v;
                    data[i][j][k] = v;
                }
            }
        }

    }

    /**
     * For creating a backing double array of another ImagePlus that uses the same geometry as the original image
     * plus.
     *
     * @param other
     */
    public void copyValues(MeshImageStack other){
        if(other.data.length != data.length
                || other.data[0].length != data[0].length
                || other.data[0][0].length != data[0][0].length ){
            return;
        }
        for(int i = 0; i<data.length; i++){
            double[][] dest = data[i];
            double[][] src = other.data[i];
            for(int j = 0; j<dest.length; j++){
                double[] line = dest[j];
                double[] sline = src[j];
                for(int k = 0; k<line.length; k++){
                    line[k] = sline[k];
                }
            }
        }

    }

    public double getInterpolatedValue(double x, double y, double z){
        return getInterpolatedValue(new double[]{x,y,z});
    }

    final static double min_interp_value=1e-4;
    public double getInterpolatedValue(double[] xyz){
        double[] ndex = new double[3];
        int[] base = new int[3];
        double[] f = new double[3];
        for(int i = 0; i<3; i++){
            ndex[i] = SCALE*(xyz[i] + offsets[i])*scale_values[i];
            base[i] = (int)ndex[i];

            //outside of image is the same as the edge value.
            if(base[i]<0){
                base[i] = 0;
            } else if(base[i]>max_dex[i]){
                base[i] = max_dex[i];
            }
            /*
            outside of image is zero.
            if(base[i]<0||base[i]>max_dex[i]){
                //out of range
                return 0;
            }
            */


            f[i] = base[i]==max_dex[i]?0:ndex[i] - base[i];
        }

        double a = getValue(base[0],base[1], base[2]);

        
        if(f[0]>min_interp_value){
            double b = getValue(base[0]+1, base[1], base[2]);
            a = a + (b-a)*f[0];
        }

        if(f[1]>min_interp_value){
            double c = getValue(base[0],base[1]+1, base[2]);

            if(f[0]>min_interp_value){
                double d = getValue(base[0]+1, base[1]+1, base[2]);
                c = c + (d-c)*f[0];
            }
            a = a + (c-a)*f[1]; //first plane.
        }

        double v = a;
        if(f[2]>min_interp_value){

            a = getValue(base[0],base[1], base[2]+1);

            if(f[0]>min_interp_value){
                double b = getValue(base[0]+1, base[1], base[2]+1);
                a = a + (b-a)*f[0];
            }

            if(f[1]>min_interp_value){
                double c = getValue(base[0],base[1]+1, base[2]+1);

                if(f[0]>min_interp_value){
                    double d = getValue(base[0]+1, base[1]+1, base[2]+1);
                    c = c + (d-c)*f[0];
                }
                a = a + (c-a)*f[1];
            }

            v = v + (a-v)*f[2];
        }

        return v;
    }

    /**
     * Gets the value at the image coordintes x,y,z doesn't perform any sort of check.
     *
     * @param x pixel loc
     * @param y pixel loc
     * @param z slice (0 based!)
     * @return the backing double
     */
    public double getValue(int x, int y, int z){
        return data[z][y][x];

    }

    public BufferedImage createSlice(FurrowTransformer transformer) {

        int xcounts = transformer.getXCounts();
        int ycounts = transformer.getYCounts();
        ImageProcessor proc = new FloatProcessor(xcounts, ycounts);
        double[] pt = new double[2];

        for (int i = 0; i < xcounts; i++) {
            for (int j = 0; j < ycounts; j++) {

                pt[0] = i;
                pt[1] = j;
                double v = getInterpolatedValue(transformer.getVolumeCoordinates(pt));
                try {
                    proc.setf(i, j, (float) v);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }

        return proc.getBufferedImage();

    }

    public Image createSlice(double[] pos, double[] normal) {
        FurrowTransformer transformer = new FurrowTransformer(new Furrow3D(pos, normal), this);
        return createSlice(transformer);
    }

    /**
     * Normalized length of the image along the z-axis.
     *
     * @return size of image along z axis
     */
    public double getNormalizedImageDepth(){
        return offsets[2]*2;
    }

    /**
     * Normalized length of the image along the x-axis.
     *
     * @return width of image along x axis.
     */
    public double getNormalizedImageWidth(){
        return offsets[0]*2;
    }

    /**
     * Normalized length of the image along the y-axis.
     *
     * @return height of image along y axis.
     */
    public double getNormalizedImageHeight(){
        return offsets[1]*2;
    }

    public FurrowTransformer createFurrowTransform(double[] pos, double[] normal){

        return new FurrowTransformer(new Furrow3D(pos, normal), this);

    }

    /**
     * Calculates the volume in normalized coordinates based on the number of
     * voxels. Converts to real world units then divides by the scale.
     * @param nVoxels the number of voxels to be converted
     * @return nVoxels*dx*dy*dz/scale^3
     */
    public double getNormalizedVolume(double nVoxels){
        return nVoxels*pixel_dimensions[0]*pixel_dimensions[1]*pixel_dimensions[2]/(SCALE*SCALE*SCALE);
    }
    public double[] getCenterOfMass(){
        double sum = 0;
        double sumx = 0;
        double sumy = 0;
        double sumz = 0;
        double[] r = new double[3];
        for(int i = 0; i<data[0][0].length; i++){
            for(int j = 0; j<data[0].length; j++){
                for(int k = 0; k<data.length; k++){
                    r[0] = i;
                    r[1] = j;
                    r[2] = k;
                    double v = getValue(i,j,k);
                    double[] nr = getNormalizedCoordinate(r);
                    sum += v;
                    sumx += nr[0]*v;
                    sumy += nr[1]*v;
                    sumz += nr[2]*v;

                }
            }
        }
        return new double[]{sumx/sum, sumy/sum, sumz/sum};
    }


    /**
     * Returns x, y, z in (px, px, slice) indexes of the backing data array.
     * The z index is in slices, but 0 indexed compared to the 1 indexed method in the
     * ImagePlus.
     * @param r x, y, z in normalized coordinates.
     * @return closest double representation to the provided point in image coordinate space.
     */
    public double[] getImageCoordinates(double[] r) {
        double[] ret = new double[3];


        for(int i = 0; i<3; i++){
            ret[i] = (r[i] + offsets[i])*SCALE/pixel_dimensions[i];
        }

        return ret;
    }

    /**
     * Smallest pixel, in the x,y, or z direction from the input image.
     *
     * @return shortest pixel dimension
     */
    public double getMinPx(){
        return PX;
    }

    public Box3D getLimits() {
        return new Box3D(ORIGIN, offsets[0]*2, offsets[1]*2, offsets[2]*2);
    }

    public static MeshImageStack getEmptyStack() {
        return  new MeshImageStack();
    }

    public ImagePlus samplePlus(Box3D box){
        ImagePlus sample = original.createImagePlus();
        //ret[i] = (r[i] + offsets[i])*SCALE/pixel_dimensions[i];
        int w = (int)((box.high[0] - box.low[0])*SCALE/pixel_dimensions[0]);
        int h = (int)((box.high[1] - box.low[1])*SCALE/pixel_dimensions[1]);
        int d = (int)((box.high[2] - box.low[2])*SCALE/pixel_dimensions[2]);
        double[] xyz1 = getImageCoordinates(box.low);
        double[] xyz2 = getImageCoordinates(box.high);

        ImageStack stack = new ImageStack(w, h);
        ImageStack ori = original.getImageStack();

        for(int i = (int)xyz1[2]; i<=xyz2[2]; i++){
            ImageProcessor proc = ori.getProcessor(i);
            ImageProcessor nproc = proc.createProcessor(w, h);
            for(int j = 0; j<w; j++){
                for(int k = 0; k<h; k++){
                    nproc.set(j,k, proc.get((int)xyz1[0] + j, (int)xyz1[1] + k));
                }
            }
            stack.addSlice(nproc);
        }

        sample.setStack(stack, 1, stack.getSize(), 1);


        return sample;
    }

    /**
     * Gets the corresponding processor
     * @param frame time point
     * @param channel channel number
     * @param slice z location 0 based.
     * @return a single 2D image representing a slice of a single channel at a time point.
     */
    ImageProcessor getProcessor(int frame, int channel, int slice){
        int n = getProcessorIndex(frame, channel, slice);
        return original.getStack().getProcessor(n);
    }

    /**
     * gets the index of corresponding image processor for the provided frame
     * channel slice in the original image data.
     * @param frame 0 based
     * @param channel 0 based
     * @param slice 0 based.
     * @return 1 based index in the original image stack.
     */
    int getProcessorIndex(int frame, int channel, int slice){
        return slice * CHANNELS + frame*CHANNELS*SLICES + channel + 1;
    }

    /**
     * Creates a crop version of the original multi-channel movie with appropriately set
     * origin.
     *
     * @param x origin in pixels
     * @param y origin in pixels
     * @param z origin in slices 0 based
     * @param w width in pixels
     * @param h height in pixels
     * @param d depth in pixels.
     * @return
     */
    ImagePlus getCroppedRegion(int x, int y, int z, int w, int h, int d){
        ImagePlus next = original.createImagePlus();

        ImageStack os = new ImageStack(w, h);
        int ow = original.getWidth();
        for(int frame = 0; frame < getNFrames(); frame++){
            for(int slice = 0; slice < d; slice++){
                int sz = slice + z;
                for(int c = 0; c<getNChannels(); c++){
                    ImageProcessor proc = getProcessor(frame, c, sz);
                    ImageProcessor crop = proc.createProcessor(w, h);
                    for(int i = 0; i<w; i++){
                        for(int j = 0; j<h; j++){
                            crop.setf(i + w * j, proc.getf(( i + x ) + ow*(j+y)));
                        }
                    }
                    String label = original.getStack().getSliceLabel(getProcessorIndex(frame, c, slice));
                    os.addSlice(label, crop);
                }
            }
        }
        next.setStack(os, getNChannels(), d, getNFrames());
        Calibration cal = next.getCalibration();
        cal.xOrigin = cal.xOrigin - x;
        cal.yOrigin = cal.yOrigin - y;
        cal.zOrigin = cal.zOrigin - z;
        next.setCalibration(cal);
        return next;
    }

    /**
     * Changes the normalized coordinate Box3D into  original image pixel/slice
     * dimensions.
     *
     * The bounds of the region will be confined to within the bounds of the
     * image. Fractional values will be inclusive when possible.
     *
     * @param region geometry in normalized coordinates.
     *
     * @return {lx, ly, lz, hx, hy, hz}
     */
    public ImageRegion3D getImageCropValues(Box3D region){
        double[] ilow = getImageCoordinates(region.low);
        double[] ihigh = getImageCoordinates(region.high);
        int lowX = (int)ilow[0];
        lowX = lowX < 0 ? 0 : lowX;
        int lowY = (int)ilow[1];
        lowY = lowY < 0 ? 0 : lowY;
        int lowZ = (int)ilow[2];
        lowZ = lowZ < 0 ? 0 : lowZ;

        int highX = (int)ihigh[0];
        highX = ihigh[0] > highX ? highX + 1 : highX;
        highX = highX >= getWidthPx() ? getWidthPx() - 1 : highX;
        int highY = (int)ihigh[1];
        highY = ihigh[1] > highY ? highY + 1 : highY;
        highY = highY >= getHeightPx() ? getHeightPx() - 1 : highY;
        int highZ = (int)ihigh[2];
        highZ = ihigh[2] > highZ ? highZ + 1 : highZ;
        highZ = highZ >= getNSlices() ? getNSlices() - 1 : highZ;

        return new ImageRegion3D(lowX, lowY, lowZ, highX, highY, highZ);
    }
    /**
     * Gets the volume crop of the current frame/channel. The resulting volume
     * will be shaped to the original image pixel/slice dimensions.
     *
     * The bounds of the region will be confined to within the bounds of the
     * image. Fractional values will be inclusive when possible.
     *
     * @param region geometry in normalized coordinates.
     *
     * @return single channel, single time point volume of the sub region.
     */
    public MeshImageStack getCrop(Box3D region){

        ImageRegion3D r = getImageCropValues(region);

        int w = r.hx - r.lx + 1;
        int h = r.hy - r.ly + 1;
        int d = r.hz - r.lz + 1;
        ImageStack stack = new ImageStack(w, h);
        for(int z = r.lz; z <= r.hz; z++){
            FloatProcessor proc = new FloatProcessor(w, h);
            for(int x = 0; x <= w; x++){
                for(int y = 0; y <= h; y++){
                    proc.setf(x, y, (float)getValue(x + r.lx, y + r.ly, z + r.lz));
                }
            }
            stack.addSlice(proc);
        }
        ImagePlus plus = original.createImagePlus();
        plus.setStack(stack, 1, d, 1);
        Calibration c = plus.getCalibration();
        Calibration oc = original.getCalibration();
        c.zOrigin = oc.zOrigin - r.lx;
        c.yOrigin = oc.yOrigin - r.ly;
        c.xOrigin = oc.xOrigin - r.lz;
        plus.setCalibration(c);
        return new MeshImageStack(plus);
    }
    /**
     * Returns a single channel image plus of the current frame.
     *
     * @return ImagePlus created by the original image contain one channel,
     * all of the slices and a single time frame. Duplicate processors.
     */
    public ImagePlus getCurrentFrame(){
        int slices = original.getNSlices();
        int py = original.getHeight();
        int px = original.getWidth();
        ImageStack stack = new ImageStack(px, py);


        for(int i = 0;i<slices; i++){
            int n = i * CHANNELS + CURRENT*CHANNELS*slices + channel + 1;
            ImageProcessor proc = original.getStack().getProcessor( n ).duplicate();
            stack.addSlice(proc);
        }
        ImagePlus plus = original.createImagePlus();
        plus.setStack(stack, 1, slices, 1);
        return plus;
    }

    public double[] getIntensityValues() {
        double[] n = new double[data.length*data[0].length*data[0][0].length];
        final int row = data[0][0].length;
        final int frame = data[0].length*row;

        for(int slice = 0; slice<data.length; slice++){
            for(int line = 0; line<data[0].length; line++){
                System.arraycopy(data[slice][line], 0, n,slice*frame + line*row,  row);
            }
        }
        return n;
    }

    public ImagePlus getOriginalPlus() {
        return original;
    }

    public int getNChannels() {
        return CHANNELS;
    }
}

