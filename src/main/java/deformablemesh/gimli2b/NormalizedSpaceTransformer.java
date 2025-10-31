package deformablemesh.gimli2b;

import deformablemesh.MeshImageStack;
import ij.ImagePlus;
import ij.measure.Calibration;

import java.util.Arrays;

/**
 * Takes the coordinates of
 */
class NormalizedSpaceTransformer {
    int ox, oy, oz;
    final double scale;
    final double dx, dy, dz;
    final double sx, sy, sz;
    final int w, h, d;

    public NormalizedSpaceTransformer(MeshImageStack stack) {
        scale = stack.SCALE;
        dx = stack.pixel_dimensions[0];
        dy = stack.pixel_dimensions[1];
        dz = stack.pixel_dimensions[2];
        sx = -stack.offsets[0];
        sy = -stack.offsets[1];
        sz = -stack.offsets[2];

        w = stack.getWidthPx();
        h = stack.getHeightPx();
        d = stack.getNSlices();
    }

    public NormalizedSpaceTransformer(ImagePlus plus){
        Calibration cal = plus.getCalibration();
        dx = cal.pixelWidth;
        dy = cal.pixelHeight;
        dz = cal.pixelDepth;

        w = plus.getWidth();
        h = plus.getHeight();
        d = plus.getNSlices();

        double[] lengths = {dx*w, dy*h, dz*d};
        scale = Arrays.stream(lengths).max().getAsDouble();
        sx = 0.5*lengths[0]/scale;
        sy = 0.5*lengths[1]/scale;
        sz = 0.5*lengths[2]/scale;
    }

    double getX(double x) {
        return (x - sx) * scale - 0.;
    }

    double getY(double y) {
        return (y - sy) * scale - 0.;
    }

    double getZ(double z) {
        return (z - sz) * scale - 0.;
    }

}
