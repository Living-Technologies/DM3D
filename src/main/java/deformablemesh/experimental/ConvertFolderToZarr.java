package deformablemesh.experimental;

import deformablemesh.MeshImageStack;
import ij.ImagePlus;
import ij.ImageStack;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConvertFolderToZarr {

    public static void main(String[] args) throws Exception {
        Path p = Paths.get(args[0]);
        Path out = Paths.get(args[0] + ".zarr");
        String filter = args.length == 1 ? ".tif" : args[1];

        System.out.println(out);
        MeshImageStack stack = MeshImageStack.fromFolder(p, filter);
        try(WriteZarrPredictions wzp = new WriteZarrPredictions(out) ){
            for(int frame = 0; frame<stack.getNFrames(); frame++) {
                ImagePlus plus = stack.getStack(frame);
                ImageStack bstack = new ImageStack(plus.getWidth(), plus.getHeight());
                int n = plus.getStack().size();
                for(int i = 1; i<=n; i++){
                    bstack.addSlice(plus.getStack().getProcessor(i).convertToByte(false));
                }
                plus.setStack(bstack, plus.getNChannels(), plus.getNSlices(), 1);
                wzp.write("dataset" + filter, plus, frame);
            }
        }
    }

}
