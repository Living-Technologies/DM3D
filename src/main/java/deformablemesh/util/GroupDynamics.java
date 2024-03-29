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
package deformablemesh.util;

import Jama.LUDecomposition;
import Jama.Matrix;
import deformablemesh.DeformableMesh3DTools;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.geometry.Sphere;
import deformablemesh.io.MeshWriter;
import deformablemesh.track.Track;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GroupDynamics {
    public static List<Track> steppingMeshes(List<Track> tracks, int i){
        return tracks.stream().filter(
                t -> t.containsKey(i) && t.containsKey(i+1)
        ).collect(Collectors.toList());
    }
    public static List<DeformableMesh3D> getMeshInFrame(List<Track> tracks, int i){
        List<DeformableMesh3D> meshes = new ArrayList<>(tracks.size());
        for(Track t: tracks){
            DeformableMesh3D mesh = t.getMesh(i);
            if(mesh == null){
                throw new RuntimeException("Track " + t.getName() + " does not have a track at " + i);
            }
            meshes.add(mesh);
        }
        return meshes;
    }
    static public double[] getCenterOfMass(List<DeformableMesh3D> meshes){
        return new MeshGroup(meshes).cm;
    }
    static public double getMass(List<DeformableMesh3D> meshes){
            return new MeshGroup(meshes).volume;
    }
    static public  MeshGroup createMeshGroup( List<DeformableMesh3D> meshes){
        return new MeshGroup(meshes);
    }
    /**
     * Consider that a rigid body rotates the group of meshes. This is found by calculating the inertial matrix
     * about the center of mass, and the change in first moment about the center of mass.
     *
     * The idea being the mass has a constant shape and undergoes a rigid rotation momentum = nu I
     */
    public static double[] getAxisRotation( List<DeformableMesh3D> previous, List<DeformableMesh3D> next) {
        MeshGroup g0 = new MeshGroup(previous);
        MeshGroup g1 = new MeshGroup(previous);

        double[][] inertialMatrix = new double[3][3];
        double[] angularMomentum = new double[3];

        for (int i = 0; i < previous.size(); i++) {
            DeformableMesh3D start = previous.get(i);
            DeformableMesh3D fin = next.get(i);

            double[] cs = DeformableMesh3DTools.centerAndRadius(start.nodes);
            double[] cf = DeformableMesh3DTools.centerAndRadius(fin.nodes);

            double[] csp = Vector3DOps.difference(cs, g0.cm);
            double[] cfp = Vector3DOps.difference(cf, g1.cm);

            double[] v = Vector3DOps.difference(cfp, csp);

            double[] r = Vector3DOps.average(csp, cfp);

            double v1 = fin.calculateVolume();
            double v2 = start.calculateVolume();

            double factor = 0.5 * (v1 + v2);

            for (int k = 0; k < 3; k++) {
                int a = k;
                int b = (k + 1) % 3;
                int c = (k + 2) % 3;

                inertialMatrix[a][a] += (r[b] * r[b] + r[c] * r[c]) * factor;
                inertialMatrix[b][a] += -r[b] * r[a] * factor;
                inertialMatrix[c][a] += -r[c] * r[a] * factor;
            }

            double[] angMom = Vector3DOps.cross(r, v);
            angularMomentum[0] += angMom[0]*factor;
            angularMomentum[1] += angMom[1]*factor;
            angularMomentum[2] += angMom[2]*factor;
        }

        Matrix iM = new Matrix(inertialMatrix);
        LUDecomposition lu = new LUDecomposition(iM);
        try {
            Matrix s = lu.solve(new Matrix(angularMomentum, 3));
            //netDisplacement[i] = Vector3DOps.mag(Vector3DOps.average(center0, center1))*meshImageStack.SCALE;
            return s.getRowPackedCopy();
        } catch(Exception e){

        }
        return new double[]{0, 0, 0};
    }

    static List<Track> createTracks(){
        List<Track> tracks = new ArrayList<>();
        double angle = 0.02;
        double[] axis = { -1, 0, 0};
        double[] O = {0, 0, 0};


        for(int i = 0; i < 50; i ++ ){
            List<DeformableMesh3D> meshes = generateGrid();
            for(int j = 0; j<i; j++){
                meshes.forEach(m ->{
                    m.rotate(axis, O, angle);
                });
            }
            if(tracks.size() == 0){
                tracks = meshes.stream().map( m-> new Track(m.toString())).collect(Collectors.toList());
            }
            for(int j = 0; j<meshes.size(); j++){
                tracks.get(j).addMesh(i, meshes.get(j));
            }
        }
        return tracks;
    }
    static public List<DeformableMesh3D> generateGrid(){
        List<DeformableMesh3D> meshes = new ArrayList<>();
        int n = 3;
        double ds = 1.0/n;
        double ox = -ds;
        double oy = -ds;
        double oz = -ds;
        double r = ds/4;
        for(int i = 0; i<3; i++){
            for(int j = 0; j<3; j++){
                for(int k = 0; k<3; k++){
                    double x = ds * i + ox;
                    double y = ds * j + oy;
                    double z = ds * k + oz;
                    double[] center = {x, y, z};
                    Sphere sp = new Sphere(center, r);
                    DeformableMesh3D mesh = RayCastMesh.rayCastMesh(sp, center, 1);
                    meshes.add(mesh);
                }
            }
        }
        return meshes;
    }
    public static void main(String[] args) throws IOException {
        List<Track> tracks = createTracks();
        int min = tracks.stream().mapToInt(Track::getFirstFrame).summaryStatistics().getMin();
        int max = tracks.stream().mapToInt(Track::getLastFrame).summaryStatistics().getMax();
        List<DeformableMesh3D> last = null;
        for(int i = min; i <= max; i++){
            final int f = i;
            List<DeformableMesh3D> current = tracks.stream().filter(
                    t->t.containsKey(f)).map(t->t.getMesh(f)).collect(Collectors.toList()
            );
            double[] cm = getCenterOfMass(current);

        }
        MeshWriter.saveMeshes(new File("rotating-grid.bmf"), tracks);

    }
}
