/*-
 * #%L
 * Triangulated surface for deforming in 3D.
 * %%
 * Copyright (C) 2013 - 2023 University College London
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package deformablemesh.meshview;

import org.jogamp.java3d.*;
import org.jogamp.java3d.utils.geometry.Cone;
import org.jogamp.java3d.utils.geometry.Cylinder;
import org.jogamp.vecmath.AxisAngle4d;
import org.jogamp.vecmath.Color3f;
import org.jogamp.vecmath.Vector3d;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class Arrow implements DataObject {
    float length = 1.0f;
    float width = length/3f;

    BranchGroup b = new BranchGroup();

    double[] position = {0, 0, 0};
    double[] direction = {0, 1, 0};
    double scale = 1.0;
    TransformGroup mainTransform;
    AxisAngle4d looking = new AxisAngle4d(0, 1, 0, 0);
    final static Vector3d forward = new Vector3d(0, 1, 0);
    double tol = 1e-6;

    /**
     * Constructs an Arrow with default length of 1, width of 1/3, tip is 0.5 and tail_fraction is 0.4.
     */
    public Arrow(){
        this(1.0, 1.0/3.0);
    }

    /**
     * Uses default values for tip: 0.5 of length and tail_width: 0.4 of width
     * @param length length of arrow along pointing axis from tail to tip.
     * @param width width of cone for arrowhead.
     */
    public Arrow(double length, double width){
        this(length, width, 0.5);
    }

    /**
     * Uses default tip tail_width of 0.4 cone width.
     *
     * @param length length of arrow along pointing axis from tail to tip.
     * @param width width of cone for arrowhead.
     * @param tip fraction of length for cone section.
     */
    public Arrow(double length, double width, double tip){
        this(length, width, tip, 0.4);
    }

    /**
     * Construct an arrow with the supplied geometry
     *
     * @param length
     * @param width
     * @param tip
     * @param tail_width
     */
    public Arrow(double length, double width, double tip, double tail_width){
        this((float)length, (float)width, (float)tip, (float)tail_width);
    }

    private Arrow(float length, float width, float tip, float fraction) {
        Cone cone = new Cone(width, tip * length, createTipAppearance());

        Cylinder cylinder = new Cylinder(fraction * width, (1 - tip) * length, createTailAppearance());
        TransformGroup tg = new TransformGroup();
        Transform3D offset = new Transform3D();
        offset.setTranslation(new Vector3d(0, length / 2 * (1 - tip), 0));
        tg.setTransform(offset);
        tg.addChild(cone);

        TransformGroup tg2 = new TransformGroup();
        Transform3D offset2 = new Transform3D();
        offset2.setTranslation(new Vector3d(0, -length / 2 * tip, 0));
        tg2.setTransform(offset2);
        tg2.addChild(cylinder);

        mainTransform = new TransformGroup();

        mainTransform.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        mainTransform.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);

        mainTransform.addChild(tg);
        mainTransform.addChild(tg2);
        b.addChild(mainTransform);
        b.setCapability(BranchGroup.ALLOW_DETACH);
    }

    public Appearance createTailAppearance(){
        Color3f eColor    = new Color3f(0.0f, 0.0f, 1.0f);
        Color3f sColor    = new Color3f(1.0f, 1.0f, 1.0f);
        Color3f objColor  = new Color3f(1.0f, 1.0f, 1.0f);

        Material m = new Material(objColor, eColor, objColor, sColor, 100.0f);
        Appearance a = new Appearance();


        m.setLightingEnable(true);
        a.setMaterial(m);

        return a;
    }
    public void update(){
        Transform3D tt = new Transform3D();
        mainTransform.getTransform(tt);

        tt.setScale(scale);
        tt.setRotation(looking);
        Vector3d n = new Vector3d(position[0], position[1], position[2]);
        tt.setTranslation(n);

        mainTransform.setTransform(tt);
    }

    public void setScale(double s){
        scale = s;
        update();
    }


    public Appearance createTipAppearance(){
        Color3f eColor    = new Color3f(0.0f, 0.0f, 1.0f);
        Color3f sColor    = new Color3f(1.0f, 1.0f, 1.0f);
        Color3f objColor  = new Color3f(0.6f, 0.6f, 0.6f);

        Material m = new Material(objColor, eColor, objColor, sColor, 100.0f);
        Appearance a = new Appearance();


        m.setLightingEnable(true);
        a.setMaterial(m);

        return a;
    }

    public void moveTo(double x, double y, double z){
        position[0] = x;
        position[1] = y;
        position[2] = z;
        update();
    }

    public void pointAlong(double[] direction){
        Vector3d p = new Vector3d(direction);
        Vector3d axis = new Vector3d(0, 0, 0);
        axis.cross(forward, p);
        double l = axis.length();
        if(l*l<tol){
            //small cross product could make axis non-normalizable.
            axis = new Vector3d(0, 0, 1);
        }
        axis.normalize();
        double dot = p.dot(forward);
        double theta = Math.acos(dot);

        looking = new AxisAngle4d(axis, theta);
        update();
    }
    @Override
    public BranchGroup getBranchGroup() {
        return b;
    }

    public static void main(String[] args) throws InterruptedException {
        MeshFrame3D frame = new MeshFrame3D();
        frame.showFrame(true);
        frame.setBackgroundColor(Color.BLACK);
        frame.addLights();
        Arrow obj = new Arrow();
        frame.addDataObject(obj);
        frame.addKeyListener(new KeyListener() {
            double radius = 1;
            double x = 0;
            double y = 0;
            double z = 0;

            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_EQUALS:
                        radius *= 2;
                        obj.setScale(radius);
                        break;
                    case KeyEvent.VK_MINUS:
                        radius /= 2;
                        obj.setScale(radius);
                        break;
                    case KeyEvent.VK_LEFT:
                        x -= 0.1;
                        obj.moveTo(x, y, z);
                        break;
                    case KeyEvent.VK_RIGHT:
                        x += 0.1;
                        obj.moveTo(x,y,z);
                        break;
                    case KeyEvent.VK_UP:
                        y += 0.1;
                        obj.moveTo(x,y,z);
                        break;
                    case KeyEvent.VK_DOWN:
                        y -= 0.1;
                        obj.moveTo(x,y,z);
                        break;
                    case KeyEvent.VK_SPACE:
                        obj.pointAlong(new double[]{0, 0, 1});
                    default:
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });

        while(true){
            for(int j = 0; j<12; j++) {
                double phi = 2*Math.PI * j/12 + 0.1;
                for (int i = 0; i < 12; i++) {
                    double theta = 2*Math.PI*i/12;
                    double z = Math.cos(phi);
                    double y = Math.sin(phi)*Math.cos(theta);
                    double x = Math.sin(phi)*Math.sin(theta);
                    obj.pointAlong(new double[]{x, y, z});
                    Thread.sleep(60);
                    phi += 2*Math.PI/144;
                }
            }

        }

    }

    public void setColor(Color r){


    }


}
