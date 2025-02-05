package deformablemesh.gimli2b;

import bdv.util.RandomAccessibleIntervalMipmapSource4D;
import bdv.viewer.Source;
import deformablemesh.MeshImageStack;
import ij.measure.Calibration;
import mpicbg.spim.data.sequence.DefaultVoxelDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MeshImageStackConversions {

    static List<Source<UnsignedShortType>> getSource(){
        RandomAccessibleInterval<UnsignedShortType> rai = new ArrayImgFactory<UnsignedShortType>()
                .create( new long[] { 256, 256, 64, 3, 5}, new UnsignedShortType() ).view();
        List<Source<UnsignedShortType>> sources = new ArrayList<>();
        for(int i = 0; i<rai.dimension(3); i++){
            sources.add(getAsBdvSource(i, rai));
        }
        return sources;
    }
    public static <T extends NumericType<T> & NativeType<T> & RealType<T>> Source<T> getAsBdvSource(int channel, RandomAccessibleInterval<T> img){
        int n = 1;
        String title = "samply-sample";
        double[] scale = {0.25, 0.25, 2};
        double[] offset = {-5, -7, -9};
        RandomAccessibleInterval<T>[] levels = new RandomAccessibleInterval[n];
        AffineTransform3D[] transforms = new AffineTransform3D[n];

        for(int i = 0; i<n; i++){
            RandomAccessibleInterval<T> rai = (RandomAccessibleInterval<T>) Views.hyperSlice(img, 3, channel);
            levels[i] = rai;



            AffineTransform3D a = new AffineTransform3D();
            a.scale(scale[0], scale[1], scale[2]);
            a.translate(offset[0], offset[1], offset[2]);
            transforms[i] = a;
        }
        T type = img.getType();
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

    static <T extends NumericType<T> & NativeType<T> & RealType<T>> void finish(MeshImageStack2<T> mist){
        Calibration cb = mist.ijCalibration;
        cb.setTimeUnit("s");
        cb.frameInterval = 120;
        cb.setUnit("um");
    }

    @Test
    public <T extends NumericType<T> & NativeType<T> & RealType<T>> void recreateOriginal(){
        MeshImageStack2<UnsignedShortType> mist = new MeshImageStack2<>(getSource());
        finish(mist);

        MeshImageStack legacy = new MeshImageStack(mist.getOriginalPlus());

        Assert.assertEquals(mist.getNFrames(), legacy.getNFrames());
        Assert.assertEquals(mist.getNChannels(), legacy.getNChannels());
        Assert.assertEquals(mist.getNSlices(), legacy.getNSlices());

        double[] center = {0, 0, 0};
        double[] alt = {16, 16, 8};
        Calibration cb = legacy.getImageJCalibration();
        System.out.println(cb.zOrigin + ", " + cb.yOrigin + ", " + cb.xOrigin + "//" + cb.pixelDepth + ", " + cb.pixelHeight + ", " + cb.pixelWidth);
        Assert.assertArrayEquals(mist.getImageCoordinates(center), legacy.getImageCoordinates(center), 1e-9);
        Assert.assertArrayEquals(mist.getNormalizedCoordinate(alt), legacy.getNormalizedCoordinate(alt), 1e-9);
    }



}
