package deformablemesh.examples;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.MeshCroppingTool;
import deformablemesh.gui.GuiTools;
import deformablemesh.io.LoadZarr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CropTheSet {
    static String stem(String filename ){
        int i = filename.lastIndexOf(".");
        if (i < 0) {
            return filename;
        } else {
            return filename.substring(0, i);
        }
    }
    public static void main(String[] args) throws IOException {
        Path rootFolder;
        if(args.length > 0){
            rootFolder = Paths.get(args[0]);
        } else{
            rootFolder = GuiTools.getDirectory(null, "Select a foldder").toPath();
        }


        List<String> image_names = Files.readAllLines(rootFolder.resolve("analysis_set.txt"));

        String prefix = "last-r_";
        for(String image_name: image_names) {
            String tag = stem(image_name);

            if(Files.exists(rootFolder.resolve(prefix + tag + "-mesh-crops"))){
                System.out.println("skipping: " + tag);
                continue;
            }
            Path ip = rootFolder.resolve(image_name);
            MeshImageStack stack;
            if(image_name.endsWith(".zarr")){
                stack = LoadZarr.loadMeshImageStack2(ip);
            } else{
                stack = MeshImageStack.fromVirtualTiff(ip.toAbsolutePath().toString());
            }

            Path meshes = rootFolder.resolve(tag + "_lbls.bmf");
            int start = stack.getNFrames() - 11;
            int size = 64;
            double factor = 20.8/size/stack.SCALE/stack.getMinPx();
            System.out.println(factor);
            MeshCroppingTool mct = new MeshCroppingTool(factor, 64);
            mct.setStartFrame(start);
            mct.setNFrames(10);
            mct.rotate = false;
            mct.setPrefix(prefix);
            //mct.setChunkSize  (5);
            mct.processMeshImages(ip, meshes);
        }
    }
}
