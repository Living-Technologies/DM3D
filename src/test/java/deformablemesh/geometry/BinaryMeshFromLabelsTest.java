package deformablemesh.geometry;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.topology.TopoCheck;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.track.Track;
import deformablemesh.util.ColorSuggestions;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class BinaryMeshFromLabelsTest {

    @Test
    public void edgeLabels(){
        ImagePlus plus = aIsotropicSpace();
        ImageStack s = plus.getStack();

        for(int i = 1; i<4; i++){
            ImageProcessor proc = s.getProcessor(i);
            for(int j = s.getWidth()/2 -1; j < s.getWidth()/2 + 2; j++){
                for(int k = s.getHeight()/2 - 1; k<s.getHeight()/2 + 2; k++){
                    proc.set(j, k, 1);
                }
            }
        }

        for(int i = plus.getNSlices()-2; i<=plus.getNSlices(); i++){
            ImageProcessor proc = s.getProcessor(i);
            for(int j = s.getWidth()/2 -1; j < s.getWidth()/2 + 2; j++){
                for(int k = s.getHeight()/2 - 1; k<s.getHeight()/2 + 2; k++){
                    proc.set(j, k, 2);
                }
            }
        }
        BinaryMeshGenerator generator = new BinaryMeshGenerator();
        List<DeformableMesh3D> meshes = generator.meshesFromLabels(new MeshImageStack(plus));
        Assert.assertEquals(2,meshes.size());

        long error = testHarness(plus);
        Assert.assertEquals(0, error);

    }



    public static ImagePlus aIsotropicSpace(){
        ImagePlus plus = new ImagePlus();
        int w = 100;
        int h = 200;
        int d = 30;
        ImageStack stack = new ImageStack(w, h);

        for(int i = 0; i<d; i++){
            stack.addSlice(new FloatProcessor(w, h));

        }
        plus.setStack(stack, 1, d, 1);
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

    static long testHarness(ImagePlus plus){
        BinaryMeshGenerator generator = new BinaryMeshGenerator();
        List<DeformableMesh3D> meshes = generator.meshesFromLabels(new MeshImageStack(plus));
        List<Track> tracks = meshes.stream().map(m -> {
            TopoCheck tc = new TopoCheck(m);
            List<DeformableMesh3D> m2 = tc.repairMesh();
            Track t = new Track("j");
            t.addMesh(0, m2.get(0));
            return t;
        }).collect(Collectors.toList());
        ImagePlus bin = DeformableMesh3DTools.asUniqueLabels(new MeshImageStack(plus), tracks);
        ImageStack os = plus.getStack();
        ImageStack bs = bin.getStack();
        long err = 0;
        for(int i = 1; i<= bs.getSize(); i++){
            float[] op = (float[])os.getProcessor(i).getPixels();
            short[] bp = (short[])bs.getProcessor(i).getPixels();
            for(int j = 0; j<op.length; j++){
                if( (op[j] == 0) != ( bp[j] == 0 )){
                    System.out.println(i + " :: " + j);
                    err++;
                }
            }
        }
        return err;
    }

    public static void main(String[] args){

        //ImagePlus plus = generate();

        ImagePlus plus = new ImagePlus("D:\\working\\maria\\sample-debug.tif");
        List<DeformableMesh3D> meshes = new BinaryMeshGenerator().meshesFromLabels(new MeshImageStack(plus));
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
