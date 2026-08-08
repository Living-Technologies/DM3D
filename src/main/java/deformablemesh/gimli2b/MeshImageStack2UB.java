package deformablemesh.gimli2b;

import bdv.viewer.Source;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

import java.util.List;

public class MeshImageStack2UB<T extends NumericType<T> & NativeType<T>> extends MeshImageStack2<T> {
    RandomAccessibleInterval<T> rai;
    public MeshImageStack2UB(List<Source<T>> sources){
        super(sources, 0, 0);
    }
    public MeshImageStack2UB(List<Source<T>> sources, int frame, int channel, int mipmap){
        super(sources, frame, channel, mipmap);
    }
    @Override
    public void copyValues(){
    }
    @Override
    public double getValue(int x, int y, int z){
        return 0;
    }

}
