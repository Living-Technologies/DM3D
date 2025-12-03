package deformablemesh.geometry;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.externalenergies.BrightRegionEnergy;
import deformablemesh.externalenergies.GradientEnergy;
import deformablemesh.externalenergies.PerpendicularGradientEnergy;
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
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;

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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MeshCroppingTool {
    private Path imageZarr;
    private Path maskZarr;
    final double factor;
    final int size;
    private int startFrame = 0;
    private int chunkSize = 1;
    String prefix="";
    public boolean rotate = false;
    boolean filter = false;
    int nFrames = -1;
    int channel = 0;
    //deforming parameters.
    public double alpha = 1.0;
    public double beta = 0.2;
    public double gamma = 1000;
    public double connection_mean = 0.009;
    public double energy_weight = 1e-2;
    public int deform_iterations = 500;
    public boolean deform_mesh = false;

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

    public void setStartFrame(int i){
        startFrame = i;
    }
    public void setChunkSize(int mesh_per_chunk){
        chunkSize = mesh_per_chunk;
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

    public void deform(DeformableMesh3D mesh, MeshImageStack stack){
        BrightRegionEnergy grad = new BrightRegionEnergy(stack, mesh, energy_weight);
        mesh.addExternalEnergy(grad);
        mesh.ALPHA = alpha;
        mesh.BETA = beta;
        mesh.GAMMA = gamma;

        //ConnectionRemesher con = new ConnectionRemesher();
        //double mean = connection_mean;
        //con.setMinAndMaxLengths(mean*1/3, mean*2/3);

        for(int i = 0; i<deform_iterations; i++){
            mesh.update();
        }
    }
    public CroppedVolume getCroppedMesh(DeformableMesh3D mesh, MeshImageStack stack, int label){
        if(deform_mesh) {
            deform(mesh, stack);
        }
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

    final int TOO_BIG=1;
    final int OUT_OF_BOUNDS=2;
    final int SUCCESS = 0;
    class Processor implements Callable<Processor> {
        int id;
        DeformableMesh3D mesh;
        MeshImageStack stack;
        int result;
        CroppedVolume cv;

        Processor(DeformableMesh3D mesh, int id, MeshImageStack stack){
            this.mesh = mesh;
            this.id = id;
            this.stack=stack;
        }
        @Override
        public Processor call() throws Exception {
            Box3D box = mesh.getBoundingBox();
            double mxLength = stack.getMinPx()*factor*size;

            if(box.high[0] - box.low[0] > mxLength || box.high[1] - box.low[1] > mxLength || box.high[2] - box.low[2] > mxLength){
                result = TOO_BIG;
                return this;
            }

            CroppedVolume mopd = getCroppedMesh(mesh, stack, id);
            if(mopd == null){
                result = OUT_OF_BOUNDS;
                return this;
            }
            cv = mopd;
            return this;
        }

    }
    public CroppedVolume cropMeshImages(List<Track> tracks, MeshImageStack mist){

        int key = mist.CURRENT;
        List<Processor> processors = new ArrayList<>();
        for(int i = 0; i<tracks.size(); i++) {
            Track t = tracks.get(i);
            int label = i + 1;
            if (t.containsKey(key)) {
                processors.add(new Processor(t.getMesh(key), label, mist));
            }
        }

        int count = 0;
        int checked = processors.size();
        int sizeLimit = 0;
        int boundaryLimit = 0;
        CroppedVolume accumulated = null;
        ExecutorService es = ForkJoinPool.commonPool();
        List<Future<Processor>> futureProcessors = processors.stream().map(es::submit).collect(Collectors.toList());
        for(Future<Processor> future: futureProcessors){
            Processor p = null;
            try {
                p = future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            switch(p.result){
                case TOO_BIG:
                    sizeLimit++;
                    continue;
                case OUT_OF_BOUNDS:
                    boundaryLimit++;
                    continue;
                case SUCCESS:
                    count++;
            }
            CroppedVolume mopd = p.cv;
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
        }
        if(accumulated == null){
            return accumulated;
        }
        System.out.println("checked: " + checked + ", to big: " + sizeLimit + ", out of bounds: " + boundaryLimit +", accepted: " + count);
        accumulated.data.setStack(accumulated.data.getStack(), 1, size, count);
        accumulated.mask.setStack(accumulated.mask.getStack(), 1, size, count);
        accumulated.data.setOpenAsHyperStack(true);
        accumulated.mask.setOpenAsHyperStack(true);
        if( key > 0 ) {
            accumulated.data.setTitle(key + "-" + prefix + "mesh-original");
            accumulated.mask.setTitle(key + "-" + prefix + "mesh-labels");
        } else{
            accumulated.data.setTitle(prefix + "mesh-original");
            accumulated.mask.setTitle(prefix + "mesh-labels");
        }
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
        double[] e0, e1, e2;
        if(rotate) {
            e0 = pa.e0;
            e1 = pa.e1;
            e2 = pa.e2;
        } else{
            e0 = Vector3DOps.xhat;
            e1 = Vector3DOps.yhat;
            e2 = Vector3DOps.zhat;
        }

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
                    if(maskIt.isMask(r)){
                        mp.set(k, j, 1);
                    }
                    if(filter && ! stack.contains(r)) {
                        return null;
                    }
                }
            }
            crop.addSlice(cp);
            maskCrop.addSlice(mp);
        }
        double vxSize = ds*stack.SCALE;
        ImagePlus plus = new ImagePlus();
        Calibration cal = plus.getCalibration();
        cal.pixelDepth = vxSize;
        cal.pixelWidth = vxSize;
        cal.pixelHeight = vxSize;
        plus.setCalibration(cal);

        plus.setStack(crop, 1, size, 1);

        ImagePlus plus2 = new ImagePlus();
        Calibration cal2 = plus.getCalibration();
        cal2.pixelDepth = vxSize;
        cal2.pixelWidth = vxSize;
        cal2.pixelHeight = vxSize;
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
    MeshImageStack getMeshImageStack(Path p){
        MeshImageStack stack;
        String fname = p.getFileName().toString();
        if(fname.endsWith(".zarr")){
            try {
                stack = LoadZarr.loadMeshImageStack2(p);
            } catch (IOException e) {
                System.out.println("Unable to load zarr file");
                throw new RuntimeException(e);
            }
        }else if(p.getFileName().toString().endsWith(".tif")){
            ImagePlus plus = FileInfoVirtualStack.openVirtual(p.toAbsolutePath().toString());
            stack = new MeshImageStack(plus);
        } else{
            ImporterOptions options;
            try {
                options = new ImporterOptions();
                options.setVirtual(true);
                options.setOpenAllSeries(true);
                options.setId(p.toAbsolutePath().toString());
                ImagePlus[] pluses = BF.openImagePlus(options);
                stack = new MeshImageStack(pluses[0]);

            } catch (IOException | FormatException e) {
                throw new RuntimeException(e);
            }
        }
        return stack;
    }



    /**
     * Uses a gui to choose mesh file and
     */
    public void processMeshes() {
        Path tf = GuiTools.getAFile(IJ.getInstance(), "select original image data");
        Path base = GuiTools.getAFile(IJ.getInstance(), "mesh file or select folder with .bmf files");
        processMeshImages(tf, base);

    }
    private int[] getChunkShape(ImagePlus data){
        return new int[]{
                data.getWidth(),
                data.getHeight(),
                data.getNSlices(),
                data.getNChannels(),
                chunkSize,
                };
    }


    private List<ImagePlus> queued = new ArrayList<>();
    private void saveVolumeData(CroppedVolume cv) throws Exception {
        if (Files.exists(imageZarr)) {
            SaveImageToZarr.appendToZarr(cv.data, imageZarr);
        } else {
            SaveImageToZarr.saveToZarr(cv.data, imageZarr, getChunkShape(cv.data));
        }
        if (Files.exists(maskZarr)) {
            SaveImageToZarr.appendToZarr(cv.mask, maskZarr);
        } else {
            SaveImageToZarr.saveToZarr(cv.mask, maskZarr, getChunkShape(cv.mask));
        }
    }

    public void processMeshImages(Path tf, Path base){
        if(tf == null || base == null) return;

        Path target = getCropFolderName(tf, "-mesh-crops");
        System.out.println("saving to: " + target);
        imageZarr = target.resolve("images.zarr");
        maskZarr = target.resolve("masks.zarr");

        MeshImageStack stack = getMeshImageStack(tf);

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
                throw new RuntimeException("Unable to open mesh file: " + base, e);
            }

        }

        if(!Files.exists(target)){
            try {
                Files.createDirectories(target);
            } catch (IOException e) {
                throw new RuntimeException("Unable to create destination directory: " + target, e);
            }
        }

        Path attributesFolder = target.resolve("attributes");
        if(!Files.exists(attributesFolder)){
            try {
                Files.createDirectories(attributesFolder);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        int lastFrame = nFrames < 0 ? stack.getNFrames() : startFrame + nFrames;

        for(int i = startFrame; i<lastFrame; i++){
            stack.setFrame(i);
            List<Track> tracks = meshProvider.apply(i);
            CroppedVolume cv = cropMeshImages(tracks, stack);

            if(cv != null) {
                try {
                    saveVolumeData(cv);
                } catch (Exception e) {
                    System.out.println("Unable to write crop data!");
                    throw new RuntimeException(e);
                }
            }
            Path p = attributesFolder.resolve("attributes-" + i + ".txt");
            try(BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8)){
                if(cv != null) {
                    for (String line : cv.attributes) {
                        bw.write(line);
                        bw.write("\n");
                    }
                }
            } catch (IOException e) {
                System.out.println("Unable to write attributes file: " + p);
                throw new RuntimeException(e);
            }

        }
    }


    private void processLabelledImages(MeshImageStack stack, MeshImageStack labels, Path cropFolder) throws Exception {

        if(!Files.exists(cropFolder)){
            Files.createDirectories(cropFolder);
        }

        imageZarr = cropFolder.resolve("images.zarr");
        maskZarr = cropFolder.resolve("masks.zarr");
        Path attributesFolder = cropFolder.resolve("attributes");
        if(!Files.exists(attributesFolder)){
            Files.createDirectories(attributesFolder);
        }
        stack.setChannel(channel);
        for(int i = 0; i<stack.getNFrames(); i++){
            stack.setFrame(i);
            labels.setFrame(i);
            CroppedVolume cv = cropLabelledImage(stack, labels);
            if(cv != null){
                saveVolumeData(cv);
            } else{
                continue;
            }

            Path p = attributesFolder.resolve("attributes-" + i + ".txt");
            try(BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8)){
                for(String line : cv.attributes){
                    bw.write(line);
                    bw.write("\n");
                }
            }

        }



    }

    /**
     * Both images represent the same space, but they can have different dimensions.
     * Crops objects found in the selected time frame.
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
        boolean floatLabels = lbls.getType() == MeshImageStack.FLOAT32;

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

            final int label = floatLabels ? (int)Float.intBitsToFloat(r.getLabel()) : r.getLabel();

            IsMasked maskIt = nc->{
                if( !lbls.contains(nc) ) return false;
                double[] ic = lbls.getImageCoordinates(nc);
                int ix = (int) ic[0];
                int iy = (int) ic[1];
                int iz = (int) ic[2];
                int px = (int)lbls.getValue(ix, iy, iz);
                return px == label;
            };

            CroppedVolume croppedVolume = crop(pa, maskIt, image, label);
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

    public void setPrefix(String s){
        prefix = s;
    }

    private Path getCropFolderName(Path tf, String suffix){
        Path up = tf.toAbsolutePath().getParent();
        String baseName;
        String filename = tf.getFileName().toString();
        int ei = filename.lastIndexOf(".");
        if( ei > 0){
            String ext = filename.substring(ei);
            baseName = filename.replace(ext, "") + suffix;
        } else{
            baseName = filename + suffix;
        }

        return up.resolve(prefix + baseName);
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

        processLabelledImages(tf, lbls);

    }

    public void processLabelledImages(Path tf, Path lbls){
        MeshImageStack stack;
        MeshImageStack labels;

        stack = getMeshImageStack(tf);

        labels = getMeshImageStack(lbls);

        Path cropFolder = getCropFolderName(tf, "-crops");

        try {
            processLabelledImages(stack, labels, cropFolder);
        } catch (Exception e) {
            System.out.println("Unable to write data!");
            throw new RuntimeException(e);
        }
    }

    public void setNFrames(int n){
        nFrames = n;
    }

    public static void main(String[] args){

        Path image,labels;

        if(args.length >= 2){
            image = Paths.get(args[0]);
            labels = Paths.get(args[1]);
        } else{
            image = GuiTools.getDirectory(null, "Select original Image zarr folder").toPath();
            labels = GuiTools.getAFile(null, "Select labels zarr folder or meshes");
        }


        long start = System.currentTimeMillis();
        MeshCroppingTool tool = new MeshCroppingTool(1.0, 64);
        tool.setPrefix("test_");
        tool.setChunkSize(100);
        tool.rotate = true;
        //tool.setStartFrame(0);
        //tool.setNFrames(3);
        //tool.channel = 3;
        //tool.processLabelledImages(image, labels);
        tool.processMeshImages(image, labels);
        System.out.println(System.currentTimeMillis()-start);


    }

}
