package deformablemesh.gimli2b;

import deformablemesh.geometry.MeshCroppingTool;
import deformablemesh.gui.GuiTools;
import ij.IJ;
import ij.ImagePlus;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import javax.swing.JOptionPane;
import java.io.File;
import java.nio.file.Path;

@Plugin(type = Command.class, name="Crop Volumes", menuPath="Plugins > DM3D> tools > Crop Volumes ")
public class MeshCroppingCommand implements Command {
    public void cropLabelledRegions(){
        Path img = GuiTools.getAFile(IJ.getInstance(), "select original images");
        Path lbls = GuiTools.getAFile(IJ.getInstance(), "select label image");
        File out = GuiTools.getDirectory(IJ.getInstance(), "select destination");
        MeshCroppingTool tool = new MeshCroppingTool();
        
    }

    public void cropMeshedRegions(){

    }
    @Override
    public void run() {
        int result = JOptionPane.showInternalConfirmDialog(IJ.getInstance(), "Process from Mesh Files?", "Cropping Tool Initialization", JOptionPane.YES_NO_CANCEL_OPTION);
        switch(result){
            case JOptionPane.CANCEL_OPTION:
                return;
            case JOptionPane.NO_OPTION:
                cropLabelledRegions();
                break;
            case JOptionPane.YES_OPTION:
                cropMeshedRegions();
        }
    }
}
