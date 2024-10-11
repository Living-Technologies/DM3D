package deformablemesh.geometry.topology;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.BinaryMeshGenerationTests;
import deformablemesh.geometry.BinaryMeshGenerator;
import deformablemesh.geometry.ConnectionRemesher;
import deformablemesh.geometry.DeformableMesh3D;
import ij.ImagePlus;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TopoCheckTests {
    DeformableMesh3D remesh(DeformableMesh3D m){
        ConnectionRemesher cm = new ConnectionRemesher();
        cm.setMinAndMaxLengths(0.5/16, 1.0/16);
        return cm.remesh(m);
    }
    @Test
    public void pointFaultTest(){
        ImagePlus plus = BinaryMeshGenerationTests.singlePointFault();
        DeformableMesh3D mesh = BinaryMeshGenerationTests.getMeshes(plus).get(0);
        TopoCheck tc = new TopoCheck(mesh);
        List<TopologyValidationError> errorList = tc.validate();
        Assert.assertEquals(1, errorList.size());
        Assert.assertEquals(TopologyValidationError.DISJOINT_NODE, errorList.get(0).type);
        List<DeformableMesh3D> fixed = tc.repairMesh();
        Assert.assertEquals(1, fixed.size());
        TopoCheck tc2 = new TopoCheck(fixed.get(0));
        List<TopologyValidationError> e2 = tc2.validate();
        Assert.assertEquals(0, e2.size());
        remesh(fixed.get(0));
    }

    @Test
    public void pinchFaultTest(){
        ImagePlus plus = BinaryMeshGenerationTests.pinchFault();
        DeformableMesh3D mesh = BinaryMeshGenerationTests.getMeshes(plus).get(0);
        TopoCheck tc = new TopoCheck(mesh);
        List<TopologyValidationError> errorList = tc.validate();
        Assert.assertEquals(1, errorList.size());
        Assert.assertEquals(TopologyValidationError.INTERSECTING_SURFACE, errorList.get(0).type);
        List<DeformableMesh3D> fixed = tc.repairMesh();
        Assert.assertEquals(1, fixed.size());
        TopoCheck tc2 = new TopoCheck(fixed.get(0));
        List<TopologyValidationError> e2 = tc2.validate();
        Assert.assertEquals(0, e2.size());
        remesh(fixed.get(0));
    }

    /**
     * Can the topo repair fix a loop
     */
    @Test
    public void loopFaultTest(){
        ImagePlus plus = BinaryMeshGenerationTests.loopFault();
        List<DeformableMesh3D> meshes = BinaryMeshGenerator.generateVoxelMeshes(new MeshImageStack(plus));
        Assert.assertEquals(1, meshes.size());
        List<TopologyValidationError> errors = TopoCheck.validate(meshes.get(0));
        Assert.assertEquals(4, errors.size());
        TopoCheck tc = new TopoCheck(meshes.get(0));
        tc.repairMesh();
        Assert.assertEquals(0, tc.validate().size());
    }
    @Test
    public void openPinchFaultTest(){
        ImagePlus plus = BinaryMeshGenerationTests.openPinchFault();
        DeformableMesh3D mesh = BinaryMeshGenerationTests.getMeshes(plus).get(0);
        TopoCheck tc = new TopoCheck(mesh);
        List<TopologyValidationError> errorList = tc.validate();
        Assert.assertEquals(2, errorList.size());
        Assert.assertEquals(TopologyValidationError.INTERSECTING_SURFACE, errorList.get(0).type);
        Assert.assertEquals(TopologyValidationError.INTERSECTING_SURFACE, errorList.get(1).type);
        List<DeformableMesh3D> fixed = tc.repairMesh();
        Assert.assertEquals(1, fixed.size());
        TopoCheck tc2 = new TopoCheck(fixed.get(0));
        List<TopologyValidationError> e2 = tc2.validate();
        Assert.assertEquals(0, e2.size());
        remesh(fixed.get(0));
    }



    @Test
    public void kissFaultTest(){
        ImagePlus plus = BinaryMeshGenerationTests.kissFault();
        DeformableMesh3D mesh = BinaryMeshGenerationTests.getMeshes(plus).get(0);
        TopoCheck tc = new TopoCheck(mesh);
        List<TopologyValidationError> errorList = tc.validate();
        Assert.assertEquals(1, errorList.size());
        Assert.assertEquals(TopologyValidationError.INTERSECTING_SURFACE, errorList.get(0).type);
        List<DeformableMesh3D> fixed = tc.repairMesh();
        Assert.assertEquals(1, fixed.size());
        TopoCheck tc2 = new TopoCheck(fixed.get(0));
        List<TopologyValidationError> e2 = tc2.validate();
        Assert.assertEquals(0, e2.size());
        remesh(fixed.get(0));
    }

    /**
     * Can the topo repair fix a loop
     */
    @Test
    public void tailLoopFaultTest(){
        ImagePlus plus = BinaryMeshGenerationTests.tailLoopFault();
        List<DeformableMesh3D> meshes = BinaryMeshGenerator.generateVoxelMeshes(new MeshImageStack(plus));
        Assert.assertEquals(1, meshes.size());
        List<TopologyValidationError> errors = TopoCheck.validate(meshes.get(0));
        Assert.assertEquals(5, errors.size());
        TopoCheck tc = new TopoCheck(meshes.get(0));
        tc.repairMesh();
        Assert.assertEquals(0, tc.validate().size());
    }

    /**
     * Can the topo repair fix a loop
     */
    @Test
    public void doubleLoopFaultTest(){
        ImagePlus plus = BinaryMeshGenerationTests.doubleLoopFault();
        List<DeformableMesh3D> meshes = BinaryMeshGenerator.generateVoxelMeshes(new MeshImageStack(plus));
        Assert.assertEquals(1, meshes.size());
        List<TopologyValidationError> errors = TopoCheck.validate(meshes.get(0));
        Assert.assertEquals(7, errors.size());
        TopoCheck tc = new TopoCheck(meshes.get(0));
        tc.repairMesh();
        Assert.assertEquals(0, tc.validate().size());
    }
}
