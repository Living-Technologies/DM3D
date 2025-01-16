package deformablemesh.zarr_communications;

import deformablemesh.examples.SaveImageToZarr;
import deformablemesh.experimental.LoadZarr;
import ij.ImagePlus;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppendImageTest {
    public static void main(String[] args) throws Exception {
        int w = 96;
        int h = 64;
        int z = 16;
        int t = 1;
        int c = 2;

        Path p = Paths.get("D:\\working\\zarr-communications\\append-test.zarr");
        IOTest.deleteTempZarrFolder(p);

        try {
            ImagePlus plus = IOTest.generic(w, h, z, t, c);
            System.out.println(p.toAbsolutePath());
            SaveImageToZarr.saveToZarr(plus, p);
            ImagePlus p2 = IOTest.generic(w, h, z, t, c);

            SaveImageToZarr.appendToZarr(p2, p);

        } finally{
            //IOTest.deleteTempZarrFolder(p);
        }
    }
}
