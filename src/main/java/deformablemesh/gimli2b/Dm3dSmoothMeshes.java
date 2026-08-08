package deformablemesh.gimli2b;

import deformablemesh.MeshImageStack;
import deformablemesh.SegmentationController;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Dm3dSmoothMeshes implements Command {
    @Parameter
    private Dm3dService dm3dService;
    @Parameter
    private String image;
    @Parameter
    private String meshes;
    @Override
    public void run() {
        SegmentationController controller = dm3dService.getApplicationController();
        MeshImageStack stack = new MeshImageStack(Paths.get(image));
        Path meshFolder = Paths.get(image);

    }

}