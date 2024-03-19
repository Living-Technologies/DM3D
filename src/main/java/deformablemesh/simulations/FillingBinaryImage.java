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
package deformablemesh.simulations;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.interceptable.BinaryInterceptible;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.io.MeshReader;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.track.Track;
import ij.ImagePlus;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A small class for testing energies to make a mesh swell and fill a binary boundary.
 *
 * - This poses a couple of questions. Should the outward growing pressure turn off when the
 * mesh exceeds the boundaries of the cavity?
 * - Should the boundary apply a force, that will balance with the outward force.
 * - Should the cavity be a distance transform. Either on the outside, or the inside or both.
 *
 */
public class FillingBinaryImage {
    int frame;
    MeshImageStack stack;
    List<DeformableMesh3D> meshes;
    public FillingBinaryImage(MeshImageStack mis, List<DeformableMesh3D> meshes){
        stack = mis;
        this.meshes = meshes;
    }
    public void showDeformation(){
        MeshFrame3D frame = new MeshFrame3D();
        frame.showFrame(false);

        for(DeformableMesh3D mesh: meshes){
            mesh.create3DObject();
            frame.addDataObject(mesh.data_object);
        }



    }

    public void setFrame(int frame){
        this.frame = frame;
    }

    public static void main(String[] args) throws IOException {
        /*new ImageJ();
        ImagePlus plus = new ImagePlus(Paths.get(args[0]).toAbsolutePath().toString());
        DistanceTransformMosaicImage dtmi = new DistanceTransformMosaicImage(plus);
        dtmi.findBlobs();

        dtmi.createCascades();
        ImagePlus p2 = dtmi.createLabeledImage();
        p2.show();*/
        ImagePlus plus = new ImagePlus(Paths.get(args[0]).toAbsolutePath().toString());
        MeshImageStack mis = new MeshImageStack(plus);
        List<Track> tracks = MeshReader.loadMeshes(Paths.get(args[1]).toFile());
        for(int i = 0; i<mis.getNFrames(); i++){
            final int fi = i;
            List<DeformableMesh3D> meshes = tracks.stream().filter(
                    t -> t.containsKey(fi)
            ).map(
                    t->t.getMesh(fi)
            ).collect(Collectors.toList());
            FillingBinaryImage fbi = new FillingBinaryImage(mis, meshes);
            fbi.setFrame(fi);
            fbi.showDeformation();
        }
    }

    public static DeformableMesh3D fillBinaryWithMesh(MeshImageStack stack, List<int[]> points){
        double[] xyz = new double[3];

        for(int[] pt: points){
            xyz[0] += pt[0];
            xyz[1] += pt[1];
            xyz[2] += pt[2];
        }

        xyz[0] = xyz[0]/points.size();
        xyz[1] = xyz[1]/points.size();
        xyz[2] = xyz[2]/points.size();
        BinaryInterceptible bi = new BinaryInterceptible(points, stack, 1);

        double[] c = stack.getNormalizedCoordinate(xyz);
        double pv = stack.pixel_dimensions[0]*stack.pixel_dimensions[1]*stack.pixel_dimensions[2];
        double r = Math.cbrt(points.size()*pv*3.0/4/Math.PI)/stack.SCALE;
        //a = new NewtonMesh3D(RayCastMesh.rayCastMesh(sA, sA.getCenter(), 2));
        DeformableMesh3D mesh = RayCastMesh.rayCastMesh(bi, bi.getCenter(), 2);
        mesh.GAMMA = 1000;
        mesh.ALPHA = 1.0;
        mesh.BETA = 0.0;

        return mesh;

    }

    public static DeformableMesh3D fillBinaryWithMesh(ImagePlus plus, List<int[]> points){

        MeshImageStack stack = new MeshImageStack(plus);
        return fillBinaryWithMesh(stack, points);


    }


}


