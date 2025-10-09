package deformablemesh.experimental;

import deformablemesh.MeshImageStack;
import deformablemesh.SegmentationController;
import deformablemesh.gui.GuiTools;
import deformablemesh.io.LoadZarr;
import deformablemesh.track.Track;
import ij.IJ;
import lightgraph.Graph;
import org.scijava.command.Command;
import org.scijava.log.AbstractLogService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import deformablemesh.gimli2b.Dm3dService;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ColorCommand implements Command{
    @Parameter
    Dm3dService service;
    @Parameter
    ObjectService objects;
    SegmentationController controls;
    Path dataFile;
    public void loadData(){
        try {
            controls.setMeshImageStack(
                    MeshImageStack.fromVirtualTiff("D:/working/dopm/binned-images/Tile_5_processed_binned-2b.tif")
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        controls.loadMeshes(new File("D:/working/dopm/dna-meshes/Tile_5_dna.bmf"));
    }
    public Map<Integer, Map<Integer, double[]>> getMappings(){
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

    public void plotMode(List<Track> tracks, Map<Integer, Map<Integer, double[]>> mapping, int mode){
        Graph graph = new Graph();
        for(int i = 0; i<tracks.size(); i++){

            Track track = tracks.get(i);
            int id = i+1;
            int n = track.size();
            double[] x = new double[n];
            double[] y = new double[n];
            int dex = 0;
            for(Integer key : track.getTrack().keySet()){
                x[dex] = key;
                Map<Integer, double[]> rows = mapping.get(key);
                if(rows != null){
                    double[] data = rows.get(id);
                    if(data != null) {
                        y[dex] = data[mode];
                    }
                }
                dex++;
            }
            graph.addData(x, y);
        }
        graph.show(false, "column: " + mode);
    }
    @Override
    public void run() {
        controls = service.getApplicationController();
        loadData();
        //dataFile = GuiTools.getAFile(IJ.getInstance(), "Select results.txt file");
        dataFile = Paths.get("D:/working/dopm/binned-images/results-Tile_5_processed_binned-2b-mesh-crops.txt");
        System.out.println("\n\n\n\n\n");
        System.out.println(dataFile);
        System.out.println("\n\n\n\n\n");
        Map<Integer, Map<Integer, double[]>> mapping = getMappings();
        System.out.println("mapping.size()" + mapping.size());
        List<Track> tracks = controls.getAllTracks();
        System.out.println(tracks.size() + " tracks to check");
    }
}
