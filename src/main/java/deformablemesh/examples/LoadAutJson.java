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
import deformablemesh.io.MeshReader;
import deformablemesh.io.MeshWriter;
import deformablemesh.track.Track;
import lightgraph.Graph;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LoadAutJson {
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TrackingDataset{
        public String version;
        public String name;
        public List<MyPoint> positions;
        public List<MyTrack> tracks;
        public List<ImageOffset> image_offsets;
    }
    static class ImageOffset{
        public int _time_point_number;
        public int x, y, z;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MyTrack{
        public int time_point_start;
        public List<List<Double>> coords_xyz_px;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MyPoint{
        public int time_point;
        public List<List<Double>> coords_xyz_px;
        public PositionMetadata position_meta;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PositionMetadata{
        public List<String> type;
    }


    public static List<Track> loadMeshes(File jsonFile, MeshImageStack geometry) throws IOException {
        if(!jsonFile.exists()){
            throw new IOException("File not found: " + jsonFile.toString());
        }
        ObjectMapper mapper = new ObjectMapper();
        TrackingDataset dataset = mapper.readValue(jsonFile, new TypeReference<TrackingDataset>() {});
        List<Track> results = new ArrayList<>();

        Map<Integer, MyPoint> tp = new HashMap<>();
        for(MyPoint pt : dataset.positions){
            tp.put(pt.time_point, pt);
        }

        Map<Integer, ImageOffset> offsets = new HashMap<>();
        for(ImageOffset off : dataset.image_offsets){
            offsets.put(off._time_point_number, off);
        }

        List<MyTrack> tracks;
        if(dataset.tracks == null){
            tracks = new ArrayList<>();
            System.out.println("no tracks found to update");
        } else{
            tracks = dataset.tracks;
        }

        ImageOffset none = new ImageOffset();
        for(MyTrack track: tracks){
            Track t = new Track("" + (results.size()));
            String type = null;
            int time = track.time_point_start;
            for(List<Double> pt : track.coords_xyz_px){
                if(tp.containsKey(time)){
                    MyPoint timepoint = tp.get(time);
                    for(int i = 0; i<timepoint.coords_xyz_px.size(); i++){
                        List<Double> other = timepoint.coords_xyz_px.get(i);
                        if(pt.equals(other)){
                            String nt = timepoint.position_meta.type  != null ? timepoint.position_meta.type.get(i) : null;
                            if( type == null ){
                                type = nt;
                            } else{
                                if(!type.equals(nt)){
                                    System.out.println("changed type: " + type + ", " + nt);
                                }
                            }
                            break;
                        }
                    }
                }

                ImageOffset offset = offsets.getOrDefault(time, none);
                double[] xyz = new double[]{pt.get(0) - offset.x, pt.get(1) - offset.y, pt.get(2) - offset.z};
                double[] npt = geometry.getNormalizedCoordinate(xyz);
                DeformableMesh3D mesh = RayCastMesh.sphereRayCastMesh(1);
                mesh.translate(npt);
                mesh.scale(0.002, npt);
                t.addMesh(time++, mesh );
            }
            if(type != null){
                t.setName(t.getName() + "-" + type);
            }
            results.add(t);
        }
        return results;

    }

    /**
     * Reads the data file, a results.txt generated from shapy blobs. It maps
     * the frame/trackid to the computed shapy blob values.
     *
     * Note Track ID's can and probably are different at each timepoint!
     *
     * The Track ID can be the id of a mesh, or the id of a labelled component.
     *
     * @param dataFile
     * @return Map[ Time ID -> Map[ Track ID -> values ] ]
     */
    static public Map<Integer, Map<Integer, double[]>> getMappings(Path dataFile){
        Map<Integer, Map<Integer, double[]>> results = new HashMap<>();
        try{
            List<String> lines = Files.readAllLines(dataFile);
            lines.forEach(line -> {
                String[] tokens = line.split("\\s");
                int frame = Integer.parseInt(tokens[0]);
                int id = Integer.parseInt(tokens[1]);
                Map<Integer, double[]> box = results.computeIfAbsent(frame, i -> new HashMap<>());
                double[] data = new double[tokens.length - 2];
                for (int i = 0; i < data.length; i++) {
                    data[i] = Double.parseDouble(tokens[i + 2]);
                }
                box.put(id, data);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return results;
    }
    static Map<Integer, Integer> getLabelMap(List<Track> tracks, MeshImageStack labels){
        Map<Integer, Integer> labelMap = new HashMap<>();
        int frame = labels.CURRENT;
        for(int tid = 0; tid<tracks.size(); tid++){
            Track t = tracks.get(tid);
            if(t.containsKey(frame)){
                DeformableMesh3D mesh = t.getMesh(frame);
                double[] c = labels.getImageCoordinates(mesh.getBoundingBox().getCenter());
                int l = (int)labels.getValue((int)c[0], (int)c[1], (int)c[2]);
                if(labelMap.containsKey(l)){
                    labelMap.put(l, -1);
                } else{
                    labelMap.put(l, tid  );
                }
            }
        }
        return labelMap;
    }

    /**
     * Gets a map between the tracks and the meshes.
     * @param tracks
     * @param meshes
     * @param frame
     * @return a map that maps the meshes index/track id to the id in tracks.
     */
    public static Map<Integer, Integer> getLabelMap(List<Track> tracks, List<Track> meshes, int frame){
        Map<Integer, Integer> labelMap = new HashMap<>();
        for(int tid = 0; tid<tracks.size(); tid++){
            Track t = tracks.get(tid);
            if(t.containsKey(frame)){
                DeformableMesh3D mesh = t.getMesh(frame);
                double[] c = mesh.getBoundingBox().getCenter();
                for(int mid = 0; mid<meshes.size(); mid++) {
                    Track o = meshes.get(mid);
                    if (o.containsKey(frame) && o.getMesh(frame).getBoundingBox().contains(c)) {
                        int l = mid + 1;
                        if (labelMap.containsKey(l)) {
                            labelMap.put(l, -1);
                        } else {
                            labelMap.put(l, tid);
                        }
                    }
                }
            }
        }
        return labelMap;
    }


    public void processLabelledImage() throws IOException {
        String loc = "D:\\working\\maria\\Jurica\\Organoid 1 (lactate).aut";
        String zarr = "D:\\working\\maria\\Jurica\\1.zarr";
        String prediction = "D:/working/maria/Jurica/pred-1.zarr/";
        String resultsFile = "D:\\working\\maria\\Jurica\\1-mask-crops\\results.txt";
        MeshImageStack2<?> mis = LoadZarr.loadMeshImageStack2(Paths.get(zarr));
        MeshImageStack2<?> labels = LoadZarr.loadMeshImageStack2(Paths.get(prediction));
        List<Track> tracks = loadMeshes(new File(loc), mis);
        Map<Integer, Map<Integer, double[]>> map = getMappings(Paths.get(resultsFile));
        Map<Integer, List<double[]>> values = new HashMap<>();

        for(int f = 0; f<mis.getNFrames(); f++){
            long start = System.nanoTime();
            labels.setFrame(f);
            Map<Integer, double[]> labelsToValues = map.get(f);

            Map<Integer, Integer> label2Track = getLabelMap(tracks, labels);
            for(Integer label : label2Track.keySet()){
                Integer mid = label2Track.get(label);
                if(mid < 0){
                    continue;
                }
                double[] row = labelsToValues.get(mid);
                if(row == null){
                    continue;
                }
                double[] logd = new double[1 + row.length];
                logd[0] = f;
                System.arraycopy(row, 0, logd, 1, row.length);
                values.computeIfAbsent(mid, i ->new ArrayList<>()).add(logd);
            }
            long total = System.nanoTime() - start;

            System.out.println(f + " : " + total*1e-9);

        }

        Graph g = new Graph();
        for(Integer i : values.keySet()){

            List<double[]> xy = values.get(i);
            int n = xy.get(0).length;
            for(int k = 0; k<n-1; k++){
                double[] x = new double[xy.size()];
                double[] y = new double[xy.size()];
                for(int j = 0; j<xy.size(); j++){
                    x[j] = xy.get(j)[0];
                    y[j] = xy.get(j)[k+1];
                }
                g.addData(x, y);
            }
        }
        g.show(false, "5th element");

    }

    public static void processMeshAndAut() throws IOException {
        String loc = "D:\\working\\maria\\Jurica\\Organoid 1 (lactate).aut";
        String zarr = "D:\\working\\maria\\Jurica\\1.zarr";
        String resultsFile = "D:\\working\\maria\\Jurica\\1-mask-crops\\results.txt";
        MeshImageStack2<?> mis = LoadZarr.loadMeshImageStack2(Paths.get(zarr));
        List<Track> tracks = loadMeshes(new File(loc), mis);
        File rawOut = new File("D:\\working\\maria\\Jurica\\1-mask-crops\\raw-tracked-tracks.bmf");
        MeshWriter.saveMeshes(rawOut, tracks);
        Map<Integer, Map<Integer, double[]>> map = getMappings(Paths.get(resultsFile));
        Path meshBase = Paths.get("D:/working/maria/Jurica/relaxed_meshes-1");

        Map<Integer, List<double[]>> values = new HashMap<>();
        List<Track> replacements = new ArrayList<>();
        for(Track t : tracks){
            replacements.add(new Track(t.getName()));
        }

        List<Track> ohNo = new ArrayList<>();

        for(int f = 0; f<mis.getNFrames(); f++){
            long start = System.nanoTime();
            List<Track> meshes = MeshReader.loadMeshes(meshBase.resolve("frame-" + f + ".bmf").toFile());
            Map<Integer, double[]> labelsToValues = map.get(f);

            for(int i = 0; i<meshes.size(); i++){

                int key = i+1;
                Track t = meshes.get(i);

                if(labelsToValues.containsKey(key) && t.containsKey(f)){
                    ohNo.add(t);
                }
            }

            Map<Integer, Integer> meshToTrack = getLabelMap(tracks, meshes, f);
            for(Integer label : meshToTrack.keySet()){
                Integer mid = meshToTrack.get(label);
                if(mid < 0){
                    continue;
                }
                double[] row = labelsToValues.get(mid);
                if(row == null){
                    continue;
                }
                double[] logd = new double[2 + row.length];
                logd[0] = f;
                logd[1] = meshes.get(label - 1).getMesh(f).calculateVolume();
                replacements.get(mid-1).addMesh(f, meshes.get(label-1).getMesh(f));
                System.arraycopy(row, 0, logd, 2, row.length);
                values.computeIfAbsent(mid, i ->new ArrayList<>()).add(logd);
            }
            long total = System.nanoTime() - start;
            System.out.println(f + " : " + total*1e-9);

        }

        File cropped = new File("D:\\working\\maria\\Jurica\\1-mask-crops\\cropped-tracks.bmf");
        MeshWriter.saveMeshes(cropped, ohNo);
        File out = new File("D:\\working\\maria\\Jurica\\1-mask-crops\\analyzed-tracks.bmf");

        MeshWriter.saveMeshes(out, replacements.stream().filter(t->!t.isEmpty()).collect(Collectors.toList()));
        Graph g = new Graph();
        for(Integer i : values.keySet()){

            List<double[]> xy = values.get(i);
            if(xy.size() == 1){
                continue;
            }
            int n = xy.get(0).length;

            for(int k = 0; k<n/2; k++){
                double[] x = new double[xy.size()];
                double[] y = new double[xy.size()];
                for(int j = 0; j<xy.size(); j++){
                    x[j] = xy.get(j)[2*k];
                    y[j] = xy.get(j)[2*k + 1];
                }
                g.addData(x, y);
            }
        }
        g.show(false, "5th element");

    }

    public static void main(String[] args) throws IOException {
        Path pth = Paths.get("/Users/msmith5/working/maria/jurica/jurica-1.zarr");
        Path lbls = Paths.get("/Users/msmith5/working/maria/jurica/jurica-1_mesh-labels.zarr");
        Path aut = Paths.get("/Users/msmith5/working/maria/jurica/Organoid 1 (lactate).aut");

        Path base = pth.getParent();
        try(        BufferedWriter writer = Files.newBufferedWriter( base.resolve("remesh-aut-tracks.txt") ) ){
            RemeshTracksFromLabels.setLogger(writer);
            RemeshTracksFromLabels.processSet(pth, lbls, aut);
        }

    }


    public static void main2(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        File autFile = new File("/Users/msmith5/working/maria/jurica/Organoid 1 (lactate).aut");
        TrackingDataset dataset = mapper.readValue(autFile, new TypeReference<TrackingDataset>() {});
        System.out.println(dataset.name);
        System.out.println(dataset.tracks.size());
        System.out.println(dataset.positions.size());

        Map<Integer, MyPoint> tp = new HashMap<>();
        for(MyPoint pt : dataset.positions){
            tp.put(pt.time_point, pt);
        }
        int found = 0;
        int count = 0;

        for(MyTrack track: dataset.tracks){
            int dex = track.time_point_start;
            String type = null;
            for(List<Double> point : track.coords_xyz_px){
                count++;
                if(tp.containsKey(dex)){
                    MyPoint pt = tp.get(dex);
                    for(int i = 0; i<pt.coords_xyz_px.size(); i++){
                        List<Double> other = pt.coords_xyz_px.get(i);
                        if(point.equals(other)){
                            found++;
                            String nt = pt.position_meta.type  != null ? pt.position_meta.type.get(i) : null;
                            if(type == null){
                                type = nt;
                            } else{
                                if(!type.equals(nt)){
                                    System.out.println("changed type: " + type + ", " + nt);
                                }
                            }
                            break;
                        }
                    }
                }
                dex++;
            }
        }
        System.out.println("found: " + found + "of " + count);

        Path pth = Paths.get("/Users/msmith5/working/maria/jurica/jurica-1.zarr");
        String tag = pth.getFileName().toString().replace(".zarr", "");
        MeshImageStack image = LoadZarr.loadMeshImageStack2(pth);
        List<Track> tracks = LoadAutJson.loadMeshes(autFile, image);
        Path mout = pth.getParent().resolve("spheres-" + tag + ".bmf");

        MeshWriter.saveMeshes(mout.toFile(), tracks);
    }
}
