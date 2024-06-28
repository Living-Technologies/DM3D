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
package deformablemesh;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import deformablemesh.geometry.*;
import deformablemesh.simulations.FillingBinaryImage;
import deformablemesh.util.connectedcomponents.ConnectedComponents3D;
import deformablemesh.util.connectedcomponents.Region;
import deformablemesh.util.connectedcomponents.RegionGrowing;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MeshDetector {
    List<Box3D> current = new ArrayList<>();
    MeshImageStack mis;
    int minSize = 50;
    int maxSize = Integer.MAX_VALUE;
    public boolean spheres = false;
    public double max_overlap = 0.3;
    int thresholdLevel = -1;
    int nextLevel = 0;
    public static int MAX_LABELLED_REGIONS = 100000;
    double minL = -1;
    double maxL = -1;

    public MeshDetector(MeshImageStack mis){
        this.mis = mis;
    }

    public void addRegionsToAvoid(List<Box3D> regions){
        current.addAll(regions);
    }
    List<DeformableMesh3D> guessMeshes(){

        List<Region> regions = getRegionsFromThreshold(thresholdLevel);
        regions = filterRegions(regions);

        growRegions(2, regions);

        List<DeformableMesh3D> guessed;
        if(spheres){
            guessed = ellipses(regions);
        } else{
            guessed = fillBinaryBlobs(regions);
        }

        return guessed;
    }

    void growRegions(int steps, List<Region> regions){

        ImageStack currentFrame = mis.getCurrentFrame().getStack();
        int w = currentFrame.getWidth();
        int h = currentFrame.getHeight();

        ImageStack growing = new ImageStack(currentFrame.getWidth(), currentFrame.getHeight());
        ImageStack threshed = new ImageStack(currentFrame.getWidth(), currentFrame.getHeight());

        for(int i = 1; i<= currentFrame.size(); i++){

            ImageProcessor proc = currentFrame.getProcessor(i).convertToShort(false);
            proc.threshold(nextLevel);
            growing.addSlice(proc);

            ImageProcessor ip = new ShortProcessor(w, h);
            short[] pixels = (short[])ip.getPixels();
            for(Region r: regions){
                short l = (short)r.getLabel();
                for(int[] px: r.getPoints()){
                    pixels[px[0] + px[1]*w] = l;
                }
            }
            threshed.addSlice(ip);
        }

        RegionGrowing rg = new RegionGrowing(threshed, growing);

        rg.setRegions(regions);
        for(int st = 0; st<steps; st++){
            rg.step();
        }

    }
    List<Region> filterRegions(List<Region> regions){
        int small = 0;
        int large = 0;
        //filter regions.
        List<Region> filtered = new ArrayList<>();
        for (Region region : regions) {
            Integer key = region.getLabel();
            List<int[]> points = region.getPoints();

            if (points.size() < minSize) {
                small++;
            } else if(points.size() > maxSize){
                large++;
            } else {
                double[] rmin = mis.getNormalizedCoordinate(region.getLowCorner());
                double[] rmax = mis.getNormalizedCoordinate(region.getHighCorner());

                Box3D candidate = new Box3D(rmin[0], rmin[1], rmin[2], rmax[0], rmax[1], rmax[2]);
                double cv = candidate.getVolume();
                boolean obstructed = false;
                for(Box3D box: current){
                    Box3D intersection = box.getIntersectingBox(candidate);
                    double bv = box.getVolume();
                    double iv = intersection.getVolume();
                    if((iv/cv > max_overlap) || (iv/bv > max_overlap)){
                        obstructed = true;
                        break;
                    }
                }
                if(!obstructed){
                    filtered.add(region);
                }
            }


        }
        return filtered;
    }
    List<Region> getRegionsFromThreshold(int level){
        long start, end;
        start = System.currentTimeMillis();
        ImageStack currentFrame = mis.getCurrentFrame().getStack();
        ImageStack threshed = new ImageStack(currentFrame.getWidth(), currentFrame.getHeight());
        for(int i = 1; i<= currentFrame.size(); i++){
            ImageProcessor proc = currentFrame.getProcessor(i).convertToShort(false);
            proc.threshold(level);
            threshed.addSlice(proc);
        }
        end = System.currentTimeMillis();
        System.out.println("prepared binary image: " + (end - start)/1000);
        start = System.currentTimeMillis();
        List<Region> regions = ConnectedComponents3D.getRegions(threshed);
        end = System.currentTimeMillis();
        System.out.println(regions.size() + " regions detected in " + (end - start)/1000);
        return regions;
    }
    @FunctionalInterface
    private interface BackgroundCheck{
        boolean isBackground(int i);
    }

    public List<Region> getRegionsFromLabelledImage(){
        Map<Integer, List<int[]>> pxRegions = new HashMap<>();
        ImagePlus plus = mis.getCurrentFrame();
        ImageStack stack = plus.getStack();
        BackgroundCheck checker;
        if(stack.getProcessor(1) instanceof ColorProcessor){
            System.out.print("Ignoring alpha channel");
            checker = i-> (i & 0xffffff) == 0;
        } else{
            checker = i -> i == 0;
        }
        for(int z = 0; z<mis.getNSlices(); z++){
            ImageProcessor proc = stack.getProcessor(z+1);
            for(int i = 0; i<mis.getWidthPx(); i++){
                for(int j = 0; j<mis.getHeightPx(); j++){
                    int px2 = proc.get(i, j);
                    if(!checker.isBackground(px2)){
                        pxRegions.computeIfAbsent(px2, p -> new ArrayList<>()).add(new int[]{i, j, z});
                    }
                }
            }
            if(pxRegions.size() > MAX_LABELLED_REGIONS){
                throw new RuntimeException("Too many unique labelled regions!");
            }
        }
        List<Region> regions = new ArrayList<>();
        for(Integer label: pxRegions.keySet()){
            Region r = new Region(label, pxRegions.get(label));
            List<Region> split = r.split();
            for(Region sr: split){
                if(sr.getPoints().size() > 2){
                    regions.add(sr);
                    if(regions.size() > MAX_LABELLED_REGIONS){
                        throw new RuntimeException(
                                "Too many labelled regions. Change MAX_LABELLED_REGIONS" +
                                "if this is incorrect.");
                    }
                }
            }

        }
        return regions;
    }

    List<DeformableMesh3D> spheres(List<Region> regions){
        List<DeformableMesh3D> meshes = new ArrayList<>(regions.size());

        for(Region region : regions){

            meshes.add(createSphere(region, mis));
        }
        return meshes;
    }
    public List<DeformableMesh3D> ellipses(List<Region> regions){
        List<DeformableMesh3D> meshes = new ArrayList<>(regions.size());

        for(Region region : regions){

            meshes.add(createEllipse(region, mis));
        }
        return meshes;
    }


    static DeformableMesh3D createSphere(Region region, MeshImageStack mis){
        double[] center = mis.getNormalizedCoordinate(region.getCenter());

        double volume = mis.getNormalizedVolume(region.calculateVolume());
        double r = Math.cbrt(volume*3/Math.PI/4);
        DeformableMesh3D m = RayCastMesh.sphereRayCastMesh(2);
        m.translate(center);
        m.scale(r, center);
        return m;
    }
    public static DeformableMesh3D createEllipse(Region region, MeshImageStack mis){
        double[] aves = {0, 0, 0};
        int n = region.getPoints().size();
        List<double[]> points = region.getPoints().stream().map(
                pt->mis.getNormalizedCoordinate(new double[]{pt[0], pt[1], pt[2]})
        ).collect(Collectors.toList());
        for(double[] pt: points){
            aves[0] += pt[0];
            aves[1] += pt[1];
            aves[2] += pt[2];
        }
        aves[0] = aves[0] / n;
        aves[1] = aves[1] / n;
        aves[2] = aves[2] / n;

        double[][] covar = new double[3][3];
        for(double[] pt: points){
            double dx = pt[0] - aves[0];
            double dy = pt[1] - aves[1];
            double dz = pt[2] - aves[2];

            covar[0][0] += dx*dx;
            covar[0][1] += dx*dy;
            covar[0][2] += dx*dz;

            covar[1][0] += dy*dx;
            covar[1][1] += dy*dy;
            covar[1][2] += dy*dz;

            covar[2][0] += dz*dx;
            covar[2][1] += dz*dy;
            covar[2][2] += dz*dz;

        }
        Matrix m = new Matrix(covar);
        EigenvalueDecomposition evd = new EigenvalueDecomposition(m);
        Matrix v = evd.getV();
        Matrix d = evd.getD();

        double lx = Math.sqrt(d.get(0, 0)*15/4/n);
        double ly = Math.sqrt(d.get(1, 1)*15/4/n);
        double lz = Math.sqrt(d.get(2, 2)*15/4/n);

        DeformableMesh3D mesh = ellipsoidMesh(lx, ly, lz);

        Matrix vector = new Matrix(3, 1);
        for(Node3D node: mesh.nodes){
            double[] c = node.getCoordinates();
            vector.set(0, 0, c[0]);
            vector.set( 1, 0, c[1]);
            vector.set( 2, 0, c[2]);

            Matrix si =  v.times(vector);

            c[0] = si.get(0, 0);
            c[1] = si.get(1, 0);
            c[2] = si.get( 2, 0);
            node.setPosition(c);
        }
        mesh.translate(aves);
        return mesh;
    }

    /**
     * Creates an elliptical mesh with the semi major axis provided.
     * @param lx
     * @param ly
     * @param lz
     * @return
     */
    static DeformableMesh3D ellipsoidMesh(double lx, double ly, double lz){
        DeformableMesh3D mesh = RayCastMesh.sphereRayCastMesh(2);
        mapEllipse(mesh, lx, ly, lz);
        ConnectionRemesher remesher = new ConnectionRemesher();

        double mstar = Math.sqrt( 15 * Math.pow( Math.cbrt(Math.PI*lx*ly*lz*4/3), 2) / 250 );
        remesher.setMinAndMaxLengths(2*mstar/3, 4*mstar/3);
        mesh = remesher.remesh(mesh);
        mapEllipse(mesh, lx, ly, lz);
        return mesh;
    }

    private static void mapEllipse(DeformableMesh3D mesh, double lx, double ly, double lz){
        for(Node3D node: mesh.nodes){
            double[] ray = node.getCoordinates();
            double cosPhi = ray[2];
            double sinPhi = Math.sqrt(1 - cosPhi*cosPhi);
            double sinTheta,cosTheta;
            if(sinPhi == 0){
                sinTheta = 1;
                cosTheta = 0;
            }else {
                sinTheta = ray[1]/sinPhi;
                cosTheta = ray[0]/sinPhi;
            }

            double ir2 = sinPhi*sinPhi*cosTheta*cosTheta/(lx*lx) +
                    sinPhi*sinPhi*sinTheta*sinTheta/(ly*ly) +
                    cosPhi*cosPhi/(lz*lz);
            double r = Math.sqrt(1/ir2);
            node.setPosition(new double[]{
                    r*sinPhi*cosTheta, r*sinPhi*sinTheta, r*cosPhi
            });
        }
    }

    List<DeformableMesh3D> fillBinaryBlobs(List<Region> regions){
        List<DeformableMesh3D> guessed = new ArrayList<>();

        for (Region region : regions) {
            int label = region.getLabel();

            DeformableMesh3D mesh;

            if(minL <= 0 || minL >= maxL){
                mesh = FillingBinaryImage.fillBinaryWithMesh(mis, region);
            }else{
                mesh = FillingBinaryImage.fillBinaryWithMesh(mis, region, minL, maxL);
            }

            mesh.clearEnergies();
            guessed.add(mesh);
        }
        return guessed;
    }
}
