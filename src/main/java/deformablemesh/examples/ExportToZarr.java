package deformablemesh.examples;

import deformablemesh.gui.GuiTools;
import deformablemesh.io.LoadZarr;
import ij.ImageJ;
import ij.ImagePlus;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import net.imglib2.img.display.imagej.ImageJFunctions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public class ExportToZarr {

    public static void main(String[] args) throws IOException, FormatException, ExecutionException, InterruptedException {
        ImageJ ij = new ImageJ();
        Path id = GuiTools.getAFile(ij, "Select file");
        Path oid = GuiTools.getAFile(ij, "Select Output Folder");
        ImagePlus plus;
        if(id.toString().endsWith(".zarr")){
            plus = ImageJFunctions.wrap(LoadZarr.load3DZarrFile(id.toString()).getRai(), id.getFileName().toString());
        } else {
            ImporterOptions options = new ImporterOptions();
            options.setVirtual(true);
            options.setOpenAllSeries(true);
            options.setId(id.toString());
            ImagePlus[] pluses = BF.openImagePlus(options);
            int w = -1;
            int h = -1;
            int z = -1;
            plus = pluses[0];
        }
        plus.show();
        /*
        N5ScalePyramidExporter n5spe = new N5ScalePyramidExporter(
                plus,
                oid.getParent().toString(),
                oid.getFileName().toString(),
                "ZarrV3",
                "256,256,256,1,1",
                true,
                "Average",
                N5Importer.MetadataOmeZarrV05Key,
                BLOSC_COMPRESSION
        );

        n5spe.run();*/
    }
}
