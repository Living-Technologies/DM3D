package deformablemesh.examples;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.ConnectionRemesher;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.LoadZarr;
import deformablemesh.io.MeshReader;
import deformablemesh.track.Track;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

public class DeformMeshes {
    static double weight = 2e-5;
    static double ALPHA = 1.0;
    static double BETA = 0.1;
    static double GAMMA = 1000;
    static double MEAN_LENGTH=0.004;

    public static void refineMeshes(String image, String meshes) throws IOException {
        List<Track> tracks = MeshReader.loadMeshes(new File(meshes));
        MeshImageStack stack = LoadZarr.loadMeshImageStack2(Paths.get(image));
        Path meshPath = Paths.get(meshes).toAbsolutePath();
        Path refinedFolder = meshPath.getParent().resolve("refined-meshes");
        Files.createDirectories(refinedFolder);

        for(int i = 0; i<stack.getNFrames(); i++) {
            final Integer frame = i;
            stack.setFrame(i);
            MeshImageStack working = new MeshImageStack(stack.getCurrentFrame());
            working.gaussianBlur(2);
            working.copyValues();
            tracks.stream().parallel().forEach(track -> {
                if(!track.containsKey(frame)) return;
                ConnectionRemesher remesh = new ConnectionRemesher();
                remesh.setMeanLength(MEAN_LENGTH);
                DeformableMesh3D old = track.getMesh(frame);
                DeformableMesh3D mesh = remesh.remesh(old);
                mesh.ALPHA = ALPHA;
                mesh.BETA = BETA;
                mesh.GAMMA = GAMMA;
                //BrightRegionEnergy erg = new BrightRegionEnergy(working, mesh, weight);
                //mesh.addExternalEnergy(erg);
                for(int z = 0; z<500; z++){
                    mesh.update();
                }
                mesh.clearEnergies();
                track.addMesh(frame, mesh);
            });
        }
        tracks.sort(Comparator.comparingInt(Track::getFirstFrame));
        File out = refinedFolder.resolve(meshPath.getFileName()).toFile();
        //MeshWriter.saveMeshes(out, tracks);

    }

    public static void main(String[] args) throws IOException {
        String[] images = {"0014-1.zarr"};//, "0113-5.zarr", "0268-4.zarr", "0112-4.zarr", "0267-4.zarr", "0014-2.zarr", "0267-3.zarr", "0461-5.zarr"};

        Path data_folder = Paths.get("/Users/msmith5/working/jari/fis-first-draft/data");
        for(String image : images) {
            String mesh = image.replace(".zarr", ".bmf");
            Path mf = data_folder.resolve(mesh);
            Path img = data_folder.resolve(image);
            System.out.println("refining: " + img + " // " + mf);
            refineMeshes(img.toAbsolutePath().toString(), mf.toAbsolutePath().toString());
        }
        System.out.println("finished!");


    }


}
