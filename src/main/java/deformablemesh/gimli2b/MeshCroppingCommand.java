package deformablemesh.gimli2b;

import deformablemesh.geometry.MeshCroppingTool;
import deformablemesh.gui.GuiTools;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import javax.swing.JOptionPane;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(type = Command.class, name="Crop Volumes", menuPath="Plugins > DM3D> tools > Crop Volumes ")
public class MeshCroppingCommand implements Command {
    @Parameter(label="Use Labelled Image", description="Loaded a labelled image if true\n" +
    "otherwise load from a mesh file/folder")
    boolean useLabels;
    @Parameter(label="scale factor", description="Resulting pixel size will be the smallest pixel\n"+
    "size times the scale factor.")
    double scale;
    @Parameter(label="size", description="Size of the volumes cropped. Each object will\n"+
    "be cropped to sizexsizexsize volumes")
    int size;

    @Parameter(label="image path", description = "Location of Image", required = false)
    Path image;
    @Parameter(label="mesh or label source", description= "Location of labels", required = false)
    Path labels;
    @Override
    public void run(){
        MeshCroppingTool tool = new MeshCroppingTool(scale, size);


        if(useLabels){
            if(image != null & labels != null){
                tool.processLabelledImages(image, labels);
            } else{
                tool.processLabelledImages();
            }
        } else{
            if(image != null & labels != null){
                tool.processMeshImages(image, labels);
            } else{
                tool.processMeshes();
            }
        }
    }
}
