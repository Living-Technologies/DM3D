package deformablemesh.examples;

import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.LoadZarr;
import deformablemesh.io.MeshWriter;
import deformablemesh.simulations.FillingBinaryImage;
import deformablemesh.track.Track;
import deformablemesh.util.connectedcomponents.Region;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RemeshTracksFromLabels {
    interface PMLogger{
        public void log(String item);
    }
    static PMLogger logger;
    MeshImageStack image;
    MeshImageStack labels;
    List<Track> tracks;
    final static Track nullTrack = new Track("null");
    public void processFrame(Integer frame){
        //image.setFrame(frame);
        labels.setFrame(frame);
        Map<Integer, Track> mapped = new HashMap<>();
        MeshDetector detector = new MeshDetector(labels);
        List<Region> regions = detector.getRegionsFromLabelledImage();
        Map<Integer, List<Region>> mappedRegions = regions.stream().collect(
                Collectors.groupingBy(Region::getLabel)
        );

        for(Track t: tracks){

            if(t.containsKey(frame)){
                double[] center = t.getMesh(frame).getBoundingBox().getCenter();
                double[] xyz = labels.getImageCoordinates(center);
                int value = (int)detector.getLabel((int)xyz[0], (int)xyz[1], (int)xyz[2]);
                mapped.compute(value, (key, old)->{
                    if(old != null){ return nullTrack;}else{ return t; }
                });
            }
        }
        logger.log("regions: " + mappedRegions.keySet() );
        logger.log("tracks:  " + mapped.keySet() );
        for(Integer key : mapped.keySet()){
            Track t = mapped.get(key);
            if(t == nullTrack){
                continue;
            }
            if(!mappedRegions.containsKey(key)){
                //hopefully background.
                continue;
            }
            List<Region> regs = mappedRegions.get(key);
            if(regs.size() > 0){
                logger.log("meshes regions: ");
                String s = regs.stream().map(
                        r ->"\t" + r.getPoints().size() + ", " + r.getLabel()
                ).collect(Collectors.joining());
                logger.log(s);
            }

            FillingBinaryImage mesher = new FillingBinaryImage(image);
            mesher.setMeanLength(0.005);
            mesher.setRelaxSteps(100);
            mesher.setRemeshSteps(3);

            DeformableMesh3D mesh = mesher.fillBlobWithMesh(regs.get(0));
            mesh.clearExtras();
            t.addMesh(frame, mesh);
        }



    }
    public static void processKnowTags(Path rootFolder) throws IOException {
        String[] tags = {
                "20190525pos13",
                "20190817pos18",
                "20200516pos033",
                "20200614pos002",
                "20200614pos10",
                "20190817pos01",
                "20190926pos01",
                "20200516pos36",
                "20200614pos07"};
        for(String tag: tags) {

            Path ip = rootFolder.resolve(tag + ".zarr");
            Path lp = rootFolder.resolve(tag + "_masks.zarr");
            Path tp = rootFolder.resolve(tag + ".aut");
            logger.log("processing: " + ip);
            if(Files.exists(rootFolder.resolve(tag + "_lbls.bmf"))){
                logger.log("exists!");
                continue;
            }

            MeshImageStack image = LoadZarr.loadMeshImageStack2(ip);
            MeshImageStack lbls = LoadZarr.loadMeshImageStack2(lp);
            List<Track> tracks = LoadAutJson.loadMeshes(tp.toFile(), image);

            RemeshTracksFromLabels rtfl = new RemeshTracksFromLabels();
            rtfl.image = image;
            rtfl.labels = lbls;
            rtfl.tracks = tracks;
            for (int i = 0; i < image.getNFrames(); i++) {
                rtfl.processFrame(i);
            }

            MeshWriter.saveMeshes(rootFolder.resolve(tag + "_lbls.bmf").toFile(), rtfl.tracks);
        }
    }
    public static void setLogger(BufferedWriter writer){
        logger = str ->{
            try {
                writer.write(str);
                writer.write("\n");
            } catch (IOException e){
                System.out.println("logging broken: " + e);
            }
        };
    }
    public static void main(String[] args) throws IOException {
        Path base = Paths.get(args[0]);
        try(        BufferedWriter writer = Files.newBufferedWriter( base.resolve("remesh-aut-tracks.txt") ) ){
            setLogger(writer);
            processKnowTags( base );
        }
    }


}
