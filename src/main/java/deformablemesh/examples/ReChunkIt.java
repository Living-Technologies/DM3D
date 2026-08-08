package deformablemesh.examples;

import deformablemesh.io.LoadZarr;
import deformablemesh.io.SaveImageToZarr;
import ij.ImagePlus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ReChunkIt {

    public static void main(String[] args) throws Exception {
        Path rootFolder = Paths.get(args[0]);
        String[] tags = {
                "20190525pos13",
                "20190817pos18",
                "20200516pos033",
                "20200614pos002",
                "20200614pos10",
                "20190817pos01",
                "20190926pos01",
                "20200516pos36",
                "20200614pos07"};
        for(String tag: tags) {
            Path cropFolder = rootFolder.resolve("last_" + tag + "-mesh-crops");
            if (Files.exists(cropFolder)) {
                long start = System.nanoTime();
                System.out.println("starting: " + cropFolder);

                Path location = cropFolder.resolve("images.zarr");
                ImagePlus plus = LoadZarr.load3DStackFromZarrFile(location.toString()).get(0);
                Path out = cropFolder.resolve("images-rc.zarr");
                SaveImageToZarr.saveToZarr(plus, out, new int[]{plus.getWidth(), plus.getHeight(), plus.getNSlices(), 1, 1000});

                location = cropFolder.resolve("masks.zarr");
                plus = LoadZarr.load3DStackFromZarrFile(location.toString()).get(0);
                out = cropFolder.resolve("masks-rc.zarr");
                SaveImageToZarr.saveToZarr(plus, out, new int[]{plus.getWidth(), plus.getHeight(), plus.getNSlices(), 1, 1000});

                System.out.println("finished after: " + ((System.nanoTime() - start)*1e-9) + "s");
            }
        }
    }

}
