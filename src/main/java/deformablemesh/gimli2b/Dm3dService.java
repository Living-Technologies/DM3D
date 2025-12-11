package deformablemesh.gimli2b;

import deformablemesh.SegmentationController;
import deformablemesh.SegmentationModel;
import deformablemesh.gui.ControlFrame;
import deformablemesh.gui.PropertySaver;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.plugins.Deforming3DMesh_Plugin;
import net.imagej.ImageJService;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

import java.io.IOException;

@Plugin(type= Service.class)
public class Dm3dService extends AbstractService implements ImageJService {
    SegmentationController controller;
    public Dm3dService(){
    }
    public SegmentationController getApplicationController(){
        if(controller == null){
            controller = createDeformingMeshApplication();
            controller.addShutdownListener( ()->{
                controller = null;
            });
        }
        return controller;
    }

    /**
     *
     * @return
     */
    public SegmentationController createController(){
        SegmentationModel model = new SegmentationModel();
        SegmentationController segmentationController = new SegmentationController(model);

        try {
            PropertySaver.loadProperties(segmentationController);
        } catch (IOException e) {
            System.err.println("cannot load properties: " + e.getMessage());
        }
        return segmentationController;
    }

    public SegmentationController createHeadlessController(){
        return new SegmentationController(new SegmentationModel(), true);
    }

    public ControlFrame startUi(SegmentationController segmentationController){
        ControlFrame controlFrame = new ControlFrame(segmentationController);
        MeshFrame3D mf3d = segmentationController.getMeshFrame3D();
        if(mf3d != null){
            controlFrame.addMeshFrame3D(mf3d);
        }
        controlFrame.showFrame();
        controlFrame.shutdownControllerOnClose();
        return controlFrame;
    }
    public MeshFrame3D start3DUi(SegmentationController segmentationController){
        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(false);
        mf3d.addLights();
        segmentationController.setMeshFrame3D(mf3d);
        return mf3d;
    }

    private SegmentationController createDeformingMeshApplication(){
        SegmentationController segmentationController = createController();
        MeshFrame3D mf3d = start3DUi(segmentationController);
        ControlFrame controlFrame = startUi(segmentationController);

        PropertySaver.positionFrames(controlFrame, mf3d);

        return segmentationController;
    }

}
