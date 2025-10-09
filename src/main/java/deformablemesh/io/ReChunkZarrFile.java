package deformablemesh.io;

import deformablemesh.gui.GuiTools;
import ij.ImagePlus;

import java.io.IOException;
import java.nio.file.Path;

public class ReChunkZarrFile {
    public static void main(String[] args) throws Exception {
        String location = GuiTools.getDirectory(null, "select zarr folder").getAbsolutePath();
        ImagePlus plus = LoadZarr.load3DStackFromZarrFile(location).get(0);
        Path out = GuiTools.getSaveFile(null, "select destination folder");
        long start = System.nanoTime();
        int frames = plus.getNFrames();

        int chunkSize = frames/50;
        if(chunkSize > 1000) chunkSize = 1000;

        SaveImageToZarr.saveToZarr(plus, out, new int[]{plus.getWidth(), plus.getHeight(), plus.getNSlices(), 1, chunkSize});
        System.out.println("finished after: " + ((System.nanoTime() - start)*1e-9) + "s");
    }
}
