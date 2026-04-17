package deformablemesh.gimli2b;

import bdv.viewer.Source;
import ij.process.ImageProcessor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;

import java.util.List;

public class MeshImageStack2UB<T extends NumericType<T> & NativeType<T> & RealType<T>> extends MeshImageStack2<T> {
    RandomAccessibleInterval<T> rai;
    public MeshImageStack2UB(List<Source<T>> sources){
        super(sources, 0, 0);
    }

    @Override
    public void copyValues(){
    }
    @Override
    public double getValue(int x, int y, int z){
        return 0;
    }

}
