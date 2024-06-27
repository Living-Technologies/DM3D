package deformablemesh.experimental;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Node3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.meshview.ChannelVolume;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.meshview.VolumeDataObject;
import deformablemesh.util.connectedcomponents.Region;
import ij.ImagePlus;

import java.awt.*;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LabelQuality {
    MeshImageStack labels;
    List<DeformableMesh3D> meshes;



    static String path = "C:\\Users\\msmith5\\OneDrive - UMC Utrecht\\Documenten\\working\\maria\\new-dataset\\pred-iso-tm10\\Interleukin_10IL_9CTRL_xy05-iso\\pred-dna-1ch-350nm-d5-norm-latest-Interleukin_10IL_9CTRL_xy05-303-iso-labels.tif";
    public static void main(String args[]){
        MeshImageStack labels = new MeshImageStack(Paths.get(path));
        LabelQuality quality = new LabelQuality();
        quality.labels = labels;

        MeshFrame3D frame = new MeshFrame3D();
        frame.showFrame(true);

        VolumeDataObject vdo = new VolumeDataObject(Color.WHITE);
        vdo.setTextureData(labels);
        vdo.setMinMaxRange(0, 1);
        vdo.setTransparencyTrim(0, 1);

        frame.addDataObject(vdo);

    }

}
