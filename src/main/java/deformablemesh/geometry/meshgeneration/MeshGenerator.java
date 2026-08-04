package deformablemesh.geometry.meshgeneration;

import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.util.connectedcomponents.Region;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This defines a pipeline for mesh generation. The idea being, there is an initial
 * region generation.
 *
 * Filtering, binary morphology
 *
 */
public class MeshGenerator {
    List<Region> regions;
    RegionGenerator regionGenerator;
    RegionMesher mesher;

    public void detectRegions(){
        regions = regionGenerator.generateRegions();
    }

    public void filterRegions(){

    }

    public List<DeformableMesh3D> getMeshes(){
        return regions.stream().map(
                mesher::meshRegion
        ).flatMap(
                List::stream
        ).collect(Collectors.toList());
    }

}
