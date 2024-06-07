package deformablemesh.util.connectedcomponents;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ShortProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ConnectedComponents3DTest {
    @Test
    public void relabelTest(){
        ImageStack stack = new ImageStack(8, 8);
        for(int i = 0; i<8; i++){
            stack.addSlice(new ShortProcessor(8, 8));
        }
        stack.getProcessor(4).set(4, 4, 1);

        List<Region> regions = ConnectedComponents3D.getRegions(stack);
        Assert.assertEquals(1, regions.size());
        List<int[]> points = regions.get(0).getPoints();
        Assert.assertEquals(1, points.size());
        int[] pt = points.get(0);
        Assert.assertNotEquals(0, stack.getProcessor(pt[2]+1).get(pt[0], pt[1]));
        Assert.assertEquals(0, stack.getProcessor(pt[2]+1).get(pt[0], 0));
    }

}
