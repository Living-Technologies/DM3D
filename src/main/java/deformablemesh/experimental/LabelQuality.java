package deformablemesh.experimental;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.MeshReader;
import deformablemesh.meshview.DeformableMeshDataObject;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.meshview.VolumeDataObject;
import deformablemesh.track.Track;
import deformablemesh.util.connectedcomponents.Region;
import ij.ImagePlus;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabelQuality {
    MeshImageStack labels;
    List<DeformableMesh3D> meshes;



    public static void main(String args[]) throws IOException {
        MeshImageStack labels = new MeshImageStack(Paths.get("quality-sample.tif"));
        List<Track> tracks = MeshReader.loadMeshes(new File("quality-sample.bmf"));
        LabelQuality quality = new LabelQuality();
        quality.labels = labels;
        ImagePlus meshLabels = DeformableMesh3DTools.asUniqueLabels( labels, tracks );
        List<Region> meshRegions = ( new MeshDetector( new MeshImageStack(meshLabels) ) ).getRegionsFromLabelledImage();
        List<Region> labelRegions = ( new MeshDetector(labels) ).getRegionsFromLabelledImage();

        MeshFrame3D frame = new MeshFrame3D();
        frame.showFrame(true);

        VolumeDataObject vdo = new VolumeDataObject(new Color(255, 255, 100));
        vdo.setTextureData(labels);
        vdo.setMinMaxRange(0, 0.001);
        vdo.setTransparencyTrim(0, 1);

        frame.addDataObject(vdo);

        for(Region region: meshRegions){
            Track track = tracks.get(region.getLabel()-1);
            DeformableMesh3D mesh = track.getMesh(0);
            Map<Integer, Double> overlaps = new HashMap<>();
            for(int[] pt: region.getPoints()){
                int px = labels.getPixelValue(pt[0], pt[1], pt[2]);
                double v = overlaps.computeIfAbsent(px, i->0.0);
                overlaps.put(px, v + 1);
            }
            for(Map.Entry<Integer, Double> entry: overlaps.entrySet()){
                System.out.println(entry.getKey() + " // " + entry.getValue());
            };
            double mx = ( overlaps.values().stream().mapToDouble(Double::valueOf).max().orElseGet(()->0.0) )/region.getPoints().size();

            DeformableMeshDataObject obj = new DeformableMeshDataObject(mesh);
            int r = (int)( 255*mx );
            int g = (int)( 255*mx );
            int b = (int)( 255*mx );

            obj.setWireColor(new Color(r, g, b));
            obj.setColor(new Color(r, g, b));
            obj.setShowSurface(true);

            frame.addDataObject(obj);
        }
        frame.addLights();
        frame.setBackgroundColor(new Color(0, 0, 50));
    }

}
