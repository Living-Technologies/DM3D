package deformablemesh.examples;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.io.MeshReader;
import deformablemesh.track.Track;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CreateCellposeLabels {
    MeshImageStack mis;
    List<Track> tracks;
    File folder = new File(".");
    public CreateCellposeLabels(MeshImageStack mis, List<Track> tracks){
        this.mis = mis;
        this.tracks = tracks;

    }
    public void setFolder(File f){
        folder = f;
    }

    public void process(){
        System.out.println("starting");
        ImagePlus iso = mis.getCurrentFrameIso();
        ImagePlus labels = DeformableMesh3DTools.asUniqueLabels( new MeshImageStack(iso), tracks);
        System.out.println("slicing");
        String top = mis.getOriginalPlus().getTitle() +"-t" + mis.CURRENT;

        ImageStack ixy = getXYSlices(iso);
        saveImages(ixy, top + "-XY", ".tif" );

        ImageStack lxy = getXYSlices(labels);
        saveImages(lxy, top + "-XY", "_masks.png" );

        ImageStack izx = getZXSlices(iso);
        saveImages(izx, top + "-ZX", ".tif" );

        ImageStack lzx = getZXSlices(labels);
        saveImages(lzx, top + "-ZX", "_masks.png" );

        ImageStack iyz = getYZSlices(iso);
        saveImages(iyz, top + "-YZ", ".tif" );

        ImageStack lyz = getYZSlices(labels);
        saveImages(lyz, top + "-YZ", "_masks.png" );
        System.out.println("finished");
    }

    void saveImages(ImageStack stack, String basename, String format){
        for(int i = 0; i<stack.size(); i++){
            String outname = basename + "-" + i + format;
            File f = new File(folder, outname);
            ImagePlus plus = new ImagePlus(outname, stack.getProcessor(i+1));
            IJ.save(plus, f.getAbsolutePath());
        }
    }
    ImageStack getXYSlices(ImagePlus plus) {
        int w = plus.getWidth();
        int h = plus.getHeight();
        ImageStack stack = new ImageStack(w, h);
        ImageStack original = plus.getStack();
        int slices = 16;
        int delta = plus.getNSlices() / slices;
        for (int i = 2 * delta; i <= plus.getNSlices() - 2 * delta; i += delta) {
            stack.addSlice(original.getProcessor(i));
        }
        return stack;
    }
    ImageStack getZXSlices(ImagePlus plus){
        int w = plus.getNSlices();
        int h = plus.getWidth();
        ImageStack stack = new ImageStack(w, h);
        ImageStack original = plus.getStack();
        int slices = 16;
        int delta = plus.getHeight()/slices;
        for(int i = 2*delta; i<=plus.getHeight() - 2*delta; i+=delta){

            ImageProcessor proc = original.getProcessor(1).createProcessor(w, h);
            stack.addSlice(proc);
            for(int z = 1; z<=plus.getNSlices(); z++){
                ImageProcessor op = original.getProcessor(z);
                for(int x = 0; x<plus.getWidth(); x++){
                    proc.set(z-1, x, op.get(x, i));
                }
            }
        }
        return stack;
    }

    ImageStack getYZSlices(ImagePlus plus){
        int w = plus.getHeight();
        int h = plus.getNSlices();
        ImageStack stack = new ImageStack(w, h);
        ImageStack original = plus.getStack();
        int slices = 16;
        int delta = plus.getWidth()/slices;
        for(int i = 2*delta; i<=plus.getWidth() - 2*delta; i+=delta){

            ImageProcessor proc = original.getProcessor(1).createProcessor(w, h);
            stack.addSlice(proc);
            for(int z = 1; z<=plus.getNSlices(); z++){
                ImageProcessor op = original.getProcessor(z);
                for(int y = 0; y<plus.getHeight(); y++){
                    proc.set( y, z-1, op.get(i, y));
                }
            }
        }
        return stack;
    }


    public static void main(String[] args) throws IOException {
        String img = IJ.getFilePath("choose image");
        String meshes = img.replace(".tif", ".bmf");
        ImagePlus plus = new ImagePlus( img );
        List<Track> tracks = MeshReader.loadMeshes(new File(meshes));

        CreateCellposeLabels ccpl = new CreateCellposeLabels(
                                         new MeshImageStack(plus),
                                         tracks
                                      );
        ccpl.process();
    }
}
