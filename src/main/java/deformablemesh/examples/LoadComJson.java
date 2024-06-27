package deformablemesh.examples;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.util.StdConverter;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.track.Track;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.FileInfoVirtualStack;
import ij.plugin.FolderOpener;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LoadComJson {
    public static double size = 0.01;

    @JsonDeserialize(converter = LoadComJson.MyPointConverter.class)
    static class MyPoint{
        final double[] xyz;
        public MyPoint(List<Double> values){
            xyz = new double[] { values.get(0), values.get(1), values.get(2)};
        }
    }
    static class MyPointConverter extends StdConverter<List<Double>, MyPoint> {
        @Override
        public MyPoint convert(List<Double> value) {
            return new MyPoint(value);
        }
    }
    public static Map<Integer, List<DeformableMesh3D>> loadMeshes(File jsonFile, MeshImageStack geometry) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<MyPoint>> values = mapper.readValue(jsonFile, new TypeReference<Map<String, List<MyPoint>>>() {});
        Map<Integer, List<DeformableMesh3D>> results = new HashMap<>();
        for(String key: values.keySet()){
            List<MyPoint> points = values.get(key);
            List<DeformableMesh3D> meshes = new ArrayList<>(points.size());
            results.put(Integer.parseInt(key), meshes);
            for(MyPoint pt : points){
                double[] npt = geometry.getNormalizedCoordinate(pt.xyz);
                DeformableMesh3D mesh = RayCastMesh.sphereRayCastMesh(1);
                mesh.translate(npt);
                mesh.scale(size, npt);
                meshes.add(mesh);
            }
        }
        return results;

    }
    public static void main(String[] args) throws IOException {
        String name = "C:/Users/msmith5/OneDrive - UMC Utrecht/Documenten/working/maria/new-dataset/Time lapses/20190817pos01";
        List<Path> images = Files.list(Paths.get(name)).filter((p) -> {
            return p.toString().contains(".tif");
        }).collect(Collectors.toList());
        int count = images.size();
        ImagePlus one = FileInfoVirtualStack.openVirtual(images.get(0).toAbsolutePath().toString());
        int slices = one.getNSlices();
        int channels = one.getNChannels();
        Calibration cal = one.getCalibration();
        ImagePlus plus = FolderOpener.open(name , "virtual");
        int total = plus.getStack().size();

        if(channels*slices*count != total){
            throw new RuntimeException("inconsistencies!");
        }
        plus.setCalibration(cal);
        plus.setDimensions(channels, slices, count);
        plus.setOpenAsHyperStack(true);
        Map<Integer, List<DeformableMesh3D> > meshes = LoadComJson.loadMeshes(new File("C:/Users/msmith5/OneDrive - UMC Utrecht/Documenten/working/maria/new-dataset/Time lapses/20190817pos01/Positions.json"), new MeshImageStack(plus));

    }

}
