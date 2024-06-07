package deformablemesh.geometry;

import java.util.List;

public class PixelBlob {
    int w, h, d;
    final int lx, ly, lz;
    final int hx, hy, hz;

    byte[][] blob;
    public PixelBlob(List<int[]> pixels){

        int[] low = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] high = {-1, -1, -1};
        for(int[] px: pixels){
            for(int i = 0; i<px.length; i++){
                low[i] = Math.min(low[i], px[i]);
                high[i] = Math.max(high[i], px[i]);
            }
        }

        lx = low[0];
        ly = low[1];
        lz = low[2];
        hx = high[0];
        hy = high[1];
        hz = high[2];
        w = hx - lx + 1;
        h = hy - ly + 1;
        d = hz - lz + 1;
        blob = new byte[d][w*h];
        for(int[] px: pixels){
            for(int i = 0; i<px.length; i++){
                blob[px[2] - lz][(px[1] - ly)*w + (px[0] - lx)] = 0x1;
            }
        }


    }

    public boolean contains(int x, int y, int z){
        int zi = z - lz;
        int yi = (y - ly);
        int xi = (x - lx);
        return (zi >= 0 && zi < d && xi >= 0 && xi < w && yi >= 0 && yi < h)
                && ( blob[ zi ][ ( yi ) * w + xi ] != 0 );
    }
}
