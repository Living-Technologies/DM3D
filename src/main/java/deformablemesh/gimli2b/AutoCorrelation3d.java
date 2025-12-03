package deformablemesh.gimli2b;

import deformablemesh.MeshImageStack;

public class AutoCorrelation3d {
    MeshImageStack stack;
    public void run(){
        int w = stack.getWidthPx();
        int h = stack.getHeightPx();
        int d = stack.getNSlices();

        int ow = w/8;
        int oh = h/8;
        int od = d/8;

        for(int k = od; k<d - od; k++){
            for(int j = oh; j< h - oh; j++){
                for(int i = ow; i<w - ow; i++){

                }
            }
        }



    }
}
