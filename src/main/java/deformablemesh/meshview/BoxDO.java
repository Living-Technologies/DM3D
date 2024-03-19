package deformablemesh.meshview;

import deformablemesh.geometry.Box3D;
import org.jogamp.java3d.*;
import org.jogamp.vecmath.Vector3f;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * For showing bounding boxes as wireframes.
 */
public class BoxDO implements DataObject{
    BranchGroup branchGroup;
    TransformGroup transform;
    ColoringAttributes colorAttributes;
    public BoxDO(Box3D box){
        Appearance ap = generateAppearance();
        LineArray la = new LineArray(24, LineArray.COORDINATES);
        la.setCoordinates(0, getPoints(box.low, box.high));
        transform = new TransformGroup();
        transform.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        Shape3D shape = new Shape3D(la);
        shape.setAppearance(ap);
        transform.addChild(shape);
        setCenter(box.getCenter());
    }

    float[] getPoints(double[] low, double[] high){

        float lx = (float)(high[0] - low[0])/2;
        float ly = (float)(high[1] - low[1])/2;
        float lz = (float)(high[2] - low[2])/2;

        return new float[]{
                -lx, -ly, -lz,   lx, -ly, -lz,
                -lx,  ly, -lz,   lx,  ly, -lz,
                -lx, -ly, -lz,  -lx,  ly, -lz,
                 lx, -ly, -lz,   lx,  ly, -lz,
                -lx, -ly,  lz,   lx, -ly,  lz,
                -lx,  ly,  lz,   lx,  ly,  lz,
                -lx, -ly,  lz,  -lx,  ly,  lz,
                 lx, -ly,  lz,   lx,  ly,  lz,
                 lx,  ly, -lz,   lx,  ly,  lz,
                -lx,  ly, -lz,  -lx,  ly,  lz,
                -lx, -ly, -lz,  -lx, -ly,  lz,
                 lx, -ly, -lz,   lx, -ly,  lz

        };
    }
    Appearance generateAppearance(){
        Appearance a = new Appearance();

        colorAttributes = new ColoringAttributes(0f, 0f, 0f, ColoringAttributes.NICEST);
        colorAttributes.setCapability(ColoringAttributes.ALLOW_COLOR_WRITE);

        LineAttributes la = new LineAttributes();
        la.setLineWidth(1f);
        a.setColoringAttributes(colorAttributes);
        a.setLineAttributes(la);

        return a;
    }

    public void setCenter(double[] center){
        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f((float)center[0], (float)center[1], (float)center[2]));
        transform.setTransform(t);
    }
    public void setColor(Color c){
        float[] comps = c.getRGBColorComponents(new float[4]);
        colorAttributes.setColor(comps[0], comps[1], comps[2]);
    }
    public BranchGroup getBranchGroup(){
        if(branchGroup ==null){
            branchGroup = new BranchGroup();
            branchGroup.setCapability(BranchGroup.ALLOW_DETACH);
            branchGroup.addChild(transform);
        }
        return branchGroup;
    }

    public static void main(String[] args){
        MeshFrame3D frame = new MeshFrame3D();
        frame.showFrame(true);
        Box3D a = new Box3D(-0.2, -0.2, -0.2, 0, 0, 0);
        Box3D b = new Box3D(0, 0, 0, 0.3, 0.1, 0.02);
        Box3D c = new Box3D(0.5, 0.5, 0.5, 0.6, 0.6, 0.8);
        BoxDO objA = new BoxDO(a);
        frame.addDataObject(objA);
        BoxDO objB = new BoxDO(b);
        frame.addDataObject(objB);
        BoxDO objC = new BoxDO(c);
        frame.addDataObject(objC);

        frame.addKeyListener(new KeyListener() {
            double[] xyz = a.getCenter();

            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                switch(e.getKeyCode()) {
                    case KeyEvent.VK_SPACE:
                        objA.setColor(new Color((float)Math.random(), (float)Math.random(), (float)Math.random()));
                        break;
                    case KeyEvent.VK_LEFT:
                        xyz[0] += 0.1;
                        objA.setCenter(xyz);
                        break;
                    case KeyEvent.VK_RIGHT:
                        xyz[0] -= 0.1;
                        objA.setCenter(xyz);
                        break;
                    case KeyEvent.VK_UP:
                        xyz[1] += 0.1;
                        objA.setCenter(xyz);
                        break;
                    case KeyEvent.VK_DOWN:
                        xyz[1] -= 0.1;
                        objA.setCenter(xyz);
                        break;
                    default:
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });
    }
}
