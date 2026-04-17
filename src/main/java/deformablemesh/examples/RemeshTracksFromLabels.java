package deformablemesh.examples;

import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.gui.GuiTools;
import deformablemesh.io.LoadZarr;
import deformablemesh.io.MeshWriter;
import deformablemesh.simulations.FillingBinaryImage;
import deformablemesh.track.Track;
import deformablemesh.util.connectedcomponents.Region;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    boolean separate = false;
    Path separatedFolder = null;
    public void processFrame(Integer frame){
        //image.setFrame(frame);
        labels.setFrame(frame);
        Map<Integer, Track> mapped = new HashMap<>();
        MeshDetector detector = new MeshDetector(labels);
        List<Region> regions = detector.getRegionsFromLabelledImage();
        Map<Integer, List<Region>> mappedRegions = regions.stream().collect(
                Collectors.groupingBy(Region::getLabel)
        );
        List<Track> fixed = new ArrayList<>();
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
            double length = 1.4/image.SCALE;
            mesher.setMeanLength(length);
            mesher.setRelaxSteps(100);
            mesher.setRemeshSteps(3);

            DeformableMesh3D mesh = mesher.fillBlobWithMesh(regs.get(0));
            mesh.clearExtras();

            if(separate){
                Track meshed = new Track(t.getName());
                meshed.addMesh(frame, mesh);
                fixed.add(meshed);
            }else{
                t.addMesh(frame, mesh);
            }

        }

        if(separate) {
            try {
                MeshWriter.saveMeshes(new File("frame-" + frame + ".bmf"), fixed);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    public void setFolderOutput(Path op){
        separatedFolder = op;
        separate = true;
    }

    public static void processSet(Path ip, Path lp, Path tp) throws IOException{
        logger.log("processing: " + ip);
        Path rootFolder = ip.getParent();
        String tag = stem(ip.getFileName().toString());
        boolean isZarr = ip.getFileName().toString().endsWith(".zarr");
        boolean skip = Files.exists(rootFolder.resolve(tag + "_lbls.bmf") );

        MeshImageStack image;
        if(isZarr){
            image = LoadZarr.loadMeshImageStack2(ip);
        } else{
            image = MeshImageStack.fromVirtualTiff(ip.toAbsolutePath().toString());
        }
        List<Track> tracks = LoadAutJson.loadMeshes(tp.toFile(), image);

        Path out = rootFolder.resolve("spheres-" + tag + ".bmf");
        MeshWriter.saveMeshes(out.toFile(), tracks);

        if(skip){
            logger.log("exists!");
            return;
        }

        MeshImageStack lbls = LoadZarr.loadMeshImageStack2(lp);
        RemeshTracksFromLabels rtfl = new RemeshTracksFromLabels();
        //rtfl.setFolderOutput(rootFolder.resolve(tag+"_meshes"));

        rtfl.image = image;
        rtfl.labels = lbls;
        rtfl.tracks = tracks;
        for (int i = 0; i < image.getNFrames(); i++) {
            rtfl.processFrame(i);
        }
        MeshWriter.saveMeshes(rootFolder.resolve(tag + "_lbls.bmf").toFile(), rtfl.tracks);
    }
    static String stem(String filename){
        return filename.substring(0, filename.lastIndexOf("."));

    }
    public static void processKnowTags(Path rootFolder) throws IOException {
        List<String> names = Files.readAllLines(rootFolder.resolve("analysis_set.txt"));

        for(String image_name: names) {
            String tag = stem(image_name);
            Path ip = rootFolder.resolve(image_name);
            Path lp = rootFolder.resolve(tag + "_mesh-labels.zarr");
            Path tp = rootFolder.resolve(tag + ".aut");
            processSet(ip, lp, tp);
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
        Path base;
        if(args.length > 0){
            base = Paths.get(args[0]);
        } else{
            base = GuiTools.getDirectory(null, "Select a foldder").toPath();
        }
        try(        BufferedWriter writer = Files.newBufferedWriter( base.resolve("remesh-aut-tracks.txt") ) ){
            setLogger(writer);
            processKnowTags( base );
        }
    }


}
