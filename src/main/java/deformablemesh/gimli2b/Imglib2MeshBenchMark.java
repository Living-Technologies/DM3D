package deformablemesh.gimli2b;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Triangle3D;
import deformablemesh.gimli2b.Imglib2Mesh;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.util.ColorSuggestions;
import ij.ImagePlus;
import ij.plugin.FileInfoVirtualStack;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.mesh.Mesh;
import net.imglib2.mesh.alg.MarchingCubesRealType;
import net.imglib2.mesh.alg.MeshConnectedComponents;
import net.imglib2.mesh.alg.RemoveDuplicateVertices;
import net.imglib2.type.numeric.integer.UnsignedByteType;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class contains remnants of code I am not ready to throw away. It includes
 * some tests that were used to benchmarking.
 */
public class Imglib2MeshBenchMark {


    static public List<DeformableMesh3D> guessMeshesAll(MeshImageStack mis){
        ImagePlus plus = mis.getCurrentFrame();
        Img<UnsignedByteType> img = ImageJFunctions.wrap(plus);
        Mesh mesh = MarchingCubesRealType.calculate(img, 3);
        Imglib2Mesh.ImageSpaceTransformer ist = new Imglib2Mesh.ImageSpaceTransformer(mis);
        long start0 = System.currentTimeMillis();
        mesh = Imglib2Mesh.removeDuplicateVertices(mesh, ist);
        System.out.println("only duplicates: " + (System.currentTimeMillis() - start0));


        //DeformableMesh3D m2 = convertMesh(mesh, new ImageSpaceTransformer(mis));
        long start = System.currentTimeMillis();
        //List<DeformableMesh3D> m3 = partition(m2, connectedComponents(m2.triangles));
        List<DeformableMesh3D> m3 = new ArrayList<>();
        for(Mesh bm : MeshConnectedComponents.iterable(mesh)){
            DeformableMesh3D dm3d = Imglib2Mesh.convertMesh(bm, ist);
            if(dm3d.calculateVolume()>0){
                m3.add(dm3d);
            }
        }
        System.out.println("connected components: " + (System.currentTimeMillis() - start));
        return m3;
    }
    public static List<DeformableMesh3D> guessMeshes(MeshImageStack mis){
        Img<UnsignedByteType> img = ImageJFunctions.wrap(mis.getCurrentFrame());
        Mesh mesh = MarchingCubesRealType.calculate(img, 3);
        long start = System.currentTimeMillis();
        mesh = RemoveDuplicateVertices.calculate(mesh, 0);
        System.out.println("vertices removed: " + (System.currentTimeMillis() - start));
        Imglib2Mesh.ImageSpaceTransformer ist = new Imglib2Mesh.ImageSpaceTransformer(mis);


        List<DeformableMesh3D> meshes = new ArrayList<>();
        start = System.currentTimeMillis();
        for(Mesh cp : MeshConnectedComponents.iterable(mesh)){
            DeformableMesh3D dm3d = Imglib2Mesh.convertMesh(cp, ist);
            if(dm3d == null){
                continue;
            }
            meshes.add(dm3d);
        }
        System.out.println("Connected components: " + (System.currentTimeMillis() - start));
        return meshes;
    }
    public static void main(String[] args){

        ImagePlus plus = FileInfoVirtualStack.openVirtual(new File(args[0]).getAbsolutePath());
        List<DeformableMesh3D> meshes = guessMeshes(new MeshImageStack(plus));
        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.addLights();
        mf3d.setBackgroundColor(new Color(200, 200, 200));

        for(DeformableMesh3D dm3d: meshes){
            dm3d.create3DObject();
            dm3d.data_object.setWireColor(ColorSuggestions.getSuggestion());
            mf3d.addDataObject(dm3d.data_object);
        }
    }

}
