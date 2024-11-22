package deformablemesh.examples;

import deformablemesh.MeshImageStack;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.blosc.BloscCompression;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.metadata.imagej.NgffToImagePlus;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SaveImageToZarr {
    static {
        System.loadLibrary("blosc");
    }
    public static void main(String[] args) throws Exception {

        //Path p = Paths.get(IJ.getFilePath("select image to convert")).toAbsolutePath();
        Path p = Paths.get("D:\\working\\zarr-communications\\sample-crop.tif");
        System.out.println(Files.exists(p));
        ImagePlus plus = IJ.openImage(p.toString());

        String name = p.getFileName().toString();

        String outName = name.replaceAll("\\.[^.]*$", ".zarr");
        Path op = p.getParent().resolve(outName);
        System.out.println("writing to zarr " + op);

        try( N5Writer writer = N5Factory.createWriter(op.toString()) ){
            String datasetPath = "";
            String arrayDatasetPath = "/s0";
            //writer.createGroup(datasetPath);
            final NgffSingleScaleAxesMetadata metadata = new NgffToImagePlus().readMetadata(plus);
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
            new OmeNgffMetadataParser().writeMetadata(ngffMetadata, writer, datasetPath);
            final RandomAccessibleInterval<?> img = ImageJFunctions.wrap(plus);

            N5Utils.save((RandomAccessibleInterval) img, writer, datasetPath + arrayDatasetPath, new int[]{plus.getWidth(), plus.getHeight(), plus.getNSlices(), plus.getNFrames()}, new BloscCompression());
        }


    }

}
