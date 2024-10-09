package deformablemesh.geometry;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.topology.TopoCheck;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.meshview.VolumeDataObject;
import deformablemesh.track.Track;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ShortProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class BinaryImageGenerationTest {

    @Test
    public void spotTest(){
        ImagePlus plus = BinaryMeshGenerationTests.spot();
        Assert.assertEquals(0, testHarness(plus));
    }

    @Test
    public void blobTest(){
        ImagePlus plus = BinaryMeshGenerationTests.blob();
        Assert.assertEquals(0, testHarness(plus));
    }

    @Test
    public void jTest(){
        ImagePlus plus = BinaryMeshGenerationTests.j();
        Assert.assertEquals(0, testHarness(plus));
    }

    @Test
    public void oTest(){
        ImagePlus plus = BinaryMeshGenerationTests.o();
        Assert.assertEquals(0, testHarness(plus));
    }

    @Test
    public void pFaultTest(){
        ImagePlus plus = BinaryMeshGenerationTests.singlePointFault();
        Assert.assertEquals(0, testHarness(plus));
    }

    @Test
    public void oPinchFaultTest(){
        ImagePlus plus = BinaryMeshGenerationTests.openPinchFault();
        Assert.assertEquals(0, testHarness(plus));
    }
    @Test
    public void pinchFaultTest(){
        ImagePlus plus = BinaryMeshGenerationTests.pinchFault();
        Assert.assertEquals(0, testHarness(plus));
    }
    static long testHarness(ImagePlus plus){
        List<DeformableMesh3D> meshes = BinaryMeshGenerationTests.getMeshes(plus);
        List<Track> tracks = meshes.stream().map(m -> {
            TopoCheck tc = new TopoCheck(m);
            List<DeformableMesh3D> m2 = tc.repairMesh();
            Track t = new Track("j");
            t.addMesh(0, m2.get(0));
            return t;
        }).collect(Collectors.toList());
        ImagePlus bin = DeformableMesh3DTools.asUniqueLabels(new MeshImageStack(plus), tracks);
        ImageStack os = plus.getStack();
        ImageStack bs = bin.getStack();
        long err = 0;
        for(int i = 1; i<= bs.getSize(); i++){
            byte[] op = (byte[])os.getProcessor(i).getPixels();
            short[] bp = (short[])bs.getProcessor(i).getPixels();
            for(int j = 0; j<op.length; j++){
                if( (op[j] == 0) != ( bp[j] == 0 ))
                    err++;
            }
        }
        return err;
    }

    /**
     * Creates a mesh and creates a low-poly mesh
     */
    @Test
    public void nextGen(){
        ImagePlus plus = new ImagePlus();
        ImageStack stack = new ImageStack(128, 128);
        for(int i = 0; i<128; i++){
            stack.addSlice(new ShortProcessor(128, 128));
        }
        plus.setStack( stack, 1, 128, 1);
        MeshImageStack mis = new MeshImageStack(plus);


        Sphere sphere = new Sphere(new double[]{0, 0, 0}, 0.25);
        DeformableMesh3D mesh = RayCastMesh.rayCastMesh(sphere, new double[]{0, 0, 0}, 0);

        //volume in px
        double volume = mesh.calculateVolume()*mis.SCALE*mis.SCALE*mis.SCALE;

        List<int[]> pixels = DeformableMesh3DTools.getContainedPixels(mis, mesh);
        Assert.assertEquals(volume,  pixels.size(), 100);

        ImagePlus bin = DeformableMesh3DTools.createBinaryRepresentation(mis, mesh);
        List<DeformableMesh3D> binned = BinaryMeshGenerator.meshesFromLabels(new MeshImageStack(bin));
        ImagePlus bin2 = DeformableMesh3DTools.createBinaryRepresentation(mis, binned.get(0));

        ImageStack os = bin2.getStack();
        ImageStack bs = bin.getStack();
        long err = 0;
        for(int i = 1; i<= bs.getSize(); i++){
            byte[] op = (byte[])os.getProcessor(i).getPixels();
            byte[] bp = (byte[])bs.getProcessor(i).getPixels();
            for(int j = 0; j<op.length; j++){
                if( (op[j] == 0) != ( bp[j] == 0 ))
                    err++;
            }
        }
        Assert.assertEquals(0, err);
    }
    public static void main(String[] args){
        ImageJ ij = new ImageJ();
        ImagePlus plus = new ImagePlus();
        ImageStack stack = new ImageStack(256, 256);
        for(int i = 0; i<256; i++){
            stack.addSlice(new ShortProcessor(256, 256));
        }
        plus.setStack( stack, 1, 256, 1);
        MeshImageStack mis = new MeshImageStack(plus);
        Sphere sphere = new Sphere(new double[]{0, 0, 0}, 0.25);
        DeformableMesh3D mesh = RayCastMesh.rayCastMesh(sphere, new double[]{0, 0, 0}, 0);

        ImagePlus bin = DeformableMesh3DTools.createBinaryRepresentation(mis, mesh);
        bin.show();


        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.setBackgroundColor(new Color(0, 90, 40));
        mf3d.addLights();
        VolumeDataObject vdo = new VolumeDataObject(Color.WHITE);
        vdo.setTextureData(new MeshImageStack(bin));
        mf3d.addDataObject(vdo);
        mesh.create3DObject();
        mf3d.addDataObject(mesh.data_object);

        DeformableMesh3D mesh2 = RayCastMesh.rayCastMesh(sphere, new double[]{0, 0, 0}, 4);
        mesh2.create3DObject();
        mesh2.data_object.setWireColor(Color.BLUE);
        mf3d.addDataObject(mesh2.data_object);
        double factor = mis.SCALE*mis.SCALE*mis.SCALE;
        double volume = 0.25*0.25*0.25*factor*4/3*Math.PI;
        System.out.println(mesh2.calculateVolume()*factor + ", " + mesh.calculateVolume()*factor + " " + volume);
        //Assert.assertEquals(4*256*256*256/64/3, pixels.size());

        DeformableMesh3D binners = BinaryMeshGenerator.meshesFromLabels(new MeshImageStack(bin)).get(0);
        binners.create3DObject();
        binners.data_object.setWireColor(Color.BLACK);
        mf3d.addDataObject(binners.data_object);

    }

}
