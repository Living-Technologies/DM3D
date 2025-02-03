package deformablemesh.io;

import bdv.util.RandomAccessibleIntervalMipmapSource4D;
import bdv.viewer.Source;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import mpicbg.spim.data.sequence.DefaultVoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJVirtualStackARGB;
import net.imglib2.img.display.imagej.ImageJVirtualStackFloat;
import net.imglib2.img.display.imagej.ImageJVirtualStackUnsignedByte;
import net.imglib2.img.display.imagej.ImageJVirtualStackUnsignedShort;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class MultiscaleImageAdapter<T extends NumericType<T> & NativeType<T>> {
    String title;

    /**
     * Calibrates the provided calibration, with the intent of setting the units.
     * The scale is set to the 0'th multiscale values.
     *
     *
     * @param ij ImagePlus calibration object it will be modified.
     */
    public void calibrateUnits(Calibration ij) {
        images.calibrate(ij, 0);
    }

    static class MultiscaleImage<V>{
        List<double[]> scales = new ArrayList<>();
        List<double[]> offsets = new ArrayList<>();
        List<RandomAccessibleInterval<V>> data = new ArrayList<>();
        List<String> datasetLabels = new ArrayList<>();
        List<Axis> axes = new ArrayList<>();
        int tDex = -1;
        int cDex = -1;
        int zDex = -1;
        int xDex = -1;
        int yDex = -1;
        public void calibrate(Calibration cb, int level){
            double[] scale = scales.get(level);
            double[] offset = offsets.get(level);

            cb.pixelWidth = scale[xDex];
            cb.pixelHeight = scale[yDex];
            cb.pixelDepth = scale[zDex];
            cb.xOrigin = offset[xDex];
            cb.yOrigin = offset[yDex];
            cb.zOrigin = offset[zDex];
            if(tDex >= 0){
                cb.setTimeUnit(axes.get(tDex).getUnit());
                cb.frameInterval = scale[tDex];
            }
            cb.setUnit(axes.get(xDex).getUnit());
        }

        public String getOrder(){
            return axes.stream().map(Axis::getName).collect(Collectors.joining(""));

        }

        public int getNChannels(){
            if(cDex < 0){
                //no explicit channel axis, single channel
                return 1;
            } else{
                return (int)data.get(0).dimensionsAsLongArray()[raiIndex(cDex)];
            }
        }

        public int getNFrames(){
            if(tDex < 0){
                //no explicit time axis, single timepoint
                return 1;
            } else{
                return (int)data.get(0).dimensionsAsLongArray()[raiIndex(tDex)];
            }
        }

        int raiIndex(int zarrIndex){
            return axes.size() - zarrIndex - 1;
        }

        public int getNSlices(int level){
            if(zDex < 0){
                //no explicit slice axis
                return 1;
            } else{
                return (int)data.get(level).dimensionsAsLongArray()[raiIndex(zDex)];
            }
        }

    }
    MultiscaleImage<T> images;
    public MultiscaleImageAdapter(List<Axis> axes){
        images = new MultiscaleImage<>();
        setAxes(axes);
    }
    public int getNChannels(){
        return images.getNChannels();
    }
    public void setTitle(String title){
        this.title = title;
    }

    public String getTitle(){
        return title;
    }

    private void setAxes(List<Axis> axes){
        int nDims = axes.size();
        for(int j = 0; j<axes.size(); j++) {
            Axis axis = axes.get(j);
            String n = axis.getName().toLowerCase();
            if (n.equals("c")) {
                images.cDex = images.axes.size();
            } else if (n.equals("t")) {
                images.tDex = images.axes.size();
            } else if (n.equals("z")) {
                images.zDex = images.axes.size();
            } else if (n.equals("y")) {
                images.yDex = images.axes.size();
            } else if (n.equals("x")) {
                images.xDex = images.axes.size();
            }
            images.axes.add(axis);
        }
    }

    public int getNextLevel(){
        return images.data.size();
    }

    public void addResolution(RandomAccessibleInterval<T> data, double[] scale, double[] offset){
        System.out.println(Arrays.toString(data.dimensionsAsLongArray()));
        System.out.println(Arrays.toString(scale));
        System.out.println(Arrays.toString(offset));
        System.out.println(images.getOrder());
        images.data.add(data);
        images.scales.add(scale);
        images.offsets.add(offset);
    }


    private ImageStack getImageStack(int level){
        RandomAccessibleInterval<T> rai = images.data.get(level);
        T t = rai.getType();
        String order = images.getOrder();
        if(order.contains("cz")){
            rai = Views.moveAxis(rai, images.raiIndex(images.cDex), images.raiIndex(images.zDex));
        }
        if(t instanceof UnsignedByteType){
            return ImageJVirtualStackUnsignedByte.wrap((RandomAccessibleInterval<? extends UnsignedByteType>) rai );
        } else if( t instanceof UnsignedShortType){
            return ImageJVirtualStackUnsignedShort.wrap((RandomAccessibleInterval<? extends UnsignedShortType>) rai );
        } else if( t instanceof FloatType){
            return ImageJVirtualStackFloat.wrap((RandomAccessibleInterval<? extends FloatType>) rai );
        } else if( t instanceof ARGBType){
            RandomAccessibleInterval<? extends ARGBType> rai2 = (RandomAccessibleInterval<? extends ARGBType>)rai;
            return ImageJVirtualStackARGB.wrap((RandomAccessibleInterval<ARGBType>) rai2);
        }
        throw new RuntimeException("Cannot map data type to ImageJ 1 datatype: " + t.getClass());
    }

    public void addDataSetLabel(String dataSetLabel){
        images.datasetLabels.add(dataSetLabel);
    }

    public int getMipMapLevels(){
        return images.data.size();
    }
    public ImagePlus getMipMapAsPlus(int level){
        ImagePlus plus = new ImagePlus();
        plus.setTitle(getTitle());

        ImageStack stack = getImageStack(level);
        System.out.println(stack.size() + " E " + images.getNChannels() + ", " + images.getNSlices(level) + ", " + images.getNFrames());
        plus.setStack(stack, images.getNChannels(), images.getNSlices(level), images.getNFrames());
        Calibration cb = plus.getCalibration();
        images.calibrate(cb, level);
        plus.setOpenAsHyperStack(true);
        return plus;
    }

    public Source<T> getAsBdvSource(int channel){
        int n = getMipMapLevels();
        RandomAccessibleInterval<T>[] levels = new RandomAccessibleInterval[n];
        AffineTransform3D[] transforms = new AffineTransform3D[n];
        T type = images.data.get(0).getType();
        for(int i = 0; i<getMipMapLevels(); i++){
            RandomAccessibleInterval<T> rai = (RandomAccessibleInterval<T>)Views.hyperSlice(images.data.get(i), 3, channel);
            levels[i] = rai;

            double[] scale = images.scales.get(i);
            double[] offset = images.offsets.get(i);


            AffineTransform3D a = new AffineTransform3D();
            a.scale(scale[images.xDex], scale[images.yDex], scale[images.zDex]);
            a.translate(offset[images.xDex], offset[images.yDex], offset[images.zDex]);
            transforms[i] = a;
        }
        DefaultVoxelDimensions vd = new DefaultVoxelDimensions(4);
        RandomAccessibleIntervalMipmapSource4D<T> source = new RandomAccessibleIntervalMipmapSource4D<>(
                levels,
                type,
                transforms,
                vd,
                title, false
        );
        return source;
    }

}
