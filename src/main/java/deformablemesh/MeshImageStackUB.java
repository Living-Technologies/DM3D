package deformablemesh;

import ij.ImagePlus;

public class MeshImageStackUB extends MeshImageStack {
    public MeshImageStackUB(ImagePlus plus){
        super(plus, plus.getFrame(), plus.getChannel());
    }
    @Override
    public void copyValues(){
        //pass
    }
    @Override
    public double getValue(int x, int y, int z){
        return 0;
    }


}
