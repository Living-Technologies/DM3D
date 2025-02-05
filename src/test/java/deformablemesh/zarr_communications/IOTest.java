package deformablemesh.zarr_communications;

import deformablemesh.io.SaveImageToZarr;
import deformablemesh.io.LoadZarr;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class IOTest {
    final static double oz = -10;
    final static double oy = -5;
    final static double ox = -15;
    final static double px = 0.25;
    final static double py = 0.25;
    final static double pz = 2.0;
    final static double fi = 60;
    final static String unit = "µm";
    final static String timeUnit = "sec";

    /**
     * Creates an image plus with the desired array dimensions. It will be
     * calibrated to the global calibration values.
     *
     * Each slice has a pixel value corresponding to its location in the
     * image stack.
     *
     * @param w width
     * @param h height
     * @param z slices
     * @param t frames
     * @param c channels
     * @return A stack of ShortProcessors
     */
    static ImagePlus generic(int w, int h, int z, int t, int c){
        Calibration cb = new Calibration();
        cb.zOrigin = oz;
        cb.yOrigin = oy;
        cb.xOrigin = ox;

        cb.pixelDepth = pz;
        cb.pixelHeight = py;
        cb.pixelWidth = px;

        cb.frameInterval = fi;
        cb.setTimeUnit(timeUnit);
        cb.setUnit(unit);

        ImagePlus plus = new ImagePlus();
        plus.setCalibration(cb);

        ImageStack stack = new ImageStack(w, h);
        for(int i = 0; i<z*c*t; i++){
            ImageProcessor proc = new ShortProcessor(w, h);
            short[] px =(short[])proc.getPixels();
            for(int j = 0; j<px.length; j++){
                px[j] = (short)i;
            }
            stack.addSlice(proc);
        }

        plus.setStack(stack, c, z, t);
        return plus;

    }

    /**
     * Validates the provided image plus using the expected shape
     * parameters and global calibration values.
     *
     *
     * @param w
     * @param h
     * @param z
     * @param t
     * @param c
     * @param plus
     */
    static void validate(int w, int h, int z, int t, int c, ImagePlus plus){

        Assert.assertEquals(w, plus.getWidth());
        Assert.assertEquals(h, plus.getHeight());
        Assert.assertEquals(z, plus.getNSlices());
        Assert.assertEquals(t, plus.getNFrames());
        Assert.assertEquals(c, plus.getNChannels());

        Calibration cb = plus.getCalibration();
        Assert.assertEquals(px, cb.pixelWidth, 1e-9);
        Assert.assertEquals(py, cb.pixelHeight, 1e-9);
        Assert.assertEquals(pz, cb.pixelDepth, 1e-9);
        Assert.assertEquals(ox, cb.xOrigin, 1e-9);
        Assert.assertEquals(oy, cb.yOrigin, 1e-9);
        Assert.assertEquals(oz, cb.zOrigin, 1e-9);
        //TODO time transformation doesn't get passed when t = 1;
        if(t > 1) {
            Assert.assertEquals(fi, cb.frameInterval, 1e-9);
        }
        //TODO um gets changed to micro meter.
        Assert.assertEquals(unit, cb.getUnit());
        //TODO s gets changed to sec.
        Assert.assertEquals(timeUnit, cb.getTimeUnit());

        ImageStack stack = plus.getStack();
        for(int i = 0; i<stack.size(); i++){
            short[] pxs = (short[])stack.getProcessor(i+1).getPixels();
            int sum = 0;
            for(int j = 0; j<pxs.length; j++){
                sum += pxs[j];
            }
            //makes sure the processors are in the correct order!
            Assert.assertEquals(i*h*w, sum);
        }
    }

    /**
     * Recursively deletes the provided path.
     *
     * @param p
     */
    static void deleteTempZarrFolder(Path p){
        try {
            if (Files.isDirectory(p)) {
                try (Stream<Path> paths = Files.list(p)) {
                    paths.forEach(IOTest::deleteTempZarrFolder);
                }
            }
            Files.deleteIfExists(p);
        } catch(Exception e){
            throw new RuntimeException(e);
        }
    }
    static Path getTempZarrPath(String s) throws IOException {
        Path p0 = Files.createTempDirectory(s);
        Path zarrPath = p0.getParent().resolve(p0.getFileName() + ".zarr");
        Files.move(p0, zarrPath);
        return zarrPath;

    }


    @Test
    public void testXYZ() throws Exception {
        int w = 96;
        int h = 64;
        int z = 16;
        int t = 1;
        int c = 1;

        Path zarrPath = getTempZarrPath("xyz");
        try {
            ImagePlus plus = generic(w, h, z, t, c);
            System.out.println(zarrPath.toAbsolutePath());
            SaveImageToZarr.saveToZarr(plus, zarrPath);
            ImagePlus round = LoadZarr.load3DStackFromZarrFile(zarrPath.toString()).get(0);
            validate(w, h, z, t, c, plus);
            validate(w, h, z, t, c, round);
        } finally{
            deleteTempZarrFolder(zarrPath);
        }
    }

    @Test
    public void testXYZT() throws Exception {
        int w = 96;
        int h = 64;
        int z = 16;
        int t = 2;
        int c = 1;

        Path p = getTempZarrPath("xyzt");
        try {
            ImagePlus plus = generic(w, h, z, t, c);
            System.out.println(p.toAbsolutePath());
            SaveImageToZarr.saveToZarr(plus, p);
            ImagePlus round = LoadZarr.load3DStackFromZarrFile(p.toString()).get(0);
            validate(w, h, z, t, c, plus);
            validate(w, h, z, t, c, round);
        } finally{
            deleteTempZarrFolder(p);
        }
    }

    @Test
    public void testXYZC() throws Exception {
        int w = 96;
        int h = 64;
        int z = 16;
        int t = 1;
        int c = 2;

        Path p = getTempZarrPath("xyzc");
        try {
            ImagePlus plus = generic(w, h, z, t, c);
            System.out.println(p.toAbsolutePath());
            SaveImageToZarr.saveToZarr(plus, p);
            ImagePlus round = LoadZarr.load3DStackFromZarrFile(p.toString()).get(0);
            validate(w, h, z, t, c, plus);
            validate(w, h, z, t, c, round);
        } finally{
            deleteTempZarrFolder(p);
        }
    }
    @Test
    public void testXYZCT() throws Exception {
        int w = 96;
        int h = 64;
        int z = 16;
        int t = 3;
        int c = 2;

        Path p = getTempZarrPath("xyzct");
        try {
            ImagePlus plus = generic(w, h, z, t, c);
            System.out.println(p.toAbsolutePath());
            SaveImageToZarr.saveToZarr(plus, p);
            ImagePlus round = LoadZarr.load3DStackFromZarrFile(p.toString()).get(0);
            validate(w, h, z, t, c, plus);
            validate(w, h, z, t, c, round);
        } finally{
            deleteTempZarrFolder(p);
        }
    }
}
