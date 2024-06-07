package deformablemesh.meshview;

import org.scijava.vecmath.Vector4f;
@FunctionalInterface
public interface VoxelPainter {
        Vector4f getColor(double value);
}
