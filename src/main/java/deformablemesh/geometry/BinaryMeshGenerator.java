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
import deformablemesh.geometry.topology.TopoCheck;
import deformablemesh.geometry.topology.TopologyValidationError;
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
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a mesh by creating a binary image, and meshing that at scale.
 */
public class BinaryMeshGenerator {
    static double tolerance = 1e-9;
    public static DeformableMesh3D voxelMesh(List<int[]> points, MeshImageStack stack, int label){
        List<DeformableMesh3D> meshes = new ArrayList<>();
        int w = stack.getWidthPx();
        int h = stack.getHeightPx();
        int d = stack.getNSlices();
        double[] a = stack.getNormalizedCoordinate(new double[]{1, 0, 0});
        double[] b = stack.getNormalizedCoordinate(new double[]{0, 0, 0});
        double nominalDistance = Vector3DOps.distance(a, b);
        if(nominalDistance*nominalDistance < tolerance){
            System.out.println("pixel difference is smaller than tolerance");
        }
        for(int[] pt: points){
            List<DeformableMesh3D> voxel = new ArrayList<>();
            for(int i = 0; i<2; i++){
                int xi = pt[0] + 2*i - 1;
                if(xi == w || xi < 0 || stack.getValue(xi, pt[1], pt[2]) != label){
                    voxel.add(generateVoxelPlane(stack, pt, new double[]{2*i -1, 0, 0}));
                }
            }

            for(int j = 0; j<2; j++){
                int yi = pt[1] + 2*j - 1;

                if(yi == h || yi < 0 || stack.getValue(pt[0], yi, pt[2]) != label){
                    voxel.add(generateVoxelPlane(stack, pt, new double[]{0, 2*j -1, 0}));
                }

            }

            for(int k = 0; k<2; k++){
                int zdex = pt[2] + 2*k - 1;
                if( zdex<0 || zdex == d || stack.getValue(pt[0], pt[1], zdex) != label){
                    voxel.add(generateVoxelPlane(stack, pt, new double[]{0, 0, 2*k - 1}));
                }

            }
            if(voxel.size()>0){

                DeformableMesh3D mesh = DeformableMesh3DTools.mergeMeshes(voxel);
                DeformableMesh3D merged = mergeOverlappingVertexes(mesh);
                meshes.add(merged);

            }
        }


        DeformableMesh3D combined = DeformableMesh3DTools.mergeMeshes(meshes);
        DeformableMesh3D merged = mergeOverlappingVertexes(combined);
        if(Math.sqrt(Math.pow(merged.calculateVolume() - combined.calculateVolume(), 2)) > tolerance){
            throw new RuntimeException("voxel mesh accuracy compromised.");
        }
        //System.out.println(merged.calculateVolume() + " // " + combined.calculateVolume());
        return merged;
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
                origin[0] - 0.5*nx[0] - 0.5*ny[0],
                origin[1] - 0.5*nx[1] - 0.5*ny[1],
                origin[2] - 0.5*nx[2] - 0.5*ny[2],

                origin[0] + 0.5*nx[0] - 0.5*ny[0],
                origin[1] + 0.5*nx[1] - 0.5*ny[1],
                origin[2] + 0.5*nx[2] - 0.5*ny[2],

                origin[0] + 0.5*nx[0] + 0.5*ny[0],
                origin[1] + 0.5*nx[1] + 0.5*ny[1],
                origin[2] + 0.5*nx[2] + 0.5*ny[2],

                origin[0] - 0.5*nx[0] + 0.5*ny[0],
                origin[1] - 0.5*nx[1] + 0.5*ny[1],
                origin[2] - 0.5*nx[2] + 0.5*ny[2]
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
    static boolean closeEnough(double[] a, double[] b){
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return dx*dx + dy*dy + dz*dz < tolerance;
    }
    static public DeformableMesh3D mergeOverlappingVertexes(DeformableMesh3D mesh){
        int[] map = new int[mesh.nodes.size()];
        List<double[]> added = new ArrayList<>();

        for(Node3D node: mesh.nodes){
            double[] x0 = node.getCoordinates();
            boolean found = false;
            for(int i = 0; i<added.size(); i++){
                double[] a = added.get(i);
                if( closeEnough(a, x0)){
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

    /**
     * This assumes a binary image that needs to be labelled with connected
     * components then meshed with an exact mesh, not a prediction.
     *
     * @param mis
     * @return
     */
    public static List<DeformableMesh3D> generateVoxelMeshes(MeshImageStack mis){
        ImagePlus frame = mis.getCurrentFrame();
        ImageStack old = frame.getStack();
        ImageStack stack = new ImageStack(frame.getWidth(), frame.getHeight());
        List<DeformableMesh3D> meshes = new ArrayList<>();
        for(int j = 1; j<=old.size(); j++){
            ImageProcessor p = old.getProcessor(j).convertToShort(false).duplicate();
            p.threshold(0);
            stack.addSlice(p);
        }
        List<Region> regions = ConnectedComponents3D.getRegions(stack);

        ImagePlus regionPlus = mis.getOriginalPlus().createImagePlus();
        regionPlus.setStack(stack);

        MeshImageStack labeledMis = new MeshImageStack(regionPlus);
        for(Region r: regions){
            DeformableMesh3D mesh = voxelMesh(r.getPoints(), labeledMis, r.getLabel());
            meshes.add(mesh);
        }
        return meshes;
    }

    /**
     * This assumes that the mis contains a distance transform. It will perform a
     * threshold, connected components, watershed, the erode/dilate.
     *
     * Then it will attempt to repair the generated meshes.
     *
     * @param mis distance transform
     * @return List of meshes that were found within the image.
     */
    public static List<DeformableMesh3D> predictMeshes(MeshImageStack mis){
        ImagePlus frame = mis.getCurrentFrame();

        ImageStack old = frame.getStack();
        ImageStack stack = new ImageStack(frame.getWidth(), frame.getHeight());

        List<DeformableMesh3D> meshes = new ArrayList<>();
        for(int j = 1; j<=old.size(); j++){
            ImageProcessor p = old.getProcessor(j).convertToShort(false).duplicate();
            p.threshold(0);
            stack.addSlice(p);
        }
        List<Region> regions = ConnectedComponents3D.getRegions(stack);

        ImageStack space = new ImageStack(frame.getWidth(), frame.getHeight());
        for(int j = 1; j<=old.size(); j++){
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
        //rg.erode();
        //rg.dilate();

        ImagePlus regionPlus = mis.getOriginalPlus().createImagePlus();
        regionPlus.setStack(stack);
        MeshImageStack regionStack = new MeshImageStack(regionPlus);
        for(Region r: regions){
            r.validate();

            DeformableMesh3D mesh = voxelMesh(r.getPoints(), regionStack, r.getLabel());

            try {
                TopoCheck checkers = new TopoCheck(mesh);
                List<DeformableMesh3D> checkedMeshes = checkers.repairMesh();

                for(DeformableMesh3D checked : checkedMeshes) {
                    List<TopologyValidationError> errors = TopoCheck.validate(checked);
                    if(errors.size() > 0){
                        meshes.add(mesh);
                        break;
                    }
                    meshes.add(checked);
                }
            } catch(Exception e){
                meshes.add(mesh);
                e.printStackTrace();
            }


        }

        return meshes;
    }
    public static void main(String[] args) throws IOException {
        new ImageJ();
        ImagePlus plus = FileInfoVirtualStack.openVirtual(new File(args[0]).getAbsolutePath());
        //ImagePlus plus = ImageJFunctions.wrap(MCBroken.image(), "3x3x3-blob");
        //plus.setDimensions(1, 9, 1);
        MeshImageStack mis = new MeshImageStack(plus);

        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.addLights();
        mf3d.setBackgroundColor(new Color(200, 200, 200));
        List<Track> broken = new ArrayList<>();

        ImageStack stack = null;
        for(int i = 0; i < 1; i++){
            mis.setFrame(i);
            long start = System.currentTimeMillis();
            System.out.println("meshing");
            List<DeformableMesh3D> meshes = predictMeshes(mis);
            System.out.println(System.currentTimeMillis() - start + " meshed");
            start = System.currentTimeMillis();
            System.out.println("smoothing");
            List<DeformableMesh3D> smoothed = new ArrayList<>(meshes.size());
            for(DeformableMesh3D mesh: meshes){
                List<TopologyValidationError> err = TopoCheck.validate(mesh);
                if(err.size() > 0){
                    Track t = new Track("red- " + err.size() + " " + err.stream().map(Object::toString).collect(Collectors.joining("-")));
                    t.addMesh(i, mesh);
                    broken.add(t);
                } else{
                    try{
                        ConnectionRemesher remesher = new ConnectionRemesher();
                        remesher.setMinAndMaxLengths(0.005, 0.01);
                        DeformableMesh3D m2 = remesher.remesh(mesh);
                        smoothed.add(m2);
                    } catch(Exception e){
                        Track t = new Track("blue-" + e.getMessage());
                        t.addMesh(i, mesh);
                        broken.add(t);
                    }
                }
            }
            mf3d.clearTransients();
            System.out.println(System.currentTimeMillis() - start + " smoothed");


            smoothed.addAll(broken.stream().map(t->t.getMesh(t.getFirstFrame())).collect(Collectors.toList()));
            for(DeformableMesh3D dm3d : smoothed){
                if(dm3d.nodes.size() == 0){
                    continue;
                }
                Color c = ColorSuggestions.getSuggestion();

                dm3d.setShowSurface(false);
                dm3d.create3DObject();
                dm3d.data_object.setShowSurface(true);
                dm3d.data_object.setColor(c);
                dm3d.data_object.setWireColor(c);
                mf3d.addTransientObject(dm3d.data_object);

            }
            ImageProcessor proc = new ColorProcessor(mf3d.snapShot());
            if(stack == null){
                stack = new ImageStack(proc.getWidth(), proc.getHeight());
            }
            stack.addSlice(proc);

        }

        if(broken.size() > 0) {
            System.out.println("saving: " + broken.size()  + " broken meshes");
            MeshWriter.saveMeshes(new File("voxel-mesh-errors.bmf"), broken);
        } else{
            System.out.println("no broken meshes!");
        }

        new ImagePlus("Snapshots", stack).show();
    }
}
