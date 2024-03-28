package deformablemesh.experimental;

import deformablemesh.geometry.Box3D;

import java.util.List;

public interface BoundingBoxGenerator {
    List<Box3D> getBoxes();
}
