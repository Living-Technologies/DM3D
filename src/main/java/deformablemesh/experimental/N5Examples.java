package deformablemesh.experimental;

import deformablemesh.MeshImageStack;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import net.imagej.*;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultAxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.display.imagej.CalibrationUtils;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ByteImagePlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Fraction;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.ij.N5IJUtils;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.metadata.imagej.CosemToImagePlus;
import org.janelia.saalfeldlab.n5.metadata.imagej.NgffToImagePlus;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMetadataParser;

import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

public class N5Examples {
    N5Writer n5;
    final static String dataset = "Interleukin_10IL_9CTRL_xy05-iso";
    N5Examples(String writerName){
        n5 = new N5FSWriter(writerName);
    }

    public void writeImage(MeshImageStack stack) throws IOException, ExecutionException, InterruptedException {
        int[] blockSize = new int[]{64, 64, 64};
        Compression compression = new GzipCompression();
        long[] dimensions = {stack.getWidthPx(), stack.getHeightPx(), stack.getNSlices()};
        DatasetAttributes dsat = new DatasetAttributes(dimensions, blockSize, DataType.UINT8, compression);
        for(int i = 0; i<stack.getNFrames(); i++) {
            stack.setFrame(i);
            ImgPlus<UnsignedByteType> p2 = ImagePlusAdapter.wrapImgPlus(stack.getCurrentFrame());
            N5Utils.saveRegion(p2, n5, dataset, dsat);
        }
    }

    public ImagePlus loadImagePlus() throws IOException {
        N5CosemMetadataParser metaDataParser = new N5CosemMetadataParser();
        CosemToImagePlus metaDataTransformer = new CosemToImagePlus();
        CachedCellImg<UnsignedByteType, ?> loaded = N5Utils.open(n5, dataset);
        ImgPlus<UnsignedByteType> p2 = new ImgPlus<UnsignedByteType>(loaded);
        return ImageJFunctions.wrap(p2, dataset);
    }
public static ImagePlus dummyPlus(){
    int h = 512;
    int w = 512;
    int z = 64;
    ImageStack stack = new ImageStack(w, h);
    for(int i = 0; i<z; i++){
        stack.addSlice(new ByteProcessor(512, 512));
    }
    ImagePlus plus = new ImagePlus("dummy");
    plus.setStack(stack, 1, z, 1);
    plus.setOpenAsHyperStack(true);
    Calibration c = plus.getCalibration();
    c.setUnit("um");
    c.pixelWidth = 0.25;
    c.pixelHeight = 0.25;
    c.pixelDepth = 2.0;

    c.xOrigin = -25;
    c.yOrigin = -25;
    c.zOrigin = -8;

    plus.setCalibration(c);
    return plus;
}
    static ImgPlus<UnsignedByteType> getByte(ImagePlus plus){
        Calibration c = plus.getCalibration();

        long[] dims = {plus.getWidth(), plus.getHeight(), plus.getNChannels(),  plus.getNSlices(), plus.getNFrames()};
        ByteImagePlus<UnsignedByteType> ret = new ByteImagePlus<>(dims, new Fraction());
        ImageStack stack = plus.getStack();
        for(int i = 0; i<stack.size(); i++){
            ret.setPlane(i, new ByteArray((byte[])stack.getProcessor(i+1).getPixels()));
        }
        final UnsignedByteType linkedType = new UnsignedByteType( ret );
        // pass it to the NativeContainer
        ret.setLinkedType( linkedType );
        CalibratedAxis[] alt = CalibrationUtils.getNonTrivialAxes(plus);

        return  new ImgPlus<UnsignedByteType>(
                        ret, plus.getTitle(), getCalibrationAxis(plus)
                    );
    }

    static CalibratedAxis[] getCalibrationAxis(ImagePlus plus){
        CalibratedAxis[] o  = CalibrationUtils.getNonTrivialAxes(plus);
        Calibration c = plus.getCalibration();
        CalibratedAxis caxis = new DefaultLinearAxis(
                new DefaultAxisType("C"),
                "channel",
                1,
                0 );
        CalibratedAxis taxis = new DefaultLinearAxis(
                new DefaultAxisType("T"),
                c.getTimeUnit(),
                c.frameInterval,
                0 );
        return new CalibratedAxis[]{o[0], o[1], caxis, o[2], taxis};
    }

    public static void saveDummyData() throws Exception {
        int[] blockSize = new int[]{64, 64, 64, 64, 64};
        Compression compression = new GzipCompression();
        String n5Root = "C:\\Users\\msmith5\\dummy.zarr";
        N5Writer n5 = new N5Factory().openWriter(n5Root);
        String dataset = "dummy-dataset";
        String arrayLocation = "/s0";
        boolean first = true;
        for(int i = 0; i<3; i++) {
            ImagePlus d = dummyPlus();
            if(first){
                ImagePlus sample = WriteZarrPredictions.createImagePlusForMetadata(d);
                WriteZarrPredictions.makeAndWriteMetadata(n5, dataset, arrayLocation, sample);
                first = false;
            }
            WriteZarrPredictions.writeTimepoint(n5, dataset + arrayLocation, d, i);

        }

    }

    public static void loadDummyData() throws IOException {
        String n5Root = "dummy.zarr";
        N5Writer n5 = new N5Factory().openWriter(n5Root);
        String dataset = "dummy-dataset";
        String arrayLocation = "/s0";
        new ImageJ();
        ImagePlus plus = N5Importer.open(n5Root, dataset + arrayLocation);
        //p.show();
        //CachedCellImg<UnsignedByteType, ?> img = N5Utils.open(n5, dataset + arrayLocation);
        //ImagePlus plus = ImageJFunctions.wrap(img, "n5Root");
        //CalibrationUtils.copyCalibrationToImagePlus(new ImgPlus<>(img), plus);
        plus.show();
    }

    public static void save() throws IOException, ExecutionException, InterruptedException {

        Path folder = Paths.get("C:/Users/msmith5/OneDrive - UMC Utrecht/Documenten/working/maria/new-dataset/pred-iso-tm10/Interleukin_10IL_9CTRL_xy05-iso");
        MeshImageStack mis = MeshImageStack.fromFolder(folder, "iso.tif");
        ImagePlus plus = mis.getOriginalPlus();

        plus.setOpenAsHyperStack(true);
        N5Examples ex = new N5Examples("./third-try.n5");
        ex.writeImage(mis);

    }
    public static void load() throws IOException {
        N5Examples ex = new N5Examples("second-try.n5");

        ImagePlus plus2 = ex.loadImagePlus();
        plus2.setOpenAsHyperStack(true);
        plus2.show();
    }
    public static void main(String[] args) throws Exception {
        saveDummyData();
        loadDummyData();
    }

}
