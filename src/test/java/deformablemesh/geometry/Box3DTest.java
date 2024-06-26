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
package deformablemesh.geometry;

import deformablemesh.geometry.interceptable.Box3DInterceptable;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by msmith on 4/8/16.
 */
public class Box3DTest {
    @Test
    public void basicTest(){
        double[][] origins = {
                DeformableMesh3D.ORIGIN,
                {0.05, 0, 0},
                {0, 0.05, 0},
                {0, 0, 0.05},
                {-0.05, 0, 0},
                {0, -0.05, 0},
                {0, 0, -0.05}
        };
        DeformableMesh3D mesh = RayCastMesh.sphereRayCastMesh(5);

        for(double[] origin: origins) {
            Box3DInterceptable box = new Box3DInterceptable(new Box3D(origin, 2, 2, 2));
            for (Node3D node : mesh.nodes) {
                List<Intersection> intersections = box.getIntersections(origin, node.getCoordinates());
                if(intersections.size()!=2){
                    intersections = box.getIntersections(origin, node.getCoordinates());
                }
                Assert.assertEquals(2, intersections.size());
            }
        }
    }
}
