package deformablemesh.geometry.interceptable;

import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.BinaryMeshGenerationTests;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.util.connectedcomponents.Region;
import ij.ImagePlus;
import org.junit.Test;

import java.util.List;

public class BinaryInterceptableTest {
    @Test
    public void rayCastAVoxel(){
        ImagePlus plus = BinaryMeshGenerationTests.spot();
        MeshImageStack stack = new MeshImageStack(plus);
        MeshDetector detector = new MeshDetector(stack);
        List<Region> regions = detector.getRegionsFromLabelledImage();
        Region r = regions.get(0);
        List<int[]> points = r.getPoints();
        BinaryInterceptible bi = new BinaryInterceptible(points, stack, r.getLabel());
        DeformableMesh3D mesh = RayCastMesh.rayCastMesh(bi, bi.getCenter(), 3);
        assert(mesh.calculateVolume() > 0);
    }
}
