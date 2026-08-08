package deformablemesh.util;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.util.connectedcomponents.ConnectedComponents3D;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ShortProcessor;

import java.util.List;

public interface ImageLabeller {
    ImagePlus labelImage(ImagePlus image);

    static ImageLabeller connectedComponents(){

        return (plus)->{
            ConnectedComponents3D cc3d = new ConnectedComponents3D();
            ImageStack stack;
            boolean ready = plus.getStack().getProcessor(1) instanceof ShortProcessor;
            if( ready ){
                stack = plus.getStack();
            } else{
                stack = new ImageStack(plus.getWidth(), plus.getHeight());
                for(int i = 1; i<=plus.getNSlices(); i++){
                    stack.addSlice(stack.getProcessor(i).convertToShort(false));
                }
            }
            ConnectedComponents3D.getRegions(stack);
            plus.setStack(stack);
            return plus;
        };
    }

    static ImageLabeller meshLabeller( List<DeformableMesh3D> meshes){
        return plus->{
            int w = plus.getWidth();
            int h = plus.getHeight();
            int n = plus.getNSlices();
            MeshImageStack stack = new MeshImageStack(plus);
            ImageStack out = new ImageStack(w, h);
            for(int j = 0; j<n; j++){
                out.addSlice(new ColorProcessor(w, h));
            }
            for(int i = 0; i<meshes.size(); i++){
                DeformableMesh3DTools.mosaicBinary(stack, out, meshes.get(i), i + 1);
            }

            ImagePlus res = plus.createImagePlus();
            res.setStack(out);
            return res;
        };
    }
}
