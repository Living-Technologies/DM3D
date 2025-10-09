package deformablemesh.examples;

import deformablemesh.BoundingBoxTransformer;
import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.Box3D;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.LoadZarr;
import deformablemesh.io.MeshReader;
import deformablemesh.io.MeshWriter;
import deformablemesh.io.SaveImageToZarr;
import deformablemesh.track.Track;
import ij.ImagePlus;
import ij.ImageStack;

import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

public class CropAlongMeshTrack {
    MeshImageStack stack;
    Track track;

    Track transformed;
    ImagePlus plus;
    CropAlongMeshTrack(MeshImageStack stack, Track track){
        this.stack = stack;
        this.track = track;
    }

    public void process(){
        double[] lengths = new double[3];
        for(Integer frame : track.getTrack().keySet()){
            DeformableMesh3D mesh = track.getMesh(frame);
            Box3D b = mesh.getBoundingBox();
            for(int i = 0; i<3; i++){
                double h = b.high[i];
                double l = b.low[i];
                double len = h - l;
                lengths[i] = lengths[i] < len ? len : lengths[i];
            }
        }
        transformed = new Track(track.getName() + "-" + "tfmd");

        ImageStack out = null;

        for(Integer frame : track.getTrack().keySet()){

            Box3D b = track.getMesh(frame).getBoundingBox();
            Box3D bb = new Box3D(b.getCenter(), lengths[0], lengths[1], lengths[2]);
            List<ImageStack> stacks = new ArrayList<>();
            for(int j = 0; j<stack.getNChannels(); j++){
                stack.setFrameAndChannel(frame, j);
                MeshImageStack cropped = stack.getCrop(bb);
                if(j == 0){
                    BoundingBoxTransformer bbt = new BoundingBoxTransformer(stack, cropped);
                    DeformableMesh3D mesh = DeformableMesh3DTools.copyOf(track.getMesh(frame));
                    bbt.transformMesh(mesh);
                    transformed.addMesh(frame, mesh);
                }
                stacks.add(cropped.getOriginalPlus().getStack());
                if(plus == null){
                    plus = cropped.getOriginalPlus();
                }
            }
            if(out == null){
                out = new ImageStack(plus.getWidth(), plus.getHeight());
            }

            for(int z = 1; z<=plus.getNSlices(); z++){
                for(ImageStack cs : stacks){
                    if(cs.size() < z ){
                        out.addSlice(cs.getProcessor(z-1));
                    } else{
                        out.addSlice(cs.getProcessor(z));
                    }
                }
            }
        }
        System.out.println(plus.getNChannels() + ", " + plus.getNSlices() + ", " + stack.getNFrames() + " // " + out.size());
        plus.setStack(out, stack.getNChannels(), plus.getNSlices(), stack.getNFrames());

    }


    public static void main(String[] args) throws Exception {
        Path p = Paths.get("E:\\org_44-r_0.zarr");
        Path op = Paths.get("E:", p.getFileName().toString().replace(".zarr", "-cropped.zarr"));
        Path m = Paths.get("D:\\working\\saskia-restoration\\org_44-r_2.bmf");
        Path mo = m.getParent().resolve(m.getFileName().toString().replace(".bmf", "-tmfd.bmf"));
        List<Track> tracks = MeshReader.loadMeshes(m.toFile());
        MeshImageStack stack = LoadZarr.loadMeshImageStack2(p);

        CropAlongMeshTrack camt = new CropAlongMeshTrack(stack, tracks.get(0));
        camt.process();
        SaveImageToZarr.saveToZarr(camt.plus, op);
        List<Track> transformed = new ArrayList<>();
        transformed.add(camt.transformed);
        MeshWriter.saveMeshes(mo.toFile(), transformed);



    }
}
