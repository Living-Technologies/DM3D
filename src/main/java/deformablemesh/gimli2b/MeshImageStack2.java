package deformablemesh.gimli2b;

import bdv.viewer.Source;
import deformablemesh.MeshImageStack;
import deformablemesh.io.LoadZarr;
import deformablemesh.meshview.ChannelVolume;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.util.ColorSuggestions;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.algorithm.blocks.convert.Convert;
import net.imglib2.img.display.imagej.ImageJVirtualStack;
import net.imglib2.img.display.imagej.ImageJVirtualStackARGB;
import net.imglib2.img.display.imagej.ImageJVirtualStackFloat;
import net.imglib2.img.display.imagej.ImageJVirtualStackUnsignedByte;
import net.imglib2.img.display.imagej.ImageJVirtualStackUnsignedShort;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MeshImageStack2<T extends NumericType<T> & NativeType<T> & RealType<T>> extends MeshImageStack {
    //Each channel is a source
    List<Source<T>> sources;
    Calibration ijCalibration;

    /**
     * Creates an imglib2 interval for the current time/channel point.
     *
     */
    public Interval getInterval(){
        return new Interval(){
            @Override
            public int numDimensions() {
                return 3;
            }
            @Override
            public long min(int i) {
                return 0;
            }
            @Override
            public long max(int i) {
                return max_dex[i];
            }
        };
    }
    public Interval getSliceInterval(int slice){
        final int[] mins = {0, 0, slice};
        final int[] maxs = {getWidthPx() - 1, getHeightPx() - 1, slice};
        return new Interval() {
            @Override
            public long min(int i) {
                return mins[i];
            }

            @Override
            public long max(int i) {
                return maxs[i];
            }

            @Override
            public int numDimensions() {
                return 3;
            }
        };
    }
    @Override
    public ImagePlus createImagePlus(){
        ImagePlus plus = new ImagePlus();
        plus.setCalibration(ijCalibration.copy());
        return plus;
    }
    @Override
    public ImageProcessor getProcessor(int frame, int channel, int slice){
        RandomAccessibleInterval<T> rai = sources.get(channel).getSource(frame, 0);
        short[] pixels = new short[getWidthPx()*getHeightPx()];
        BlockSupplier.of(rai).andThen(Convert.convert( new UnsignedShortType())).copy(getSliceInterval(slice), pixels);
        ImageProcessor proc = new ShortProcessor(getWidthPx(), getHeightPx());
        proc.setPixels(pixels);
        return proc;
    }
    public MeshImageStack2(List<Source<T>> sources){
        //The assumption is each source is a channel for the same volume
        this.sources = sources;
        Source<T> source = sources.get(0);
        AffineTransform3D at = new AffineTransform3D();
        source.getSourceTransform(0, 0, at);
        double[] dt = at.getTranslation();

        double[] scale = new double[3];
        double[] op2 = new double[3];
        at.apply(new double[]{1, 1, 1}, scale);
        at.apply(new double[]{0, 0, 0}, op2);

        scale[0] = scale[0] - op2[0];
        scale[1] = scale[1] - op2[1];
        scale[2] = scale[2] - op2[2];
        long[] dims = source.getSource(0, 0).dimensionsAsLongArray();

        SLICES=(int)dims[2];
        this.dims = new int[]{(int)dims[0], (int)dims[1], (int)dims[2]};

        //TODO is there a way to get the number of frames from the source.
        int nFrames = 0;
        while(source.isPresent(nFrames)){
            nFrames++;
        }

        FRAMES = nFrames;
        CHANNELS = sources.size();
        CURRENT=0;
        channel = 0;
        int py = (int)dims[1];
        int px = (int)dims[0];

        max_dex = new int[]{px-1, py-1, SLICES-1};

        double[] dim3d = new double[]{
                scale[0]*px,
                scale[1]*py,
                scale[2]*SLICES
        };


        pixel_dimensions = scale;

        SCALE = dim3d[0];

        for(int i = 1; i<=2; i++){
            if(dim3d[i]>SCALE){
                SCALE = dim3d[i];
            }
        }

        scale_values = new double[]{
                1/pixel_dimensions[0],
                1/pixel_dimensions[1],
                1/pixel_dimensions[2]
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

        ijCalibration = new Calibration();
        calibrate(ijCalibration);
    }
    @Override
    public MeshImageStack duplicate(){
        return new MeshImageStack2<T>(sources);
    }
    @Override
    public ImagePlus getOriginalPlus(){
        ImagePlus original = new ImagePlus();
        //how to make the stack?
        Source<T> source = sources.get(0);
        List<RandomAccessibleInterval<T>> timeStacked = new ArrayList<>();
        for(int i = 0; i<getNFrames(); i++){
            List<RandomAccessibleInterval<T>> zstacks = new ArrayList<>();
            for(int j = 0; j<getNChannels(); j++){
                RandomAccessibleInterval<T> rai = sources.get(j).getSource(i, 0);
                rai = Views.addDimension(rai, 0, 0);
                zstacks.add(rai);
            }

            timeStacked.add(
                    Views.addDimension(
                        Views.concatenate(3, zstacks), 0, 0
                    )
                );
        }
        RandomAccessibleInterval<T> stacked = Views.concatenate(4, timeStacked);

        stacked = Views.moveAxis(stacked, 2, 3);
        Type<T> t = stacked.getType();
        ImageStack ij1Stack;

        if(t instanceof UnsignedByteType){
            ij1Stack = ImageJVirtualStackUnsignedByte.wrap((RandomAccessibleInterval<? extends UnsignedByteType>) stacked );
        } else if( t instanceof UnsignedShortType){
            ij1Stack = ImageJVirtualStackUnsignedShort.wrap((RandomAccessibleInterval<? extends UnsignedShortType>) stacked );
        } else if( t instanceof FloatType){
            ij1Stack = ImageJVirtualStackFloat.wrap((RandomAccessibleInterval<? extends FloatType>) stacked );
        } else if( t instanceof IntType){
            RandomAccessibleInterval<? extends ARGBType> rai2 = (RandomAccessibleInterval)stacked;
            ij1Stack = ImageJVirtualStackARGB.wrap((RandomAccessibleInterval<ARGBType>) rai2);
        } else{
                ij1Stack = new ImageJVirtualStack<T>(stacked, stacked.getType().getBitsPerPixel()){};

        }

        original.setCalibration(ijCalibration);
        original.setStack(ij1Stack, getNChannels(), getNSlices(), getNFrames());
        original.setTitle(getShortTitle());
        original.setOpenAsHyperStack(true);

        return original;
    }
    double[] getScale(){
        Source<T> source = sources.get(0);
        AffineTransform3D at = new AffineTransform3D();
        source.getSourceTransform(0,0,at);
        double[] scale = new double[3];
        double[] op2 = new double[3];
        at.apply(new double[]{1, 1, 1}, scale);
        at.apply(new double[]{0, 0, 0}, op2);
        scale[0] = scale[0] - op2[0];
        scale[1] = scale[1] - op2[1];
        scale[2] = scale[2] - op2[2];
        return scale;
    }
    double[] getTranslation(){
        Source<T> source = sources.get(0);
        AffineTransform3D at = new AffineTransform3D();
        source.getSourceTransform(0,0,at);
        return  at.getTranslation();
    }
    public void calibrate(Calibration c){
        AffineTransform3D at = new AffineTransform3D();

        double[] scale = getScale();
        double[] translation = getTranslation();
        c.frameInterval = -1;
        c.pixelWidth = scale[0];
        c.pixelHeight = scale[1];
        c.pixelDepth = scale[2];
        c.xOrigin = translation[0];
        c.yOrigin = translation[1];
        c.zOrigin = translation[2];

    }
    @Override
    public Calibration getImageJCalibration(){
        return ijCalibration;
    }
    @Override
    public void copyValues(){
        int n = dims[2]*dims[1]*dims[0];
        if(n != data.length) {
            data = new double[n];
        }
        RandomAccessibleInterval<T> rai = sources.get(channel).getSource(CURRENT, 0);
        extract(rai, data);
    }

    private void extract(RandomAccessibleInterval<T> rai, double[] buffer) {
        BlockSupplier.of(rai)
                .andThen(Convert.convert(new DoubleType()))
                .copy(getInterval(), buffer);
    }

    @Override
    public double getValue(int x, int y, int z){
        return data[x + y*dims[0] + z*dims[0]*dims[1]];
    }

    public static void main(String[] args) throws IOException {
        new ImageJ();
        String location = IJ.getDirectory("Select zarr folder");
        if(location == null) return;

        MeshImageStack mist = LoadZarr.loadMeshImageStack2( Paths.get(location) );
        mist.getOriginalPlus().show();
        MeshFrame3D frame = new MeshFrame3D();
        frame.showFrame(true);
        frame.setBackgroundColor(new Color(0, 0, 50));

        //Each mesh image stack maintains its own current buffer.
        List<MeshImageStack> stacks = new ArrayList<>();

        stacks.add(mist);
        for(int channel = 1; channel < mist.getNChannels(); channel++){
            MeshImageStack next = mist.duplicate();
            next.setChannel(channel);
            stacks.add(next);
        }

        //show all the channels.
        List<ChannelVolume> cVolumes = new ArrayList<>();
        for(MeshImageStack stack : stacks){
            Color color = ColorSuggestions.getSuggestion();
            ChannelVolume cv = frame.getMultiChannelVolumeObject(stack, color );
            cv.getVolumeDataObject().setMinMaxRange(0.2, 0.6);
            cVolumes.add(cv);
        }
        JComponent comp = (JComponent)frame.getJFrame().getContentPane();
        int[] fc = new int[2];

        // Add a controls to move through the channels.
        comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "left");
        comp.getActionMap().put("left", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                long start = System.nanoTime();
                fc[0] = fc[0] > 0 ? fc[0] - 1 : mist.getNFrames() - 1;

                for(ChannelVolume cv : cVolumes){
                    cv.frameChanged(fc[0]);
                }
                System.out.println( ((System.nanoTime() - start)*1e-9) + "to change");
            }
        });

        comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "right");
        comp.getActionMap().put("right", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                long start = System.nanoTime();
                fc[0] = (fc[0] + 1)%mist.getNFrames();
                cVolumes.forEach(cv -> cv.frameChanged(fc[0]));
                System.out.println( ((System.nanoTime() - start)*1e-9) + "to change");
            }
        });

    }
}
