package deformablemesh.externalenergies;

import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.util.Vector3DOps;

public class CellCenterAttraction implements ExternalEnergy{
    DeformableMesh3D id, neighbor;
    double weight;
    public CellCenterAttraction(DeformableMesh3D id, DeformableMesh3D neighbor, double weight){
        this.id = id;
        this.neighbor = neighbor;
        this.weight = weight;
    }
    @Override
    public void updateForces(double[] positions, double[] fx, double[] fy, double[] fz) {
        double[] c = id.getBoundingBox().getCenter();
        double[] d = neighbor.getBoundingBox().getCenter();
        double[] delta = Vector3DOps.difference(d, c);
        for(int i = 0; i<fx.length; i++){
            fx[i] += delta[0]*weight;
            fy[i] += delta[1]*weight;
            fz[i] += delta[2]*weight;
        }
    }

    @Override
    public double getEnergy(double[] pos) {
        return 0;
    }
}
