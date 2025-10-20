package deformablemesh.gimli2b;

import deformablemesh.geometry.Node3D;
import deformablemesh.geometry.Triangle3D;
import net.imglib2.mesh.Mesh;
import net.imglib2.mesh.impl.nio.BufferMesh;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Imglib2MeshReader {

    final long limit;
    File input;
    DataInputStream dis;
    long pos = 0;
    List<String> names;
    public Imglib2MeshReader(File meshFile){
        input = meshFile;
        this.limit = meshFile.length();
        names = new ArrayList<>();
    }
    public static List<Map<Integer, Mesh>> loadMeshes(File file) throws IOException {
        return new Imglib2MeshReader(file).loadMeshes();
    }
    private DataInputStream startReading() throws IOException{

        dis = new DataInputStream(
            new BufferedInputStream(
                            Files.newInputStream(input.toPath(), StandardOpenOption.READ)
            )
        );

        return dis;
    }


    public List<Map<Integer, Mesh>> loadMeshes() throws IOException{
        List<Map<Integer, Mesh>> tracks = new ArrayList<>();
        try(
                DataInputStream stream = startReading()
        ) {


            int version = dis.readInt();
            pos += Integer.BYTES;

            if (version > 0) {
                //instead of a version number there was a number of frames.
                throw new IOException("BMF version not supported by this reader");
            } else if (version == -1) {
                int trackCount = dis.readInt();
                pos += Integer.BYTES;
                for (int i = 0; i < trackCount; i++) {
                    Map<Integer, Mesh> t = loadTrack();
                    tracks.add(t);
                }
            } else {
                throw new IOException("Unsupported Version");
            }
        }
        return tracks;
    }

    /**
     * Reads a mesh from the provided DataInputStream and places it into the mapped data.
     *
     * @param dis input stream data will be read from.
     * @param map mapping of frames to meshes.
     * @throws IOException
     */
    private void readMesh(DataInputStream dis, Map<Integer, Mesh> map) throws IOException {
        int current = dis.readInt();
        pos += Integer.BYTES;
        int pos_count = checkCount(dis.readInt(), Double.BYTES);
        pos += Integer.BYTES;

        double[] positions = new double[pos_count];
        pos += pos_count * Double.BYTES;
        for (int j = 0; j < pos_count; j++) {
            positions[j] = dis.readDouble();
        }

        int con_count = checkCount(dis.readInt(), Integer.BYTES);
        pos += Integer.BYTES;
        int remaining = con_count*4;

        while(remaining > 0){
            long next = dis.skip(remaining);
            remaining -= next;
        }
        pos += con_count*Integer.BYTES;

        int tri_count = checkCount(dis.readInt(), Integer.BYTES);
        pos += Integer.BYTES;

        int[] triangle_indices = new int[tri_count];
        for (int j = 0; j < tri_count; j++) {
            triangle_indices[j] = dis.readInt();
        }
        pos += tri_count*Integer.BYTES;
        Mesh mesh = new BufferMesh(pos_count/3, tri_count/3);
        for( int i = 0; i<pos_count/3; i++){
            mesh.vertices().add(
                    positions[3*i],
                    positions[3*i + 1],
                    positions[3*i+2]
            );
        }

        for( int i = 0; i<tri_count/3; i++){
            mesh.triangles().add(
                    triangle_indices[3*i + 0],
                    triangle_indices[3*i + 1],
                    triangle_indices[3*i + 2]
            );
        }

        map.put(current, mesh);

    }

    private Map<Integer, Mesh> loadTrack() throws IOException {
        String name = dis.readUTF();
        names.add(name);
        pos += 2 + name.getBytes(StandardCharsets.UTF_8).length;

        int timePoints = dis.readInt();
        pos += Integer.BYTES;
        Map<Integer, Mesh> map = new HashMap<>();

        for(int i = 0; i<timePoints; i++){
            readMesh(dis, map);
        }

        return map;
    }


    /**
     * When a counting variable is read this check if it will go out of bounds or if it
     * @param count number of bytes being requested.
     * @param dataWidth width of data in bytes
     * @return count if it is not broken.
     */
    private int checkCount(int count, int dataWidth) throws IOException {
        if(count < 0 || count*dataWidth > (limit - pos)){
            throw new IOException("invalid count variable: " + count + ", file size " + limit + " read " + pos);
        }

        return count;
    }

}
