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
package deformablemesh.meshview;

import java.awt.Color;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.List;

import org.jogamp.java3d.ImageComponent;
import org.jogamp.java3d.ImageComponent3D;
import org.jogamp.java3d.Texture;
import org.jogamp.java3d.Texture3D;
import org.jogamp.vecmath.Color3f;
import org.jogamp.vecmath.Vector4f;

public class MultiChannelVolumeTexture extends Texture3D {
    private List<TextureProducer> textures = new ArrayList<>();

    //image indecies
    private int xDim,yDim,zDim;

    //float XDIM, YDIM, ZDIM;

    //WHITE:
    private List<Calibration> calibrations = new ArrayList<>();
    private boolean paused;

    public double[] getMinMax(int channel) {

        Calibration c = calibrations.get(channel);
        return new double[] {c.requestedMin, c.requestedMax};

    }

    public double[] getAbsoluteMinMax(int channel){
        Calibration c = calibrations.get(channel);

        return new double[] {c.clampedMin, c.clampedMax};
    }

    public double[] getMaxRangeMinMax(int channel) {
        Calibration c = calibrations.get(channel);
        return new double[] {c.min, c.max};

    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setDisplayColor(int dex, Color c) {
        float[] f = c.getColorComponents(new float[4]);
        setColor(dex, f[0], f[1], f[2]);
    }

    static class Calibration{
        double clampedMin;
        double clampedMax;
        double requestedMin, requestedMax;
        double min;
        double max;
        boolean scaled = true;

        double clear = 0;
        double opaque = 5;

        Color3f color;

        /**
         * Sets the clamp min and max range, if the value is scaled then the range is set
         * relative to the min/max values if the range is not scaled then the clamp
         * values.
         *
         * is rmin and rmax.
         *
         * @param rmin
         * @param rmax
         */
        void setRange(double rmin, double rmax){
            requestedMin = rmin;
            requestedMax = rmax;
            if(scaled){
                clampedMin = min + (max - min)*rmin;
                clampedMax = min + (max - min)*rmax;
            } else{
                clampedMin = rmin;
                clampedMax = rmax;
            }
        }

        void setTransparencyRange( double clear, double opaque){
            this.clear = clear;
            this.opaque = opaque;
        }

        /**
         * Determines the transparency of the provided clamped value.
         * @param scale clamped value.
         * @return normalized
         */
        private float alphaFromScale( float scale){

            if(scale < clear){
                return 0f;
            } if(scale > opaque){
                return 1f;
            }
            return (float)((scale - clear)/(opaque - clear));
        }
        VoxelPainter painter = getClampedPainter();

        public VoxelPainter getClampedPainter(){
            return data -> {
                if (data < clampedMin) data = clampedMin;
                if (data > clampedMax) data = clampedMax;
                float scale = (float) ((data - clampedMin) / (clampedMax - clampedMin));
                return new Vector4f(color.x * scale, color.y * scale, color.z * scale, alphaFromScale(scale));
            };
        }
    }




    /**
     * Creates a MultiChannelVolumeTexture with a single channel.
     *
     * @param xyz desired size of Texture3D backing data.
     * @param cl_min
     * @param cl_max
     * @param c
     */
    public MultiChannelVolumeTexture(int[] xyz){
        super(Texture.BASE_LEVEL, Texture.RGBA, xyz[0], xyz[1], xyz[2]);
        setCapability(ALLOW_IMAGE_WRITE);
        setCapability(ALLOW_ENABLE_WRITE);
        this.xDim = xyz[0];
        this.yDim = xyz[1];
        this.zDim = xyz[2];


        setEnable(true);
        setMinFilter(Texture.BASE_LEVEL_LINEAR);
        setMagFilter(Texture.BASE_LEVEL_LINEAR);
        setBoundaryModeS(Texture.CLAMP);
        setBoundaryModeT(Texture.CLAMP);
        setBoundaryModeR(Texture.CLAMP);

    }

    public void updateTextureData(int index, TextureProducer tex, double cl_min, double cl_max, Color3f c){

        textures.set(index, tex);

        Calibration cal = calibrations.get(index);
        cal.color = c;

        cal.setRange(cl_min, cl_max);
        clamp();
    }

    /**
     * Adds a channel and sets the absolute ranges.
     *
     * @param channelValues
     * @param minRange
     * @param maxRange
     * @param c
     * @return
     */
    public int addChannel(TextureProducer channelValues, double minRange, double maxRange, Color3f c){
        System.out.println("adding: " + minRange + " max: " + maxRange);
        Calibration cal = new Calibration();
        cal.color = c;
        cal.min = minRange;
        cal.max = maxRange;
        cal.setRange(0, 1);

        int dex = -1;
        for(int i = 0; i<textures.size(); i++){
            if(textures.get(i) == null){
                //empty slot.
                calibrations.set(i, cal);
                textures.set(i, channelValues);
                dex = i;
            }
        }
        if(dex == -1) {
            dex = textures.size();
            calibrations.add(cal);
            textures.add(channelValues);
        }
        clamp();

        return dex;
    }

    public void setVolumePainter(int channel, VoxelPainter painter){
        calibrations.get(channel).painter = painter;
        refresh();
    }

    public VoxelPainter getVolumePainter(int channel){
        return calibrations.get(channel).painter;
    }


    /**
     * Adds ints a and b together as unsigned bytes 0 -> 255 twos compliment. [ -128, 127 ] becomes
     * [0, 255] then they're added, and clipped at 255.
     *
     * @param a
     * @param b
     * @return
     */
    static byte accumulate(int a, int b){
        int s = (a & 0xff) + (b & 0xff);
        s = s>255 ? 255 : s;
        return (byte)s;
    }

    public void refresh(){
        clamp();
    }
    /**
     * Mixes the channels and passed the results to the 3D texture.
     *
     * If paused this method will not do anything.
     */
    protected void clamp() {
        if(paused) return;
        ImageComponent3D pArray = new ImageComponent3D(ImageComponent.FORMAT_RGBA, xDim, yDim, zDim);


        ColorSpace colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);

        ComponentColorModel colorModel =
                new ComponentColorModel(colorSpace, true, false,
                        Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);

        WritableRaster raster =
                colorModel.createCompatibleWritableRaster(xDim, yDim);

        BufferedImage bImage =
                new BufferedImage(colorModel, raster, false, null);

        byte[] byteData = ((DataBufferByte)raster.getDataBuffer()).getData();

        //COLORS: [0;255] 0 - black, 255 - white
        //TRANSP: [0;255] 0 - fully transparent, 255 - opaque

        //find the first non-null channel so that it erases.
        int firstChannel = 0;
        for(int i = 0; i<textures.size(); i++){
            if(textures.get(i) != null){
                firstChannel = i;
                break;
            }
        }

        for (int z = 0; z < zDim; z++) {

            for(int channel = 0; channel < textures.size(); channel ++ ) {
                int index = 0;
                TextureProducer double3d = textures.get(channel);
                if(double3d != null) {

                    Calibration cal = calibrations.get(channel);
                    //final Vector4f color4f = new Vector4f(cal.color.x, cal.color.y, cal.color.z, 1.f);
                    int notFirst = channel == firstChannel ? 0 : 1;
                    for (int y = 0; y < yDim; y++) {
                        for (int x = 0; x < xDim; x++) {

                            double data = double3d.get(x, yDim - y - 1, z);
                            Vector4f v = cal.painter.getColor(data);

                            //R
                            byteData[index] = accumulate(byteData[index] * notFirst, (int) (v.x * 255));
                            index++;
                            //G
                            byteData[index] = accumulate(byteData[index] * notFirst, (int) (v.y * 255));
                            index++;
                            //B
                            byteData[index] = accumulate(byteData[index] * notFirst, (int) (v.z * 255));
                            index++;
                            //transparency
                            byteData[index] = accumulate(byteData[index] * notFirst, (int) (v.w * 255));
                            index++;
                        }
                    }
                }
            }
            pArray.set(z, bImage);
        }
        setEnable(false);
        setImage(0, pArray);
        setEnable(true);
    }

    public void setColor(int channel, double x, double y, double z){
        Calibration cal = calibrations.get(channel);
        cal.color = new Color3f((float)x,(float)y,(float)z);
        setVolumePainter(channel, cal.getClampedPainter());
    }

    /**
     * Sets the transparency to the range associated relative to the clamped min and max.
     * 0 and 1 will be same range.
     * @param channel
     * @param low
     * @param high
     */
    public void setTransparencyRange(int channel, double low, double high){
        Calibration c = calibrations.get(channel);
        c.setTransparencyRange(low, high);
        clamp();
    }

    public void removeChannel(int index){
        textures.set(index, null);
        clamp();
    }
}
