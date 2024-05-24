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

import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.externalenergies.*;
import deformablemesh.geometry.ConnectionRemesher;
import deformablemesh.geometry.interceptable.BinaryInterceptible;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.io.MeshReader;
import deformablemesh.meshview.CanvasView;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.meshview.VolumeDataObject;
import deformablemesh.track.Track;
import deformablemesh.util.ColorSuggestions;
import deformablemesh.util.connectedcomponents.Region;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.jogamp.java3d.utils.picking.PickResult;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
    static boolean spheres = true;
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
        ImagePlus plus = new ImagePlus(Paths.get("sample-mosaic.tif").toAbsolutePath().toString());
        //Calibration c = plus.getCalibration();
        //c.pixelDepth = 2.0;
        //c.pixelHeight = 0.35;
        //c.pixelWidth = 0.35;
        //plus.setCalibration(c);
        MeshImageStack mis = new MeshImageStack(plus);
        MeshDetector detector = new MeshDetector(mis);
        List<Region> regions = detector.getRegionsFromLabelledImage();

        System.out.println("meshing " + regions.size() + "regions.");

        MeshFrame3D frame = new MeshFrame3D();
        frame.showFrame(true);

        frame.setBackgroundColor(Color.BLACK);

        ImageStack blobs = new ImageStack(plus.getWidth(), plus.getHeight());

        for(int i = 1; i<=plus.getNSlices(); i++){
            blobs.addSlice(new ByteProcessor(blobs.getWidth(), blobs.getHeight()));
        }

        for(Region r: regions) {
            for (int[] px : r.getPoints()) {
                blobs.getProcessor(px[2] + 1).set(px[0], px[1], 1);
            }
        }



        ImagePlus p2 = plus.createImagePlus();
        p2.setStack(blobs);

        MeshImageStack mis2 = new MeshImageStack(p2);
        VolumeDataObject vdo = new VolumeDataObject(Color.WHITE);
        vdo.setTextureData(mis2);
        vdo.setMinMaxRange(0, 1);

        VolumeDataObject vdo2 = new VolumeDataObject(Color.RED);
        vdo2.setTextureData(mis);
        vdo2.setMinMaxRange(0, 1);
        //vdo2.setTransparencyTrim(0, 100);
        frame.addDataObject(vdo);
        frame.addDataObject(vdo2);

        List<DeformableMesh3D> meshes = regions.stream().map(r-> {
                DeformableMesh3D mesh = FillingBinaryImage.fillBinaryWithMesh(mis2, r.getPoints(), 0.007, 0.014);
                mesh.setColor(ColorSuggestions.getSuggestion());
                mesh.create3DObject();
                frame.addDataObject(mesh.data_object);

                return mesh;
            }
        ).collect(Collectors.toList());


        AtomicInteger flag = new AtomicInteger(0);
        new Thread(()->{
            try{
                while(!Thread.interrupted()){
                    if(flag.get() == 0){
                        synchronized (flag){
                            flag.wait();
                        }
                    } else{
                        for(int i = 0; i<50; i++) {
                            meshes.forEach(DeformableMesh3D::update);
                        }
                    }
                }
            } catch(InterruptedException x){
                return;
            }
        }).start();
        frame.addPickListener( new CanvasView(){


            @Override
            public void updatePressed(PickResult[] results, MouseEvent evt) {

            }

            @Override
            public void updateReleased(PickResult[] results, MouseEvent evt) {

            }

            @Override
            public void updateClicked(PickResult[] results, MouseEvent evt) {
                int i = flag.get();
                if(i == 0){
                    flag.set(1);
                    synchronized (flag) {
                        flag.notify();
                    }
                } else{
                    flag.set(0);
                }
            }

            @Override
            public void updateMoved(PickResult[] results, MouseEvent evt) {

            }

            @Override
            public void updateDragged(PickResult[] results, MouseEvent evt) {

            }

            public void check(){

                System.out.println("updated");
            }
        });
    }

    public static DeformableMesh3D fillBinaryWithMesh(MeshImageStack stack, List<int[]> points){
        return fillBinaryWithMesh(stack, points, 0.01, 0.02);
    }


    public static DeformableMesh3D fillBinaryWithMesh(MeshImageStack stack, List<int[]> points, double minl, double maxl){
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
        DeformableMesh3D mesh;

        if(spheres){
            double[] center = stack.getNormalizedCoordinate(xyz);
            mesh = RayCastMesh.sphereRayCastMesh(2);
            mesh.translate(c);
            mesh.scale(r, c);
        } else {
            mesh = RayCastMesh.rayCastMesh(bi, bi.getCenter(), 2);
        }



        int meshin = 5;
        for(int rm = 0; rm<meshin; rm++) {
            ConnectionRemesher remesher = new ConnectionRemesher();
            remesher.setMinAndMaxLengths(minl, maxl);
            DeformableMesh3D remeshed = remesher.remesh(mesh);

            remeshed.GAMMA = 1000;
            remeshed.ALPHA = 2.0;
            remeshed.BETA = 1.0;

            remeshed.addExternalEnergy(new BallooningEnergy(bi, remeshed, 1000));
            //remeshed.addExternalEnergy(new PerpendicularGradientEnergy(stack, remeshed, 1.0));
            //remeshed.addExternalEnergy(new PerpendicularIntensityEnergy(stack, remeshed, -1.0));
            int smoothingSteps = 100;
            for (int i = 0; i < smoothingSteps; i++) {
                remeshed.update();
            }
            mesh = remeshed;
        }

        return mesh;

    }

    public static DeformableMesh3D fillBinaryWithMesh(ImagePlus plus, List<int[]> points){

        MeshImageStack stack = new MeshImageStack(plus);
        return fillBinaryWithMesh(stack, points);


    }


}


