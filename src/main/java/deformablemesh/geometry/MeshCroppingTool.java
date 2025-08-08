package deformablemesh.geometry;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.interceptable.InterceptingMesh3D;
import deformablemesh.gui.GuiTools;
import deformablemesh.io.LoadZarr;
import deformablemesh.io.MeshReader;
import deformablemesh.io.SaveImageToZarr;
import deformablemesh.track.Track;
import deformablemesh.util.Vector3DOps;
import deformablemesh.util.connectedcomponents.Region;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.FileInfoVirtualStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MeshCroppingTool {
    final double factor;
    final int size;

    static class PrincipleAxes{
        double[] e0, e1, e2;
        double[] cm;

        public PrincipleAxes(double[] cm, double[] e0, double[] e1, double[] e2) {
            this.cm = cm;
            this.e0 = e0;
            this.e1 = e1;
            this.e2 = e2;
        }
    }

    public MeshCroppingTool(){
        this(1.5, 64);
    }

    public MeshCroppingTool(double factor, int size){
        this.factor = factor;
        this.size = size;
    }

    private PrincipleAxes getPrincipleAxis(Region r, double[] pxSizes){
        double Ixx = 0;
        double Ixy = 0;
        double Ixz = 0;
        double Iyy = 0;
        double Iyz = 0;
        double Izz = 0;

        double IxxCm = 1.0/12.0*(pxSizes[1]*pxSizes[1] + pxSizes[2]*pxSizes[2]);
        double IyyCm = 1.0/12.0*(pxSizes[0]*pxSizes[0] + pxSizes[2]*pxSizes[2]);
        double IzzCm = 1.0/12.0*(pxSizes[1]*pxSizes[1] + pxSizes[0]*pxSizes[0]);
        double[] cm = new double[3];

        int n = r.getPoints().size();
        for(int[] xyz : r.getPoints()){
            cm[0] += xyz[0]*pxSizes[0];
            cm[1] += xyz[1]*pxSizes[1];
            cm[2] += xyz[2]*pxSizes[2];
        }
        cm[0] = cm[0]/n;
        cm[1] = cm[1]/n;
        cm[2] = cm[2]/n;
        for(int[] xyz : r.getPoints()){
            double z = xyz[2]*pxSizes[2] - cm[2];
            double y = xyz[1]*pxSizes[1] - cm[1];
            double x = xyz[0]*pxSizes[0] - cm[0];
            Ixx += (y*y + z*z) + IxxCm;
            Ixy += -x*y;
            Ixz += -x*z;
            Iyy += (x*x + z*z) + IyyCm;
            Iyz += -y*z;
            Izz += (x*x + y*y) + IzzCm;
        }
        double[] I = new double[]{
                Ixx, Ixy, Ixz,
                Ixy, Iyy, Iyz,
                Ixz, Iyz, Izz
        };

        Matrix mat = new Matrix(I, 3);
        EigenvalueDecomposition ed = mat.eig();
        List<double[]> values = new ArrayList<>(4);
        double[] ev = ed.getRealEigenvalues();
        double[] vecs = ed.getV().getColumnPackedCopy();
        for(int i = 0; i<3; i++){
            double[] vec = new double[3];
            for(int j = 0; j<3; j++){
                vec[j] = vecs[3*i + j];
            }
            Vector3DOps.normalize(vec);
            values.add(vec);
        }
        values.add(ev);

        PrincipleAxes pa = new PrincipleAxes(cm, values.get(0), values.get(1), values.get(2));

        return pa;

    }

    public CroppedVolume getCroppedMesh(DeformableMesh3D mesh, MeshImageStack stack, int label){
        BinaryMomentsOfInertia bmi = new BinaryMomentsOfInertia(mesh, stack);
        List<double[]> eigen = bmi.getEigenVectors();
        double[] cm = bmi.getCenterOfMass();
        cm[0] = cm[0] - stack.offsets[0];
        cm[1] = cm[1] - stack.offsets[1];
        cm[2] = cm[2] - stack.offsets[2];

        double[] e0 = eigen.get(0);
        double[] e1 = eigen.get(1);
        double[] e2 = eigen.get(2);
        PrincipleAxes pa = new PrincipleAxes(cm, e0, e1, e2);
        InterceptingMesh3D im3d = new InterceptingMesh3D(mesh);
        IsMasked isMasked = im3d::contains;
        CroppedVolume cropd = crop(pa, isMasked, stack, label);
        return cropd;
    }

    public CroppedVolume cropMeshImages(List<Track> tracks, MeshImageStack mist){
        int count = 0;
        CroppedVolume accumulated = null;
        int key = mist.CURRENT;
        for(int i = 0; i<tracks.size(); i++){
            Track t = tracks.get(i);
            int label = i+1;
            if( t.containsKey(key)){
                DeformableMesh3D mesh = t.getMesh(key);
                CroppedVolume mopd = getCroppedMesh(mesh, mist, label);
                if(mopd == null){
                    continue;
                }
                if(accumulated == null){
                    accumulated = mopd;
                } else {
                    int n = mopd.data.getNSlices();
                    for (int slice = 1; slice <= n; slice++) {
                        accumulated.data.getStack().addSlice(mopd.data.getStack().getProcessor(slice));
                        accumulated.mask.getStack().addSlice(mopd.mask.getStack().getProcessor(slice));
                    }
                    accumulated.attributes.addAll(mopd.attributes);
                }
                count += 1;
            }
        }
        accumulated.data.setStack(accumulated.data.getStack(), 1, size, count);
        accumulated.mask.setStack(accumulated.mask.getStack(), 1, size, count);
        accumulated.data.setOpenAsHyperStack(true);
        accumulated.mask.setOpenAsHyperStack(true);
        accumulated.data.setTitle("mesh-original");
        accumulated.mask.setTitle("mesh-labels");

        return accumulated;
    }
    interface IsMasked{
        public boolean isMask(double[] imageCoordinate);
    }
    CroppedVolume crop(PrincipleAxes pa, IsMasked maskIt, MeshImageStack stack, int label){
        double ds = stack.getMinPx()*factor;
        double l = ds*size;
        ImageStack crop = new ImageStack(size, size);
        ImageStack maskCrop = new ImageStack(size, size);
        double[] cm = pa.cm;
        double[] e0 = pa.e0;
        double[] e1 = pa.e1;
        double[] e2 = pa.e2;

        double oz = cm[2] - l/2*e0[2] - l/2*e1[2] - l/2*e2[2];
        double oy = cm[1] - l/2*e0[1] - l/2*e1[1] - l/2*e2[1];
        double ox = cm[0] - l/2*e0[0] - l/2*e1[0] - l/2*e2[0];
        for( int i = 0; i<size; i++){
            ImageProcessor cp = new ShortProcessor(size, size);
            ImageProcessor mp = new ByteProcessor(size, size);
            for(int j = 0; j<size; j++){
                for(int k = 0; k<size; k++){
                    double z = oz + i*ds*e0[2] + j*ds*e1[2]+ k*ds*e2[2];
                    double y = oy + i*ds*e0[1] + j*ds*e1[1]+ k*ds*e2[1];
                    double x = ox + i*ds*e0[0] + j*ds*e1[0]+ k*ds*e2[0];
                    double[] r = {x, y, z};
                    float f = (float)stack.getInterpolatedValue(r);
                    cp.setf(k, j, f);
                    if(stack.contains(r)) {
                        if(maskIt.isMask(r)){
                            mp.set(k, j, 1);
                        }
                    } else{
                        return null;
                    }
                }
            }
            crop.addSlice(cp);
            maskCrop.addSlice(mp);
        }

        ImagePlus plus = new ImagePlus();
        Calibration cal = plus.getCalibration();
        cal.pixelDepth = ds;
        cal.pixelWidth = ds;
        cal.pixelHeight = ds;
        plus.setCalibration(cal);

        plus.setStack(crop, 1, size, 1);

        ImagePlus plus2 = new ImagePlus();
        Calibration cal2 = plus.getCalibration();
        cal2.pixelDepth = ds;
        cal2.pixelWidth = ds;
        cal2.pixelHeight = ds;
        plus2.setCalibration(cal);
        plus2.setStack(maskCrop, 1, size, 1);
        String line = label + "\t" + cm[0] + "\t" + cm[1] + "\t" + cm[2]
                + "\t" + pa.e0[0] + "\t" + pa.e0[1] + "\t" + pa.e0[2]
                + "\t" + pa.e1[0] + "\t" + pa.e1[1] + "\t" + pa.e1[2]
                + "\t" + pa.e2[0] + "\t" + pa.e2[1] + "\t" + pa.e2[2];
        CroppedVolume ret = new CroppedVolume(plus, plus2);
        ret.attributes.add(line);
        return ret;
    }

    /**
     * Both images represent the same space, but they can have different dimensions.
     *
     * The
     * @param image
     * @param lbls
     * @return
     */
    CroppedVolume cropLabelledImage(MeshImageStack image, MeshImageStack lbls){

        CroppedVolume accumulated = null;
        int tally = 0;

        MeshDetector md = new MeshDetector(lbls);
        md.setSplitRegions(false);
        List<Region> regions = md.getRegionsFromLabelledImage();
        System.out.println("cropping regions: " + regions.size());

        double normalizedLength = image.getMinPx()*factor*size;
        double[] pxSizes = lbls.scaleToNormalizedLength(new double[]{1, 1, 1});
        int[] mxd = {
                (int)(normalizedLength/pxSizes[0] + 1),
                (int)(normalizedLength/pxSizes[1] + 1),
                (int)(normalizedLength/pxSizes[2] + 1)
        };
        int tooBig = 0;
        int ob = 0;
        for (Region r : regions) {
            int[] dims = r.getDimensions();
            if(dims[0]>mxd[0] || dims[1] > mxd[1] || dims[2] > mxd[2]){
                tooBig++;
                continue;
            }
            PrincipleAxes pa = getPrincipleAxis(r, lbls.pixel_dimensions);
            pa.cm[0] = pa.cm[0] / lbls.SCALE - lbls.offsets[0];
            pa.cm[1] = pa.cm[1] / lbls.SCALE - lbls.offsets[1];
            pa.cm[2] = pa.cm[2] / lbls.SCALE - lbls.offsets[2];

            final int label = r.getLabel();
            IsMasked maskIt = nc->{
                double[] ic = lbls.getImageCoordinates(nc);
                int ix = (int) ic[0];
                int iy = (int) ic[1];
                int iz = (int) ic[2];
                int px = (int)lbls.getValue(ix, iy, iz);
                return px == label;
            };

            CroppedVolume croppedVolume = crop(pa, maskIt, image, r.getLabel());
            if (croppedVolume == null) {
                ob++;
                continue;
            }

            if (accumulated == null) {
                accumulated = croppedVolume;
                tally++;
            } else {
                ImageStack stack = croppedVolume.data.getStack();
                ImageStack pop = accumulated.data.getStack();
                for (int i = 1; i <= stack.size(); i++) {
                    pop.addSlice(stack.getProcessor(i));
                }
                stack = croppedVolume.mask.getStack();
                ImageStack mpop = accumulated.mask.getStack();
                for (int i = 1; i <= stack.size(); i++) {
                    mpop.addSlice(stack.getProcessor(i));
                }
                ;
                tally++;
                accumulated.attributes.addAll(croppedVolume.attributes);
            }
        }
        System.out.println("tally: " + tally + ", too big: " + tooBig + ", out of image: " + ob);
        ImagePlus plus = accumulated.data;
        ImagePlus maskPlus = accumulated.mask;

        plus.setStack(plus.getStack(), 1, size, tally);
        maskPlus.setStack(maskPlus.getStack(), 1, size, tally);
        plus.setOpenAsHyperStack(true);
        maskPlus.setOpenAsHyperStack(true);
        plus.setTitle("lbls-original");
        maskPlus.setTitle("lbls-labels");
        return accumulated;
    }


    /**
     * Uses a gui to choose mesh file and
     */
    public void processMeshes() {
        Path tf = GuiTools.getAFile(IJ.getInstance(), "select original image data");

        if(tf == null) return;

        Path base = GuiTools.getAFile(IJ.getInstance(), "mesh file or select folder with .bmf files");


        Path target = getCropFolderName(tf, "-mesh-crops");
        System.out.println("saving to: " + target);
        Path imageCrops = target.resolve("images.zarr");
        Path maskCrops = target.resolve("masks.zarr");

        MeshImageStack stack;
        String fname = tf.getFileName().toString();
        if(fname.endsWith(".zarr")){
            try {
                stack = LoadZarr.loadMeshImageStack2(tf);
            } catch (IOException e) {
                System.out.println("Unable to load zarr file");
                throw new RuntimeException(e);
            }
        }else if(tf.getFileName().toString().endsWith(".tif")){
            ImagePlus plus = FileInfoVirtualStack.openVirtual(tf.toAbsolutePath().toString());
            stack = new MeshImageStack(plus);
        } else{
            stack = new MeshImageStack(tf);
        }

        Function<Integer, List<Track>> meshProvider;

        if(Files.isDirectory(base)) {
            meshProvider = (key)->{
                Path meshFile = base.resolve("frame-" + key + ".bmf");
                try {
                    return MeshReader.loadMeshes(meshFile.toFile());
                } catch (IOException e) {
                    System.err.println("Could not open " + base.resolve("frame-" + key + ".bmf"));
                    return new ArrayList<>();
                }
            };

        } else {
            try {
                List<Track> tracks = MeshReader.loadMeshes(base.toFile());
                meshProvider = key->tracks;
            } catch (IOException e) {
                System.out.println("Unable to open mesh file: " + base);
                throw new RuntimeException(e);
            }

        }

        if(!Files.exists(target)){
            try {
                Files.createDirectories(target);
            } catch (IOException e) {
                System.out.println("Unable to create destination directory");
                throw new RuntimeException(e);
            }
        }

        for(int i = 0; i<stack.getNFrames(); i++){
            stack.setFrame(i);
            List<Track> tracks = meshProvider.apply(i);
            CroppedVolume cv = cropMeshImages(tracks, stack);

            try {
                if (Files.exists(imageCrops)) {
                    SaveImageToZarr.appendToZarr(cv.data, imageCrops);
                } else {
                    SaveImageToZarr.saveToZarr(cv.data, imageCrops);
                }
                if (Files.exists(maskCrops)) {
                    SaveImageToZarr.appendToZarr(cv.mask, maskCrops);
                } else {
                    SaveImageToZarr.saveToZarr(cv.mask, maskCrops);
                }
            } catch(Exception e){
                System.out.println("Unable to write crop data!");
                throw new RuntimeException(e);
            }

            Path p = target.resolve("attributes-" + i + ".txt");
            try(BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8)){
                for(String line : cv.attributes){
                    bw.write(line);
                    bw.write("\n");
                }
            } catch (IOException e) {
                System.out.println("Unable to write attirbutes file: " + p);
                throw new RuntimeException(e);
            }

        }
    }

    public void processLabelledImages(MeshImageStack stack, MeshImageStack labels, Path cropFolder) throws Exception {

        if(!Files.exists(cropFolder)){
            Files.createDirectories(cropFolder);
        }

        Path imageZarr = cropFolder.resolve("images.zarr");
        Path masksZarr = cropFolder.resolve("masks.zarr");
        for(int i = 0; i<stack.getNFrames(); i++){
            stack.setFrame(i);
            labels.setFrame(i);
            CroppedVolume cv = cropLabelledImage(stack, labels);
            if(Files.exists(imageZarr)){
                SaveImageToZarr.appendToZarr(cv.data, imageZarr);
            } else{
                SaveImageToZarr.saveToZarr(cv.data, imageZarr);
            }
            if(Files.exists(masksZarr)){
                SaveImageToZarr.appendToZarr(cv.mask, masksZarr);
            } else{
                SaveImageToZarr.saveToZarr(cv.mask, masksZarr);
            }

            Path p = cropFolder.resolve("attributes-" + i + ".txt");
            try(BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8)){
                for(String line : cv.attributes){
                    bw.write(line);
                    bw.write("\n");
                }
            }

        }



    }

    private Path getCropFolderName(Path tf, String suffix){
        Path up = tf.getParent();
        String baseName;
        String filename = tf.getFileName().toString();
        int ei = filename.lastIndexOf(".");
        if( ei > 0){
            String ext = filename.substring(ei);
            baseName = filename.replace(ext, "") + suffix;
        } else{
            baseName = filename + suffix;
        }

        return up.resolve(baseName);
    }

    /**
     * Uses a gui to create the required Images.
     *
     */
    public void processLabelledImages(){

        Path tf = GuiTools.getAFile(IJ.getInstance(), "select original image data");

        if(tf == null) return;

        Path lbls = GuiTools.getAFile(IJ.getInstance(), "select labels image data");
        if(lbls == null) return;


        MeshImageStack stack;
        MeshImageStack labels;

        if(!Files.isDirectory(tf)){
            stack = new MeshImageStack(tf);
        } else{
            try {
                stack = LoadZarr.loadMeshImageStack2(tf);
            } catch (IOException e) {
                System.out.println("unable to load Zarr data structure: " + tf);
                throw new RuntimeException(e);
            }
        }

        if(!Files.isDirectory(lbls)){
            labels = new MeshImageStack(lbls);
        } else{
            try {
                labels = LoadZarr.loadMeshImageStack2(lbls);
            } catch (IOException e) {
                System.out.println("unable to load Zarr data structure: " + lbls);
                throw new RuntimeException(e);
            }
        }

        Path cropFolder = getCropFolderName(tf, "-crops");

        try {
            processLabelledImages(stack, labels, cropFolder);
        } catch (Exception e) {
            System.out.println("Unable to write data!");
            throw new RuntimeException(e);
        }
    }


}
