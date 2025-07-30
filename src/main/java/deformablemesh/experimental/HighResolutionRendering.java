package deformablemesh.experimental;

import deformablemesh.MeshDetector;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.gimli2b.MeshImageStack2;
import deformablemesh.gui.GuiTools;
import deformablemesh.io.LoadZarr;
import deformablemesh.meshview.DataCanvas;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.simulations.FillingBinaryImage;
import deformablemesh.util.connectedcomponents.Region;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import org.jogamp.java3d.Screen3D;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class HighResolutionRendering {
    public static void main(String[] args) throws IOException {
        ImageJ ij;
        if(IJ.getInstance() == null){
            ij = new ImageJ();
        } else{
            ij = IJ.getInstance();
        }

        File tf = GuiTools.getDirectory(ij, "Select original Image");
        File lbls = GuiTools.getDirectory(ij, "Select original Image");
        MeshImageStack2 mist = LoadZarr.loadMeshImageStack2(lbls.toPath());

        MeshDetector detector = new MeshDetector(mist);
        List<Region> regions = detector.getRegionsFromLabelledImage();
        System.out.println(regions.size());
        FillingBinaryImage mesher = new FillingBinaryImage(mist);
        mesher.setMinMaxLengths(0.008,0.016);
        mesher.setRemeshSteps(3);
        mesher.setRelaxSteps(300);
        List<DeformableMesh3D> meshes = regions.stream().map(
                mesher::fillBlobWithMesh
        ).collect(Collectors.toList());



        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);

        meshes.forEach(mesh ->{
            mesh.create3DObject();
            mesh.setShowSurface(true);
            mesh.data_object.setWireColor(new Color(0,0,0,0));
            mesh.data_object.setShowSurface(false);
            mesh.data_object.setShowSurface(true);
            mf3d.addDataObject(mesh.data_object);
        });

        mf3d.addLights();

        BufferedImage alt = mf3d.snapShot();
        new ImagePlus("low-res", alt).show();

        mf3d.getAxis().setLineWidth(3.0f);
        DataCanvas canvas = mf3d.getCanvas();
        float resolutionFactor = 10;
        BufferedImage img = canvas.snapShot(resolutionFactor);
        new ImagePlus("high-res", img).show();
    }

}
