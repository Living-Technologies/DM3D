package deformablemesh.geometry;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.topology.TopoCheck;
import deformablemesh.track.Track;
import ij.ImagePlus;
import ij.ImageStack;
import org.junit.Assert;
import org.junit.Test;

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

}
