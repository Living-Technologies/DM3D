package deformablemesh.geometry;

import deformablemesh.MeshImageStack;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.FloatProcessor;

public class InterpolatingCropper {
    int w, h, d;
    MeshImageStack mist;
    public InterpolatingCropper(int w, int h, int d){
        this.w = w;
        this.h = h;
        this.d = d;
    }
    public void setMeshImageStack(MeshImageStack stack){
        this.mist = stack;
    }

    /**
     * Crops the currentframe / current channel of the mesh image stack.
     * This will scan the volume evenly which can change pixel sizes.
     * @param box
     * @return
     */
    public ImagePlus crop( Box3D box ){
        ImageStack stack = new ImageStack(w, h);
        double dx = (box.high[0] - box.low[0])/w;
        double dy = (box.high[1] - box.low[1])/h;
        double dz = (box.high[2] - box.low[2])/d;

        for(int z = 0; z < d; z++){
            FloatProcessor proc = new FloatProcessor(w, h);
            for(int x = 0; x < w; x++){
                for(int y = 0; y < h; y++){
                    proc.setf(
                            x, y,
                            (float)mist.getInterpolatedValue(
                                    dx*(x + 0.5) + box.low[0],
                                    dy*(y + 0.5) + box.low[1],
                                    dz*(z + 0.5) + box.low[2] )
                        );
                }
            }
            stack.addSlice(proc);
        }
        ImagePlus plus = mist.createImagePlus();
        plus.setStack(stack, 1, d, 1);
        Calibration c = plus.getCalibration();
        Calibration oc = mist.getImageJCalibration();

        double[] oset = mist.getImageCoordinates(box.low);
        c.zOrigin = oc.zOrigin - oset[2];
        c.yOrigin = oc.yOrigin - oset[1];
        c.xOrigin = oc.xOrigin - oset[0];

        plus.setCalibration(c);
        return plus;

    }

}
