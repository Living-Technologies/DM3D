package deformablemesh.io;

import ij.IJ;
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
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.*;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.coordinateTransformations.CoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.coordinateTransformations.ScaleCoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.coordinateTransformations.TranslationCoordinateTransformation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class SaveImageToZarr {
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

    public static <T extends NativeType<T> & NumericType<T>> RandomAccessibleInterval<T>  getXYZCTRandomAccessIntervale(ImagePlus plus){
        RandomAccessibleInterval<T> img = (RandomAccessibleInterval<T>)VirtualStackAdapter.wrap(plus);
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
        return img;
    }

    public static <T extends NativeType<T> & NumericType<T>> void appendToZarrRa(ImagePlus plus, Path op, int timepoint) throws Exception {
        boolean newZarr = !Files.exists(op);
        N5Factory factory = new N5Factory();

        try( N5Writer writer = factory.openWriter(op.toString()) ) {
            RandomAccessibleInterval<T> img = getXYZCTRandomAccessIntervale(plus);

            String datasetPath = "";
            String arrayDatasetPath = "/s0";

            long[] translation = new long[5];
            translation[4] = timepoint;
            if( !newZarr ) {
                N5Utils.saveRegion(Views.translate(img,translation ), writer, datasetPath + arrayDatasetPath);
            } else{
                System.out.println("to here!");
                int[] blocks = {plus.getWidth(), plus.getHeight(), plus.getNSlices(), 1, 1};
                N5Utils.save(
                        Views.translate(img,translation ),
                        writer,
                        datasetPath + arrayDatasetPath,
                        blocks,
                        new BloscCompression()
                );
                saveMetadata(plus, writer, img.dimensionsAsLongArray());
            }
        }
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

    public static <T extends NativeType<T> & NumericType<T>> void saveMetadata(ImagePlus plus, N5Writer writer, long[] dimensions) throws Exception {
            Calibration cb = plus.getCalibration();
            String datasetPath = "";
            String arrayDatasetPath = "/s0";
            double[] scale = new double[dimensions.length];
            double[] translation = new double[dimensions.length];

            int spatial = 0;
            Axis[] axes = new Axis[dimensions.length];

            //keep xyczt order
            scale[spatial] = cb.pixelWidth;
            translation[spatial] = -cb.xOrigin * cb.pixelWidth;
            axes[spatial] = new Axis(Axis.SPACE, "x", cb.getXUnit());
            //y
            scale[spatial + 1] = cb.pixelHeight;
            translation[spatial + 1] = -cb.yOrigin * cb.pixelHeight;
            axes[spatial + 1] = new Axis(Axis.SPACE, "y", cb.getYUnit());

            scale[spatial + 2] = cb.pixelDepth;
            translation[spatial + 2] = -cb.zOrigin * cb.pixelDepth;
            axes[spatial + 2] = new Axis(Axis.SPACE, "z", cb.getZUnit());

            //c (if present.)
            spatial++;
            scale[3] = 1;
            translation[3] = 0;
            axes[3] = new Axis(Axis.CHANNEL, "c", null, true);

            double ds = cb.frameInterval == 0 ? 1 : cb.frameInterval;
            scale[spatial + 3] = ds;
            axes[spatial + 3] = new Axis(Axis.TIME, "t", cb.getTimeUnit());

            DataType type = getDataType(plus);
            int[] blocks = {plus.getWidth(), plus.getHeight(), plus.getNSlices(), 1, 1};
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
            for (int i = 0; i < identity.length; i++) {
                origin[i] = 0.0;
                identity[i] = 1.0;
            }
            CoordinateTransformation<?> id = new ScaleCoordinateTransformation(identity);
            CoordinateTransformation<?> og = new TranslationCoordinateTransformation(origin);

            final OmeNgffMultiScaleMetadata meta = new OmeNgffMultiScaleMetadata(
                    metadata.getAxes().length, datasetPath, datasetPath,
                    "AVERAGE", "0.5", metadata.getAxes(),
                    ms.getDatasets(),
                    new CoordinateTransformation<?>[]{id, og},
                    new DatasetAttributes[]{da},
                    null
            );
            final OmeNgffMetadata ngffMetadata = new OmeNgffMetadata(datasetPath, new OmeNgffMultiScaleMetadata[]{meta});
            new OmeNgffMetadataParser().writeMetadata(ngffMetadata, writer, datasetPath);

    }

    public static <T extends NativeType<T> & NumericType<T>> void saveToZarr(ImagePlus plus, Path op, int[] blocks) throws Exception {
        N5Factory factory = new N5Factory();
        factory.zarrDimensionSeparator("/");
        RandomAccessibleInterval<T> img = getXYZCTRandomAccessIntervale(plus);
        try( N5Writer writer = factory.openWriter(op.toString()) ){
            Calibration cb = plus.getCalibration();
            String datasetPath = "";
            String arrayDatasetPath = "/s0";
            long[] dimensions = img.dimensionsAsLongArray();
            double[] scale = new double[dimensions.length];
            double[] translation = new double[dimensions.length];

            int spatial = 0;
            Axis[] axes = new Axis[dimensions.length];

            //keep xyczt order
            scale[spatial] = cb.pixelWidth;
            translation[spatial] = - cb.xOrigin * cb.pixelWidth;
            axes[spatial] = new Axis(Axis.SPACE, "x", cb.getXUnit());
            //y
            scale[spatial+1] = cb.pixelHeight;
            translation[spatial+1] = - cb.yOrigin * cb.pixelHeight;
            axes[spatial + 1] = new Axis(Axis.SPACE, "y", cb.getYUnit());

            scale[spatial+2] = cb.pixelDepth;
            translation[spatial+2] = - cb.zOrigin * cb.pixelDepth;
            axes[spatial + 2] = new Axis(Axis.SPACE, "z", cb.getZUnit());

            //c (if present.)
            spatial++;
            scale[3] = 1;
            translation[3] = 0;
            axes[3] = new Axis(Axis.CHANNEL, "c", null, true);

            double ds = cb.frameInterval == 0 ? 1 : cb.frameInterval;
            scale[spatial + 3] = ds;
            axes[spatial + 3] = new Axis(Axis.TIME, "t", cb.getTimeUnit());

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
            final OmeNgffMultiScaleMetadata meta = new OmeNgffMultiScaleMetadata(
                        metadata.getAxes().length, datasetPath, datasetPath,
                    "AVERAGE", "0.5", metadata.getAxes(),
                    ms.getDatasets(),
                    new CoordinateTransformation<?>[]{id, og},
                    new DatasetAttributes[]{da},
                    null
                    );
            final OmeNgffMetadata ngffMetadata = new OmeNgffMetadata(datasetPath, new OmeNgffMultiScaleMetadata[]{meta});


            N5Utils.save(img, writer,datasetPath + arrayDatasetPath, blocks, new BloscCompression());
            new OmeNgffMetadataParser().writeMetadata(ngffMetadata, writer, datasetPath);

        }
    }
    public static <T extends NativeType<T> & NumericType<T>> void saveToZarr(ImagePlus plus, Path op) throws Exception {
        int x = plus.getWidth();
        int y = plus.getHeight();
        int z = plus.getNSlices();
        x = x>256 ? 256 : x;
        y = y>256 ? 256 : y;
        z = z>256 ? 256 : z;
        int[] blocks = {x, y, z, 1, 1};
        saveToZarr(plus, op, blocks);
    }
    public static void main(String[] args) throws Exception {
        Path p = Paths.get(IJ.getFilePath("select image to convert")).toAbsolutePath();
        //Path p = Paths.get("");
        String name = p.getFileName().toString();
        String outName = name.replaceAll("\\.[^.]*$", ".zarr");
        if(!outName.endsWith(".zarr")){
            outName = outName + ".zarr";
        }
        Path op = p.getParent().resolve(outName);
        ImagePlus plus = new ImagePlus(p.toAbsolutePath().toString());
        System.out.println("writing to zarr " + op);
        saveToZarr(plus, op);
        System.out.println("written");
    }

}
