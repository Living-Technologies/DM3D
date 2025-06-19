package deformablemesh.examples;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.util.StdConverter;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.gimli2b.MeshImageStack2;
import deformablemesh.io.LoadZarr;
import deformablemesh.track.Track;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoadAutJson {
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TrackingDataset{
        public String version;
        public String name;
        public List<MyPoint> positions;
        public List<MyTrack> tracks;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MyTrack{
        public int time_point_start;
        public List<List<Double>> coords_xyz_px;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MyPoint{
        List<Double> values;
    }

    public static List<Track> loadMeshes(File jsonFile, MeshImageStack geometry) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        TrackingDataset dataset = mapper.readValue(jsonFile, new TypeReference<TrackingDataset>() {});
        List<Track> results = new ArrayList<>();
        for(MyTrack track: dataset.tracks){

            Track t = new Track("" + (results.size() + 1));
            int time = track.time_point_start;
            for(List<Double> pt : track.coords_xyz_px){

                double[] xyz = new double[]{pt.get(0), pt.get(1), pt.get(2)};
                double[] npt = geometry.getNormalizedCoordinate(xyz);
                DeformableMesh3D mesh = RayCastMesh.sphereRayCastMesh(1);
                mesh.translate(npt);
                mesh.scale(0.01, npt);
                t.addMesh(time++, mesh );
            }
            results.add(t);
        }
        return results;

    }

    public static void main(String[] args) throws IOException {
        String loc = "D:\\working\\maria\\Jurica\\Organoid 1 (lactate).aut";
        String zarr = "D:\\working\\maria\\Jurica\\1.zarr";
        MeshImageStack2<?> mis = LoadZarr.loadMeshImageStack2(Paths.get(zarr));
        List<Track> tracks = loadMeshes(new File(loc), mis);
        for(Track t: tracks){
            System.out.println(t.getName() + " : " + t.getTrack().size());
            for(Integer i : t.getTrack().keySet()){
                System.out.println("\t" + i + "//" + t.getMesh(i).calculateVolume());
            }
        }
    }
}
