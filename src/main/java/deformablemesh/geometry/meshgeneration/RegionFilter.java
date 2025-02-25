package deformablemesh.geometry.meshgeneration;

import deformablemesh.util.connectedcomponents.Region;

public interface RegionFilter {
    boolean filter(Region r);
}
