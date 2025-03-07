package deformablemesh.geometry.meshgeneration;

import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.util.connectedcomponents.Region;

import java.util.List;

public interface RegionMesher {
    List<DeformableMesh3D> meshRegion(Region r);
}
