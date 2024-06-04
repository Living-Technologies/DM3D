package deformablemesh.meshview;

import org.jogamp.vecmath.Vector4f;
@FunctionalInterface
public interface VoxelPainter {
        Vector4f getColor(double value);
}
