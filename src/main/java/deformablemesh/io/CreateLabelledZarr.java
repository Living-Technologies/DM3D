package deformablemesh.io;

import deformablemesh.MeshImageStack;
import deformablemesh.util.ImageLabeller;
import ij.ImagePlus;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class will create a labelled image frame by frame and save them
 * as it goes to a zarr file.
 *
 */
public class CreateLabelledZarr implements AutoCloseable{
    Path location;
    ImageLabeller labeller;
    public CreateLabelledZarr(Path location){
        this.location = location;
    }

    public void labelImage( MeshImageStack stack){
        for(int i = 0; i<stack.getNFrames(); i++){
            ImagePlus plus = stack.getStack(i);
            ImagePlus labeled = labeller.labelImage(plus);
            labelTimePoint(i, labeled);
        }
    }
    public void labelTimePoint(int point, ImagePlus timepoint){
        if( Files.exists( location )){
            appendImage( timepoint );
        }
        else{
            createZarrFile( timepoint);
        }

    }
    private void appendImage(ImagePlus timepoint){
        try {
            SaveImageToZarr.appendToZarr(timepoint, location);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createZarrFile(ImagePlus timepoint){
        try {
            SaveImageToZarr.saveToZarr(timepoint, location);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setLabeller(ImageLabeller labeller){
        this.labeller = labeller;
    }

    public void close(){

    }

}
