package deformablemesh.meshview;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.AngleGenerator;
import deformablemesh.geometry.Box3D;
import deformablemesh.geometry.Furrow3D;
import deformablemesh.ringdetection.FurrowTransformer;
import deformablemesh.util.Vector3DOps;
import ij.ImagePlus;
import org.scijava.java3d.*;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Quat4d;
import org.scijava.vecmath.TexCoord2f;
import org.scijava.vecmath.Vector3d;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.nio.file.Paths;
import java.util.Arrays;

public class TexturedPlane implements DataObject {
    int width = 512;
    int height = 512;

    final static int[] triangle_index_front = new int[]{
            0, 2, 1,
            0, 3, 2,
            0, 1, 2,
            0, 2, 3
    };

    BranchGroup branchGroup;

    IndexedTriangleArray surface;
    double[] rawPositions, positions;

    Shape3D front;
    Color color = new Color(255, 255, 255, 255);
    Color bg = new Color(0, 0, 0, 150);
    double max =2000;
    double min = 200;
    MeshImageStack stack;
    double[] xhat, yhat, c0;
    public TexturedPlane(double[] cm, double[] normal, double length) {
        rawPositions = new double[]{
                -length,-length,0,
                length,-length,0,
                length,length,0,
                -length,length,0

        };

        positions = Arrays.copyOf(rawPositions, rawPositions.length);


        surface = new IndexedTriangleArray(4,GeometryArray.COORDINATES | GeometryArray.TEXTURE_COORDINATE_2, 12);

        surface.setCoordinates(0, positions);
        surface.setCoordinateIndices(0, triangle_index_front);
        surface.setTextureCoordinateIndices(0, 0, triangle_index_front);
        surface.setTextureCoordinate(0, 0, new TexCoord2f(0, 0));
        surface.setTextureCoordinate(0, 1, new TexCoord2f(1, 0));
        surface.setTextureCoordinate(0, 2, new TexCoord2f(1, 1));
        surface.setTextureCoordinate(0, 3, new TexCoord2f(0, 1));

        surface.setCapability(GeometryArray.ALLOW_COORDINATE_WRITE);

        front = new Shape3D(surface);
        front.setAppearance(createAppearance());
        front.setCapability(Shape3D.ALLOW_GEOMETRY_WRITE);


        branchGroup = new BranchGroup();
        branchGroup.setCapability(BranchGroup.ALLOW_DETACH);
        branchGroup.setCapability(BranchGroup.ALLOW_PICKABLE_READ);
        branchGroup.setCapability(BranchGroup.ALLOW_PICKABLE_WRITE);

        branchGroup.addChild(front);

        updatePosition(cm, normal);

    }
    public void setStack(MeshImageStack stack){
        this.stack = stack;
        updateTexture();
    }

    /**
     *  Moves the position of the visible plane.
     *
     * @param cm
     * @param normal
     */
    private final static Vector3d UP = new Vector3d(new double[]{0,0,1});
    public void updatePosition(double[] cm, double[] normal){
        synchronized(positions) {
            Transform3D tt = new Transform3D();
            Vector3d norm = new Vector3d(normal);
            Vector3d x = new Vector3d();
            x.cross(UP, norm);
            double cos = UP.dot(norm);
            double st2 = Math.sqrt(0.5 - cos * 0.5);
            double ct2 = Math.sqrt(0.5 + cos * 0.5);

            if (x.lengthSquared() != 0) {
                x.normalize();
            } else {
                x.cross(new Vector3d(new double[]{0, 1, 0}), norm);
            }


            Quat4d rot = new Quat4d(new double[]{x.x * st2, x.y * st2, x.z * st2, ct2});
            tt.setRotation(rot);
            tt.setTranslation(new Vector3d(cm));

            double[] tty = new double[positions.length];
            for (int i = 0; i < 4; i++) {
                Point3d point = new Point3d(rawPositions[i * 3], rawPositions[i * 3 + 1], rawPositions[i * 3 + 2]);
                tt.transform(point);
                tty[3 * i] = point.x;
                tty[3 * i + 1] = point.y;
                tty[3 * i + 2] = point.z;
            }

            System.arraycopy(tty, 0, positions, 0, tty.length);
            c0 = new double[]{positions[0], positions[1], positions[2]};
            xhat = Vector3DOps.difference(new double[]{positions[3], positions[4], positions[5]}, c0);
            yhat = Vector3DOps.difference(new double[]{positions[9], positions[10], positions[11]}, c0);

            if(stack != null){
                ImageComponent2D ic2d = updateTexture();
                surface.setCoordinates(0, positions);
                front.getAppearance().getTexture().setImage(0, ic2d);
            } else{
                surface.setCoordinates(0, positions);
            }
        }
    }
    private Appearance createAppearance(){
        Texture2D texture = new Texture2D(Texture2D.BASE_LEVEL, Texture2D.RGBA, width, height);
        texture.setCapability(Texture2D.ALLOW_IMAGE_WRITE);
        ImageComponent2D com = updateTexture();
        texture.setImage(0, com);
        Appearance a = new Appearance();
        a.setTexture(texture);
        a.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.NICEST, 0.01f));
        return a;

    }

    /**
     * The plane exists in normalized coordinates. It has an image on
     * it that goes for 0, 0 to width, height. The corresponding positions
     * are at vertex 0 and vertex 2. The x direction is at vertex 1 and the
     * y direction is at vertex 3;
     *
     *
     * @param x
     * @param y
     * @return
     */
    double[] getNormalizedCoordinates(int x, int y){
        double xf = x*1.0/width;
        double yf = (height - y)*1.0/height;
        return Vector3DOps.add(Vector3DOps.add(c0, xhat, xf), yhat, yf);
    }
    byte[] interpolate(Color a, Color b, double factor){

        double r = (a.getRed() - b.getRed())*factor + b.getRed();
        double g = (a.getGreen() - b.getGreen())*factor + b.getGreen();
        double bl = (a.getBlue() - b.getBlue())*factor + b.getBlue();
        double al = (a.getAlpha() - b.getAlpha())*factor + b.getAlpha();
        return new byte[] { (byte)r, (byte)g, (byte)bl, (byte)al};
    }
    public ImageComponent2D updateTexture(){
        ColorSpace colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        ComponentColorModel colorModel =
                new ComponentColorModel(colorSpace, true, false,
                        Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
        WritableRaster raster =
                colorModel.createCompatibleWritableRaster(512, 512);
        BufferedImage img = new BufferedImage(colorModel, raster, false, null);


        if(stack == null){
            return new ImageComponent2D(ImageComponent.FORMAT_RGBA, img);
        }

        byte[] byteData = ((DataBufferByte)raster.getDataBuffer()).getData();
        Box3D bounds = stack.getBounds();
        byte[] empty = new byte[4];

        for(int i = 0; i<width; i++){
            for(int j = 0; j<height; j++){

                double[] pt = getNormalizedCoordinates(i, j);
                byte[] c;
                if(bounds.contains(pt)) {
                    double v = stack.getInterpolatedValue(pt);
                    if (v > max) {
                        c = interpolate(color, bg, 1);
                    } else if (v > 200) {
                        c = interpolate(color, bg, (v - min)/(max - min));
                    } else{
                        c = interpolate(color, bg, 0);
                    }
                } else{
                    c = empty;
                }

                int n = 4*(i + j*width);
                byteData[n] = c[0];
                byteData[n+1] = c[1];
                byteData[n+2] = c[2];
                byteData[n+3] = c[3];


            }
        }
        return new ImageComponent2D(ImageComponent.FORMAT_RGBA, img);

    }

    public Shape3D getShape(){
        return front;
    }

    @Override
    public BranchGroup getBranchGroup() {
        return branchGroup;
    }

    public static void main(String[] args) throws Exception{
        ImagePlus plus = new ImagePlus(Paths.get("sample.tif").toAbsolutePath().toString());
        plus.show();
        MeshImageStack stack = new MeshImageStack(plus);

        TexturedPlane plane = new TexturedPlane(new double[]{0, 0,0}, new double[]{0, 0, 1}, 1);
        plane.setStack(stack);
        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.addDataObject(plane);
        double[] center = {0, 0, 0};
        double[] normal = {0, 0, 1};
        plane.updateTexture();
        double[] tmp = new double[3];
        for(int i = 0; i<stack.getNSlices(); i++){
            tmp[2] = i;
            center[2] = stack.getNormalizedCoordinate(tmp)[2];
            plane.updatePosition(center, normal);
        }
        AngleGenerator gen = new AngleGenerator(100, 20);

        center[2] = 0;
        while(gen.hasNext()){
            normal = gen.next();
            plane.updatePosition(center, normal);
            Thread.sleep(30);
        }


    }
}