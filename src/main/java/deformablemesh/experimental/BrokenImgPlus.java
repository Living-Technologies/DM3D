package deformablemesh.experimental;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultAxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.axis.LinearAxis;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ByteImagePlus;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Fraction;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;

import java.nio.file.Paths;

public class BrokenImgPlus {
    public static void showACalibratedImage(){
        ByteImagePlus<UnsignedByteType> bplus = new ByteImagePlus<>(new long[]{64, 64, 64, 3}, new Fraction());
        final UnsignedByteType linkedType = new UnsignedByteType( bplus );
        // pass it to the NativeContainer
        bplus.setLinkedType( linkedType );
        CalibratedAxis ca = new DefaultLinearAxis(0.25);

        ImageJFunctions.show(
                new ImgPlus<UnsignedByteType>(
                        bplus, "testing",
                        new DefaultLinearAxis(new DefaultAxisType("X", true), 0.25, -10),
                        new DefaultLinearAxis(new DefaultAxisType("Y", true), 0.25, -10),
                        new DefaultLinearAxis(new DefaultAxisType("Z", true), 2.0, -2),
                        new DefaultLinearAxis(new DefaultAxisType("T", false), 120, 0)
                ) );
    }
    public static void main(String[] args){
        String localPath = "test.zarr";
        String absolutePath = Paths.get(localPath).toAbsolutePath().toUri().toString();
        System.out.println(absolutePath);
        N5Writer n5 = new N5Factory().openWriter(absolutePath);
    }
}
