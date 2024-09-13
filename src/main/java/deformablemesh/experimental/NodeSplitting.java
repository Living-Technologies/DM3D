package deformablemesh.experimental;

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
    public void split(Node3D node, List<Triangle3D> triangles){
        split.put(node, triangles);
        int adex = positions.size();
        double[] pos1 = node.getCoordinates();
        positions.add(pos1);
        for (Triangle3D tri : triangles) {
            int[] values = triIndexes.get(trianglesOld.indexOf(tri));
            replace(node, adex, values);
        }
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
