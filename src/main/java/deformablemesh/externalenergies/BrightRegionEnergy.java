package deformablemesh.externalenergies;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.CurvatureCalculator;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.interceptable.InterceptingMesh3D;
import deformablemesh.util.GaussianKernels;

import java.util.Arrays;

public class BrightRegionEnergy implements ExternalEnergy{
    double[] kernel = GaussianKernels.firstDerivative1DKernel();
    double[] kp = GaussianKernels.secondDerivative1DKernel();
    MeshImageStack stack;
    CurvatureCalculator calculator;
    DeformableMesh3D mesh;
    double limit;
    double weight = 1.0;
    InterceptingMesh3D start;
    double ds;

    public BrightRegionEnergy(MeshImageStack stack, DeformableMesh3D mesh, double weight){
        this.stack = stack;
        calculator = new CurvatureCalculator(mesh);
        this.weight = weight;
        this.mesh = mesh;
        start = new InterceptingMesh3D(mesh);
        ds = stack.getMinPx();

    }
    boolean contains(double[] pt){
        return start.contains(pt);
    }

    @Override
    public void updateForces(double[] positions, double[] fx, double[] fy, double[] fz) {
        for(int i = 0; i<positions.length/3; i++){
            double[] r = mesh.nodes.get(i).getCoordinates();
            double[] normal = calculator.getNormal(i);
            if(Double.isNaN(normal[0] + normal[1] + normal[2])){
                continue;
            }
            double f = getForce(r[0], r[1], r[2], normal)*weight;

            fx[i] += f*normal[0];
            fy[i] += f*normal[1];
            fz[i] += f*normal[2];

        }
    }

    public double getForce(double x, double y, double z, double[] direction){
        int width = kernel.length/2;
        double m0 = 0;
        double m1 = 0;
        double[] pos = new double[3];
        for(int i = 0; i<kernel.length; i++){
            pos[0] = (i - width)*ds*direction[0] + x;
            pos[1] = (i - width)*ds*direction[1] + y;
            pos[2] = (i - width)*ds*direction[2] + z;
            double v = stack.getInterpolatedValue(pos);
            m0 += v*kernel[i];
            m1 += v*kp[i];
        }

        return m0 < 0 ? -m1 : 0;
    }


    @Override
    public double getEnergy(double[] pos) {
        return contains(pos) ? 1 : 0;
    }
}
