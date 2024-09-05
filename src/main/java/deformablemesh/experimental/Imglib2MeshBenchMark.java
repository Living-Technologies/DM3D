package deformablemesh.experimental;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Triangle3D;
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
class Imglib2MeshBenchMark {

    static List<Set<Integer>> connectedComponents(List<Triangle3D> triangles){
        List<Set<Integer>> ret = new ArrayList<>();
        List<Set<Integer>> adding = new ArrayList<>();

        for(Triangle3D t : triangles){

            Set<Integer> ts = new HashSet<>();
            ts.add(t.A.index);
            ts.add(t.B.index);
            ts.add(t.C.index);
            adding.clear();
            for(Set<Integer> group : ret){
                if( ts.stream().anyMatch(group::contains) ){
                    adding.add(group);
                }
            }
            if(adding.size() == 0){
                ret.add(ts);
            } else if(adding.size() == 1){
                adding.get(0).addAll(ts);
            } else{
                Set<Integer> a = adding.get(0);
                adding.stream().skip(1).forEach( s ->{
                            ret.remove(s);
                            a.addAll(s);
                        }
                );
            }


        }


        return ret;

    }

    static List<DeformableMesh3D> partition(DeformableMesh3D mesh, List<Set<Integer>> partitions){
        int n = partitions.size();

        List<DeformableMesh3D> meshes = new ArrayList<>();
        List<List<Triangle3D>> results = new ArrayList<>(n);
        List<Set<Imglib2Mesh.Con>> connections = new ArrayList<>();
        for(int i = 0; i<n; i++){
            results.add(new ArrayList<>());
            connections.add(new HashSet<>());
        }

        for(Triangle3D triangle: mesh.triangles){
            boolean found = false;
            for(int i = 0; i<n && !found; i++){
                if(partitions.get(i).contains(triangle.A.index)){
                    results.get(i).add(triangle);

                    int i0 = triangle.A.index;
                    int i1 = triangle.B.index;
                    int i2 = triangle.C.index;

                    connections.get(i).add(new Imglib2Mesh.Con(i0, i1));
                    connections.get(i).add(new Imglib2Mesh.Con(i1, i2));
                    connections.get(i).add(new Imglib2Mesh.Con(i2, i0));

                    found = true;
                }
            }
            if(!found){
                System.out.println("Broken!");
            }
        }
        int[] map = new int[mesh.triangle_index.length];
        for(int i = 0; i<n; i++){
            Set<Integer> nodes = partitions.get(i);
            double[] positions = new double[nodes.size()*3];
            int current = 0;
            for(Integer dex : nodes){
                map[dex] = current;
                System.arraycopy(mesh.getCoordinates(dex), 0, positions, 3*current, 3);
                current++;
            }

            List<Triangle3D> triangles = results.get(i);
            int[] tindexes = new int[triangles.size()*3];
            current = 0;
            for(Triangle3D triangle : triangles){
                tindexes[3*current] = map[triangle.A.index];
                tindexes[3*current + 1] = map[triangle.B.index];
                tindexes[3*current + 2] = map[triangle.C.index];
                current++;
            }
            Set<Imglib2Mesh.Con> cons = connections.get(i);
            int[] cindexes = new int[cons.size()*2];
            current = 0;
            for(Imglib2Mesh.Con con : cons){
                cindexes[current*2] = map[con.a];
                cindexes[current*2 + 1] = map[con.b];
                current++;
            }
            meshes.add(new DeformableMesh3D(positions, cindexes, tindexes));
        }

        return meshes;
    }

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
