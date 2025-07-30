package deformablemesh.geometry;

import ij.ImagePlus;

import java.util.ArrayList;
import java.util.List;

public class CroppedVolume{
    public List<String> attributes = new ArrayList<>();
    ImagePlus data;
    ImagePlus mask;
    CroppedVolume(ImagePlus d, ImagePlus m){
        data = d;
        mask = m;
    }

}