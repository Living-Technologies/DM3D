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

public class BrokenImgPlus {

    public static void main(String[] args){
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
}
