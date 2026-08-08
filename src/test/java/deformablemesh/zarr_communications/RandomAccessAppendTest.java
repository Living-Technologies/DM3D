package deformablemesh.zarr_communications;

import deformablemesh.io.LoadZarr;
import deformablemesh.io.SaveImageToZarr;
import ij.ImagePlus;
import org.junit.Assert;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RandomAccessAppendTest {
    /**
     * The goal of this test it to create a "large" zarr file and
     * then update different regions.
     *
     * TODO fix
     */
    //@Test
    public void createAndModifyTest(){
        int w = 96;
        int h = 64;
        int z = 16;
        int t = 2;
        int c = 2;

        Path p = Paths.get("random-access-write-test.zarr");
        IOTest.createdFolders.add(p);
        try {
            ImagePlus plus = IOTest.generic(w, h, z, t, c);
            System.out.println(p.toAbsolutePath());
            SaveImageToZarr.appendToZarrRa(plus, p, 2);
            SaveImageToZarr.appendToZarrRa(plus, p, 4);

            ImagePlus loaded = LoadZarr.load3DStackFromZarrFile(p.toString()).get(0);
            Assert.assertEquals(6, loaded.getNFrames());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally{
            IOTest.deleteTempZarrFolder(p);
        }

    }
}
