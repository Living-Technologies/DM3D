package deformablemesh.experimental;

import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.BinaryMeshGenerator;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.meshview.ChannelVolume;
import deformablemesh.meshview.MeshFrame3D;
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
import net.imglib2.mesh.Mesh;
import net.imglib2.mesh.Vertex;
import net.imglib2.mesh.alg.MarchingCubesRealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.ImgUtil;

import java.awt.*;
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
        Mesh mesh = MarchingCubesRealType.calculate(img, 1);
        for(Vertex v : mesh.vertices()){
            System.out.println(v.x() + ", " + v.y() + ", " + v.z());
        }
        ImagePlus plus = ImageJFunctions.wrap(img, "debuggles");
        plus.setDimensions(1, 9, 1);
        plus.show();
        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.addLights();
        mf3d.setBackgroundColor(new Color(200, 200, 200));

        MeshImageStack mis = new MeshImageStack(plus);
        Imglib2Mesh.ImageSpaceTransformer ist = new Imglib2Mesh.ImageSpaceTransformer(mis);
        DeformableMesh3D dm3d = Imglib2Mesh.convertMesh(mesh, ist);
        dm3d.create3DObject();
        dm3d.data_object.setWireColor(Color.BLUE);
        mf3d.addDataObject(dm3d.data_object);
        ChannelVolume cv = new ChannelVolume(mis, Color.BLACK);
        VolumeDataObject vdo = cv.getVolumeDataObject();
        vdo.setMinMaxRange(0, 1);
        vdo.setTransparencyTrim(0, 1);
        mf3d.addDataObject(cv.getVolumeDataObject());

        List<Region> r = new MeshDetector(mis).getRegionsFromLabelledImage();
        DeformableMesh3D voxel = BinaryMeshGenerator.voxelMesh(r.get(0).getPoints(), mis, 1);
        voxel.create3DObject();
        voxel.setColor(Color.BLUE);
        mf3d.addDataObject(voxel.data_object);
    }
}
