package deformablemesh.experimental;

import bdv.util.RandomAccessibleIntervalMipmapSource4D;
import bdv.viewer.Source;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import deformablemesh.io.MultiscaleImageAdapter;
import ij.ImageJ;
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
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
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

public class LoadZarr {
    static {
        System.loadLibrary("blosc");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AxisThing {
        public String name;
        public String type;
        public String unit;

        public String toString(){
            return "Axis: " + name + ", " + type + ", " + unit;
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Transformations{
        public List<Double> scale;
        public List<Double> translation;
        public String type;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class DataSet{
        public List<Transformations> coordinateTransformations;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MultiScaleSpatial{
        public List<AxisThing> axes;
        public List<DataSet> datasets;
        public String name;
        public String version;
        public String toString(){
            return "version: " + version + " " + name + " axes: " + axes;
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Stuff{
        public List<MultiScaleSpatial> multiscales;

    }
    private static List<MultiScaleSpatial> getSpatialAttributes(Path json){
        System.out.println("reading" + json);
        ObjectMapper mapper = new ObjectMapper();
        try {
            Stuff stuff = mapper.readValue(json.toFile(), new TypeReference<Stuff>(){});
            return stuff.multiscales;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static <T extends NumericType<T> & NativeType<T>> MultiscaleImageAdapter<T> load3DZarrFile(String location) throws IOException {
        N5Factory factory = new N5Factory();
        N5Reader reader = factory.openReader(location);

        Path origin = Paths.get(location);

        String[] sets = reader.deepListDatasets("/");
        if(sets.length == 0){
            throw new IOException("cannot access data");
        }
        Path attrs = origin.resolve(".zattrs");
        List<MultiScaleSpatial> things = getSpatialAttributes(attrs);
        System.out.println("resolution levels!" + things.size());
        //only works for one set.
        List<Axis> axes = things.get(0).axes.stream().map(t->new Axis(t.type, t.name, t.unit)).collect(Collectors.toList());
        MultiscaleImageAdapter<T> adapter = new MultiscaleImageAdapter<>(axes);
        for(String s: sets){
            int mipmap =adapter.getNextLevel();

            MultiScaleSpatial mss = things.get(mipmap);
            CachedCellImg<T, ?> cachedCellImg = N5Utils.open(reader, s);
            long[] dims = cachedCellImg.dimensionsAsLongArray();

            List<Transformations> ts = mss.datasets.get(mipmap).coordinateTransformations;
            double[] scale = null;
            double[] offset = null;
            for(Transformations t : ts){
                if(t.type.equals("scale")){
                    scale = t.scale.stream().mapToDouble(Double::valueOf).toArray();
                } else if(t.type.equals("translation")){
                    offset = t.translation.stream().mapToDouble(Double::valueOf).toArray();
                }
            }
            adapter.addResolution(cachedCellImg, scale, offset);
        }
        adapter.setTitle(location);
        return adapter;
    }
    public static <T extends NumericType<T> & NativeType<T>> List<Source<T>> load3DSourceAndConverter(String location ) throws IOException {
        N5Factory factory = new N5Factory();
        N5Reader reader = factory.openReader(location);

        Path origin = Paths.get(location);
        String baseName = origin.getFileName().toString();

        N5Metadata rootMetadata = N5MetadataUtils.parseMetadata(reader, "/");
        System.out.println("root metadata: " + rootMetadata);
        List<Source<T>> sources = new ArrayList<>();
        if(rootMetadata instanceof OmeNgffMetadata){
            OmeNgffMetadata metadata = (OmeNgffMetadata)rootMetadata;
            for ( OmeNgffMultiScaleMetadata md : metadata.multiscales ){
                System.out.println(md);
            }
        }
        String[] sets = reader.deepListDatasets("/");
        //only works for one set.
        for(String s: sets){
            N5Metadata n5md = N5MetadataUtils.parseMetadata(reader, s);
            if(n5md instanceof N5DefaultSingleScaleMetadata){
                N5DefaultSingleScaleMetadata def = (N5DefaultSingleScaleMetadata) n5md;
                Map<String, Object> objs = def.getAttributes().asMap();
                for(Map.Entry<String, Object> row : objs.entrySet()){
                    System.out.println("row: " + row);
                }
            } else if(n5md instanceof NgffSingleScaleAxesMetadata){
                NgffSingleScaleAxesMetadata ngffmd = (NgffSingleScaleAxesMetadata)n5md;
                Map<String, Object> objs = ngffmd.getAttributes().asMap();
                for(String str: objs.keySet()){
                    System.out.println("ngff row " + str);
                }
            }

            Path attrs = origin.resolve(".zattrs");
            if(!Files.exists(attrs)){
                //Possibly n5 data structure!
                attrs = origin.resolve("attributes.json");
            }
            List<MultiScaleSpatial> things = getSpatialAttributes(attrs);
            MultiScaleSpatial mss = things.get(0);



            CachedCellImg<T, ?> cachedCellImg = N5Utils.open(reader, s);
            long[] dims = cachedCellImg.dimensionsAsLongArray();
            System.out.println("cci: " + Arrays.toString(dims) );
            int channels = (int)dims[3];
            for(int i = 0; i<channels; i++){

                RandomAccessibleInterval<T> rai = (RandomAccessibleInterval<T>)Views.hyperSlice(cachedCellImg, 3, 0);
                System.out.println("view: " + Arrays.toString(rai.dimensionsAsLongArray()));

                RandomAccessibleInterval<T>[] images = new RandomAccessibleInterval[]{ rai };
                List<Transformations> ts = mss.datasets.get(0).coordinateTransformations;
                System.out.println("transformations: " + ts.size());
                double[] scale = null;
                double[] offset = null;
                for(Transformations t : ts){
                    if(t.type.equals("scale")){
                        scale = new double[]{t.scale.get(4), t.scale.get(3), t.scale.get(2)};
                    } else if(t.type.equals("translation")){
                        offset = new double[] {t.translation.get(4), t.translation.get(3), t.translation.get(2)};
                    }
                }
                System.out.println(Arrays.toString(scale) + Arrays.toString(offset));
                DefaultVoxelDimensions vd = new DefaultVoxelDimensions(4);
                AffineTransform3D a = new AffineTransform3D();
                a.scale(scale[0], scale[1], scale[2]);
                a.translate(offset[0], offset[1], offset[2]);
                AffineTransform3D b = new AffineTransform3D();
                RandomAccessibleIntervalMipmapSource4D<T> source = new RandomAccessibleIntervalMipmapSource4D<>(
                        images,
                        rai.getType(),
                        new AffineTransform3D[]{a},
                        vd,
                        s, false
                );

                sources.add(source);

            }


        }

        return sources;
    }
    public static ImagePlus load3DStack(String location ) throws IOException {
        MultiscaleImageAdapter<?> adapter = load3DZarrFile(location);
        return adapter.getMipMapAsPlus(0);
    }

    public static List<ImagePlus> load3DStackFromZarrFile( String location ) throws IOException {
        MultiscaleImageAdapter<?> images = load3DZarrFile(location);
        List<ImagePlus> pluses = new ArrayList<>();
        for(int i = 0; i<images.getMipMapLevels(); i++){
            pluses.add(images.getMipMapAsPlus(i));
        }
        return pluses;
    }
    public static List<ImagePlus> load3DStackFromZarrFileDep( String location ) throws IOException {
        N5Factory factory = new N5Factory();
        N5Reader reader = factory.openReader(location);

        List<ImagePlus> pluses = new ArrayList<>();
        Path origin = Paths.get(location);
        String baseName = origin.getFileName().toString();

        N5Metadata rootMetadata = N5MetadataUtils.parseMetadata(reader, "/");
        System.out.println("root metadata: " + rootMetadata);
        if(rootMetadata instanceof OmeNgffMetadata){
            OmeNgffMetadata metadata = (OmeNgffMetadata)rootMetadata;
            for ( OmeNgffMultiScaleMetadata md : metadata.multiscales ){
                System.out.println(md);
            }
        }
        String[] sets = reader.deepListDatasets("/");
        for(String s: sets){
            N5Metadata n5md = N5MetadataUtils.parseMetadata(reader, s);
            System.out.println(n5md);
            if(n5md instanceof N5DefaultSingleScaleMetadata){
                N5DefaultSingleScaleMetadata def = (N5DefaultSingleScaleMetadata) n5md;
                Map<String, Object> objs = def.getAttributes().asMap();
                for(Map.Entry<String, Object> row : objs.entrySet()){
                    System.out.println("row: " + row);
                }
            } else if(n5md instanceof NgffSingleScaleAxesMetadata){
                NgffSingleScaleAxesMetadata ngffmd = (NgffSingleScaleAxesMetadata)n5md;
                System.out.println(Arrays.toString(ngffmd.getAxes()));
                System.out.println(Arrays.toString(ngffmd.getScale()));
                System.out.println(Arrays.toString(ngffmd.getTranslation()));

            }

            Path attrs = origin.resolve(".zattrs");
            if(!Files.exists(attrs)){
                //Possibly n5 data structure!
                attrs = origin.resolve("attributes.json");
            }
            List<MultiScaleSpatial> things = getSpatialAttributes(attrs);

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
            ImagePlus img = N5IJUtils.load(reader, s);

            int n = shape.length;
            int frames = 1;
            int channels = 1;
            int slices = 1;

            MultiScaleSpatial mss = things.get(0);

            Calibration cb = img.getCalibration();

            List<Double> scales = Arrays.asList(1., 1., 1., 1., 1.);
            List<Double> offsets = Arrays.asList(0., 0., 0., 0., 0.);
            for(Transformations t : mss.datasets.get(0).coordinateTransformations){
                switch(t.type){
                    case "scale":
                        scales = t.scale;
                        break;
                    case "translation":
                        offsets = t.translation;
                        break;
                    default:
                        System.out.println("unknown transformation: " + t);
                }
            }
            int cIndex, zIndex;
            String order = mss.axes.stream().map(a->a.name.toLowerCase()).collect(Collectors.joining());
            System.out.println("zarr file order: " + order);

            for(int i = 0; i<mss.axes.size(); i++){
                AxisThing a = mss.axes.get(i);
                switch(a.name){
                    case "t":
                        cb.setTimeUnit(a.unit);
                        cb.frameInterval = scales.get(i);
                        frames = (int)shape[n - i - 1];
                        break;
                    case "z":
                        cb.pixelDepth =  scales.get(i);
                        cb.setZUnit(a.unit);
                        cb.zOrigin = offsets.get(i);
                        slices = (int)shape[n - i - 1];
                        break;
                    case "y":
                        cb.pixelHeight = scales.get(i);
                        cb.setYUnit(a.unit);
                        cb.yOrigin = offsets.get(i);
                        break;
                    case "x":
                        cb.pixelWidth = scales.get(i);
                        cb.xOrigin = offsets.get(i);
                        cb.setXUnit(a.unit);
                        break;
                    case "c":
                        channels = (int)shape[n - i - 1];
                        break;
                    default:
                        System.out.println("unknown axis " + a);
                }

            }

            ImageStack stack = img.getStack();
            if(order.contains("cz")){
                ImageStack temp = new ImageStack(stack.getWidth(), stack.getHeight());
                //channels and z are backwards.
                for(int i = 0; i<frames; i++){
                    for(int k = 0; k<slices; k++){
                        for(int j = 0; j<channels; j++){
                            temp.addSlice(stack.getProcessor(1 + i*channels*slices + k + j*slices));
                        }
                    }
                }
                stack = temp;
            }

            img.setStack(stack, channels, slices, frames);
            img.setTitle(baseName + "/" + s);
            pluses.add(img);
        }
        return pluses;
    }
    public static void main(String[] args) throws IOException {
        new ImageJ();
        //String location = IJ.getDirectory("select zarr folder");
        //String location = "D:\\working\\zarr-communications\\xyz-py.zarr";
        String location = Paths.get("D:/working/sonnen/3D_small_organoid/3D_small_organoid.zarr").toAbsolutePath().toString();
        //List<ImagePlus> ps = load3DStackFromZarrFile(location);
        //ps.forEach(ImagePlus::show);
        //List<ImagePlus> sac = load3DStackFromZarrFile(location);
        ImagePlus plus = load3DStack(location);
        plus.setOpenAsHyperStack(true);
        plus.show();
    }
}
