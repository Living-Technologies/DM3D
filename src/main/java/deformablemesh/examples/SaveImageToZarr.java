package deformablemesh.examples;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.VirtualStackAdapter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.blosc.BloscCompression;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadataMutable;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.CoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.ScaleCoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.TranslationCoordinateTransformation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class SaveImageToZarr {
    static {
        System.loadLibrary("blosc");
    }
    public static DataType getDataType(ImagePlus plus){
        ImageProcessor proc = plus.getStack().getProcessor(1);

        if(proc instanceof FloatProcessor){
            return DataType.FLOAT32;
        } else if(proc instanceof ShortProcessor){
            return DataType.INT16;
        } else if(proc instanceof ByteProcessor){
            return DataType.INT8;
        } else if(proc instanceof ColorProcessor){
            return DataType.INT32;
        }
        return DataType.FLOAT32;
    }

    static <T extends NativeType<T> & NumericType<T>> RandomAccessibleInterval<T>  getXYZCTRandomAccessIntervale(ImagePlus plus){
        //RandomAccessibleInterval<T> img = ImageJFunctions.wrap(plus);
        RandomAccessibleInterval<T> img = (RandomAccessibleInterval<T>)VirtualStackAdapter.wrap(plus);
        System.out.println("Starting shape: " + Arrays.toString(img.dimensionsAsLongArray()));
        if(plus.getNChannels() > 1){
            //switches channesl with z.
            img = Views.moveAxis(img, 2, 3);
        } else{
            //add a channel.
            img = Views.addDimension(img, 0L, 0L);
            if( plus.getNFrames() > 1 ){
                //switch time to last position.
                img = Views.moveAxis(img, 3, 4);
            }
        }
        if(plus.getNFrames() == 1){
            //adds time frame.
            img = Views.addDimension(img, 0L, 0L);
        }
        System.out.println("finished shape: " + Arrays.toString(img.dimensionsAsLongArray()));
        return img;
    }
    public static <T extends NativeType<T> & NumericType<T>> void appendToZarr(ImagePlus plus, Path op) throws Exception{
        N5Factory factory = new N5Factory();
        factory.zarrDimensionSeparator("/");

        try( N5Writer writer = factory.openWriter(op.toString()) ) {
            RandomAccessibleInterval<T> img = getXYZCTRandomAccessIntervale(plus);

            String datasetPath = "";
            String arrayDatasetPath = "/s0";

            String shapeKey = "shape";
            long[] shape = writer.getAttribute(datasetPath + arrayDatasetPath, shapeKey, long[].class);
            int n = shape.length - 1;

            long[] translation = new long[shape.length];
            translation[n] = shape[n];
            //N5Utils.save(img, writer,datasetPath + arrayDatasetPath, blocks, new BloscCompression());

            N5Utils.saveRegion(Views.translate(img,translation ), writer, datasetPath + arrayDatasetPath);
        }
    }
    public static <T extends NativeType<T> & NumericType<T>> void saveToZarr(ImagePlus plus, Path op) throws Exception {
        N5Factory factory = new N5Factory();
        factory.zarrDimensionSeparator("/");

        try( N5Writer writer = factory.openWriter(op.toString()) ){
            RandomAccessibleInterval<T> img = getXYZCTRandomAccessIntervale(plus);
            Calibration cb = plus.getCalibration();
            String datasetPath = "";
            String arrayDatasetPath = "/s0";
            long[] dimensions = img.dimensionsAsLongArray();
            int[] blocks = new int[dimensions.length];
            double[] scale = new double[dimensions.length];
            //TODO why isn't there a time offset?
            double[] translation = new double[dimensions.length];

            int spatial = 0;
            Axis[] axes = new Axis[dimensions.length];

            //keep xyczt order
            //x
            blocks[spatial] = plus.getWidth();
            scale[spatial] = cb.pixelWidth;
            translation[spatial] = cb.xOrigin;
            axes[spatial] = new Axis(Axis.SPACE, "x", cb.getXUnit());
            //y
            blocks[spatial + 1] = plus.getHeight();
            scale[spatial+1] = cb.pixelHeight;
            translation[spatial+1] = cb.yOrigin;
            axes[spatial + 1] = new Axis(Axis.SPACE, "y", cb.getYUnit());

            blocks[spatial + 2] = plus.getNSlices();
            scale[spatial+2] = cb.pixelDepth;
            translation[spatial+2] = cb.zOrigin;
            axes[spatial + 2] = new Axis(Axis.SPACE, "z", cb.getZUnit());

            //c (if present.)
            //TODO always saving with 5 axes no need to check.
            if(plus.getNChannels()  > 0){
                spatial++;
                blocks[3] = 1;
                scale[3] = 1;
                translation[3] = 0;
                axes[3] = new Axis(Axis.CHANNEL, "c", null, true);
            }

            if(plus.getNFrames() > 0){
                blocks[spatial + 3] = 1;
                double ds = cb.frameInterval == 0 ? 1 : cb.frameInterval;
                scale[spatial + 3] = ds;
                axes[spatial + 3] = new Axis(Axis.TIME, "t", cb.getTimeUnit());
            }

            System.out.println(Arrays.toString(scale));
            System.out.println(Arrays.toString(translation));
            DataType type = getDataType(plus);
            DatasetAttributes da = new DatasetAttributes(
                    dimensions,
                    blocks, type, new BloscCompression()
            );
            NgffSingleScaleAxesMetadata metadata = new NgffSingleScaleAxesMetadata(
                    arrayDatasetPath,
                    scale,
                    translation, axes, da
            );

            final OmeNgffMultiScaleMetadataMutable ms = new OmeNgffMultiScaleMetadataMutable(datasetPath);
            ms.addChild(metadata);
            double[] identity = new double[dimensions.length];
            double[] origin = new double[dimensions.length];
            for(int i = 0; i<identity.length; i++){
                origin[i] = 0.0;
                identity[i] = 1.0;
            }
            CoordinateTransformation<?> id = new ScaleCoordinateTransformation(identity);
            CoordinateTransformation<?> og = new TranslationCoordinateTransformation(origin);
            final OmeNgffMultiScaleMetadata meta = new OmeNgffMultiScaleMetadata(metadata.getAxes().length,
                    datasetPath, datasetPath, "AVERAGE", "0.4",
                    metadata.getAxes(),
                    ms.getDatasets(), null,
                    new CoordinateTransformation[]{id, og},
                    ms.metadata,
                    true);

            final OmeNgffMetadata ngffMetadata = new OmeNgffMetadata(datasetPath, new OmeNgffMultiScaleMetadata[]{meta});

            new OmeNgffMetadataParser().writeMetadata(ngffMetadata, writer, datasetPath);

            N5Utils.save(img, writer,datasetPath + arrayDatasetPath, blocks, new BloscCompression());
        }
    }
    public static void main(String[] args) throws Exception {
        //Path p = Paths.get(IJ.getFilePath("select image to convert")).toAbsolutePath();
        Path p = Paths.get("");
        String name = p.getFileName().toString();
        String outName = name.replaceAll("\\.[^.]*$", ".zarr");
        Path op = p.getParent().resolve(outName);
        ImagePlus plus = new ImagePlus(p.toAbsolutePath().toString());
        System.out.println("writing to zarr " + op);
        saveToZarr(plus, op);
        System.out.println("written");
    }

}
