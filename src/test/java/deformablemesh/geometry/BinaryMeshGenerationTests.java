package deformablemesh.geometry;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.experimental.Imglib2MeshBenchMark;
import deformablemesh.geometry.topology.TopoCheck;
import deformablemesh.geometry.BinaryMeshGenerator;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.topology.TopologyValidationError;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.meshview.VolumeDataObject;
import deformablemesh.util.ColorSuggestions;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;
import java.util.List;

public class BinaryMeshGenerationTests {
    public static ImagePlus space(){
        ImagePlus plus = new ImagePlus();
        ImageStack stack = new ImageStack(16, 16);

        for(int i = 0; i<16; i++){
            stack.addSlice(new ByteProcessor(16, 16));

        }
        plus.setStack(stack, 1, 16, 1);
        return plus;
    }
    public static ImagePlus spot(){
        ImagePlus plus = space();
        ImageStack stack = plus.getStack();
        for(int i = 6; i<9; i++){
            ImageProcessor proc = stack.getProcessor(i);
            for(int j = 6; j<9; j++){
                for(int k = 6; k<9; k++){
                    proc.set(j, k, 1);
                }
            }
        }
        return plus;
    }

    public static ImagePlus blob(){
        ImagePlus plus = space();
        ImageStack stack = plus.getStack();
        for(int i = 6; i<9; i++){
            ImageProcessor proc = stack.getProcessor(i);
            for(int j = 6; j<9; j++){
                for(int k = 6; k<9; k++){
                    proc.set(j, k, 1);
                }
            }
        }
        stack.getProcessor(5).set(7, 7, 1);
        stack.getProcessor(9).set(   7, 7, 1);
        stack.getProcessor(7).set(5, 7, 1);
        stack.getProcessor(7).set(   9, 7, 1);
        stack.getProcessor(7).set(7, 5, 1);
        stack.getProcessor(7).set(   7, 9, 1);

        return plus;
    }

    public static ImagePlus j(){
        ImagePlus plus = space();
        ImageStack stack = plus.getStack();
        for(int i = 6; i<9; i++){
            ImageProcessor proc = stack.getProcessor(i);
            proc.set(7, 7, 1);
        }
        stack.getProcessor(8).set(6, 7, 1);
        stack.getProcessor(8).set(   5, 7, 1);

        return plus;
    }

    @Test
    public void jTest(){
        ImagePlus j = j();
        MeshImageStack mis = new MeshImageStack(j);
        List<DeformableMesh3D> meshes = BinaryMeshGenerator.generateVoxelMeshes(mis);
        Assert.assertEquals(1, meshes.size());
        DeformableMesh3D mesh = meshes.get(0);
        Assert.assertEquals(24, mesh.nodes.size());
        Assert.assertEquals(44, mesh.triangles.size());
        Assert.assertEquals(66, mesh.connections.size());
        double pxVolume = mesh.calculateVolume();
        Assert.assertEquals(mis.getNormalizedVolume(5), pxVolume, 1e-9);
        List<TopologyValidationError> errors = TopoCheck.validate(mesh);
        Assert.assertEquals(0, errors.size());
    }

    public static ImagePlus o(){
        ImagePlus plus = space();

        ImageStack stack = plus.getStack();
        for(int i = 0; i<3; i++){

            ImageProcessor proc = stack.getProcessor(i + 2);
            proc.set(1, 1, 1);
            proc.set(3, 1, 1);

            stack.getProcessor(8).set(8 + i, 8, 1);
            stack.getProcessor(   8).set(8 + i, 10, 1);

        }
        stack.getProcessor(8).set(8, 9, 1);
        stack.getProcessor(8).set(   10, 9, 1);

        stack.getProcessor(2).set(2, 1, 1);
        stack.getProcessor(4).set(   2, 1, 1);


        return plus;
    }

    @Test
    public void oTest(){
        ImagePlus o = o();
        MeshImageStack mis = new MeshImageStack(o);
        List<DeformableMesh3D> meshes = BinaryMeshGenerator.generateVoxelMeshes(mis);
        Assert.assertEquals(2, meshes.size());
        for(DeformableMesh3D mesh : meshes) {
            List<TopologyValidationError> errors = TopoCheck.validate(mesh);
            Assert.assertEquals(0, errors.size());
            Assert.assertEquals(32, mesh.nodes.size());
            Assert.assertEquals(64, mesh.triangles.size());
            Assert.assertEquals(96, mesh.connections.size());
            double pxVolume = mesh.calculateVolume();
            Assert.assertEquals(mis.getNormalizedVolume(8), pxVolume, 1e-9);


        }
    }

    public static ImagePlus shell(){
        ImagePlus plus = space();

        ImageStack stack = plus.getStack();
        for(int i = 6; i<10; i++){
            ImageProcessor proc = stack.getProcessor(i);
            for(int j = 6; j<10; j++){
                for(int k = 6; k<10; k++){
                    proc.set(j, k, 1);
                }
            }
        }

        for(int i = 7; i<9; i++){
            ImageProcessor proc = stack.getProcessor(i);
            for(int j = 7; j<9; j++){
                for(int k = 7; k<9; k++){
                    proc.set(j, k, 0);
                }
            }
        }

        return plus;
    }

    @Test
    public void shellTest(){
        ImagePlus o = shell();
        MeshImageStack mis = new MeshImageStack(o);
        List<DeformableMesh3D> meshes = BinaryMeshGenerator.generateVoxelMeshes(mis);
        Assert.assertEquals(1, meshes.size());
        List<DeformableMesh3D> innerOuter = Imglib2MeshBenchMark.connectedComponents(meshes.get(0));
        for(DeformableMesh3D mesh : innerOuter) {
            int nerrors, nodes, triangles, connections;
            double volume;
            if( mesh.calculateVolume() < 0 ){
                //smaller inner mesh.
                nerrors = 0;
                nodes = 26;
                triangles = 48;
                connections = 72;
                volume = -mis.getNormalizedVolume(8);
            } else{
                //larger outer mesh
                nerrors = 0;
                nodes = 98; //25*2 + 3*5*2 + 3*3*2;
                triangles = 192; //16*6*2
                connections = 96*3; //192 * 3 / 2
                volume = mis.getNormalizedVolume(4*4*4);
            }
            List<TopologyValidationError> errors = TopoCheck.validate(mesh);
            Assert.assertEquals(nerrors, errors.size());
            Assert.assertEquals(nodes, mesh.nodes.size());
            Assert.assertEquals(triangles, mesh.triangles.size());
            Assert.assertEquals(connections, mesh.connections.size());
            Assert.assertEquals(volume, mesh.calculateVolume(), 1e-9);
        }
    }

    public static List<DeformableMesh3D> getMeshes(ImagePlus plus){
        return BinaryMeshGenerator.generateVoxelMeshes(new MeshImageStack(plus));
    }

    public static ImagePlus singlePointFault(){
        ImagePlus plus = space();
        ImageStack stack = plus.getStack();
        stack.getProcessor(5).set(7, 7, 1);
        stack.getProcessor(5).set(8, 7, 1);
        stack.getProcessor(6).set(8, 7, 1);
        stack.getProcessor(6).set(8, 8, 1);
        stack.getProcessor(6).set(8, 9, 1);
        stack.getProcessor(6).set(7, 9, 1);
        stack.getProcessor(6).set(6, 9, 1);
        stack.getProcessor(6).set(6, 8, 1);

        return plus;
    }

    public static ImagePlus kissFault(){
        ImagePlus plus = space();
        ImageStack stack = plus.getStack();
        stack.getProcessor(5).set(6, 7, 1);
        stack.getProcessor(5).set(7, 7, 1);
        stack.getProcessor(5).set(8, 7, 1);
        stack.getProcessor(6).set(8, 7, 1);
        stack.getProcessor(6).set(8, 8, 1);
        stack.getProcessor(6).set(8, 9, 1);
        stack.getProcessor(6).set(7, 9, 1);
        stack.getProcessor(6).set(6, 9, 1);
        stack.getProcessor(6).set(6, 8, 1);

        return plus;
    }

    public static ImagePlus pinchFault(){
        ImagePlus plus = space();
        ImageStack stack = plus.getStack();
        stack.getProcessor(5).set(6, 7, 1);
        stack.getProcessor(5).set(7, 7, 1);
        stack.getProcessor(5).set(8, 7, 1);
        stack.getProcessor(6).set(6, 7, 1);
        stack.getProcessor(6).set(7, 8, 1);
        stack.getProcessor(6).set(8, 7, 1);
        stack.getProcessor(6).set(8, 8, 1);
        stack.getProcessor(6).set(6, 8, 1);

        return plus;
    }

    public static ImagePlus openPinchFault(){
        ImagePlus plus = space();
        ImageStack stack = plus.getStack();
        stack.getProcessor(5).set(6, 7, 1);
        stack.getProcessor(5).set(7, 7, 1);
        stack.getProcessor(5).set(8, 7, 1);
        stack.getProcessor(6).set(7, 8, 1);
        stack.getProcessor(6).set(8, 7, 1);
        stack.getProcessor(6).set(8, 8, 1);
        stack.getProcessor(6).set(6, 8, 1);

        return plus;
    }

    static void smooth(DeformableMesh3D mesh){
        mesh.ALPHA = 1;
        mesh.BETA = 0.5;
        mesh.GAMMA = 100;
        mesh.update();
    }

    interface PlusMaker{
        ImagePlus getPlus();
    }
    public static void main(String[] args){

        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.addLights();
        mf3d.setBackgroundColor(new Color(200, 200, 200));

        long start = System.currentTimeMillis();
        ImagePlus volume = o();
        List<DeformableMesh3D> meshes = getMeshes(volume);
        System.out.println(System.currentTimeMillis() - start);
        MeshImageStack mis = new MeshImageStack(volume);
        VolumeDataObject vdo = new VolumeDataObject(Color.WHITE);
        vdo.setTextureData(mis);
        mf3d.addDataObject(vdo);
        for(DeformableMesh3D dm3d : meshes){
            ImagePlus plus = DeformableMesh3DTools.createBinaryRepresentation(mis, dm3d);
            VolumeDataObject vdo2 = new VolumeDataObject(Color.ORANGE);
            vdo2.setTextureData(new MeshImageStack(plus));
            vdo2.setMinMaxRange(0, 1);
            vdo2.setTransparencyTrim(0, 0.0);
            mf3d.addDataObject(vdo2);
            TopoCheck checkers = new TopoCheck(dm3d);
            List<TopologyValidationError> errors = checkers.validate();
            if(errors.size()>0){
                System.out.println(errors);
                DeformableMesh3D dm3d2 = checkers.repairMesh().get(0);
                smooth(dm3d2);
                dm3d2.create3DObject();
                mf3d.addDataObject(dm3d2.data_object);
                System.out.println(TopoCheck.validate(dm3d2));
            }
            System.out.println("checked");
            Color c = ColorSuggestions.getSuggestion();
            if(dm3d.nodes.size() == 0){
                continue;
            }
            dm3d.setShowSurface(false);
            dm3d.create3DObject();
            dm3d.data_object.setShowSurface(true);
            dm3d.data_object.setColor(ColorSuggestions.addTransparency(c, 0.25f));
            dm3d.data_object.setWireColor(c);
            mf3d.addTransientObject(dm3d.data_object);

        }

    }

}
