package deformablemesh.meshview;

import org.scijava.vecmath.Vector4f;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class LabeledVoxelPainter implements VoxelPainter{
    Map<Double, Vector4f> colors = new HashMap<>();
    float alpha = 1f;
    public LabeledVoxelPainter(double background){
        colors.put(background, new Vector4f(0, 0, 0, 0));
    }
    @Override
    public Vector4f getColor(double value) {
        return colors.computeIfAbsent(value, this::newColor);
    }
    private Vector4f newColor(Double value){
        int i = value.intValue();
        i = ((i & 0xffff0000) >> 16) + (i&0xffff);
        int r = ( i * 37 ) & 0xff;
        int g = ( i * 49 + 127) & 0xff;
        int b = ( i * 173 >> 4 ) & 0xff;

        return new Vector4f( r/255f, g/255f, b/255f, alpha);
    }
    public void setColor(double value, Color color){
        float[] comps = color.getColorComponents(new float[4]);
        comps[3] = alpha;
        colors.put(value, new Vector4f(comps));
    }
}
