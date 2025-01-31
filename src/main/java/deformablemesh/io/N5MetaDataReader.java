package deformablemesh.io;

import bdv.util.RandomAccessibleIntervalMipmapSource4D;
import bdv.viewer.Source;
import deformablemesh.experimental.LoadZarr;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import mpicbg.spim.data.sequence.DefaultVoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.ij.N5IJUtils;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5MetadataUtils;
import org.janelia.saalfeldlab.n5.universe.metadata.N5DefaultSingleScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class N5MetaDataReader {
    public void getMetaData(String location){
        N5Factory factory = new N5Factory();
        N5Reader reader = factory.openReader(location);
        List<ImagePlus> pluses = new ArrayList<>();

        N5Metadata rootMetadata = N5MetadataUtils.parseMetadata(reader, "/");
        System.out.println("root metadata: " + rootMetadata);
        if(rootMetadata instanceof OmeNgffMetadata){
            OmeNgffMetadata metadata = (OmeNgffMetadata)rootMetadata;
            for ( OmeNgffMultiScaleMetadata md : metadata.multiscales ){
                System.out.println(md);
            }

        }
        String[] sets = reader.deepListDatasets("/");
        for(String s: sets) {
            N5Metadata n5md = N5MetadataUtils.parseMetadata(reader, s);
            System.out.println(n5md);
            if (n5md instanceof N5DefaultSingleScaleMetadata) {
                N5DefaultSingleScaleMetadata def = (N5DefaultSingleScaleMetadata) n5md;
                Map<String, Object> objs = def.getAttributes().asMap();
                for (Map.Entry<String, Object> row : objs.entrySet()) {
                    System.out.println("row: " + row);
                }
            } else if (n5md instanceof NgffSingleScaleAxesMetadata) {
                NgffSingleScaleAxesMetadata ngffmd = (NgffSingleScaleAxesMetadata) n5md;
                System.out.println(Arrays.toString(ngffmd.getAxes()));
                System.out.println(Arrays.toString(ngffmd.getScale()));
                System.out.println(Arrays.toString(ngffmd.getTranslation()));
                Map<String, Object> objs = ngffmd.getAttributes().asMap();
                for(String str: objs.keySet()){
                    System.out.println("ngff row " + str);
                }

            }

            Map<String, Class<?>> attributes = reader.listAttributes(s);
            Set<String> keys = attributes.keySet();
            for( String att : attributes.keySet() ){
                try {
                    System.out.println("\t*" + att + ", " + reader.getAttribute(s, att, attributes.get(att)));
                } catch(Exception e){
                    System.out.println("\t% could not get " + s + "/" + att);
                }
            };
            String shapeKey = keys.contains("shape") ? "shape" : "dimensions";
            long[] shape = reader.getAttribute(s, shapeKey, long[].class);
            System.out.println("read shape: " + Arrays.toString(shape));

            int n = shape.length;
            int frames = 1;
            int channels = 1;
            int slices = 1;
        }

    }

}
