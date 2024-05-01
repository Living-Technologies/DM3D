package deformablemesh.meshview;

import org.jogamp.java3d.*;
import org.jogamp.vecmath.Point3d;
import org.jogamp.vecmath.Quat4d;
import org.jogamp.vecmath.TexCoord2f;
import org.jogamp.vecmath.Vector3d;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;

public class TexturedPlane implements DataObject {
    final static int[] triangle_index_front = new int[]{
            0, 2, 1,
            0, 3, 2
    };

    BranchGroup branchGroup;

    IndexedTriangleArray surface;
    double[] positions;

    Shape3D front;

    public TexturedPlane(double[] cm, double[] normal, double length) {
        positions = new double[]{
                -length,-length,0,
                length,-length,0,
                length,length,0,
                -length,length,0

        };


        surface = new IndexedTriangleArray(4,GeometryArray.COORDINATES | GeometryArray.TEXTURE_COORDINATE_2, 6);

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

        //updatePosition(cm, normal);

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
                Point3d point = new Point3d(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
                tt.transform(point);
                tty[3 * i] = point.x;
                tty[3 * i + 1] = point.y;
                tty[3 * i + 2] = point.z;
            }


            surface.setCoordinates(0, tty);
            front.setGeometry(surface);
        }
    }
    private Appearance createAppearance(){
        Texture2D texture = new Texture2D(Texture2D.BASE_LEVEL, Texture2D.RGBA, 512, 512);
        texture.setCapability(Texture2D.ALLOW_IMAGE_WRITE);
        ColorSpace colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        ComponentColorModel colorModel =
                new ComponentColorModel(colorSpace, true, false,
                        Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
        WritableRaster raster =
                colorModel.createCompatibleWritableRaster(512, 512);
        BufferedImage img = new BufferedImage(colorModel, raster, false, null);
        byte[] byteData = ((DataBufferByte)raster.getDataBuffer()).getData();
        for(int i = 0; i<byteData.length; i++){
            if(i%4 == 3){
                byteData[i] = 100;
            } else {
                byteData[i] = (byte)((i*i*i)%255);
            }
        }
        ImageComponent2D ic2d = new ImageComponent2D(ImageComponent.FORMAT_RGBA, img);
        texture.setImage(0, ic2d);
        Appearance a = new Appearance();
        a.setTexture(texture);
        a.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.NICEST, 0.01f));

        return a;

    }

    @Override
    public BranchGroup getBranchGroup() {
        return branchGroup;
    }

    public static void main(String[] args){

        TexturedPlane plane = new TexturedPlane(new double[]{0, 0,0}, new double[]{0, 0, 1}, 1);
        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.addDataObject(plane);


    }
}