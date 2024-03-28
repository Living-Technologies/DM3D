package deformablemesh;

import deformablemesh.geometry.Box3D;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.junit.Assert;
import org.junit.Test;

public class BoundingBoxTransformerTesting{

    MeshImageStack og = new MeshImageStack(testStack(64, 128, 32, 0.25, 0.25, 1));
    MeshImageStack iso = new MeshImageStack(testStack(32, 64, 64, 0.5, 0.5, 0.5));

    public static ImagePlus testStack(int w, int h, int d, double px, double py, double pz){
        ImageStack stack = new ImageStack(w, h);
        for(int i = 0; i<d; i++){
            ImageProcessor improc = new ShortProcessor(w, h);
            for(int j = 0; j<d; j++){
                for(int k = 0; k<w; k++){
                    int s = 2*j + ((2*k)<<4) + ((2*i)<<8);
                    improc.set(j,k,s);
                }
            }
            stack.addSlice("no label",improc);
        }

        ImagePlus imp = new ImagePlus("original",stack);
        Calibration cal = imp.getCalibration();
        cal.pixelWidth = px;
        cal.pixelHeight = py;
        cal.pixelDepth = pz;
        imp.setCalibration(cal);

        return imp;

    }
    static double[] getOrigin(MeshImageStack stack){
        Calibration c = stack.original.getCalibration();

        return new double[]{c.xOrigin, c.yOrigin, c.zOrigin};
    }

    @Test
    public void cropTest(){


        Box3D a = new Box3D(0, 0, 0, 0.2, 0.2, 0.2);

        MeshImageStack crop = og.getCrop(a);
        MeshImageStack dup = iso.getCrop(a);

        Assert.assertArrayEquals(
                new double[]{-og.getWidthPx()/2.0, -og.getHeightPx()/2.0, -og.getNSlices()/2.0 },
                getOrigin(crop),
                 0);
        Assert.assertArrayEquals(
                getOrigin(dup),
                new double[]{-iso.getWidthPx()/2.0, -iso.getHeightPx()/2.0, -iso.getNSlices()/2.0 }, 0);
    }

    @Test
    public void transformTest(){
        Box3D a = new Box3D(0, 0, 0, 0.125, 0.125, 0.125);

        MeshImageStack crop = og.getCrop(a);
        MeshImageStack dup = iso.getCrop(a);

        BoundingBoxTransformer identity = new BoundingBoxTransformer(crop, dup);
        //values are chosen to make double arithmetic exact.
        double[][] os = {
                new double[]{0., 0., 0.},
                new double[]{0.125, 0.125, 0.125},
                new double[]{-0.125, -0.125, -0.125}
        };

        for(double[] o : os) {

            double[] o2 = identity.transform(o);

            Assert.assertArrayEquals(o, o2, 0);

            BoundingBoxTransformer rev = new BoundingBoxTransformer(crop, og);
            BoundingBoxTransformer trans = new BoundingBoxTransformer(dup, og);

            double[] o3 = rev.transform(o);
            double[] o4 = trans.transform(o);
            Assert.assertArrayEquals(o3, o4, 0);
        }
    }
}
