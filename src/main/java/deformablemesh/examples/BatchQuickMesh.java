package deformablemesh.examples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BatchQuickMesh {
    public static void main(String[] args){
        String[] images = {"0014-1.zarr", "0113-5.zarr", "0268-4.zarr", "0112-4.zarr", "0267-4.zarr", "0014-2.zarr", "0267-3.zarr", "0461-5.zarr"};

        Path base = Paths.get("/Users/msmith5/working/jari/fis-first-draft/crops");

        String name = "test_%s-mesh-crops";


        for(String img : images){
            Path crop_folder = base.resolve(
                    String.format(name, img.replace(".zarr", ""))
            );
            Path mask_file = crop_folder.resolve("masks.zarr");
            Path dat_file = crop_folder.resolve("quickmesh.dat");
            if( Files.exists(mask_file) && !Files.exists(dat_file)) {
                QuickMeshExample qm = new QuickMeshExample();
                qm.setInput(mask_file.toAbsolutePath().toString());
                qm.setOutput(dat_file.toAbsolutePath().toString());
                qm.run();
            }
        }



    }
}
