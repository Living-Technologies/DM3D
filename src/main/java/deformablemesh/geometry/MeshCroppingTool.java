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
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MeshCroppingTool {
    double factor = 1.5;
    int size = 64;

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

    public static CroppedVolume cropMeshImages(List<Track> tracks, MeshImageStack mist){
        MeshCroppingTool mct = new MeshCroppingTool();
        int count = 0;
        CroppedVolume accumulated = null;
        Pattern p = Pattern.compile(".*lbl_(\\d+)");

        for(Track t: tracks){
            int label;
            Matcher m = p.matcher(t.getName());
            if(m.find()){
                label = Integer.parseInt(m.group(1));
            } else{
                label = 255;
            }
            for(Integer key : t.getTrack().keySet()){
                if(key != mist.CURRENT){
                    mist.setFrame(key);
                }
                DeformableMesh3D mesh = t.getMesh(key);
                CroppedVolume mopd = mct.getCroppedMesh(mesh, mist, label);
                if(mopd == null){
                    continue;
                }
                if(accumulated == null){
                    accumulated = mopd;
                } else {
                    int n = mopd.data.getNSlices();
                    for (int i = 1; i <= n; i++) {
                        accumulated.data.getStack().addSlice(mopd.data.getStack().getProcessor(i));
                        accumulated.mask.getStack().addSlice(mopd.mask.getStack().getProcessor(i));
                    }
                    accumulated.attributes.addAll(mopd.attributes);
                }
                count += 1;
            }
        }
        accumulated.data.setStack(accumulated.data.getStack(), 1, mct.size, count);
        accumulated.mask.setStack(accumulated.mask.getStack(), 1, mct.size, count);
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
    CroppedVolume cropLabelledImage(MeshImageStack lbls, MeshImageStack image){

        CroppedVolume accumulated = null;
        int tally = 0;

        MeshDetector md = new MeshDetector(lbls);
        List<Region> regions = md.getRegionsFromLabelledImage();
        System.out.println("cropping regions: " + regions.size());
        for (Region r : regions) {

            PrincipleAxes pa = getPrincipleAxis(r, image.pixel_dimensions);
            pa.cm[0] = pa.cm[0] / image.SCALE - image.offsets[0];
            pa.cm[1] = pa.cm[1] / image.SCALE - image.offsets[1];
            pa.cm[2] = pa.cm[2] / image.SCALE - image.offsets[2];
            final int label = r.getLabel();
            IsMasked maskIt = nc->{
                double[] ic = image.getImageCoordinates(nc);
                int ix = (int) ic[0];
                int iy = (int) ic[1];
                int iz = (int) ic[2];
                int px = (int)lbls.getValue(ix, iy, iz);
                return px == label;
            };

            CroppedVolume croppedVolume = crop(pa, maskIt, image, r.getLabel());
            if (croppedVolume == null) {
                continue;
            }

            double[] cm = image.getImageCoordinates(pa.cm);


            if (accumulated == null) {
                accumulated = croppedVolume;
                tally++;
            } else {
                ImageStack stack = croppedVolume.data.getStack();
                ImageStack pop = accumulated.data.getStack();
                for (int i = 1; i <= stack.size(); i++) {
                    pop.addSlice(stack.getProcessor(i));
                }
                ;
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


    public static void processMeshes() throws Exception {
        File zarr = GuiTools.getDirectory(null, "select zarr folder");
        Path tf;
        if(zarr == null){
            tf = GuiTools.getOpenFile(null, "select image file");
        } else{
            tf = zarr.toPath();
        }

        Path base;
        File folder = GuiTools.getDirectory(null, "select folder with .bmf files");
        if(folder == null){
            base = GuiTools.getOpenFile(null, "select mesh file");
        } else{
            base = folder.toPath();
        }

        //Path tf = Paths.get("D:\\working\\maria\\extraction-tests\\labels.tif");
        //Path base = Paths.get("D:\\working\\maria\\extraction-tests\\meshes.bmf");
        String cropBase = tf.getFileName().toString().replace(".zarr", "-mesh-crops");
        Path target = tf.getParent().resolve(cropBase);
        System.out.println("saving to: " + target);
        if(!Files.exists(target)){
            Files.createDirectories(target);
        }
        Path imageCrops = target.resolve("images.zarr");
        Path maskCrops = target.resolve("masks.zarr");
        MeshImageStack stack;
        if(tf.getFileName().toString().endsWith(".tif")){
            stack = new MeshImageStack(tf);
        } else{
            stack = LoadZarr.loadMeshImageStack2(tf);
        }
        MeshCroppingTool tool = new MeshCroppingTool();
        for(int i = 0; i<stack.getNFrames(); i++){
            Path meshFile;
            if(Files.isDirectory(base)) {
                meshFile = base.resolve("frame-" + i + ".bmf");
            } else {
                //all of the meshes are in meshfile.
                //all time points will get handled.
                meshFile = base;
                i = stack.getNFrames();
            }
            List<Track> tracks = MeshReader.loadMeshes(meshFile.toFile());
            CroppedVolume cv = MeshCroppingTool.cropMeshImages(tracks, stack);
            if(Files.exists(imageCrops)){
                SaveImageToZarr.appendToZarr(cv.data, imageCrops);
            } else{
                SaveImageToZarr.saveToZarr(cv.data, imageCrops);
            }
            if(Files.exists(maskCrops)){
                SaveImageToZarr.appendToZarr(cv.mask, maskCrops);
            } else{
                SaveImageToZarr.saveToZarr(cv.mask, maskCrops);
            }

            Path p = target.resolve("attributes-" + i + ".txt");
            try(BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8)){
                for(String line : cv.attributes){
                    bw.write(line);
                    bw.write("\n");
                }
            }

        }
    }

    public static void processLabelledImages(MeshImageStack stack, MeshImageStack labels, Path cropFolder) throws Exception {

        if(!Files.exists(cropFolder)){
            Files.createDirectories(cropFolder);
        }

        MeshCroppingTool mct = new MeshCroppingTool();
        Path imageZarr = cropFolder.resolve("images.zarr");
        Path masksZarr = cropFolder.resolve("masks.zarr");
        for(int i = 0; i<stack.getNFrames(); i++){
            stack.setFrame(i);
            labels.setFrame(i);
            CroppedVolume cv = mct.cropLabelledImage(labels, stack);
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

    static public void processLabelledImages() throws Exception {
        File zarr = GuiTools.getDirectory(IJ.getInstance(), "select image data as zarr folder");
        Path tf;
        if(zarr == null){
            tf = GuiTools.getOpenFile(IJ.getInstance(), "select image file");
        } else{
            tf = zarr.toPath();
        }

        if(tf == null) return;

        Path lbls;

        File lzarr = GuiTools.getDirectory(IJ.getInstance(), "select labels data as zarr folder");
        if(lzarr == null){
            lbls = GuiTools.getOpenFile(IJ.getInstance(), "select image file");
        } else{
            lbls = lzarr.toPath();
        }

        if(lbls == null) return;

        MeshImageStack stack;
        MeshImageStack labels;

        if(!Files.isDirectory(tf)){
            stack = new MeshImageStack(tf);
        } else{
            stack = LoadZarr.loadMeshImageStack2(tf);
        }

        if(!Files.isDirectory(lbls)){
            labels = new MeshImageStack(lbls);
        } else{
            labels = LoadZarr.loadMeshImageStack2(lbls);
        }

        Path up = tf.getParent();
        String baseName;
        String filename = tf.getFileName().toString();
        int ei = filename.lastIndexOf(".");
        if( ei > 0){
            String ext = filename.substring(ei);
            baseName = filename.replace(ext, "") + "-crops";
        } else{
            baseName = filename + "-crops";
        }

        Path cropFolder = up.resolve(baseName);

        processLabelledImages(stack, labels, cropFolder);
    }

    public static void main(String[] args) throws Exception {
        //processMeshes();
        processLabelledImages();
    }


}
