/*-
 * #%L
 * Triangulated surface for deforming in 3D.
 * %%
 * Copyright (C) 2013 - 2023 University College London
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package deformablemesh.geometry;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.experimental.Imglib2Mesh;
import deformablemesh.io.MeshWriter;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.track.Track;
import deformablemesh.util.ColorSuggestions;
import deformablemesh.util.Vector3DOps;
import deformablemesh.util.connectedcomponents.ConnectedComponents3D;
import deformablemesh.util.connectedcomponents.Region;
import deformablemesh.util.connectedcomponents.RegionGrowing;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.FileInfoVirtualStack;
import ij.process.ImageProcessor;
import net.imglib2.img.Img;
import net.imglib2.mesh.Mesh;
import net.imglib2.mesh.alg.MarchingCubesRealType;
import net.imglib2.mesh.alg.MeshConnectedComponents;
import net.imglib2.type.numeric.integer.UnsignedByteType;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Generates a mesh by creating a binary image, and meshing that at scale.
 */
public class BinaryMeshGenerator {
    public static DeformableMesh3D voxelMesh(List<int[]> points, MeshImageStack stack, int label){
        List<DeformableMesh3D> meshes = new ArrayList<>();

        for(int[] pt: points){
            List<DeformableMesh3D> voxel = new ArrayList<>();
            for(int i = 0; i<2; i++){
                if(stack.getValue(pt[0] + 2*i -1, pt[1], pt[2])!=label){
                    voxel.add(generateVoxelPlane(stack, pt, new double[]{2*i -1, 0, 0}));
                }
            }

            for(int j = 0; j<2; j++){
                if(stack.getValue(pt[0], pt[1] + 2*j - 1, pt[2])!=label){
                    voxel.add(generateVoxelPlane(stack, pt, new double[]{0, 2*j -1, 0}));
                }

            }

            for(int k = 0; k<2; k++){
                int zdex = pt[2] + 2*k - 1;
                if(zdex<0||zdex>=stack.getNSlices()||stack.getValue(pt[0], pt[1], zdex)!=label){
                    voxel.add(generateVoxelPlane(stack, pt, new double[]{0, 0, 2*k - 1}));
                }

            }
            System.out.println(voxel.size());
            if(voxel.size()>0){
                //DeformableMesh3D mesh = voxel.get(0);
                //for(int i = 1; i<voxel.size(); i++){
                //    mesh = mergeMeshes(mesh, voxel.get(i));
                //}
                //meshes.add(mesh);
                DeformableMesh3D mesh = DeformableMesh3DTools.mergeMeshes(voxel);

                meshes.add(mergeOverlappingVertexes(mesh));
            }
        }



        return mergeOverlappingVertexes(DeformableMesh3DTools.mergeMeshes(meshes));
    }

    static DeformableMesh3D mergeMeshes(DeformableMesh3D a, DeformableMesh3D b){

        if(!a.getBoundingBox().intersects(b.getBoundingBox())){
            return DeformableMesh3DTools.mergeMeshes(Arrays.asList(b));
        }
        List<Node3D> aNodes = a.nodes;
        List<Node3D> bNodes = b.nodes;
        Map<Integer, Integer> map = new HashMap<>();

        for(Node3D nb: bNodes){

            for(Node3D na: aNodes){
                double d = Vector3DOps.distance(na.getCoordinates(), nb.getCoordinates());
                if(d<1e-10){
                    map.put( nb.getIndex(), na.getIndex());
                }
            }
        }

        if(map.size()==0){
            return DeformableMesh3DTools.mergeMeshes(Arrays.asList(a,b));
        }

        int newNodes = bNodes.size() - map.size();

        double[] positions = new double[3*(newNodes) + a.positions.length];
        int[] connections = new int[b.connection_index.length + a.connection_index.length];
        int[] triangles = new int[b.triangle_index.length + a.triangle_index.length];

        System.arraycopy(a.positions, 0, positions, 0, a.positions.length);
        System.arraycopy(a.triangle_index, 0, triangles, 0, a.triangle_index.length);
        System.arraycopy(a.connection_index, 0, connections, 0, a.connection_index.length);

        int offset = aNodes.size();
        for(Node3D bnode: bNodes){
            if(!map.containsKey(bnode.index)){

                map.put(bnode.index, offset);
                double[] pos = bnode.getCoordinates();
                System.arraycopy(pos, 0, positions, 3*offset, 3);
                offset++;

            }
        }

        int toffset = a.triangle_index.length;
        for(int i = 0; i<b.triangle_index.length; i++){
            triangles[i+toffset] = map.get(b.triangle_index[i]);
        }
        int coffset = a.connection_index.length;
        for(int i = 0; i<b.connection_index.length; i++){
            connections[i+coffset] = map.get(b.connection_index[i]);
        }

        Set<DummyConnection> c3D = new HashSet<>();
        for(int i = 0; i<connections.length/2; i++){
            c3D.add(new DummyConnection(connections[2*i], connections[2*i+1]));
        }
        if(c3D.size()*2!=connections.length){
            //some connections were redundant.
            connections = new int[c3D.size()*2];
            int index = 0;
            for(DummyConnection d: c3D){
                connections[index++] = d.a;
                connections[index++] = d.b;
            }
        }

        return new DeformableMesh3D(positions, connections, triangles);



    }

    static class DummyConnection{
        final int a;
        final int b;
        DummyConnection(int a, int b){
            this.a = a;
            this.b = b;
        }
        @Override
        public int hashCode(){
            return a+b;
        }
        @Override
        public boolean equals(Object c){
            if(c instanceof DummyConnection){
                DummyConnection o = (DummyConnection)c;
                return o.a==a ?
                        o.b==b:
                        o.a==b && o.b==a;
            }
            return false;
        }
    }

    static public DeformableMesh3D generateVoxelPlane(MeshImageStack stack, int[] px, double[] normal) {
        double[] up = Vector3DOps.zhat;
        if(normal[2]!=0){
            up = Vector3DOps.xhat;
        }

        double[] lat = Vector3DOps.cross(up, normal);
        double[] nx = stack.scaleToNormalizedLength(lat);
        double[] ny = stack.scaleToNormalizedLength(up);
        double[] nc = stack.getNormalizedCoordinate(new double[]{px[0]*1.0 + 0.5, px[1]*1.0 + 0.5, px[2]*1.0 + 0.5});
        double[] offset = stack.scaleToNormalizedLength(normal);
        return getQuad(Vector3DOps.add( nc, offset, 0.5), nx, ny);

    }

    static public DeformableMesh3D getQuad(double[] origin, double[] nx, double[] ny){

        double[] positions = {
                origin[0] - 0.5*nx[0] - 0.5*ny[0], origin[1] - 0.5*nx[1] - 0.5*ny[1], origin[2] - 0.5*nx[2] - 0.5*ny[2],
                origin[0] + 0.5*nx[0] - 0.5*ny[0], origin[1] + 0.5*nx[1] - 0.5*ny[1], origin[2] + 0.5*nx[2] - 0.5*ny[2],
                origin[0] + 0.5*nx[0] + 0.5*ny[0], origin[1] + 0.5*nx[1] + 0.5*ny[1], origin[2] + 0.5*nx[2] + 0.5*ny[2],
                origin[0] - 0.5*nx[0] + 0.5*ny[0], origin[1] - 0.5*nx[1] + 0.5*ny[1], origin[2] - 0.5*nx[2] + 0.5*ny[2]
        };

        int[] connections = {
                0, 1,
                1, 2,
                2, 0,
                2, 3,
                3, 0
        };
        int[] triangles = {
                0, 1, 2,
                0, 2, 3
        };

        return new DeformableMesh3D(positions, connections, triangles);
    }
    static public DeformableMesh3D mergeOverlappingVertexes(DeformableMesh3D mesh){
        int[] map = new int[mesh.nodes.size()];
        List<double[]> added = new ArrayList<>();

        for(Node3D node: mesh.nodes){
            double[] x0 = node.getCoordinates();
            boolean found = false;
            for(int i = 0; i<added.size(); i++){
                double[] a = added.get(i);
                if( a[0] == x0[0] && a[1] == x0[1] && a[2] == x0[2]){
                    map[node.index] = i;
                    found = true;
                    break;
                }
            }
            if( !found ){
                map[node.index] = added.size();
                added.add(x0);
            }
        }

        double[] positions = new double[3*added.size()];
        int i = 0;
        for(double[] pt : added){
            positions[i++] = pt[0];
            positions[i++] = pt[1];
            positions[i++] = pt[2];
        }
        List<int[]> triangles = new ArrayList<>(mesh.triangles.size());
        for(Triangle3D t : mesh.triangles){
            int[] a = {map[t.A.index], map[t.B.index], map[t.C.index]};
            triangles.add(a);
        }
        return DeformableMesh3DTools.fromTriangles(positions, triangles);
    }

    public static DeformableMesh3D remesh(DeformableMesh3D mesh, MeshImageStack stack){
        ImagePlus binaryBlob = DeformableMesh3DTools.createBinaryRepresentation(stack, mesh);
        MeshImageStack binstack = new MeshImageStack(binaryBlob);
        long start = System.currentTimeMillis();
        List<int[]> points = getPoints(binaryBlob);
        System.out.println(System.currentTimeMillis() - start);
        return voxelMesh(points, binstack, 255);



    }

    private static List<int[]> getPoints(ImagePlus blob) {

        List<int[]> points = new ArrayList<>();
        int slices = blob.getNSlices();
        int w = blob.getWidth();
        int h = blob.getHeight();

        for(int i = 1; i<= slices; i++){
            ImageProcessor proc = blob.getStack().getProcessor(i);
            for(int j = 0; j<h; j++){
                for(int k = 0; k<w; k++){

                    if(proc.get(j*w +  k)!=0 && isEdge(k, j, i, blob.getStack())){
                        points.add(new int[]{k, j, i-1});
                    }


                }

            }

        }
        return points;
    }

    static boolean isEdge(int x, int y, int k, ImageStack stack){
        if ( x == 0 || x + 1 == stack.getWidth() || y == 0 || y+1 == stack.getHeight() || k == 1 || k == stack.getSize()){
            return true;
        }
        return
                stack.getProcessor(k + -1).get(x + 1, y + -1) == 0 ||
                stack.getProcessor(k + -1).get(x + 1, y + 0) == 0 ||
                stack.getProcessor(k + -1).get(x + 1, y + 1) == 0 ||
                stack.getProcessor(k + -1).get(x + 0, y + -1) == 0 ||
                stack.getProcessor(k + -1).get(x + 0, y + 0) == 0 ||
                stack.getProcessor(k + -1).get(x + 0, y + 1) == 0 ||
                stack.getProcessor(k + -1).get(x + -1, y + -1) == 0 ||
                stack.getProcessor(k + -1).get(x + -1, y + 0) == 0 ||
                stack.getProcessor(k + -1).get(x + -1, y + 1) == 0 ||
                stack.getProcessor(k + 1).get(x + 1, y + -1) == 0 ||
                stack.getProcessor(k + 1).get(x + 1, y + 0) == 0 ||
                stack.getProcessor(k + 1).get(x + 1, y + 1) == 0 ||
                stack.getProcessor(k + 1).get(x + 0, y + -1) == 0 ||
                stack.getProcessor(k + 1).get(x + 0, y + 0) == 0 ||
                stack.getProcessor(k + 1).get(x + 0, y + 1) == 0 ||
                stack.getProcessor(k + 1).get(x + -1, y + -1) == 0 ||
                stack.getProcessor(k + 1).get(x + -1, y + 0) == 0 ||
                stack.getProcessor(k + 1).get(x + -1, y + 1) == 0 ||
                stack.getProcessor(k + 0).get(x + 1, y + -1) == 0 ||
                stack.getProcessor(k + 0).get(x + 1, y + 0) == 0 ||
                stack.getProcessor(k + 0).get(x + 1, y + 1) == 0 ||
                stack.getProcessor(k + 0).get(x + 0, y + -1) == 0 ||
                stack.getProcessor(k + 0).get(x + 0, y + 1) == 0 ||
                stack.getProcessor(k + 0).get(x + -1, y + -1) == 0 ||
                stack.getProcessor(k + 0).get(x + -1, y + 0) == 0 ||
                stack.getProcessor(k + 0).get(x + -1, y + 1) == 0;
    }

    static boolean isEdgeLoop(int x, int y, int k, ImageStack stack){
        if ( x == 0 || x + 1 == stack.getWidth() || y == 0 || y+1 == stack.getHeight() || k == 1 || k == stack.getSize()){
            return true;
        }
        for(int dz = -1; dz<=1; dz++){
            ImageProcessor p = stack.getProcessor(dz + k);
            for(int dy = -1; dy<=1; dy++ ){
                for(int dx = -1; dx<=1; dx++){
                    if(p.get(dx + x, dy + y)==0){
                        return true;
                    }
                }
            }
        }
        return false;
    }
    public static List<DeformableMesh3D> predictMeshes(MeshImageStack mis){
        ImagePlus frame = mis.getCurrentFrame();

        ImageStack old = frame.getStack();
        ImageStack stack = new ImageStack(frame.getWidth(), frame.getHeight());

        List<DeformableMesh3D> meshes = new ArrayList<>();
        for(int j = 1; j<old.size(); j++){
            ImageProcessor p = old.getProcessor(j).convertToShort(false).duplicate();
            p.threshold(3);
            stack.addSlice(p);
        }
        List<Region> regions = ConnectedComponents3D.getRegions(stack);

        ImageStack space = new ImageStack(frame.getWidth(), frame.getHeight());
        for(int j = 1; j<old.size(); j++){
            ImageProcessor p = old.getProcessor(j).convertToShort(false).duplicate();
            p.threshold(0);
            space.addSlice(p);
        }

        RegionGrowing rg = new RegionGrowing(stack, space);
        rg.setRegions(regions);

        while(rg.getFrontierSize()>0){
            rg.step();
        }

        //Removes topological errors that cannot be handled.
        rg.erode();
        rg.dilate();
        ImagePlus regionPlus = mis.getOriginalPlus().createImagePlus();
        regionPlus.setStack(stack);
        MeshImageStack regionStack = new MeshImageStack(regionPlus);
        for(Region r: regions){
            r.validate();

            DeformableMesh3D mesh = voxelMesh(r.getPoints(), mis, r.getLabel());
            meshes.add(mesh);

        }

        return meshes;
    }
    public static void main(String[] args){
        new ImageJ();
        ImagePlus plus = FileInfoVirtualStack.openVirtual(new File(args[0]).getAbsolutePath());
        MeshImageStack mis = new MeshImageStack(plus);

        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.addLights();
        mf3d.setBackgroundColor(new Color(200, 200, 200));
        List<DeformableMesh3D> broken = new ArrayList<>();


        for(int i = 0; i < 2; i++){
            mis.setFrame(i);
            long start = System.currentTimeMillis();
            List<DeformableMesh3D> meshes = predictMeshes(mis);
            mf3d.clearTransients();
            System.out.println(System.currentTimeMillis() - start);

            for(DeformableMesh3D dm3d : meshes){
                Color c = ColorSuggestions.getSuggestion();

                dm3d.setShowSurface(false);
                dm3d.create3DObject();
                dm3d.data_object.setShowSurface(true);
                dm3d.data_object.setColor(c);
                dm3d.data_object.setWireColor(c);
                mf3d.addTransientObject(dm3d.data_object);

            }

        }
    }
}
