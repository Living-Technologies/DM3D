package deformablemesh.externalenergies;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;

public class BrightRegionEnergy implements ExternalEnergy{
    PerpendicularIntensityEnergy pie;
    PerpendicularGradientEnergy pge;
    public BrightRegionEnergy(MeshImageStack stack, DeformableMesh3D mesh, double weight){

        pie = new PerpendicularIntensityEnergy(stack, mesh, -weight);
        pge = new PerpendicularGradientEnergy(stack, pie.map, weight);

    }

    @Override
    public void updateForces(double[] positions, double[] fx, double[] fy, double[] fz) {
        pie.updateForces(positions, fx, fy, fz);
        pge.updateForces(positions, fx, fy, fz);
    }

    @Override
    public double getEnergy(double[] pos) {
        return pie.getEnergy(pos) + pge.getEnergy(pos);
    }
}
