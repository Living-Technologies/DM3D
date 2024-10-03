package deformablemesh.util;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.Box3D;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Intersection;
import deformablemesh.geometry.interceptable.InterceptingMesh3D;
import ij.ImageStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MeshVolumeToBinary {

    public static void mosaicBinary(MeshImageStack stack, ImageStack out, DeformableMesh3D mesh, int rgb) {
        List<int[]> px = getContainedPixels(stack, mesh);
        for(int[] pt: px){
            out.getProcessor(pt[2] + 1).set(pt[0], pt[1], rgb);
        }
    }

    /**
     * This puts topographical constraints on the intersections. When an intersection is 'dirty'
     * it means it was decided at a region of low confidence, eg near the edge of a triangle.
     *
     * @param sections a list of intersections that will be scanned
     */
    public static void scanDirty(List<Intersection> sections) {
        for (int i = 0; i < sections.size(); i++) {
            Intersection section = sections.get(i);
            if (section.dirty != 0) {
                int startI = i;
                double min = Double.MAX_VALUE;
                for (int j = 0; j < sections.size(); j++) {
                    if (j == startI) {
                        continue;
                    }
                    Intersection other = sections.get(j);

                    double m = Vector3DOps.mag(Vector3DOps.difference(other.location, section.location));
                    if (m < min) {
                        min = m;
                    }
                    if (m < Math.abs(section.dirty)) {
                        if (startI > i) {
                            continue;
                        }
                        sections.remove(i);
                        i--;
                        j--;
                    }
                }
            }
        }


    }

    public static List<int[]> getContainedPixels(MeshImageStack stack, DeformableMesh3D mesh){
        Box3D box = mesh.getBoundingBox();
        double[] lowI = stack.getImageCoordinates(box.low);
        double[] highI = stack.getImageCoordinates(box.high);

        InterceptingMesh3D picker = new InterceptingMesh3D(mesh);
        double[] xdirection = {1, 0, 0};

        int slices = stack.getNSlices();
        int w = stack.getWidthPx();
        int h = stack.getHeightPx();
        double[] center = new double[3];

        int sliceLow = (int) lowI[2];
        int sliceHigh = (int) highI[2];
        //verify
        sliceLow = sliceLow < 0 ? 0 : sliceLow;
        sliceHigh = sliceHigh < slices ? sliceHigh : slices;

        int jlo = (int) lowI[1];
        int jhi = (int) highI[1];
        jlo = jlo < 0 ? 0 : jlo;
        jhi = jhi <= h ? jhi : h;

        int xlo = (int) lowI[0];
        int xhi = (int) highI[0];
        xlo = xlo < 0 ? 0 : xlo;
        xhi = xhi > w ? w : xhi;
        int n = (xhi - xlo)*(jhi - jlo)*(sliceHigh - sliceLow);
        List<int[]> contained = new ArrayList<>(n);

        for (int slice = sliceLow; slice < sliceHigh; slice++) {

            center[2] = slice + 0.5;


            for (int j = jlo; j < jhi; j++) {

                int offset = j * w;
                center[1] = j + 0.5;

                List<Intersection> sections = picker.getIntersections(stack.getNormalizedCoordinate(center), xdirection);
                scanDirty(sections);
                if (sections.size() % 2 != 0) {
                    for (int zeta = 0; zeta < 3; zeta += 2) {
                        center[1] = j - 0.1 + zeta * 0.1;
                        sections = picker.getIntersections(stack.getNormalizedCoordinate(center), xdirection);
                        scanDirty(sections);
                        if (sections.size() % 2 == 0) {
                            break;
                        }
                    }
                }

                if (sections.size() == 0) {
                    //No intersections. No points inside.
                    continue;
                }


                sections.sort((a, b) -> Double.compare(a.location[0], b.location[0]));

                boolean startInside = false;
                double count = 0;
                double[] boundaries = new double[sections.size() + 1];

                //the number of boundaries that switch the state from inside to outside.
                int valid = 0;
                double lowestEntry = Double.MAX_VALUE;
                double highestExit = -Double.MAX_VALUE;

                for (int k = 0; k < sections.size(); k++) {

                    double bound = stack.getImageCoordinates(sections.get(k).location)[0];

                    boolean facingLeft = sections.get(k).surfaceNormal[0] < 0;
                    boolean facingRight = !facingLeft;
                    //going through all interfaces, and either going further in
                    //or back out.
                    if (facingLeft) {
                        count++;
                        if (bound < lowestEntry) {
                            lowestEntry = bound;
                        }
                    } else {
                        count--;
                        if (bound > highestExit) {
                            highestExit = bound;
                        }
                    }
                    if (bound > 0) {
                        //check if it is actually a boundary
                        if (count == 1 && facingLeft) {
                            //boundary entering region.
                            if (valid == 0) {
                                startInside = false;
                            }
                            boundaries[valid] = bound;
                            valid++;
                        } else if (count == 0 && facingRight) {
                            //stepped out.
                            if (valid == 0) {
                                startInside = true;
                            }
                            boundaries[valid] = bound;
                            valid++;
                        }

                    }
                }

                boolean inside = startInside;


                if (lowestEntry < lowI[0]) {
                    System.out.println("Topo Error: lowest entry is less than bounding box!");
                }
                if ((int) highestExit > highI[0]) {
                    System.out.println("Topo Error: highest exit is outside of bounding box!");
                }

                if (startInside && lowestEntry > 0) {
                    System.out.println("Topo Error: Lower bound above zero but mesh starts inside.");
                }

                boundaries[valid] = w;

                //This isn't necessarily true.
                //lowestIntersection >= lowI[0] and highestIntersection <= highI[0]
                boolean finishesOutsideImage = lowestEntry <= (w - 1) && highestExit >= (w - 1);

                int current = 0;


                for (int p = 0; p < w; p++) {
                    if (p == boundaries[current]) {
                        //switch.
                        current++;
                        inside = !inside;
                    }
                    if (inside && p <= xhi) {
                        contained.add(new int[]{p,j, slice});
                    }
                    if (p > xhi && inside) {
                    }
                }
                if (finishesOutsideImage && !inside) {
                    System.out.println("topography warning: bounds outside image, but not inside the shape at end");
                }

                if (!finishesOutsideImage && inside) {
                    System.out.println("Inconsistent bounding box: End of image is out of bounds, but state is inside the shape");
                    System.out.println("slice: " + slice + ", y: " + j);
                    System.out.println(Arrays.toString(lowI) + " [~] " + Arrays.toString(highI));
                }

            }

        }
        return contained;
    }

}
