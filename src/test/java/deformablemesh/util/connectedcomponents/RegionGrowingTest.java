package deformablemesh.util.connectedcomponents;

import deformablemesh.geometry.BinaryMeshGenerationTests;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RegionGrowingTest {

    @Test
    public void sixFoldConnectivityCheck(){
        ImageStack stack = new ImageStack(9, 9);
        ImageStack space = new ImageStack(9, 9);
        for(int i = 0; i<9; i++){
            stack.addSlice(new ShortProcessor(9, 9));
            space.addSlice(new ShortProcessor(9, 9));
        }
        stack.getProcessor(5).set(4, 4, 255);
        List<int[]> pts = new ArrayList<>();
        pts.add(new int[]{4, 4, 4});
        Region r = new Region(255, pts);
        List<Region> rs = new ArrayList<>();
        rs.add(r);
        RegionGrowing rg = new RegionGrowing(stack, space);
        rg.setRegions(rs);

        space.getProcessor(5).set(6, 4, 1);
        space.getProcessor( 5 ).set(2, 4, 1);
        space.getProcessor(5).set( 4,6, 1);
        space.getProcessor( 5 ).set(4,2,  1);

        space.getProcessor(5).set(5, 5, 1);
        space.getProcessor( 5 ).set(3, 3, 1);
        space.getProcessor(5).set(3, 5, 1);
        space.getProcessor( 5 ).set(5, 3, 1);

        int steps = 0;
        while(rg.getFrontierSize() > 0){
            steps++;
            rg.step();
        }
        Assert.assertEquals(1, steps);
        Assert.assertEquals( 1, r.getPoints().size());

        space.getProcessor(5).set(3, 4, 255);

        RegionGrowing rg2 = new RegionGrowing(stack, space);
        rg2.setRegions(rs);
        steps = 0;
        while(rg2.getFrontierSize() > 0){
            steps++;
            rg2.step();
        }
        Assert.assertEquals(3, steps);
        Assert.assertEquals(5, r.getPoints().size());
    }
    @Test
    public void simpleBlobs(){
        ImagePlus plus = BinaryMeshGenerationTests.space();
        ImageStack s = plus.getStack();

        for(int i = 1; i<4; i++){
            ImageProcessor proc = s.getProcessor(i);
            for(int j = s.getWidth()/2 -1; j < s.getWidth()/2 + 2; j++){
                for(int k = s.getHeight()/2 - 1; k<s.getHeight()/2 + 2; k++){
                    proc.set(j, k, 1);
                }
            }
        }

        for(int i = plus.getNSlices()-2; i<=plus.getNSlices(); i++){
            ImageProcessor proc = s.getProcessor(i);
            for(int j = s.getWidth()/2 -1; j < s.getWidth()/2 + 2; j++){
                for(int k = s.getHeight()/2 - 1; k<s.getHeight()/2 + 2; k++){
                    proc.set(j, k, 1);
                }
            }
        }


        s.getProcessor(2).set(s.getWidth()/2, s.getHeight()/2, 3);

        s.getProcessor(plus.getNSlices() - 1).set(s.getWidth()/2, s.getHeight()/2, 3);


        ImageStack init = new ImageStack(plus.getWidth(), plus.getHeight());

        for(int j = 1; j<=s.size(); j++){
            ImageProcessor p = s.getProcessor(j).convertToShort(false).duplicate();
            p.threshold(2);
            init.addSlice(p);
        }

        List<Region> regions = ConnectedComponents3D.getRegions(init);
        regions.forEach(r->{
            Assert.assertEquals(1, r.getPoints().size());
        });
        ImageStack space = new ImageStack(plus.getWidth(), plus.getHeight());
        for(int j = 1; j<=s.size(); j++){
            ImageProcessor p = s.getProcessor(j).convertToShort(false).duplicate();
            p.threshold(0);
            space.addSlice(p);
        }

        RegionGrowing rg = new RegionGrowing(init, space);
        rg.setRegions(regions);

        while(rg.getFrontierSize()>0){
            rg.step();
        }


        regions.forEach(r->{
            Assert.assertEquals(27, r.getPoints().size());
        });

    }

}
