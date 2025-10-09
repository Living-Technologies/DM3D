package deformablemesh.examples;

import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.gui.GuiTools;
import deformablemesh.io.MeshReader;
import deformablemesh.track.Track;
import ij.IJ;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
public class BmfToQuickMesh {
    public static void main(String[] args) throws IOException {
        Path p = GuiTools.getAFile(IJ.getInstance(), "Select a mesh file");
        if(p == null) return;
        List<Track> meshes = MeshReader.loadMeshes(p.toFile());
        boolean first = true;
        Path op = p.getParent().resolve(
                p.getFileName().toString().replace(".bmf", ".dat")
        );

        try(DataOutputStream dos = new DataOutputStream(Files.newOutputStream(op))  ){
            for(Track t: meshes){
                for(Integer key : t.getTrack().keySet()){
                    DeformableMesh3D mesh = t.getMesh(key);
                    if(first){
                        dos.writeInt(mesh.triangle_index.length);
                        for(int index : mesh.triangle_index){
                            dos.writeInt(index);
                        }
                        dos.writeInt(mesh.connection_index.length);
                        for(int index : mesh.connection_index){
                            dos.writeInt(index);
                        }
                        first = false;
                    }
                    dos.writeInt(mesh.positions.length);
                    for(double d: mesh.positions){
                        dos.writeDouble(d);
                    }

                }

            }
        }


    }
}
