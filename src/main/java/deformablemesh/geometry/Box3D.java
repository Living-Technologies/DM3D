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

import deformablemesh.geometry.interceptable.AxisPlane;
import deformablemesh.geometry.interceptable.Box3DInterceptable;
import deformablemesh.ringdetection.FurrowTransformer;
import deformablemesh.util.Vector3DOps;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.*;
import java.util.List;

import static deformablemesh.util.Vector3DOps.TOL;

/**
 * A 3D box, with edges along x-y-z axis.
 *
 * Created by msmith on 5/28/14.
 */
public class Box3D{
    public double[] low;
    public double[] high;
    final static Box3D empty = new Box3D(0, 0, 0, 0, 0, 0);

    public Box3D(double[] center, double width, double length, double height){
        low = new double[]{
                center[0] - 0.5*width,
                center[1] - 0.5*length,
                center[2] - 0.5*height
        };

        high = new double[]{
                center[0] + 0.5*width,
                center[1] + 0.5*length,
                center[2] + 0.5*height
        };
    }

    public Box3D(double minx, double miny, double minz, double maxx, double maxy, double maxz) {
        low = new double[]{minx, miny, minz};
        high = new double[]{maxx, maxy, maxz};
    }

    public Box3D(Box3D box){
        this(box.low[0], box.low[1], box.low[2], box.high[0], box.high[1], box.high[2]);
    }

    public Box3DInterceptable interceptable(){
        return new Box3DInterceptable(this);
    }
    public boolean contains(double[] point){

        return point[0]>=low[0] && point[0]<=high[0]
                && point[1]>=low[1] && point[1]<=high[1]
                && point[2]>=low[2] && point[2]<=high[2];

    }

    double[] overlap( double a0, double a1, double b0, double b1){
        if(a0 < b0){
            if(b0 < a1){
                //there is overlap.
                if(b1 < a1){
                    return new double[]{b0, b1};
                } else{
                    return new double[]{b0, a1};
                }
            } else{
                return new double[] { 0, 0 };
            }
        } else{
            if(a0 < b1){
                //overlap
                if(a1 < b1){
                    //contained
                    return new double[]{a0, a1};
                } else{
                    return new double[]{a0, b1};
                }
            } else{
                return new double[]{ 0, 0};
            }
        }

    }

    public boolean intersects(Box3D other){
        for(int i = 0; i<3; i++){
            double[] ol = overlap(low[i], high[i], other.low[i], other.high[i]);
            if(ol[1] - ol[0] <= 0){
                return false;
            }
        }
        return true;
    }

    public Box3D getIntersectingBox(Box3D other){
        double[] xl = overlap(low[0], high[0], other.low[0], other.high[0]);
        double[] yl = overlap(low[1], high[1], other.low[1], other.high[1]);
        double[] zl = overlap(low[2], high[2], other.low[2], other.high[2]);

        return new Box3D(xl[0], yl[0], zl[0], xl[1], yl[1], zl[1]);
    }

    public boolean contains(Box3D boundingBox) {
        for(int i = 0; i<3; i++){
            if(boundingBox.low[i]<low[i] || boundingBox.high[i]>high[i]){
                return false;
            }
        }
        return true;
    }

    public double getVolume() {
        return (high[0] - low[0])*(high[1]-low[1])*(high[2] - low[2]);
    }

    public double[] getCenter() {
        return Vector3DOps.average(high, low);
    }

    @Override
    public String toString(){
        return "Box3D: " + Arrays.toString(low) + " :: " + Arrays.toString(high);
    }

    public void translate(double[] delta){
        for(int i = 0; i<low.length; i++){
            low[i] += delta[i];
            high[i] += delta[i];
        }
    }

    public void setCenter(double[] center){
        double[] old = getCenter();
        old[0] = center[0] - old[0];
        old[1] = center[1] - old[1];
        old[2] = center[2] - old[2];
        translate(old);
    }


}

