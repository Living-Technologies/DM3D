package deformablemesh;

public class ImageRegion3D {
    public final int lx, ly, lz, hx, hy, hz;

    ImageRegion3D(int lx, int ly, int lz, int hx, int hy, int hz) {
        this.lx = lx;
        this.ly = ly;
        this.lz = lz;
        this.hx = hx;
        this.hy = hy;
        this.hz = hz;
    }
    @Override
    public String toString(){
        return "ImageRegion3D: (" + lx + ", " + ly + ", " + lz + ") (" + hx + ", " + hy +", " + hz + ")";
    }
}
