package deformablemesh.geometry;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import ij.ImagePlus;
import ij.ImageStack;
import org.junit.Assert;
import org.junit.Test;

public class BinaryImageGenerationTest {

    @Test
    public void spotTest(){
        ImagePlus plus = BinaryMeshGenerationTests.spot();
        DeformableMesh3D mesh = BinaryMeshGenerationTests.getMeshes(plus).get(0);
        ImagePlus bin = DeformableMesh3DTools.createBinaryRepresentation(new MeshImageStack(plus), mesh);
        ImageStack os = plus.getStack();
        ImageStack bs = bin.getStack();
        for(int i = 1; i<= bs.getSize(); i++){
            byte[] op = (byte[])os.getProcessor(i).getPixels();
            byte[] bp = (byte[])bs.getProcessor(i).getPixels();
            long err = 0;
            for(int j = 0; j<op.length; j++){
                if( (op[j] != 0) != ( bp[j] != 0 ))
                    err++;
            }
            Assert.assertEquals(0, err);
        }



    }

    @Test
    public void blobTest(){
        ImagePlus plus = BinaryMeshGenerationTests.blob();
        DeformableMesh3D mesh = BinaryMeshGenerationTests.getMeshes(plus).get(0);
        ImagePlus bin = DeformableMesh3DTools.createBinaryRepresentation(new MeshImageStack(plus), mesh);
        ImageStack os = plus.getStack();
        ImageStack bs = bin.getStack();
        for(int i = 1; i<= bs.getSize(); i++){
            byte[] op = (byte[])os.getProcessor(i).getPixels();
            byte[] bp = (byte[])bs.getProcessor(i).getPixels();
            long err = 0;
            for(int j = 0; j<op.length; j++){
                if( (op[j] == 0) != ( bp[j] == 0 ))
                    err++;
            }
            Assert.assertEquals(0, err);
        }



    }

}
