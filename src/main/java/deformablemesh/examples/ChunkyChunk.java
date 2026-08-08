package deformablemesh.examples;

import deformablemesh.MeshImageStack;
import deformablemesh.io.LoadZarr;
import deformablemesh.io.SaveImageToZarr;
import ij.ImagePlus;
import ij.ImageStack;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ChunkyChunk {

    public static ImagePlus duplicate(MeshImageStack stack){
        int first = 5000;
        int frames = 5000;
        stack.setFrame(first);
        ImagePlus plus = stack.getCurrentFrame();
        ImageStack is = plus.getImageStack();
        for(int i = 1; i<frames; i++){
            stack.setFrame(i);
            ImageStack os = stack.getCurrentFrame().getImageStack();
            for(int j =1; j<=os.size(); j++){
                is.addSlice(os.getProcessor(j));
            }
        }
        plus.setStack(is, stack.getNChannels(), stack.getNSlices(), frames);
        return plus;
    }
    public static void main(String[] args) throws Exception {
        Path root = Paths.get("/Users/msmith5/working/maria/jurica/test_jurica-1-mesh-crops");
        String[] tag = {"images.zarr", "masks.zarr"};
        Path outFolder = Paths.get("/Users/msmith5/working/maria/tracked/last_11_crops/jurica-1");
        for(String t : tag){
            MeshImageStack plus = LoadZarr.loadMeshImageStack2(root.resolve(t));
            ImagePlus dupe = duplicate(plus);
            SaveImageToZarr.saveToZarr(dupe, outFolder.resolve(t), new int[]{plus.getWidthPx(), plus.getHeightPx(), plus.getNSlices(), 1, 100});
        }

    }
}
