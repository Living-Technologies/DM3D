package deformablemesh.gimli2b;

import bdv.viewer.Source;
import deformablemesh.MeshImageStack;
import deformablemesh.experimental.LoadZarr;
import deformablemesh.meshview.ChannelVolume;
import deformablemesh.meshview.MeshFrame3D;
import ij.ImagePlus;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MeshImageStack2<T extends NumericType<T> & NativeType<T>> extends MeshImageStack {
    interface Converter{
        double get(Object value);
    }
    static class UBConverter implements Converter{
        @Override
        public double get(Object ubt){
            return ((UnsignedByteType)ubt).get();
        }
    }

    static class SConverter implements Converter{
        @Override
        public double get(Object value){
            return ((UnsignedShortType)value).get();
        }
    }

    Converter converter;
    //Each channel is a source
    List<Source<T>> sources;
    public MeshImageStack2(List<Source<T>> sources){
        //The assumption is each source is a channel for the same volume
        this.sources = sources;
        Source<T> source = sources.get(0);
        AffineTransform3D at = new AffineTransform3D();
        source.getSourceTransform(0, 0, at);
        double[] dt = at.getTranslation();
        System.out.println( "translation: " + Arrays.toString(dt) );
        double[] scale = new double[3];
        double[] op2 = new double[3];
        at.apply(new double[]{1, 1, 1}, scale);
        at.apply(new double[]{0, 0, 0}, op2);
        System.out.println(Arrays.toString( scale ) + " and " + Arrays.toString(op2));
        System.out.println("scale: "  + (scale[0] - op2[0]) + ", "  + (scale[1] - op2[1]) + ", "  + (scale[2] - op2[2]) + ", " );
        scale[0] = scale[0] - op2[0];
        scale[1] = scale[1] - op2[1];
        scale[2] = scale[2] - op2[2];
        long[] dims = source.getSource(0, 0).dimensionsAsLongArray();
        System.out.println(Arrays.toString(dims));
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
        int px = (int)dims[2];

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
        this.converter = getConverter(source.getType());
    }
    Converter getConverter(T type){
        if(type instanceof UnsignedByteType){
            return new UBConverter();
        } else if(type instanceof UnsignedShortType){
            return new SConverter();
        }
        throw new RuntimeException( "Cannot handle: " + type.getClass());
    }
    @Override
    public void copyValues(){

    }

    @Override
    public double getValue(int x, int y, int z){
        T px = sources.get(channel).getSource(CURRENT, 0).getAt(x, y, z);
        return converter.get(px);
    }

    public static void main(String[] args) throws IOException {
        String location = "D:\\working\\sonnen\\3D_small_organoid\\3D_small_organoid.zarr";
        //List<Source<UnsignedByteType>> sources = LoadZarr.<UnsignedByteType>load3DSource(location);
        //MeshImageStack2<UnsignedByteType> mist = new MeshImageStack2<>(sources);
        List<ImagePlus> pluses = LoadZarr.load3DStackFromZarrFile(location);
        MeshImageStack mist = new MeshImageStack(pluses.get(0));
        MeshFrame3D frame = new MeshFrame3D();
        frame.showFrame(true);
        frame.setBackgroundColor(new Color(0, 0, 50));
        ChannelVolume cv = frame.getMultiChannelVolumeObject(mist, new Color(255, 0, 255) );
        cv.getVolumeDataObject().setMinMaxRange(0.1, 0.5);

        JComponent comp = (JComponent)frame.getJFrame().getContentPane();
        int[] fc = new int[2];

        comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false), "up");
        comp.getActionMap().put("up", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("keying");
                fc[1] = (fc[1] + 1)%mist.getNChannels();
                mist.setChannel(fc[1]);
                cv.frameChanged(fc[0]);
            }
        });

        comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false), "down");
        comp.getActionMap().put("down", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("keying");
                fc[1] = fc[1] > 0 ? fc[1] - 1 : mist.getNChannels() - 1;
                mist.setChannel(fc[1]);
                cv.frameChanged(fc[0]);
            }
        });

        comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "left");
        comp.getActionMap().put("left", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("keying");
                fc[0] = fc[0] > 0 ? fc[0] - 1 : mist.getNFrames() - 1;
                cv.frameChanged(fc[0]);
            }
        });

        comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "right");
        comp.getActionMap().put("right", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("keying");
                fc[0] = (fc[0] + 1)%mist.getNChannels();
                cv.frameChanged(fc[0]);
            }
        });


    }

}
