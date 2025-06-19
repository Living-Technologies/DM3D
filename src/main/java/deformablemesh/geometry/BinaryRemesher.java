package deformablemesh.geometry;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.util.connectedcomponents.Region;
import deformablemesh.util.connectedcomponents.RegionGrowing;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;

import java.awt.Image;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BinaryRemesher {
    int downsample = 4;
    int threshold = 1;
    MeshImageStack fullResolution;
    public BinaryRemesher(MeshImageStack original){
        fullResolution = original;
    }

    public void setDownsample( int d){
        downsample = 1;
    }

    public void setThreshold(int t){
        threshold = t;
    }

    public List<DeformableMesh3D> remeshMeshes(List<DeformableMesh3D> meshes){
        ImagePlus start;
        if(downsample != 1){
            start = fullResolution.getCurrentFrameScaled(
                    fullResolution.getWidthPx()/downsample,
                    fullResolution.getHeightPx()/downsample
            );
        } else{
            start = fullResolution.getCurrentFrame();
        }
        MeshImageStack original = new MeshImageStack(start);

        ImageStack stack = start.getStack();
        ImageStack binaryStack = new ImageStack(stack.getWidth(), stack.getHeight());

        for(int i = 1; i<=start.getNSlices(); i++){
            stack.getProcessor(i).threshold(threshold);
            binaryStack.addSlice(new ShortProcessor(stack.getWidth(), stack.getHeight()));
        }
        List<Region> regions = new ArrayList<>();


        for(int i = 0; i<meshes.size(); i++){
            DeformableMesh3D mesh = meshes.get(i);
            int lbl = i+1;
            List<int[]> points = DeformableMesh3DTools.getContainedPixels(original, mesh);
            for(int[] pt : points){
                binaryStack.getProcessor(pt[2]+1).set(pt[0], pt[1], lbl);
            }
            Region r = new Region(lbl, points);
            regions.add(r);

        }
        RegionGrowing growing = new RegionGrowing(binaryStack, stack);
        growing.setRegions(regions);

        while(growing.getFrontierSize() > 0){
            growing.step();
        }
        ImagePlus labelledPlus = start.createImagePlus();
        labelledPlus.setStack(binaryStack);
        MeshImageStack labelledMis = new MeshImageStack(labelledPlus);

        List<DeformableMesh3D> newMeshes = regions.stream().map(
                r ->{
                    r.validate();
                    List<Region> rs = r.split();
                    System.out.println(rs.size());
                    rs.sort( Comparator.comparingInt(reg->reg.getPoints().size()));
                    return BinaryMeshGenerator.voxelMesh(rs.get(rs.size() - 1), labelledMis);
                }
        ).collect(Collectors.toList());

        return newMeshes;

    }
    public static List<DeformableMesh3D> remesh(List<DeformableMesh3D> meshes, MeshImageStack original, int threshold){
        BinaryRemesher br = new BinaryRemesher(original);
        br.setThreshold(threshold);
        return br.remeshMeshes(meshes);
    }
    public static DeformableMesh3D remesh(DeformableMesh3D mesh, MeshImageStack original, int threshold){
        BinaryRemesher br = new BinaryRemesher(original);
        br.setThreshold(threshold);
        List<DeformableMesh3D> meshes = new ArrayList<>();
        return br.remeshMeshes(meshes).get(0);
    }
}
