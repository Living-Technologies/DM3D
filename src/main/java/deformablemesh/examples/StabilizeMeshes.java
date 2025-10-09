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
package deformablemesh.examples;

import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.MeshReader;
import deformablemesh.io.MeshWriter;
import deformablemesh.track.Track;
import deformablemesh.util.GroupDynamics;
import deformablemesh.util.Vector3DOps;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StabilizeMeshes {
    File primary;
    List<File> auxilary;
    final static double[] origin = {0, 0, 0};
    public StabilizeMeshes(String primary, List<String> auxilary){
        this.primary = new File(primary);
        this.auxilary = auxilary.stream().map(File::new).collect(Collectors.toList());
    }


    public void process() throws IOException {
        List<Track> tracks = MeshReader.loadMeshes(primary);

        List<List<DeformableMesh3D>> stacked = new ArrayList<>();

        int min = tracks.stream().mapToInt(t->t.getFirstFrame()).min().getAsInt();
        int max = tracks.stream().mapToInt(t->t.getLastFrame()).max().getAsInt();

        List<double[]> rotations = new ArrayList<>();
        List<DeformableMesh3D> previous = null;
        List<double[]> cmTransform = new ArrayList<>();
        for(int i = min; i<=max; i++){
            int frame = i;
            List<DeformableMesh3D> meshes = tracks.stream().filter(
                    t->t.containsKey(frame)
            ).map(
                    t->t.getMesh(frame)
            ).collect(Collectors.toList());
            stacked.add(meshes);

            double[] cm = GroupDynamics.getCenterOfMass(meshes);
            cm[0] = -cm[0];
            cm[1] = -cm[1];
            cm[2] = -cm[2];
            meshes.forEach(m -> {
                m.translate(cm);
                for(double[] rotation: rotations){
                    m.rotate(rotation, origin, rotation[3]);
                }
            });
            cmTransform.add(cm);



            if(previous != null ) {
                List<Track> filtered = tracks.stream().filter(
                        t-> t.containsKey(frame - 1) && t.containsKey(frame)
                ).collect(Collectors.toList());

                List<DeformableMesh3D> first = filtered.stream().map(t->t.getMesh(frame-1)).collect(Collectors.toList());
                List<DeformableMesh3D> second = filtered.stream().map(t->t.getMesh(frame)).collect(Collectors.toList());


                double[] rotation = GroupDynamics.getAxisRotation(first, second);

                double angle = Vector3DOps.normalize(rotation);
                System.out.println( angle + " along " + Arrays.toString(rotation));

                rotations.add(new double[]{rotation[0], rotation[1], rotation[2], -angle});
                meshes.forEach(m->m.rotate(rotation, origin, -angle));
            }


            previous = meshes;

        }
        String on = primary.getName().replace(".bmf", "-aligned.bmf");
        File out = new File(primary.getParentFile(), on);
        MeshWriter.saveMeshes(out, tracks);

        //additional meshes can be transformed.
        for(File aux : auxilary){
            List<Track> at = MeshReader.loadMeshes(aux);
            int dex = 0;
            for(int j = min; j<=max; j++){
                int frame = j;
                double[] cm = cmTransform.get(dex);
                for(Track t: at){
                    if(t.containsKey(j)){
                        DeformableMesh3D m = t.getMesh(j);
                        m.translate(cm);
                        for(int k = 0; k<dex; k++){
                            double[] rot = rotations.get(k);
                            m.rotate(rot, origin, rot[3]);
                        }
                    }
                }
                dex++;
            }
            File auxOut = new File(aux.getParentFile(), aux.getName().replace(".bmf", "-aux-aligned.bmf"));
            MeshWriter.saveMeshes(auxOut, at );
        }

    }
    public static void main(String[] args) throws IOException {
        List<String> aux = new ArrayList<>();
        for(int i = 1; i<args.length; i++){
            aux.add(args[i]);
        }
        StabilizeMeshes sm = new StabilizeMeshes(args[0], aux);

    }



    List<Track> tracks;

}
