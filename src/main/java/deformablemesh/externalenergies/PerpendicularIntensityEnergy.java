/*-
 * #%L
 * Triangulated surface for deforming in 3D.
 * %%
 * Copyright (C) 2013 - 2023 University College London
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package deformablemesh.externalenergies;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.CurvatureCalculator;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Triangle3D;
import deformablemesh.util.GaussianKernels;
import deformablemesh.util.Vector3DOps;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by msmith on 2/10/16.
 */
public class PerpendicularIntensityEnergy implements ExternalEnergy {
    CurvatureCalculator calculator;
    MeshImageStack stack;
    double ds;
    double[] kernel = GaussianKernels.firstDerivative1DKernel();
    double weight;
    DeformableMesh3D mesh;
    public PerpendicularIntensityEnergy(MeshImageStack stack, DeformableMesh3D mesh, double weight){
        calculator = mesh.getCurvatureCalculator();
        ds = stack.getMinPx();
        this.stack = stack;
        this.weight = weight;
        this.mesh = mesh;
    }

    @Override
    public void updateForces(double[] positions, double[] fx, double[] fy, double[] fz) {
        mesh.triangles.forEach(Triangle3D::update);
        int n = positions.length/3;
        for(int i = 0; i<n; i++){

            double[] normal = getNormal(i);
            if(normal[0]==0 && normal[1] == 0 && normal[2] == 0){
                continue;
            }

            int dex = i*3;
            double f = getForce(positions[dex], positions[dex+1], positions[dex + 2], normal)*weight;
            fx[i] += f*normal[0];
            fy[i] += f*normal[1];
            fz[i] += f*normal[2];


        }
    }

    public double getForce(double x, double y, double z, double[] direction){
        double width = kernel.length/2;
        double m = 0;
        double[] pos = new double[3];
        for(int i = 0; i<kernel.length; i++){
            pos[0] = (i - width)*ds*direction[0] + x;
            pos[1] = (i - width)*ds*direction[1] + y;
            pos[2] = (i - width)*ds*direction[2] + z;
            m += stack.getInterpolatedValue(pos)*kernel[i];
        }
        return m;
    }


    public double[] getNormal(int i){
        return calculator.getNormal(i);
    }
    @Override
    public double getEnergy(double[] pos) {
        return stack.getInterpolatedValue(pos);
    }


}
