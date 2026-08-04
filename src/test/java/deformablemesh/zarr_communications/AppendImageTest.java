package deformablemesh.zarr_communications;

import deformablemesh.io.LoadZarr;
import deformablemesh.io.SaveImageToZarr;
import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.VirtualStackAdapter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5MetadataUtils;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadata;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class AppendImageTest {

    public  <T extends NativeType<T> & NumericType<T>> void updateZarrFile() throws ExecutionException, InterruptedException {
        int w = 96;
        int h = 64;
        int z = 16;
        int t = 1;
        int c = 2;
        ImagePlus plus = IOTest.generic(w, h, z, t, c);
        N5Factory factory = new N5Factory();
        Path op = Paths.get("modify-test.zarr");

        N5Reader reader = factory.openReader(op.toString());
        OmeNgffMetadata rootMetadata = (OmeNgffMetadata)N5MetadataUtils.parseMetadata(reader, "/");
        DatasetAttributes da = rootMetadata.getChildrenMetadata()[0].getAttributes();
        long[] shape = da.getDimensions();
        try (N5Writer writer = factory.openWriter(op.toString())) {
            RandomAccessibleInterval<T> img = SaveImageToZarr.getXYZCTRandomAccessIntervale(plus);

            String datasetPath = "";
            String arrayDatasetPath = "/s0";

            int n = shape.length - 1;

            long[] translation = new long[shape.length];
            translation[n] = 2;
            //N5Utils.save(img, writer,datasetPath + arrayDatasetPath, blocks, new BloscCompression());

            N5Utils.saveRegion(Views.translate(img, translation), writer, datasetPath + arrayDatasetPath);
        }
    }

    @Test
    public void modifyFolderTest(){
        int w = 96;
        int h = 64;
        int z = 16;
        int t = 3;
        int c = 2;

        Path p = Paths.get("modify-test.zarr");
        IOTest.createdFolders.add(p);

        try {
            ImagePlus plus = IOTest.generic(w, h, z, t, c);
            System.out.println(p.toAbsolutePath());
            SaveImageToZarr.saveToZarr(plus, p);
            updateZarrFile();
            ImagePlus loaded = LoadZarr.load3DStackFromZarrFile(p.toString()).get(0);
            System.out.println(loaded);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally{
            IOTest.deleteTempZarrFolder(p);
        }
    }
    public static void main(String[] args) throws Exception {
        int w = 96;
        int h = 64;
        int z = 16;
        int t = 1;
        int c = 2;

        Path p = Paths.get("D:\\working\\zarr-communications\\append-test.zarr");
        IOTest.deleteTempZarrFolder(p);

        try {
            ImagePlus plus = IOTest.generic(w, h, z, t, c);
            System.out.println(p.toAbsolutePath());
            SaveImageToZarr.saveToZarr(plus, p);
            ImagePlus p2 = IOTest.generic(w, h, z, t, c);

            SaveImageToZarr.appendToZarr(p2, p);

        } finally{
            //IOTest.deleteTempZarrFolder(p);
        }
    }
}
