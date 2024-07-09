package deformablemesh.experimental;

import ij.ImagePlus;
import ij.ImageStack;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.metadata.imagej.NgffToImagePlus;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.*;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public class WriteZarrPredictions {
    final N5Writer n5;

    public WriteZarrPredictions(Path volumeName){
        n5 = new N5Factory().openWriter(volumeName.toString());
    }

    public void write(String dataset, ImagePlus data, int frame) throws Exception {
        writeTimepoint(n5, dataset, data, frame);
    }

    public void finish(){
        n5.close();
    }

    static void makeAndWriteMetadata(final N5Writer n5,
                                     final String datasetPath,
                                     final String arrayDatasetPath,
                                     final ImagePlus metadataImp) throws Exception {
        // some data munging to create and write the appropriate NGFF metadata objects
        // eventually I'll make some convenience methods...
        n5.createGroup(datasetPath);
        final NgffSingleScaleAxesMetadata metadata = new NgffToImagePlus().readMetadata(metadataImp);
        final OmeNgffMultiScaleMetadataMutable ms = new OmeNgffMultiScaleMetadataMutable(datasetPath);
        ms.addChild(new NgffSingleScaleAxesMetadata(arrayDatasetPath, metadata.getScale(), metadata.getTranslation(), null));
        final OmeNgffMultiScaleMetadata meta = new OmeNgffMultiScaleMetadata(metadata.getAxes().length,
                datasetPath, datasetPath, "AVERAGE", "0.4",
                metadata.getAxes(),
                ms.getDatasets(), null,
                ms.coordinateTransformations,
                ms.metadata,
                true);

        final OmeNgffMetadata ngffMetadata = new OmeNgffMetadata(datasetPath, new OmeNgffMultiScaleMetadata[]{meta});
        new OmeNgffMetadataParser().writeMetadata(ngffMetadata, n5, datasetPath);

    }

    public static ImagePlus createImagePlusForMetadata(final ImagePlus firstTimepoint) {
        final ImagePlus metadataImp = new ImagePlus();
        metadataImp.setCalibration(firstTimepoint.getCalibration());

        // make this have multiple time points and multiple slices for metadata purposes
        // data doesn't matter here, just number and semantics of dimensions
        final ImageStack stack = new ImageStack(firstTimepoint.getWidth(), firstTimepoint.getHeight());
        stack.addSlice(firstTimepoint.getProcessor());
        stack.addSlice(firstTimepoint.getProcessor());
        stack.addSlice(firstTimepoint.getProcessor());
        stack.addSlice(firstTimepoint.getProcessor());
        metadataImp.setStack(stack, 1, 2, 2); // two slices, two time frames
        return metadataImp;
    }

    public static <T extends RealType<T> & NativeType<T>> void writeTimepoint(final N5Writer n5, final String dataset, final ImagePlus imp, final int t) throws Exception {
        if(!n5.datasetExists(dataset + "/s0")){
            ImagePlus meta = createImagePlusForMetadata(imp);
            makeAndWriteMetadata(n5, dataset, "/s0", meta);
        }
        @SuppressWarnings("unchecked")
        final Img<T> img = (Img<T>) ImageJFunctions.wrap(imp);

        // img is 3d, so add a singleton time dimension.
        // set its coordinate to the time frame index so n5 can save it to the right place
        final IntervalView<T> imgWithTime = Views.addDimension(img, t, t);

        if (n5.datasetExists(dataset + "/s0")) {
            N5Utils.saveRegion(imgWithTime, n5, dataset + "/s0");
        } else {
            N5Utils.save(imgWithTime, n5, dataset + "/s0", new int[]{64, 64, 64, 1}, new GzipCompression());
        }
    }

}
