package deformablemesh.experimental;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.ij.N5IJUtils;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class LoadZarr {
    static {
        System.loadLibrary("blosc");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Axis{
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
        public List<Axis> axes;
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
            System.out.println(stuff.multiscales);
            return stuff.multiscales;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<ImagePlus> load3DStackFromZarrFile( String location ) throws IOException {
        N5Factory factory = new N5Factory();
        N5Reader reader = factory.openReader(location);
        String[] sets = reader.deepListDatasets("/");
        List<ImagePlus> pluses = new ArrayList<>();
        for(String s: sets){
            Path attrs = Paths.get(location, s).getParent().resolve(".zattrs");
            List<MultiScaleSpatial> things = getSpatialAttributes(attrs);

            long[] shape = reader.getAttribute(s, "shape", long[].class);
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
                Axis a = mss.axes.get(i);
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
            pluses.add(img);
        }
        return pluses;
    }
    public static void main(String[] args) throws IOException {
        new ImageJ();
        String location = IJ.getDirectory("select zarr folder");

        List<ImagePlus> ps = load3DStackFromZarrFile(location);
        ps.forEach(ImagePlus::show);

    }
}
