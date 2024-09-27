package deformablemesh.geometry.topology;

import deformablemesh.geometry.Connection3D;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Node3D;
import deformablemesh.geometry.Triangle3D;

import java.util.*;
import java.util.stream.Collectors;

public class NodeSplitting {
    List<double[]> positions;
    List<int[]> triIndexes;
    List<Triangle3D> trianglesOld;
    Map<Node3D, List<Triangle3D>> split = new HashMap<>();
    public NodeSplitting(DeformableMesh3D mesh){
        positions = mesh.nodes.stream().map(Node3D::getCoordinates).collect(Collectors.toCollection(ArrayList::new));;
        trianglesOld = new ArrayList<>(mesh.triangles);
        triIndexes = trianglesOld.stream().map(Triangle3D::getIndices).collect(Collectors.toList());
    }
    public boolean wasSplit(Node3D node){
        return split.containsKey(node);
    }
    void split(Node3D node, List<Triangle3D> triangles){
        if(triangles.size() == 0){
            System.out.println("wtf");
        }
        split.put(node, triangles);
        int adex = positions.size();
        double[] pos1 = node.getCoordinates();
        positions.add(pos1);
        for (Triangle3D tri : triangles) {
            int[] values = triIndexes.get(trianglesOld.indexOf(tri));
            replace(node, adex, values);
        }
    }

    public void split(Node3D node, Node3D neighbor, List<List<Triangle3D>> partitions){
        if(wasSplit(node)){
            throw new RuntimeException("Resplitting is not allow!");
        }
        if(wasSplit(neighbor)){
            List<Triangle3D> separated = split.get(neighbor);
            List<Triangle3D> next = regroupTriangles(separated, partitions);
            if(next.size() == 0) return;
            split(node, next);
        } else{
            //free to choose!
            List<Triangle3D> next = arbitraryGroup(partitions);
            split(node, next);
        }
    }
    List<Triangle3D> arbitraryGroup(List<List<Triangle3D>> partitions){

        if(partitions.size() == 2){
            return partitions.get(0);
        }
        throw new RuntimeException("Working on it!!! NodeSplitting.class");
    }
    /**
     * The changed triangles will border a section of the triangles that need to be changed.
     * it is important that the remapped triangles belong to the same section.
     *
     * @param changed
     * @param toChange
     * @return
     */
    List<Triangle3D> regroupTriangles(List<Triangle3D> changed, List<List<Triangle3D>> toChange){
        List<Triangle3D> grouped = new ArrayList<>();
        List<Triangle3D> leftover = new ArrayList<>();
        List<Triangle3D> intersection = new ArrayList<>();
        for(List<Triangle3D> incomplete : toChange){
            List<Triangle3D> inter = incomplete.stream().filter(changed::contains).collect(Collectors.toList());
            if(inter.size() > 0){
                grouped.addAll(incomplete);
            } else{
                leftover.addAll(incomplete);
            }
            intersection.addAll(inter);

        }
        if (grouped.size() == 0) {
            //How do these have no intersection!
            System.out.println("broken!");
        }
        return grouped;
    }

    void replace(Node3D old, int next, int[] dest){
        int i = old.index;
        for(int dex = 0; dex<3; dex++){
            if( dest[dex] == i){
                dest[dex] = next;
                return;
            }
        }
        throw new RuntimeException("node " + old.index + " not found! " + Arrays.toString(dest));
    }



}
