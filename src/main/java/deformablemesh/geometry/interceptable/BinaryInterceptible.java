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
package deformablemesh.geometry.interceptable;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.Intersection;
import deformablemesh.geometry.PixelBlob;
import deformablemesh.util.Vector3DOps;

import java.util.ArrayList;
import java.util.List;

/**
 * class for using
 */
public class BinaryInterceptible implements Interceptable {
    double[] center;
    List<double[]> edge;
    int label;
    double[] mins = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
    double[] maxs = {-mins[0], -mins[1], -mins[2]};
    PixelBlob blob;
    MeshImageStack stack;
    double[] offset;
    final static double fuzz = 0.0001;

    /**
     *
     * @param pixels index based image coordinates, z=0 is the first slice.
     * @param stack image the pixels are from, used for geometry.
     * @param label the label the pixels represent.
     */
    public BinaryInterceptible(List<int[]> pixels, MeshImageStack stack, int label){
        this.stack = stack;
        double[] img = new double[3];
        center = new double[3];
        edge = new ArrayList<>();
        this.label = label;
        for(int[] px: pixels){

            img[0] = px[0];
            img[1] = px[1];
            img[2] = px[2];

            double[] nspace = stack.getNormalizedCoordinate(img);
            center[0] += nspace[0];
            center[1] += nspace[1];
            center[2] += nspace[2];

            for(int j = 0; j<3; j++){
                mins[j] = Double.min(mins[j], nspace[j]);
                maxs[j] = Double.max(maxs[j], nspace[j]);
            }

            if(isEdge(px)){
                edge.add(nspace);
            }
        }




        blob = new PixelBlob(pixels);

        offset = stack.scaleToNormalizedLength(new double[]{0.5, 0.5, 0.5});
        center[0] = center[0]/pixels.size() + offset[0];
        center[1] = center[1]/pixels.size() + offset[1];
        center[2] = center[2]/pixels.size() + offset[2];
    }


    /**
     * Image to be check. int[] pt is in px,px, slice coordinates. px are 0 based indexes
     * and slice is 1 based.
     *
     * @param pt px, py, slice z points that represent the pixels in the stack.
     * @return
     */
    boolean isEdge(int[] pt){
        if(
                pt[0] == 0 || pt[0] == stack.getWidthPx() - 1
                || pt[1] == 0 || pt[1] == stack.getHeightPx() - 1
                || pt[2] == 0 || pt[2] == stack.getNSlices()  - 1
        ) {
            //edge of the image is an edge.
            return true;
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    if (stack.getValue(pt[0] + i - 1, pt[1] + j - 1, pt[2] + k - 1) != label) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Assuming the origin is contained within the shape, and and the pixels are points.
     * @param origin normalized coordinates
     * @param direction normalized coordinates
     * @return closest edge pixel the array passes.
     */
    @Override
    public List<Intersection> getIntersections(double[] origin, double[] direction) {
        List<Intersection> intersections = new ArrayList<>();
        for(double[] pt: edge){
            Intersection x = getXIntersection(origin, direction, pt);
            if(x != null){
                intersections.add(x);
            }
            Intersection y = getYIntersection(origin, direction, pt);
            if(y != null){
                intersections.add(y);
            }
            Intersection z = getZIntersection(origin, direction, pt);
            if(z != null){
                intersections.add(z);
            }
        }

        return intersections;
    }

    private Intersection getXIntersection(double[] origin, double[] direction, double[] pt) {
        if(direction[0] == 0){
            return null;
        }
        double x0 = direction[0] > 0 ? pt[0] + 2 * offset[0] : pt[0];
        double[] n = direction[0] > 0 ? Vector3DOps.xhat : Vector3DOps.nxhat;

        double dx = x0 - origin[0];
        double l = dx/direction[0];
        if( l > 0 ){
            double z0 = origin[2] + l * direction[2];
            double y0 = origin[1] + l * direction[1];
            if(
                    z0 >= pt[2] - fuzz * offset[2] && z0 <= pt[2] + 2 * (1 + fuzz) * offset[2] &&
                    y0 >= pt[1] - fuzz * offset[1] && y0 <= pt[1] + 2 * (1 + fuzz) * offset[1]  ){
                return new Intersection(new double[]{x0, y0, z0}, n);
            }
        }
        return null;
    }
    private Intersection getYIntersection(double[] origin, double[] direction, double[] pt) {
        if(direction[1] == 0){
            return null;
        }
        double y0 = direction[1] > 0 ? pt[1] + 2*offset[1] : pt[1];
        double[] n = direction[1] > 0 ? Vector3DOps.yhat : Vector3DOps.nyhat;

        double dy = y0 - origin[1];
        double l = dy/direction[1];
        if( l > 0 ){
            double z0 = origin[2] + l*direction[2];
            double x0 = origin[0] + l*direction[0];
            if(
                    x0 >= pt[0] - fuzz * offset[0] && x0 <= pt[0] + 2 * (1 + fuzz) * offset[0] &&
                    z0 >= pt[2] - fuzz * offset[2] && z0 <= pt[2] + 2 * (1 + fuzz) *  offset[2]  ){
                return new Intersection(new double[]{x0, y0, z0}, n);
            }
        }
        return null;
    }

    private Intersection getZIntersection(double[] origin, double[] direction, double[] pt) {
        if(direction[2] == 0){
            return null;
        }
        double z0 = direction[2] > 0 ? pt[2] + 2*offset[2] : pt[2];
        double[] n = direction[2] > 0 ? Vector3DOps.zhat : Vector3DOps.nzhat;

        double dz = z0 - origin[2];
        double l = dz/direction[2];
        if( l > 0 ){
            double x0 = origin[0] + l*direction[0];
            double y0 = origin[1] + l*direction[1];
            if(
                    x0 >= pt[0] - fuzz * offset[0] && x0 <= pt[0] + 2 * (1 + fuzz) * offset[0] &&
                    y0 >= pt[1] - fuzz * offset[1] && y0<= pt[1] + 2 * (1 + fuzz) * offset[1]  ){
                return new Intersection(new double[]{x0, y0, z0}, n);
            }
        }
        return null;
    }

    @Override
    public boolean contains(double[] pt){
        double[] img = stack.getImageCoordinates(pt);
        if( img[2] < 0 || img[1] < 0 || img[0] < 0){
            //negative image coordinates are always out of bound.
            //they also do not repsect "round down" behavior of truncation.
            return false;
        }

        int x = (int)img[0];
        int y = (int)img[1];
        int z = (int)img[2];
        return blob.contains(x, y, z);
    }


    public double[] getCenter() {
        return center;
    }
}
