package deformablemesh.externalenergies;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.CurvatureCalculator;
import deformablemesh.geometry.DeformableMesh3D;

public class BrightRegionEnergy implements ExternalEnergy{
    MeshImageStack stack;
    CurvatureCalculator calculator;
    DeformableMesh3D mesh;
    double limit;
    double weight = 1.0;
    public BrightRegionEnergy(MeshImageStack stack, DeformableMesh3D mesh, double weight){
        this.stack = stack;
        calculator = new CurvatureCalculator(mesh);
        limit = weight;
        this.mesh = mesh;
    }
    boolean contains(double[] pt){
        return stack.getBounds().contains(pt) && stack.getInterpolatedValue(pt) > limit;
    }
    @Override
    public void updateForces(double[] positions, double[] fx, double[] fy, double[] fz) {
        for(int i = 0; i<positions.length/3; i++){
            if(contains(mesh.nodes.get(i).getCoordinates())){
                double[] normal = calculator.getNormal(i);
                double area = calculator.calculateMixedArea(mesh.nodes.get(i));
                fx[i] += weight*area*normal[0];
                fy[i] += weight*area*normal[1];
                fz[i] += weight*area*normal[2];
            }

        }
    }

    @Override
    public double getEnergy(double[] pos) {
        return contains(pos) ? 1 : 0;
    }
}
