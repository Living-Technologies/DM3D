package deformablemesh.util;

import deformablemesh.SegmentationController;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.image.BufferedImage;

public class SnapShotRecorder {
    ImageStack stack;
    SegmentationController controller;
    String title = "recorded";
    public SnapShotRecorder(SegmentationController controller){
        this.controller = controller;
    }

    public void snapshot(){
        ImageProcessor proc = new ColorProcessor(controller.getMeshFrame3D().snapShot());
        if(stack == null){
            stack = new ImageStack(proc.getWidth(), proc.getHeight());
        }
        stack.addSlice(proc);
    }

    public void show(){
        new ImagePlus(title, stack).show();
    }

}
