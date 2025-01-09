package deformablemesh.gimli2b;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

public interface NatNumType<T extends NumericType<T> & NativeType<T>> extends NumericType<T>, NativeType<T> {
}
