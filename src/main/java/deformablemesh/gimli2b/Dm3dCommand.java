package deformablemesh.gimli2b;

import deformablemesh.SegmentationController;
import ij.ImagePlus;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, name="Start DM3D", menuPath="Plugins > DM3D>  Start DM3D ")

public class Dm3dCommand implements Command {
    @Parameter(required=false)
    private ImagePlus currentData;
    @Parameter
    private Dm3dService dm3dService;

    @Override
    public void run() {
        SegmentationController controller = dm3dService.getApplicationController();

        if(currentData != null && currentData.getStack().size() > 0 ){
            controller.setOriginalPlus(currentData);
        }
    }

}
