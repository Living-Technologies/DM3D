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

import deformablemesh.geometry.Node3D;
import deformablemesh.util.Vector3DOps;
import org.jogamp.java3d.*;
import org.jogamp.java3d.utils.geometry.Sphere;
import org.jogamp.vecmath.Color3f;
import org.jogamp.vecmath.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * User: msmith
 * Date: 8/8/13
 * Time: 7:52 AM
 */
public class Axis3D implements DataObject {
    BranchGroup bg;
    Color wireColor = Color.LIGHT_GRAY;
    double lx, ly, lz, hx, hy, hz;
    List<LineDataObject> wires = new ArrayList<>();
    double[] positions;
    float lineWidth = 0.5f;
    void generatePositions(){
        positions = new double[] {
                lx, ly, lz,
                lx,  hy, lz,
                hx,  hy, lz,
                hx, ly, lz,
                lx, ly, hz,
                lx,  hy,  hz,
                hx,  hy, hz,
                hx, ly, hz,
                0, 0, 0,
                hx - lx, 0, 0,
                0, hy- ly, 0,
                0, 0, hz - lz
        };
    }
    public Axis3D(){
        this(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);
    }

    public Axis3D(double lx, double ly, double lz, double hx, double hy, double hz){
        this.lx = lx;
        this.ly = ly;
        this.lz = lz;
        this.hx = hx;
        this.hy = hy;
        this.hz = hz;
        generatePositions();

        bg = new BranchGroup();
        bg.setCapability(BranchGroup.ALLOW_DETACH);

        Transform3D transformx = new Transform3D();
        transformx.setTranslation(new Vector3f((float)(hx - lx),0f,0f));

        Appearance a = new Appearance();

        a.setColoringAttributes(new ColoringAttributes(new Color3f(1f,0f,0f),ColoringAttributes.FASTEST));
        Sphere spherex = new Sphere((float)0.01, Sphere.GENERATE_NORMALS, 50, a);
        TransformGroup tgx = new TransformGroup(transformx);
        tgx.addChild(spherex);

        a = new Appearance();
        a.setColoringAttributes(new ColoringAttributes(new Color3f(0f,1f,0f),ColoringAttributes.FASTEST));
        Sphere spherey = new Sphere((float)0.01, Sphere.GENERATE_NORMALS, 50, a);
        Transform3D transformy = new Transform3D();
        transformy.setTranslation(new Vector3f(0f,(float)(hy - ly),0f));
        TransformGroup tgy = new TransformGroup(transformy);
        tgy.addChild(spherey);

        a = new Appearance();
        a.setColoringAttributes(new ColoringAttributes(new Color3f(0f, 0f, 1f), ColoringAttributes.FASTEST));
        Sphere spherez = new Sphere((float)0.01, Sphere.GENERATE_NORMALS, 50, a);

        Transform3D transformz = new Transform3D();
        transformz.setTranslation(new Vector3f(0f, 0f, (float)(hz - lz)));
        TransformGroup tgz = new TransformGroup(transformz);
        tgz.addChild(spherez);

        bg.addChild(tgx);
        bg.addChild(tgy);
        bg.addChild(tgz);


        List<Node3D> nodes = new ArrayList<>();
        for(int i = 0; i<positions.length/3; i++){
            nodes.add(new Node3D(positions, i));
        }
        LineDataObject obj = new LineDataObject(Arrays.asList(nodes.get(0), nodes.get(1), nodes.get(2), nodes.get(3), nodes.get(0)), lineWidth);
        wires.add(obj);
        obj = new LineDataObject(Arrays.asList(nodes.get(4), nodes.get(5), nodes.get(6), nodes.get(7), nodes.get(4)), lineWidth);
        wires.add(obj);
        for(int i = 0; i<4; i++){
            obj = new LineDataObject(Arrays.asList(nodes.get(i), nodes.get(i+4)), lineWidth);
            wires.add(obj);
        }

        obj = new LineDataObject(Arrays.asList(nodes.get(8), nodes.get(9)), lineWidth);
        wires.add(obj);
        obj = new LineDataObject(Arrays.asList(nodes.get(8), nodes.get(10)), lineWidth);
        wires.add(obj);
        obj = new LineDataObject(Arrays.asList(nodes.get(8), nodes.get(11)), lineWidth);
        wires.add(obj);

        //four xs
        drawTics(new double[]{lx, ly, lz}, new double[]{hx, ly, lz}, Vector3DOps.yhat);
        drawTics(new double[]{lx, ly, lz}, new double[]{hx, ly, lz}, Vector3DOps.zhat);
        drawTics(new double[]{lx, hy, lz}, new double[]{hx, hy, lz}, Vector3DOps.nyhat);
        drawTics(new double[]{lx, hy, lz}, new double[]{hx, hy, lz}, Vector3DOps.zhat);
        drawTics(new double[]{lx, ly, hz}, new double[]{hx, ly, hz}, Vector3DOps.yhat);
        drawTics(new double[]{lx, ly, hz}, new double[]{hx, ly, hz}, Vector3DOps.nzhat);
        drawTics(new double[]{lx, hy, hz}, new double[]{hx, hy, hz}, Vector3DOps.nyhat);
        drawTics(new double[]{lx, hy, hz}, new double[]{hx, hy, hz}, Vector3DOps.nzhat);

        //four ys
        drawTics(new double[]{lx, ly, lz}, new double[]{lx, hy, lz}, Vector3DOps.xhat);
        drawTics(new double[]{lx, ly, lz}, new double[]{lx, hy, lz}, Vector3DOps.zhat);
        drawTics(new double[]{hx, ly, lz}, new double[]{hx, hy, lz}, Vector3DOps.nxhat);
        drawTics(new double[]{hx, ly, lz}, new double[]{hx, hy, lz}, Vector3DOps.zhat);
        drawTics(new double[]{lx, ly, hz}, new double[]{lx, hy, hz}, Vector3DOps.xhat);
        drawTics(new double[]{lx, ly, hz}, new double[]{lx, hy, hz}, Vector3DOps.nzhat);
        drawTics(new double[]{hx, ly, hz}, new double[]{hx, hy, hz}, Vector3DOps.nxhat);
        drawTics(new double[]{hx, ly, hz}, new double[]{hx, hy, hz}, Vector3DOps.nzhat);
        //four zs
        drawTics(new double[]{lx, ly, lz}, new double[]{lx, ly, hz}, Vector3DOps.xhat);
        drawTics(new double[]{lx, ly, lz}, new double[]{lx, ly, hz}, Vector3DOps.yhat);
        drawTics(new double[]{hx, ly, lz}, new double[]{hx, ly, hz}, Vector3DOps.nxhat);
        drawTics(new double[]{hx, ly, lz}, new double[]{hx, ly, hz}, Vector3DOps.yhat);
        drawTics(new double[]{lx, hy, lz}, new double[]{lx, hy, hz}, Vector3DOps.xhat);
        drawTics(new double[]{lx, hy, lz}, new double[]{lx, hy, hz}, Vector3DOps.nyhat);
        drawTics(new double[]{hx, hy, lz}, new double[]{hx, hy, hz}, Vector3DOps.nxhat);
        drawTics(new double[]{hx, hy, lz}, new double[]{hx, hy, hz}, Vector3DOps.nyhat);

        for(LineDataObject ldo : wires){
            float[] c = wireColor.getRGBComponents(new float[4]);
            ldo.setColor(c[0], c[1], c[2]);
            bg.addChild(ldo.getBranchGroup());
        }
    }
    public void setLineWidth(float lw){
        lineWidth = lw;
        for(LineDataObject ldo : wires){
            ldo.setLineWidth(lineWidth);
        }
    }

    private void drawTics(double[] a, double[] b, double[] n){
        double[] axis = Vector3DOps.difference(b, a);
        double l = Vector3DOps.normalize(axis);
        double ll = 1.0;

        //Longest axis has 50 tics
        int tics = 50;
        double dl = ll/tics;
        double minor = dl/2;

        int ntics = (int)(l/dl);

        for(int i = 0; i<ntics; i++){
            double[] a0 = Vector3DOps.add(a, axis, dl*i);
            double tl = i%5 == 0 ? 2*minor : minor;
            double[] a1 = Vector3DOps.add(a0, n, tl);
            LineDataObject ldo = new LineDataObject(
                    Arrays.asList(new Node3D(a0, 0), new Node3D(a1, 0)), lineWidth
            );
            wires.add(ldo);
        }
    }

    public void setWireColor(Color c){
        wireColor = c;
        float[] comps = wireColor.getRGBComponents(new float[4]);
        for(LineDataObject ldo: wires){
            ldo.setColor(comps[0], comps[1], comps[2]);
        }
    }

    @Override
    public BranchGroup getBranchGroup() {
        return bg;
    }
}
