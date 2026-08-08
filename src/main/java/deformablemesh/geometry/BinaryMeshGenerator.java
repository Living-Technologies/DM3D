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
import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.meshgeneration.RegionFilter;
import deformablemesh.geometry.topology.TopoCheck;
import deformablemesh.geometry.topology.TopologyValidationError;
import deformablemesh.io.MeshWriter;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.track.Track;
import deformablemesh.util.ColorSuggestions;
import deformablemesh.util.connectedcomponents.ConnectedComponents3D;
import deformablemesh.util.connectedcomponents.Region;
import deformablemesh.util.connectedcomponents.RegionGrowing;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.FileInfoVirtualStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Generates a mesh by creating a binary image, and meshing that at scale.
 */
public class BinaryMeshGenerator {
    static private ExecutorService es = null;
    static double tolerance = 1e-9;

    int openSteps = 0;
    int closeSteps = 0;
    ImageStack ones;
    int initialThreshold;
    int secondThreshold;
    static volatile boolean validate = false;
    private int DOWNSAMPLE = 1;
    List<RegionFilter> preMeshFilters = new ArrayList<>();

    public BinaryMeshGenerator(){
        initialThreshold = 2;
        secondThreshold = 0;
    }
    public void setInitialThreshold(int t){
        initialThreshold = t;
    }
    public void setSecondThreshold(int t){
        secondThreshold = t;
    }

    public void addPreMeshFilter(RegionFilter filter){
        preMeshFilters.add(filter);
    }
    /**
     * This will downsample in the x-y direction before doing any mesh creation.
     *
     * @param factor
     */
    public void setDownsample(int factor){
        DOWNSAMPLE = factor;
    }

    public static DeformableMesh3D voxelMesh(Region r, MeshImageStack stack){
        int w = stack.getWidthPx();
        int h = stack.getHeightPx();
        int d = stack.getNSlices();
        List<int[]> points = r.getPoints();
        int[] samplePoint = points.get(0);
        double label = stack.getValue(samplePoint[0], samplePoint[1], samplePoint[2]);

        List<long[]> triangles = new ArrayList<>(points.size()*3);

        double[] lc = r.getLowCorner();
        //lc = new double[]{0, 0, 0};
        double[] up = r.getHighCorner();
        long lx = (long)lc[0];
        long ly = (long)lc[1];
        long lz = (long)lc[2];
        long hx = (long)up[0];
        long hy = (long)up[1];
        long hz = (long)up[2];
        long tw = hx - lx + 1;
        long th = hy - ly + 1;
        long td = hz - lz + 1;
        for(int[] pt: points){
            for(int i = 0; i<2; i++){
                int xi = pt[0] + 2*i - 1;

                if(xi == w || xi < 0 || stack.getValue(xi, pt[1], pt[2]) != label){
                    long delta = i;

                    long ia = (th*tw)*(pt[2]-lz) + tw*(pt[1] - ly) + pt[0] - lx + delta;
                    long ib = (th*tw)*(pt[2]-lz) + tw*(pt[1] + 1 - ly) + pt[0] - lx + delta;
                    long ic = (th*tw)*(pt[2] + 1 - lz) + tw*(pt[1] + 1 - ly) + pt[0] - lx + delta;
                    long id = (th*tw)*(pt[2] + 1 - lz) + tw*(pt[1] - ly) + pt[0] - lx + delta;

                    long[][] pair = getQuadTriangles(ia, ib, ic, id, delta==0);
                    triangles.add(pair[0]);
                    triangles.add(pair[1]);
                }
            }

            for(int j = 0; j<2; j++){
                int yi = pt[1] + 2*j - 1;
                if(yi == h || yi < 0 || stack.getValue(pt[0], yi, pt[2]) != label){
                    long delta = j;
                    long ia = ( th * tw ) * (pt[2] - lz) + tw * ( pt[1]  + delta - ly) + pt[0] - lx;
                    long ib = ( th * tw ) * ( pt[2] + 1 - lz) + tw*( pt[1]  + delta - ly) + pt[0] - lx;
                    long ic = ( th * tw ) * ( pt[2] + 1 - lz) + tw * ( pt[1] + delta - ly) + pt[0] - lx + 1;
                    long id = (th*tw) * (pt[2] - lz) + tw * ( pt[1] + delta - ly) + pt[0] - lx + 1;

                    long[][] pair = getQuadTriangles(ia, ib, ic, id, delta == 0);
                    triangles.add(pair[0]);
                    triangles.add(pair[1]);
                }

            }

            for(int k = 0; k<2; k++){
                int zdex = pt[2] + 2*k - 1;
                if( zdex<0 || zdex == d || stack.getValue(pt[0], pt[1], zdex) != label){
                    //voxel.add(generateVoxelPlane(stack, pt, new double[]{0, 0, 2*k - 1}));
                    long delta = k;
                    long ia = ( th * tw )*(pt[2] - lz + delta) + tw * ( pt[1] - ly ) + pt[0] - lx;
                    long ib = ( th * tw )*(pt[2] - lz + delta) + tw * ( pt[1] - ly  ) + pt[0] - lx + 1;
                    long ic = ( th * tw )*(pt[2] - lz + delta) + tw * ( pt[1] - ly  + 1 ) + pt[0] - lx + 1;
                    long id = ( th * tw )*(pt[2] - lz + delta) + tw * ( pt[1] - ly  + 1 ) + pt[0] - lx;

                    long[][] pair = getQuadTriangles(ia, ib, ic, id, delta == 0);
                    triangles.add(pair[0]);
                    triangles.add(pair[1]);
                }

            }
        }

        List<double[]> meshPoints = new ArrayList<>();
        int[] map = new int[(int)(tw*th*td)];

        for(long[] ta : triangles){
            for(long l : ta) {
                if (map[(int) l] == 0) {
                    int x = (int) (l % tw);
                    int y = (int) ((l / tw) % th);
                    int z = (int) (l / (tw * th));
                    meshPoints.add(stack.getNormalizedCoordinate(new double[]{x + lx, y + ly, z + lz}));
                    map[(int) l] = meshPoints.size();
                }
            }
        }
        List<int[]> prepped = triangles.stream().map(
                ta -> new int[]{ map[(int)ta[0]] - 1,map[(int)ta[1]] - 1,map[(int)ta[2]] - 1 }
        ).collect(Collectors.toList());

        DeformableMesh3D mesh = DeformableMesh3DTools.fromTriangles(flatten(meshPoints), prepped);
        return mesh;
    }
    static double[] flatten(List<double[]> pts){
        double[] array = new double[pts.size()*3];
        int dex = 0;
        for(double[] pt : pts){
            System.arraycopy(pt, 0, array, dex, 3);
            dex += 3;
        }
        return array;
    }

    static public long[][] getQuadTriangles(long a, long b, long c, long d, boolean sign){
        long[] t1, t2;
        if(sign){
            t1 = new long[]{a, c, b};
            t2 = new long[]{a, d, c};
        } else{
            t1 = new long[]{a, b, c};
            t2 = new long[]{a, c, d};
        }
        return new long[][]{t1, t2};
    }



    public void setOpenSteps(int openSteps) {
        this.openSteps = openSteps;
    }
    public void setCloseSteps(int closeSteps){
        this.closeSteps = closeSteps;
    }

    /**
     * Performs any morphological operations on the provided mesh image stack.
     * @param regions
     * @param stack
     */
    private void morphologyStep(List<Region> regions, MeshImageStack stack){
        if(openSteps != 0 || closeSteps != 0){
            ones = ones(stack.getCurrentFrame().getStack());

            RegionGrowing rg = new RegionGrowing(stack.getOriginalPlus().getStack(), ones);
            rg.setRegions(regions);
            for(int i = 0; i<openSteps; i++){
                rg.erode();
            }
            for(int i = 0; i<openSteps; i++){
                rg.dilate();
            }
            for(int i = 0; i<closeSteps; i++){
                rg.dilate();
            }
            for(int i = 0; i<closeSteps; i++){
                rg.erode();
            }

            stack.copyValues();
        }

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

    /**
     * Creates voxel mesh from the provided binary blob. There is no processing done,
     * this method is used to test the topology correction routines.
     *
     * @param mis
     * @return
     */
     public static List<DeformableMesh3D> generateRawVoxelMeshes(MeshImageStack mis){
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


        MeshImageStack labelledMis = new MeshImageStack(regionPlus);

        for(Region r: regions){
            DeformableMesh3D mesh = voxelMesh(r, labelledMis);
            meshes.add(mesh);
        }
        return meshes;
    }
    private ImageStack ones(ImageStack stack){
        int w = stack.getWidth();
        int h = stack.getHeight();
        ImageStack ones = new ImageStack(w, h);
        for(int i = 0; i<stack.size(); i++){
            ImageProcessor p = new ShortProcessor(w, h);
            short[] px = (short[])p.getPixels();
            Arrays.fill(px, (short)1);
            ones.addSlice(p);
        }

        return ones;
    }

    private ImageStack zeros(ImageStack stack){
        int w = stack.getWidth();
        int h = stack.getHeight();
        ImageStack ones = new ImageStack(w, h);
        for(int i = 0; i<stack.size(); i++){
            ImageProcessor p = new ShortProcessor(w, h);
            short[] px = (short[])p.getPixels();
            ones.addSlice(p);
        }

        return ones;
    }

    private static List<DeformableMesh3D> processRegion(Region r, MeshImageStack regionStack){
        List<DeformableMesh3D> meshes = new ArrayList<>();

        if(r.getPoints().size() == 0){
            //System.out.println(r.getLabel() + " is empty!?");
            return meshes;
        }
        r.validate();

        System.out.println("creating voxel mesh");
        DeformableMesh3D mesh = voxelMesh(r, regionStack);
        if(validate){
            try {
                TopoCheck checkers = new TopoCheck(mesh);
                List<DeformableMesh3D> checkedMeshes = checkers.repairMesh();
                for(DeformableMesh3D debug : checkedMeshes){
                    List<TopologyValidationError> errs = TopoCheck.validate(debug);
                    if(errs.size() > 0){
                        System.out.println("borked!" + checkedMeshes.size() + "//" + errs.size() + " " + errs);
                    }
                }
                meshes.addAll(checkedMeshes);
            } catch(Exception e){
                meshes.add(mesh);
                e.printStackTrace();
            }
        } else{
            //System.out.println( TopoCheck.validate(mesh) );
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
    public List<DeformableMesh3D> predictMeshes(MeshImageStack mis){
        ImagePlus frame;
        if(DOWNSAMPLE > 1){
            frame = mis.getCurrentFrameScaled(mis.getWidthPx()/DOWNSAMPLE, mis.getHeightPx()/DOWNSAMPLE);
        } else{
            frame = mis.getCurrentFrame();
        }
        //TODO This whole section should be switched to using a MeshImageStack instead of an imageplus
        ImageStack old = frame.getStack();
        ImageStack stack = new ImageStack(frame.getWidth(), frame.getHeight());

        for(int j = 1; j<=old.size(); j++){
            ImageProcessor p = old.getProcessor(j).convertToShort(false).duplicate();
            p.threshold(initialThreshold);
            stack.addSlice(p);
        }
        List<Region> regions = ConnectedComponents3D.getRegions(stack);

        if(preMeshFilters.size() > 0){
            List<Region> remove = new ArrayList<>();
            for(Region r : regions){
                if( preMeshFilters.stream().anyMatch(f -> f.filter(r) ) ){
                    remove.add(r);
                }
            }
            for(Region r : remove){
                regions.remove(r);
                for(int[] p : r.getPoints()){
                    stack.getProcessor(p[2] + 1).set(p[0], p[1], 0);
                }
            }
        }

        ImageStack space = new ImageStack(frame.getWidth(), frame.getHeight());
        for(int j = 1; j<=old.size(); j++){
            ImageProcessor p = old.getProcessor(j).convertToShort(false).duplicate();
            p.threshold(secondThreshold);
            space.addSlice(p);
        }

        RegionGrowing rg = new RegionGrowing(stack, space);
        rg.setRegions(regions);

        while(rg.getFrontierSize()>0){
            rg.step();
        }

        //ImagePlus regionPlus = mis.createImagePlus();
        ImagePlus regionPlus = frame.createImagePlus();
        regionPlus.setStack(stack);

        MeshImageStack regionStack = new MeshImageStack(regionPlus);

        morphologyStep(regions, regionStack);

        List<DeformableMesh3D> meshes = new ArrayList<>();

        List<Callable<List<DeformableMesh3D>>> callables = regions.stream().map(
                r -> (Callable<List<DeformableMesh3D>> )() -> processRegion(r, regionStack)
            ).collect(Collectors.toList());
        if(es == null){
            es = ForkJoinPool.commonPool();
        }
        List<Future<List<DeformableMesh3D>>> working = null;
        try {
            working = es.invokeAll( callables );
            for(Future<List<DeformableMesh3D>> f : working){
                try {
                    meshes.addAll(f.get());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return meshes;
    }

    public List<DeformableMesh3D> meshesFromLabels(MeshImageStack stack){
        //creates a defensive copy
        MeshImageStack mis = new MeshImageStack(stack.getCurrentFrame());
        List<Region> regions = null;
        MeshDetector detects = new MeshDetector(mis);
        regions = detects.getRegionsFromLabelledImage();

        morphologyStep(regions, mis);

        return regions.stream().map(
                r->processRegion(r, mis)
            ).flatMap(List::stream).collect(Collectors.toList());
    }
    public static void main(String[] args) throws IOException {
        es = Executors.newFixedThreadPool(4);
        new ImageJ();
        //String loc = Paths.get("D:/working/zarr-communications/xyzt_cp-masks.zarr").toAbsolutePath().toString();
        //List<ImagePlus> pluses = LoadZarr.load3DStackFromZarrFile(loc);
        ImagePlus plus = FileInfoVirtualStack.openVirtual(new File(args[0]).getAbsolutePath());
        //ImagePlus plus = ImageJFunctions.wrap(MCBroken.image(), "3x3x3-blob");
        //ImagePlus plus = pluses.get(0);
        //plus.setDimensions(1, 9, 1);
        MeshImageStack mis = new MeshImageStack(plus);

        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.addLights();
        mf3d.setBackgroundColor(new Color(200, 200, 200));
        List<Track> broken = new ArrayList<>();
        int saved = 0;
        ImageStack stack = null;
        BinaryMeshGenerator generator = new BinaryMeshGenerator();
        generator.openSteps = 1;
        generator.closeSteps = 0;
        int fin = mis.getNFrames();
        for(int i = 0; i < fin; i++){
            mis.setFrame(i);
            long start = System.currentTimeMillis();
            //List<DeformableMesh3D> meshes = predictMeshes(mis);
            List<DeformableMesh3D> meshes = generator.meshesFromLabels(mis);
            int triangles = meshes.stream().mapToInt(m -> m.triangles.size()).sum();
            System.out.println(System.currentTimeMillis() - start + " meshed + " + triangles);
            start = System.currentTimeMillis();
            System.out.println("smoothing");
            List<DeformableMesh3D> smoothed = new ArrayList<>(meshes.size());

            List<Future<List<TopologyValidationError>>> futureErrors = new ArrayList<>();
            for(DeformableMesh3D mesh: meshes){
                final int frame = i;
                futureErrors.add(es.submit( ()->{
                    List<TopologyValidationError> err = TopoCheck.validate(mesh);
                    if(err.size() > 0){
                        Track t = new Track("red- " + err.size() + " " + err.stream().map(Object::toString).collect(Collectors.joining("-")));
                        t.addMesh(frame, mesh);
                        broken.add(t);
                    } else{
                        //Add non-broken meshes for validation.
                        Track t = new Track("blue- " + err.size() + " " + err.stream().map(Object::toString).collect(Collectors.joining("-")));
                        t.addMesh(frame, mesh);
                        broken.add(t);
                    }
                    return err;
                } ) );
                     /*else{
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
                }*/

            }
            for (Future<List<TopologyValidationError>> futureError : futureErrors) {
                try {
                    futureError.get();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
            mf3d.clearTransients();
            System.out.println(System.currentTimeMillis() - start + " smoothed");


            smoothed.addAll(broken.stream().map(t->t.getMesh(t.getFirstFrame())).collect(Collectors.toList()));
            for(DeformableMesh3D dm3d : smoothed){
                System.out.println("nodes: " + dm3d.nodes.size());
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

            if(broken.size() > saved) {
                System.out.println("saving: " + broken.size()  + " broken meshes after " + i + " frames.");
                MeshWriter.saveMeshes(new File("voxel-mesh-errors.bmf"), broken);
                saved = broken.size();
            } else{
                System.out.println("No more broken meshes! " + saved);
            }

        }

        es.shutdown();

        new ImagePlus("Snapshots", stack).show();
    }
}
