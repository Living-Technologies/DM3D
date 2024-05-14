package deformablemesh.util;

import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Node3D;
import deformablemesh.geometry.SurfaceDistance;

public class DistanceSurfacePlot extends SurfacePlot{
    SurfaceDistance sd;
    Node3D origin;
    public DistanceSurfacePlot(DeformableMesh3D mesh, double[] location){
        this.mesh = mesh;
        sd = new SurfaceDistance();
        sd.setMesh(mesh);
        origin = sd.findClosest(location);
    }
    @Override
    public double sample(Node3D node) {
        return 1/sd.findDistance(origin, node);
    }
}
