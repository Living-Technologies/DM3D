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
import org.jogamp.vecmath.Point3d;
import org.jogamp.vecmath.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 *  Construct with normalized coordinates.
 * Created by msmith on 4/15/14.
 */
public class ContractileRingDataObject implements DataObject {
    float LINEWIDTH = 3;

    Shape3D line3d;
    Transform3D scale;
    int FRAME;
    private BranchGroup BG;
    ColoringAttributes c_at;
    List<double[]> snake;
    public ContractileRingDataObject(List<double[]> s){
        snake = s;
        LineArray line = new LineArray(2*(snake.size()-1), GeometryArray.COORDINATES);
        for(int i=0; i<snake.size()-1; i++){
            line.setCoordinate(2*i,new Point3d(snake.get(i)));
            line.setCoordinate(2*i+1,new Point3d(snake.get(i+1)));

        }
        line3d = new Shape3D(line);
        line3d.setAppearance(createAppearance());
        line3d.setCapability(Shape3D.ALLOW_GEOMETRY_WRITE);



    }


    public BranchGroup getBranchGroup(){
        if(BG==null){
            Transform3D scale = new Transform3D();
            scale.setTranslation(new Vector3d(0,0,0));
            Vector3d saxis = new Vector3d(new double[]{1.,1.,1.});
            scale.setScale( saxis);
            TransformGroup tg = new TransformGroup();
            tg.setTransform(scale);
            tg.addChild(line3d);
            BG = new BranchGroup();
            BG.setCapability(BranchGroup.ALLOW_DETACH);
            BG.addChild(tg);

        }
        return BG;

    }

    public Appearance createAppearance(){
        Appearance a = new Appearance();

        c_at = new ColoringAttributes(1f, 0f, 0f, ColoringAttributes.NICEST);
        c_at.setCapability(ColoringAttributes.ALLOW_COLOR_WRITE);

        LineAttributes la = new LineAttributes();
        la.setLineWidth(LINEWIDTH);
        a.setColoringAttributes(c_at);
        a.setLineAttributes(la);

        return a;

    }

    /**
     * updates the geometry of snakes, not safe if there are zero points.
     */
    public void updateGeometry(){

        List<double[]> spts = new ArrayList<>(snake);
        int N =spts.size();
        if(N>1){
            int nodes = 2*(N-1);
            LineArray line = new LineArray(nodes,GeometryArray.COORDINATES);

            for(int i=0; i<N-1; i++){
                line.setCoordinate(2*i,new Point3d(spts.get(i)));
                line.setCoordinate(2*i+1,new Point3d(spts.get(i+1)));

            }
            line3d.setGeometry(line);
        } else{
            line3d.setGeometry(null);
        }

    }
    public Node getNode(){
        return line3d;
    }

    public void setColor(int v){
        if(v==0){
            c_at.setColor(1f,0f,0f);

        } else{

            c_at.setColor(0f,1f,0f);
        }

    }
}
