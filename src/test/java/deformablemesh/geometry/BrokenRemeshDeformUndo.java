package deformablemesh.geometry;

import org.junit.Test;

public class BrokenRemeshDeformUndo {

    @Test
    public static void checkRemeshPlusUndo(){
        DeformableMesh3D mesh = RayCastMesh.sphereRayCastMesh(2);
        ConnectionRemesher remesher = new ConnectionRemesher();
        remesher.setMeanLength(0.01);
        DeformableMesh3D mesh2 = new DeformableMesh3D(mesh.positions, mesh.connection_index, mesh.triangle_index);
    }


}
