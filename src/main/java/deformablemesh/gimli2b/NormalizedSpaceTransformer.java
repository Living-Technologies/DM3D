package deformablemesh.gimli2b;

import deformablemesh.MeshImageStack;
import ij.ImagePlus;

class NormalizedSpaceTransformer {
    int ox, oy, oz;
    double scale;
    double dx, dy, dz;
    double sx, sy, sz;
    int w, h, d;

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
        this(new MeshImageStack(plus));
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
