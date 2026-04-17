package deformablemesh.io;

import bdv.viewer.Source;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import deformablemesh.gimli2b.MeshImageStack2;
import deformablemesh.gimli2b.MeshImageStack2UB;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LoadZarr {
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
        MultiScaleSpatial mss = things.get(0);
        //only works for one set.
        List<Axis> axes = things.get(0).axes.stream().map(t->new Axis(t.type, t.name, t.unit)).collect(Collectors.toList());
        MultiscaleImageAdapter<T> adapter = new MultiscaleImageAdapter<>(axes);
        for(String s: sets){
            CachedCellImg<T, ?> cachedCellImg = N5Utils.open(reader, s);
            int dex = adapter.addResolutionData(cachedCellImg);
            adapter.addDataSetLabel(dex, s);
        }
        for(int i = 0; i<adapter.getMipMapLevels(); i++){
            List<Transformations> ts = mss.datasets.get(i).coordinateTransformations;
            double[] scale = null;
            double[] offset = null;
            for(Transformations t : ts){
                if(t.type.equals("scale")){
                    scale = t.scale.stream().mapToDouble(Double::valueOf).toArray();
                } else if(t.type.equals("translation")){
                    offset = t.translation.stream().mapToDouble(Double::valueOf).toArray();
                }
            }
            adapter.addResolutionTransforms(scale, offset);
        }

        adapter.setTitle(location);
        return adapter;
    }

    public static <T extends NumericType<T> & NativeType<T> & RealType<T> > MeshImageStack2<T> loadMeshImageStack2(Path location) throws IOException {
        
        MultiscaleImageAdapter<T> msia = load3DZarrFile(location.toAbsolutePath().toString());
        List<Source<T>> sources = new ArrayList<>();
        for(int i = 0; i<msia.getNChannels(); i++){
            sources.add(msia.getAsBdvSource(i));
        }
        RandomAccessibleInterval<T> rai = msia.images.data.get(0);
        long px = rai.dimension(0)*rai.dimension(1)*rai.dimension(2);
        MeshImageStack2<T> mist;
        if (px < Integer.MAX_VALUE) {
            mist = new MeshImageStack2<>(sources);
        } else{
            System.out.println("no buffer!");
            mist = new MeshImageStack2UB<>(sources);
        }

        mist.setShortTitle(location.getFileName().toString());
        Calibration ij = mist.getImageJCalibration();
        msia.calibrateUnits(ij);
        return mist;
    }

    public static <T extends NumericType<T> & NativeType<T> & RealType<T>> MeshImageStack2<T> loadMeshImageStack2(URI uri) throws IOException {
        MultiscaleImageAdapter<T> msia = load3DZarrFile(uri.toString());
        List<Source<T>> sources = new ArrayList<>();
        for(int i = 0; i<msia.getNChannels(); i++){
            sources.add(msia.getAsBdvSource(i));
        }
        RandomAccessibleInterval<T> rai = msia.images.data.get(0);
        int n = rai.numDimensions();
        long px = rai.dimension(0)*rai.dimension(1)*rai.dimension(2);

        MeshImageStack2<T> mist;
        if (px < Integer.MAX_VALUE) {
            mist = new MeshImageStack2<>(sources);
        } else{
            System.out.println("no buffer!");
            mist = new MeshImageStack2UB<>(sources);
        }

        mist.setShortTitle(uri.getPath());
        Calibration ij = mist.getImageJCalibration();
        msia.calibrateUnits(ij);
        return mist;
    }

    public static <T extends NumericType<T> & NativeType<T>> List<Source<T>> load3DSource(String location ) throws IOException {
        MultiscaleImageAdapter<T> msia = load3DZarrFile(location);
        List<Source<T>> sources = new ArrayList<>();
        for(int i = 0; i<msia.getNChannels(); i++){
            sources.add(msia.getAsBdvSource(i));
        }
        return sources;

    }


    public static List<ImagePlus> load3DStackFromZarrFile( String location ) throws IOException {
        MultiscaleImageAdapter<?> images = load3DZarrFile(location);
        List<ImagePlus> pluses = new ArrayList<>();
        for(int i = 0; i<images.getMipMapLevels(); i++){
            pluses.add(images.getMipMapAsPlus(i));
        }
        return pluses;
    }

    public static void main(String[] args) throws IOException {
        new ImageJ();
        String location = IJ.getDirectory("select zarr folder");
        List<ImagePlus> pluses = load3DStackFromZarrFile(location);
        for (ImagePlus plus : pluses) {
            plus.setOpenAsHyperStack(true);
            plus.show();
        }
    }
}
