package deformablemesh.experimental;

import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.BinaryMeshGenerator;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.meshview.ChannelVolume;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.meshview.MultiChannelVolumeTexture;
import deformablemesh.meshview.VolumeDataObject;
import deformablemesh.util.connectedcomponents.Region;
import ij.ImagePlus;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;

import java.awt.Color;
import java.util.List;


public class MCBroken {
    public static Img<UnsignedByteType> image(){
        final ImgFactory< UnsignedByteType > factory = new ArrayImgFactory<>(new UnsignedByteType(0));
        ArrayImg<UnsignedByteType, ByteArray> img = (ArrayImg<UnsignedByteType, ByteArray>)factory.create(new int[]{9, 9, 9});
        RandomAccess<UnsignedByteType> r = img.randomAccess();

        for(int i = 3; i<6; i++){
            for(int j = 3; j<6; j++) {
                for (int k = 3; k < 6; k++) {
                    UnsignedByteType bt = r.setPositionAndGet(i, j, k);
                    bt.set(1);
                }
            }
        }
        return img;
    }
    public static void main(String[] args){
        Img<UnsignedByteType> img = image();
    }
}
