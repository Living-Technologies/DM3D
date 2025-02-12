package deformablemesh.experimental;

import deformablemesh.MeshImageStack;
import deformablemesh.io.SaveImageToZarr;
import ij.ImagePlus;
import ij.ImageStack;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ConvertFolderToZarr {

    public static void main(String[] args) throws Exception {
        Path p = Paths.get(args[0]);
        Path out = Paths.get(args[0] + ".zarr");
        String filter = args.length == 1 ? ".tif" : args[1];

        MeshImageStack stack = MeshImageStack.fromFolder(p, filter);
        SaveImageToZarr.saveToZarr(stack.getOriginalPlus(), out);
    }

}
