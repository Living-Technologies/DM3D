package deformablemesh.util.connectedcomponents;

import deformablemesh.geometry.BinaryMeshGenerationTests;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class RegionGrowingTest {

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
