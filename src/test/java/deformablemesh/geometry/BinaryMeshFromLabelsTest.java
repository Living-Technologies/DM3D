package deformablemesh.geometry;

import deformablemesh.MeshImageStack;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.util.ColorSuggestions;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.*;
import java.util.List;

public class BinaryMeshFromLabelsTest {

    public static ImagePlus aIsotropicSpace(){
        ImagePlus plus = new ImagePlus();
        ImageStack stack = new ImageStack(64, 64);

        for(int i = 0; i<16; i++){
            stack.addSlice(new FloatProcessor(64, 64));

        }
        plus.setStack(stack, 1, 16, 1);
        Calibration c = plus.getCalibration();
        c.pixelHeight = 0.35;
        c.pixelDepth = 2.0;
        c.pixelWidth = 0.35;
        c.setUnit("um");
        plus.setCalibration(c);

        return plus;
    }

    static ImagePlus generate(){
        ImagePlus plus = aIsotropicSpace();
        ImageStack stack = plus.getStack();
        for(int i = 3; i<12; i++){
            ImageProcessor proc = stack.getProcessor(i);
            for(int j = 1; j<20; j++){
                for(int k = 20; k<40; k++){
                    proc.set(j, k, 1);
                }
            }
        }
        for(int i = 8; i<16; i++){
            ImageProcessor proc = stack.getProcessor(i);
            for(int j = 20; j<40; j++){
                for(int k = 20; k<40; k++){
                    proc.set(j, k, 2);
                }
            }
        }


        return plus;
    }
    public static void main(String[] args){

        //ImagePlus plus = generate();

        ImagePlus plus = new ImagePlus("D:\\working\\maria\\sample-debug.tif");
        List<DeformableMesh3D> meshes = BinaryMeshGenerator.meshesFromLabels(new MeshImageStack(plus));
        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.setBackgroundColor(Color.BLACK);

        for(DeformableMesh3D mesh: meshes){
            mesh.create3DObject();
            mesh.data_object.setWireColor(ColorSuggestions.getSuggestion());
            mf3d.addDataObject(mesh.data_object);
        }


    }
}
