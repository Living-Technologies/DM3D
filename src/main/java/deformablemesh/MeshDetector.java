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

import deformablemesh.geometry.Box3D;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.RayCastMesh;
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
            guessed = spheres(regions);
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
                    if( ! checker.isBackground(px2) ) {
                        pxRegions.computeIfAbsent(px2, k->new ArrayList<>()).add(new int[]{i, j, z});
                    }

                }
            }
        }
        List<Region> regions = new ArrayList<>();
        for(Integer label: pxRegions.keySet()){
            Region r = new Region(label, pxRegions.get(label));
            List<Region> split = r.split();
            for(Region sr: split){
                if(sr.getPoints().size() > 2){
                    regions.add(sr);
                }
            }
        }
        return regions;
    }

    List<DeformableMesh3D> spheres(List<Region> regions){
        List<DeformableMesh3D> meshes = new ArrayList<>(regions.size());

        for(Region region : regions){
            double[] center = mis.getNormalizedCoordinate(region.getCenter());

            double volume = mis.getNormalizedVolume(region.calculateVolume());
            double r = Math.cbrt(volume*3/Math.PI/4);
            DeformableMesh3D m = RayCastMesh.sphereRayCastMesh(2);
            m.translate(center);
            m.scale(r, center);
            meshes.add(m);
        }
        return meshes;
    }

    List<DeformableMesh3D> fillBinaryBlobs(List<Region> regions){
        List<DeformableMesh3D> guessed = new ArrayList<>();

        for (Region region : regions) {
            int label = region.getLabel();
            List<int[]> rs = region.getPoints();

            //Collections.sort(rs, (a,b)->Integer.compare(a[2], b[2]));
            ImagePlus original = mis.original;
            ImagePlus plus = original.createImagePlus();
            int w = original.getWidth();
            int h = original.getHeight();
            ImageStack new_stack = new ImageStack(w, h);

            for (int dummy = 0; dummy < original.getNSlices(); dummy++) {
                new_stack.addSlice(new ByteProcessor(w, h));
            }
            for (int[] pt : rs) {
                new_stack.getProcessor(pt[2] + 1).set(pt[0], pt[1], 1);
            }

            plus.setStack(new_stack);
            plus.setTitle("label: " + label);
            //plus.show();

            DeformableMesh3D mesh = FillingBinaryImage.fillBinaryWithMesh(plus, rs);
            mesh.clearEnergies();
            guessed.add(mesh);
        }
        return guessed;
    }
}
