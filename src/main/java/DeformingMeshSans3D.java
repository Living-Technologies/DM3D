import deformablemesh.SegmentationController;
import deformablemesh.SegmentationModel;
import deformablemesh.gui.ControlFrame;
import deformablemesh.gui.PropertySaver;
import deformablemesh.gui.RingController;
import deformablemesh.meshview.MeshFrame3D;
import ij.ImageJ;
import ij.ImagePlus;
import jogamp.nativewindow.jawt.JAWTUtil;

import java.awt.EventQueue;
import java.io.File;

public class DeformingMeshSans3D {

    public static SegmentationController createDeformingMeshApplication(){
        SegmentationModel model = new SegmentationModel();
        SegmentationController control = new SegmentationController(model);

        try{
            PropertySaver.loadProperties(control);
        } catch(Exception e){
            System.err.println("cannot load properties: " + e.getMessage());
        }

        ControlFrame controller = new ControlFrame(control);
        controller.showFrame();
        RingController ring_control = new RingController(control);
        ring_control.startUI();
        controller.addTabbedPanel(ring_control.getContentPane(controller.getFrame()), "furrow");
        model.setRingController(ring_control);
        return control;
    }




    private static void startApplication(File input){
        ImageJ.main(new String[]{});

        SegmentationController controls = createDeformingMeshApplication();

        if(input!=null) {
            String o = input.getAbsolutePath();
            controls.setOriginalPlus(new ImagePlus(o));
        }
    }
    public static void main(String[] args){
        File input;
        if(args.length>0){
            input = new File(args[0]);
        } else{
            input = null;
        }
        EventQueue.invokeLater(()->startApplication( input ));
    }

}