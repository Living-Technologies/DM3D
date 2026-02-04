package deformablemesh.examples;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.externalenergies.BrightRegionEnergy;
import deformablemesh.geometry.ConnectionRemesher;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.LoadZarr;
import deformablemesh.io.MeshReader;
import deformablemesh.io.MeshWriter;
import deformablemesh.simulations.FillingBinaryImage;
import deformablemesh.track.Track;
import deformablemesh.util.connectedcomponents.Region;
import ij.ImagePlus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class RefineMaskToOriginalImage {
    final MeshImageStack original;
    final MeshImageStack roughLabels;
    Path output;
    public double meanLength = 0.009;
    public double alpha = 1.0;
    public double beta = 0.1;
    public double gamma = 1000;
    public double energyWeight = 1e-2;
    public int deformSteps = 250;
    boolean overwrite = false;
    public String MESH_TAG="_refined-meshes";

    public RefineMaskToOriginalImage(MeshImageStack original, MeshImageStack roughLabels){
        this.original = original;
        this.roughLabels = roughLabels;
    }

    public void process(int frame){
        MeshImageStack labels;
        Path outPath = output.resolve("frame-"+ frame + ".bmf");
        if( (!overwrite) && Files.exists(outPath) ){
            try{
                List<Track> tracks = MeshReader.loadMeshes(outPath.toFile());
                return;
            } catch (IOException e) {
                System.out.println("Failed to read file, re-calculating.");
            }
        }

        synchronized (roughLabels){
            roughLabels.setFrame(frame);
            labels = new MeshImageStack(roughLabels.getCurrentFrame());
        }

        MeshDetector detector = new MeshDetector(labels);
        List<Region> regions = detector.getRegionsFromLabelledImage();
        FillingBinaryImage mesher = new FillingBinaryImage(labels);
        mesher.setMeanLength(meanLength);
        mesher.setRemeshSteps(3);
        mesher.setRelaxSteps(500);
        List<DeformableMesh3D> meshes = regions.stream().map(
                mesher::fillBlobWithMesh
        ).collect(Collectors.toList());
        List<Integer> labelled = regions.stream().map(
                Region::getLabel
        ).collect(Collectors.toList());

        MeshImageStack energy;
        synchronized(original){
            original.setFrame(frame);
            energy = new MeshImageStack(original.getCurrentFrame());
        }
        List<Track> tracks = new ArrayList<>();
        for(int i = 0; i<meshes.size(); i++){
            DeformableMesh3D mesh = meshes.get(i);

            BrightRegionEnergy grad = new BrightRegionEnergy(energy, mesh, energyWeight);
            mesh.addExternalEnergy(grad);
            mesh.ALPHA = alpha;
            mesh.BETA = beta;
            mesh.GAMMA = gamma;

            for(int j = 0; j<deformSteps; j++){
                mesh.update();
            }
            mesh.clearEnergies();
            ConnectionRemesher con = new ConnectionRemesher();
            con.setMeanLength(meanLength);
            DeformableMesh3D mesh2 = con.remesh(mesh);
            mesh2.ALPHA = alpha;
            mesh2.BETA = beta;
            mesh2.GAMMA = gamma;

            BrightRegionEnergy grad2 = new BrightRegionEnergy(energy, mesh2, energyWeight);
            mesh2.addExternalEnergy(grad2);

            for(int j = 0; j<deformSteps; j++){
                mesh2.update();
            }
            mesh2.clearEnergies();
            Track t = new Track("lbl_" + labelled.get(i));
            t.addMesh(frame, mesh2);
            tracks.add(t);
        }

        try {
            MeshWriter.saveMeshes(outPath.toFile(), tracks);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void run(){
        int max = original.getNFrames()<roughLabels.getNFrames() ? original.getNFrames() : roughLabels.getNFrames();
        ArrayDeque<Callable<Integer>> queue = new ArrayDeque<>();
        for(int i = 0; i<max; i++){
            final int frame = i;
            queue.add(()->{
                process(frame);
                return frame;
            });
        }
        ExecutorService service = ForkJoinPool.commonPool();
        int working = ForkJoinPool.getCommonPoolParallelism();
        System.out.println("Processing: " + queue.size() + " tasks with " + working + " jobs");
        Deque<Future<Integer>> running = new ArrayDeque<>();
        for(int i = 0; i<working && queue.size() > 0; i++){
            running.add(service.submit(queue.pop()));
        }
        while(!running.isEmpty()){
            try {
                Integer finished = running.pop().get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
            if( !queue.isEmpty() ){
                running.add(service.submit(queue.pop()));
            }
        }
        System.out.println("Successfully completed!");
    }

    public void setOutput(Path op) throws IOException {
        output = op;
        if(!Files.exists(op)){
            Files.createDirectories(op);
        }
    }
    static String outputName(String originalName){
        int stop = originalName.lastIndexOf('.');
        if(stop >= 0){
            return originalName.substring(0, stop) + "_refined-meshes";
        }
        return originalName + "_refined-meshes";
    }

    /**
     * The string provides a path for the labels and original data that will be loaded
     * and processed.
     *
     * @param originalImage
     * @param labels
     * @throws IOException
     */
    public static void refineMeshes(String originalImage, String labels) throws IOException {
        Path img = Paths.get(originalImage);
        Path lbl = Paths.get(labels);
        Path parent = img.getParent();
        MeshImageStack stack = LoadZarr.loadMeshImageStack2(img);
        MeshImageStack roughLabels = LoadZarr.loadMeshImageStack2(lbl);

        RefineMaskToOriginalImage refinary = new RefineMaskToOriginalImage(stack, roughLabels);

        String meshName = outputName(img.getFileName().toString());
        Path output = parent.resolve(meshName);


        refinary.setOutput(output);
        refinary.run();
    }
    public static void main(String[] args) throws IOException {
        System.out.println( Runtime.getRuntime().maxMemory() * 1e-6 );
        refineMeshes(args[0], args[1]);
    }

}
