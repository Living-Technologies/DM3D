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

import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.CurvatureCalculator;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.geometry.interceptable.BinaryInterceptible;
import deformablemesh.geometry.interceptable.Interceptable;
import deformablemesh.util.connectedcomponents.Region;

import java.nio.file.Paths;
import java.util.List;

/**
 *
 * The goal of this energy is to grow outwards, until it hits the interceptable constraint,
 * then it should stop growing outward.
 *
 * Created by msmith on 3/8/16.
 */
public class BallooningEnergy implements ExternalEnergy{
    final Interceptable constraint;
    final DeformableMesh3D mesh;
    final double weight;
    CurvatureCalculator calculator;

    public BallooningEnergy(Interceptable constraint, DeformableMesh3D mesh, double weight){
        this.constraint = constraint;
        this.mesh = mesh;
        this.weight = weight;
        calculator = new CurvatureCalculator(mesh);
    }

    @Override
    public void updateForces(double[] positions, double[] fx, double[] fy, double[] fz) {

        for(int i = 0; i<positions.length/3; i++){
            if(constraint.contains(mesh.nodes.get(i).getCoordinates())){
                double[] normal = calculator.getNormal(i);
                double area = calculator.calculateMixedArea(mesh.nodes.get(i));

                if(area == 0){
                    //normal will be NaN
                    continue;
                }
                fx[i] += weight*area*normal[0];
                fy[i] += weight*area*normal[1];
                fz[i] += weight*area*normal[2];
            }

        }
    }

    @Override
    public double getEnergy(double[] pos) {
        return constraint.contains(pos)?1:0;
    }

    public static void main(String[] args){
        MeshImageStack stack = new MeshImageStack(Paths.get("D:/working/maria/Jurica/1-mask-crops/BrokenBalloon.tif"));
        MeshDetector detector = new MeshDetector(stack);
        List<Region> regions = detector.getRegionsFromLabelledImage();
        Region r = regions.get(0);
        List<int[]> points = r.getPoints();
        BinaryInterceptible bi = new BinaryInterceptible(points, stack, r.getLabel());
        DeformableMesh3D mesh = RayCastMesh.rayCastMesh(bi, bi.getCenter(), 3);

        mesh.GAMMA = 1000;
        mesh.ALPHA = 2.0;
        mesh.BETA = 1.0;

        mesh.addExternalEnergy(new BallooningEnergy(bi, mesh, 1000));
        System.out.println(mesh.calculateVolume());
        for (int i = 0; i < 2; i++) {
            mesh.update();
        }
        System.out.println(mesh.calculateVolume());

    }

}
