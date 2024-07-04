package deformablemesh.experimental;

import deformablemesh.MeshImageStack;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import net.imagej.DefaultDataset;
import net.imagej.ImgPlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.ij.N5IJUtils;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.metadata.imagej.CosemToImagePlus;
import org.janelia.saalfeldlab.n5.metadata.imagej.N5ImagePlusMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMetadataParser;

import java.io.IOException;
import java.nio.file.Files;
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
    ImagePlus plus = new ImagePlus("dummy", stack);
    Calibration c = plus.getCalibration();
    c.setUnit("um");
    c.pixelWidth = 0.25;
    c.pixelHeight = 0.25;
    c.pixelDepth = 2.0;
    plus.setCalibration(c);
    return plus;
}

    public static void saveDummyData() throws ExecutionException, InterruptedException {
        int[] blockSize = new int[]{64, 64, 64};
        Compression compression = new GzipCompression();
        N5Writer n5 = new N5FSWriter("dummy.n5");
        boolean first = true;
        for(int i = 0; i<3; i++) {
            ImagePlus d = dummyPlus();
            ImgPlus<UnsignedByteType> p2 = ImagePlusAdapter.wrapImgPlus(d);
            if(!first) {
                N5Utils.saveRegion(p2, n5, "dummy-dataset");
            } else{
                N5Utils.save(p2, n5, "dummy-dataset", blockSize, compression);
                first = false;
            }
        }
    }

    public static void save() throws IOException, ExecutionException, InterruptedException {

        MeshImageStack mis = MeshImageStack.fromFolder(folder, "iso.tif");
        ImagePlus plus = mis.getOriginalPlus();

        plus.setOpenAsHyperStack(true);
        N5Examples ex = new N5Examples("second-try.n5");
        ex.writeImage(mis);

    }
    public static void load() throws IOException {
        N5Examples ex = new N5Examples("second-try.n5");

        ImagePlus plus2 = ex.loadImagePlus();
        plus2.setOpenAsHyperStack(true);
        plus2.show();
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        saveDummyData();
    }

}
