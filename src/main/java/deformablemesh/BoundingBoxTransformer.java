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
package deformablemesh;

import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Node3D;
import deformablemesh.track.Track;
import deformablemesh.util.Vector3DOps;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.FileInfoVirtualStack;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * This class will transform a mesh in normalized coordiantes of one image into the normalized coordinates of another
 * image.
 *
 * The origin units are assumed to be in "pixels" of the displayed image, I've interpretted that as
 * px, px, slice. Given an original image with bounding box, [(0, 0, 0), (W, H, D)] then another
 * bounding box [ (x0, y0, z0), (w, h, d) ] that is contained within the origin bounding box.
 *
 * The resulting origin is (-x0, -y0, -z0).
 */
public class BoundingBoxTransformer {

    //origin of bounding boxes in real units.
    double[] o1, o2;
    MeshImageStack source, destination;

    /**
     * This will transform normalized coordinates of the source image to normalized coordinates of the destination image.
     *
     * @param source image that the mesh currently exists in.
     * @param destination image that the mesh will represent
     */
    public BoundingBoxTransformer( MeshImageStack source, MeshImageStack destination){
        this.source = source;
        Calibration cal = source.original.getCalibration();
        //origin in real units
        o1 = new double[]{ cal.xOrigin * cal.pixelWidth, cal.yOrigin * cal.pixelHeight, cal.zOrigin * cal.pixelDepth};
        this.destination = destination;
        cal = destination.original.getCalibration();
        o2 = new double[]{ cal.xOrigin * cal.pixelWidth, cal.yOrigin * cal.pixelHeight, cal.zOrigin * cal.pixelDepth};

    }

    /**
     * Transforms a point from the source normalized coordinates to a point in the
     * destination normalized coordinates.
     *
     * @param pt3 the location of a point in the source MeshImageStack Normalized coordinates
     * @return location of the same point in the destination MeshImageStack
     */
    public double[] transform(double[] pt3){
        //px, px, slice
        double[] r1 = source.getImageCoordinates(pt3);
        Calibration cal = source.original.getCalibration();

        //real unit coordinate.
        r1[0] = r1[0]*cal.pixelWidth;
        r1[1] = r1[1]*cal.pixelHeight;
        r1[2] = r1[2]*cal.pixelDepth;

        //real coordinates global space
        double[] R = Vector3DOps.add(r1, o1, -1);

        //real coordinates destination space
        double[] r2 = Vector3DOps.add(R, o2, 1);
        cal = destination.original.getCalibration();

        r2[0] = r2[0]/cal.pixelWidth;
        r2[1] = r2[1]/cal.pixelHeight;
        r2[2] = r2[2]/cal.pixelDepth;
        //r2 is image coordinates destination space.

        return destination.getNormalizedCoordinate(r2);

    }

    /**
     * Applies "transform" to all of the points in the mesh.
     *
     * @param mesh that gets transformed.
     */
    public void transformMesh(DeformableMesh3D mesh){
        for(Node3D node: mesh.nodes){
            node.setPosition(transform(node.getCoordinates()));
        }
    }

    /**
     * Applies transformMesh to all of the meshes in the track.
     *
     * @param track to be transformed.
     */
    public void transformTrack(Track track){
        for(Integer i: track.getTrack().keySet()){
            transformMesh(track.getMesh(i));
        }
    }


}
