package deformablemesh.examples;

import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.io.MeshWriter;
import deformablemesh.track.Track;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RotatingSphere {
    public static void main(String[] args) throws IOException {

        Track t = new Track("blue");
        for(int i = 0; i<10; i++){
            DeformableMesh3D mesh = RayCastMesh.fiveTriangleSphere();
            mesh.rotate(new double[]{0, 0, 1}, new double[]{0, 0, 0}, i*Math.PI/10 );
            t.addMesh(i, mesh);
        }
        List<Track> tracks = new ArrayList<>();
        tracks.add(t);
        MeshWriter.saveMeshes(new File("rotating-shape.bmf"), tracks);
    }
}
