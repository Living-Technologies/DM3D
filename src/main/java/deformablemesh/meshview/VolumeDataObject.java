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

import deformablemesh.MeshImageStack;
import org.jogamp.java3d.BranchGroup;
import org.jogamp.java3d.Transform3D;
import org.jogamp.java3d.TransformGroup;
import org.jogamp.vecmath.Vector3d;

import java.awt.Color;
import java.util.IntSummaryStatistics;
import java.util.List;

/**
 * Created by smithm3 on 23/02/18.
 */
public class VolumeDataObject implements DataObject {
    Color color;
    Sizeable3DSurface surface;
    final MultiChannelVolumeTexture volume;
    double scale;
    /*size of the backing texture block, (w, h, d), essentially pixels.*/
    int[] sizes;
    double[] offsets;
    double[] lengths;
    TextureProducer textureProducer;
    BranchGroup branchGroup;
    TransformGroup tg;
    double min  = 0;
    double max = 1;

    double tLow = 1;
    double tHigh = 1;
    int dex = -1;
    public VolumeDataObject(Color c, MultiChannelVolumeTexture tex) {
        color = c;
        offsets = new double[]{0,0,0};
        volume = tex;

    }

    public void setGeometry(Sizeable3DSurface surface){
        this.surface = surface;
    }
    public Sizeable3DSurface getGeometry(){
        return surface;
    }

    public void setColor(Color c){
        color = c;
    }

    /**
     * Sets the position of the lowest corner. .
     * @param x
     * @param y
     * @param z
     */
    public void setPosition(double x, double y, double z){
        offsets = new double[]{x , y, z};

        if(tg!=null){
            Transform3D tt = new Transform3D();
            tg.getTransform(tt);

            Vector3d n = new Vector3d(x, y, z);

            tt.setTranslation(n);

            tg.setTransform(tt);
        }
    }

    /**
     * For creating a volume representing the all of the pixes of the provided mesh image stack.
     *
     * @param stack
     */
    public void setTextureData(MeshImageStack stack){
        int lowx = 0;
        int highx = stack.getWidthPx() - 1;
        int lowy = 0;
        int highy = stack.getHeightPx() - 1;
        int lowz = 0;
        int highz = stack.getNSlices() - 1;

        int d = highz - lowz + 1;
        int h = highy - lowy + 1;
        int w = highx - lowx + 1;

        sizes = new int[]{w, h, d};
        double[] unit = {sizes[0], sizes[1], sizes[2]};
        //size of the texture backing data in normalized units.
        lengths = stack.scaleToNormalizedLength(new double[]{sizes[0], sizes[1], sizes[2]});

        setPosition(0, 0, -stack.offsets[2]);
        textureProducer = stack::getValue;
        updateVolume();
    }


    /**
     * For creating a volume that shows part of an image stack.
     *
     * @param stack
     * @param pts
     */
    public void setTextureData(MeshImageStack stack, List<int[]> pts){
        int lowx = 0;
        int highx = stack.getWidthPx() - 1;
        int lowy = 0;
        int highy = stack.getHeightPx() - 1;
        int lowz = 0;
        int highz = stack.getNSlices() - 1;
        IntSummaryStatistics iss = pts.stream().mapToInt(i->i[2]).summaryStatistics();
        System.out.println(iss.getMin() + ", " + iss.getMax() );
        int d = highz - lowz + 1;
        int h = highy - lowy + 1;
        int w = highx - lowx + 1;

        sizes = new int[]{w, h, d};

        textureProducer = (x, y, z) -> {
            for(int[] pt : pts){
                if(x == pt[0] && y == pt[1] && z == pt[2]){
                    return 1.0;
                }
            }
            return 0.0;
        };
        lengths = stack.scaleToNormalizedLength(new double[]{sizes[0], sizes[1], sizes[2]});
        updateVolume();
    }

    public void setMinMaxRange(double min, double max){
        System.out.println("Setting min and Max for: " + dex);
        this.min = min;
        this.max = max;
        updateVolume();
    }

    /**
     * Sets the transparency clipping of the clamped data values.
     * @param low Values below this will be fully transpaent.
     * @param high Values above this will be fully opaque. Setting this
     *             value greating than 1 will cause the transparency to
     *             never be fully opaque.
     */
    public void setTransparencyTrim(double low, double high){
        tLow = low;
        tHigh = high;
        if(volume != null){
            volume.setTransparencyRange(0, tLow, tHigh);
            surface.setTexture(volume);
        }
    }
    /**
     * Creates the 3D representation of the data in "texture_data"
     *
     */
    public void updateVolume(){
        Color volumeColor = color;
        if(dex == -1){
            dex = volume.addChannel(textureProducer, min, max, DataCanvas.getComponents(volumeColor));
        } else{
            volume.updateTextureData(dex, textureProducer, min, max, DataCanvas.getComponents(volumeColor));
        }


        if(surface==null){
            /*
             * The surface is positioned such that the origin corner is at -lengths[0]/2, -lengths[1]/2, 0
             */
            surface = new Sizeable3DSurface(volume, sizes, lengths);

            tg = new TransformGroup();
            tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
            tg.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);

            Transform3D tt = new Transform3D();
            tg.getTransform(tt);

            Vector3d n = new Vector3d(offsets[0], offsets[1], offsets[2]);
            tt.setTranslation(n);

            tg.setTransform(tt);
            tg.addChild(surface.getBranchGroup());
            branchGroup = new BranchGroup();
            branchGroup.addChild(tg);
            branchGroup.setCapability(BranchGroup.ALLOW_DETACH);
        }

    }

    public void showAsLabeledVolume(){
        volume.setVolumePainter(dex, new LabeledVoxelPainter(0));
    }

    @Override
    public BranchGroup getBranchGroup() {
        return branchGroup;
    }

    public double[] getMaxRangeMinMax(){
        return volume.getMaxRangeMinMax(0);
    }
    public double[] getClampedMinMax(){
        return volume.getAbsoluteMinMax(0);
    }
    public double[] getMinMax() {
        return new double[] {min, max};
    }

    public void unlinkFromTexture() {
        volume.removeChannel(dex);
    }
}
