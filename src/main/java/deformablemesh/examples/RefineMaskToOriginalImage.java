package deformablemesh.examples;

import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.externalenergies.BrightRegionEnergy;
import deformablemesh.geometry.ConnectionRemesher;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.LoadZarr;
import deformablemesh.io.MeshWriter;
import deformablemesh.simulations.FillingBinaryImage;
import deformablemesh.track.Track;
import deformablemesh.util.connectedcomponents.Region;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RefineMaskToOriginalImage {
    final MeshImageStack original;
    final MeshImageStack roughLabels;
    Path output;
    double meanLength = 0.009;
    public double alpha = 1.0;
    public double beta = 0.2;
    public double gamma = 1000;
    public double energyWeight = 1e-2;

    public RefineMaskToOriginalImage(MeshImageStack original, MeshImageStack roughLabels){
        this.original = original;
        this.roughLabels = roughLabels;
    }

    public void process(int frame){
        MeshImageStack labels;
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

            for(int j = 0; j<250; j++){
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

            for(int j = 0; j<250; j++){
                mesh2.update();
            }
            mesh2.clearEnergies();
            Track t = new Track("lbl_" + labelled.get(i));
            t.addMesh(frame, mesh2);
            tracks.add(t);
        }

        try {
            MeshWriter.saveMeshes(output.resolve("frame-"+ frame + ".bmf").toFile(), tracks);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void run(){
        int max = original.getNFrames()<roughLabels.getNFrames() ? original.getNFrames() : roughLabels.getNFrames();
        IntStream.range(0, max).parallel().forEach(this::process);
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
            return originalName.substring(0, stop) + "-refined-meshes";
        }
        return originalName + "-refined-meshes";
    }
    public static void main(String[] args) throws IOException {
        Path img = Paths.get(args[0]);
        Path lbl = Paths.get(args[1]);
        Path parent = img.getParent();
        Path output = parent.resolve(outputName(img.getFileName().toString()) );
        MeshImageStack stack = LoadZarr.loadMeshImageStack2(img);
        MeshImageStack roughLabels = LoadZarr.loadMeshImageStack2(lbl);
        RefineMaskToOriginalImage refinary = new RefineMaskToOriginalImage(stack, roughLabels);
        refinary.setOutput(output);
        refinary.run();
    }
}
