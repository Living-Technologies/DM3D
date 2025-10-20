package deformablemesh.gimli2b;

import deformablemesh.MeshImageStack;
import deformablemesh.util.connectedcomponents.Region;

/**
 * Converts normalized coordinates to image based coordinates.
 */
public class ImageSpaceTransformer {
    int ox, oy, oz;
    double iscale;
    double dx, dy, dz;
    double sx, sy, sz;
    int w, h, d;
    public ImageSpaceTransformer(MeshImageStack stack){
        iscale = 1.0/stack.SCALE;
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

    /**
     * Sets the pixel origin of this transformer to be the origin of the
     * pixel based region.
     *
     * @param r the region that pixels will correspond to.
     */
    public void update(Region r){
        ox = (int)r.getLowCorner()[0];
        oy = (int)r.getLowCorner()[1];
        oz = (int)r.getLowCorner()[2];
        w = (int)r.getHighCorner()[0] - ox;
        h = (int)r.getHighCorner()[1] - oy;
        d = (int)r.getHighCorner()[2] - oz;
    }
    double getX(double x){
        return (x + ox + 0.5)*dx * iscale  + sx;
    }
    double getY(double y){
        return (y + oy + 0.5)*dy * iscale + sy;
    }
    double getZ(double z){
        return (z + oz + 0.5)*dz * iscale + sz;
    }
}
