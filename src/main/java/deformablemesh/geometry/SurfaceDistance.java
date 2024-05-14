package deformablemesh.geometry;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.io.MeshReader;
import deformablemesh.meshview.CanvasView;
import deformablemesh.meshview.LineDataObject;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.meshview.SphereDataObject;
import deformablemesh.track.Track;
import deformablemesh.util.Vector3DOps;
import org.jogamp.java3d.utils.picking.PickIntersection;
import org.jogamp.java3d.utils.picking.PickResult;
import org.jogamp.vecmath.Point3d;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class SurfaceDistance {
    DeformableMesh3D mesh;
    List<List<Node3D>> nodeMap;
    public Node3D findClosest(double[] xyz){
        double min = Double.MAX_VALUE;
        Node3D closest = null;
        for(Node3D node: mesh.nodes){
            double d = Vector3DOps.distance(node.getCoordinates(), xyz);
            if(d < min){
                min = d;
                closest = node;
            }
        }
        return closest;
    }
    public double findDistance(Node3D a, Node3D b){

        List<Node3D> path = DeformableMesh3DTools.findPath(nodeMap, a, b);
        double distance = 0;
        for(int i = 1; i<path.size(); i++){
            Node3D ai = path.get(i);
            Node3D bi = path.get(i - 1);
            distance += Vector3DOps.distance(ai.getCoordinates(), bi.getCoordinates());
        }
        return distance;
    }



    private void buildNodeMap(){
        nodeMap = new ArrayList<>(mesh.nodes.size());
        for(Node3D node: mesh.nodes){
            nodeMap.add(new ArrayList<>());
        }

        for(Connection3D connection: mesh.connections){
            nodeMap.get(connection.A.index).add(connection.B);
            nodeMap.get(connection.B.index).add(connection.A);
        }
    }

    public void setMesh(DeformableMesh3D mesh){
        this.mesh = mesh;
        buildNodeMap();
    }

}
