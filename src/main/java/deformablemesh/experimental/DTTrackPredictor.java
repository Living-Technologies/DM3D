package deformablemesh.experimental;

import deformablemesh.BoundingBoxTransformer;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.Box3D;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.util.connectedcomponents.ConnectedComponents3D;
import deformablemesh.util.connectedcomponents.Region;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class DTTrackPredictor implements BoundingBoxGenerator {
    MeshImageStack stack;
    Box3D region;
    public int level = 3;
    public int minSize = 20;
    public double factor = 3;
    public DTTrackPredictor(MeshImageStack distanceTransform){
        stack = distanceTransform;
        region = stack.getLimits();
    }

    public void trackable(DeformableMesh3D mesh){
        Box3D b = mesh.getBoundingBox();
        double lx = b.high[0] - b.low[0];
        double ly = b.high[1] - b.low[1];
        double lz = b.high[2] - b.low[2];
        region = new Box3D( b.getCenter(), factor*lx, factor*ly, factor*lz);
    }

    @Override
    public List<Box3D> getBoxes(){
        MeshImageStack roi = stack.getCrop(region);
        ImageStack currentFrame = roi.getCurrentFrame().getStack();
        ImageStack threshed = new ImageStack(currentFrame.getWidth(), currentFrame.getHeight());
        for(int i = 1; i<= currentFrame.size(); i++){
            ImageProcessor proc = currentFrame.getProcessor(i).convertToShort(false);
            proc.threshold(level);
            threshed.addSlice(proc);
        }

        BoundingBoxTransformer bbt = new BoundingBoxTransformer(roi, stack);


        List<Region> regions = ConnectedComponents3D.getRegions(threshed).stream().filter(
                                    r -> r.getPoints().size() > minSize
                                ).collect(Collectors.toList());
        List<Box3D> fin = new ArrayList<>();

        for(Region region: regions){
            double[] low = bbt.transform(roi.getNormalizedCoordinate(region.getLowCorner()));
            double[] high = bbt.transform(roi.getNormalizedCoordinate(region.getHighCorner()));
            fin.add(new Box3D(low, high));
        }

        return fin;
    }
}
