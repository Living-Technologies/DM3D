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
package deformablemesh.gui.meshinitialization;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.projectable.Projectable;
import deformablemesh.geometry.projectable.ProjectableMesh;
import deformablemesh.gui.Drawable;
import deformablemesh.gui.GuiTools;
import deformablemesh.gui.Slice3DView;
import deformablemesh.ringdetection.FurrowTransformer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by msmith on 2/5/16.
 */
public class SlicePicker{
    Slice3DView view;
    MeshImageStack stack;
    double[] pos;
    double[] normal;
    Rectangle2D bounds;
    FurrowTransformer transformer;
    int transpose = 0;
    JLabel label;
    Map<Projectable, Drawable> projectDrawingMapper = new HashMap<>();
    double length = 1;
    ThreeDCursor cursor;

    JScrollPane scroll;
    JSlider slider;
    int graduations = 10000;
    public SlicePicker(MeshImageStack s, double[] normal, double[] center){

        stack = s;
        pos = center;
        this.normal = normal;
        transformer = stack.createFurrowTransform(pos, normal);
        cursor = new ThreeDCursor(0,0,0);
        cursor.setVisible(false);
    }

    /**
     * Sets the limits that this picker can choose, ranging from -l/2 to +l/2. This defaults to 1.
     * @param l
     */
    public void setLength(double l){
        length = l;
    }

    /**
     *
     * @param cursor
     */
    public void addCursor(ThreeDCursor cursor){
        view.addDrawable(cursor.getDrawable(transformer));
        this.cursor = cursor;
    }
    public void refreshSlice(){
        transformer = stack.createFurrowTransform(pos, normal);
        for(int i = 0; i<transpose; i++){
            transformer.rotatePiOver2();
        }
        view.setSlice(stack.createSlice(transformer));
        view.panel.repaint();

    }

    /**
     * Sets the slider value to the coresponding graduation where
     * p = -length/2 is 0 and p = length/2 is graduations.
     * @param p
     */
    public void setPositionAlongNormal(double p){
        double v = (p + length/2)/length*graduations;
        int i = (int)v;
        if(i < 0) i = 0;
        if(i > graduations) i = graduations;
        setSliderValue(i);
    }
    public void setSliderValue(int v){
        slider.setValue(v);
    }
    /**
     * When the slider is adjusted this value sets the position.
     * @param v
     */
    public void sliderValueSet(int v){
        double f = (v*1.0/graduations - 0.5)*length;

        pos[0] = f*normal[0];
        pos[1] = f*normal[1];
        pos[2] = f*normal[2];
        cursor.toPosition(f, normal);
        transformer = stack.createFurrowTransform(pos, normal);
        for(int i = 0; i<transpose; i++){
            transformer.rotatePiOver2();
        }
        view.setSlice(stack.createSlice(transformer));
        view.panel.repaint();

    }
    public void rotateView(){
        transpose =(transpose+1)%4;
    }

    public JPanel buildView(){
        JPanel container = new JPanel();
        container.setLayout(new BorderLayout());
        view = new Slice3DView();

        transformer = stack.createFurrowTransform(pos, normal);
        for(int i = 0; i<transpose; i++){
            transformer.rotatePiOver2();
        }

        Image img = stack.createSlice(transformer);

        view.setSlice(img);
        bounds = new Rectangle2D.Double(0, 0, img.getWidth(view.panel), img.getHeight(view.panel));
        scroll = new JScrollPane(view.panel);
        scroll.setMaximumSize(new Dimension(600, 600));
        container.add(scroll, BorderLayout.CENTER);

        label = new JLabel("Select the center plane, and click on the extreme points.");
        label.setMaximumSize(new Dimension(600, 30));

        container.add(label, BorderLayout.NORTH);
        slider = new JSlider(0, graduations);
        slider.setValue(5000);
        slider.setOrientation(JSlider.VERTICAL);
        slider.addChangeListener(evt->{
            int v = slider.getValue();
            sliderValueSet(v);
        });

        slider.setMaximumSize(new Dimension(30, 600));
        container.add(slider, BorderLayout.EAST);
        addDragListener();
        return container;
    }

    public void addProjectable(Projectable p){
        Drawable d = g->g.draw(p.getProjection(transformer));
        projectDrawingMapper.put(p, d);
        view.addDrawable(d);
    }

    public void addProjectable(Projectable p, Color c){
        Drawable d = g->{
            g.setColor(c);
            g.draw(p.getProjection(transformer));
        };
        projectDrawingMapper.put(p, d);
        view.addDrawable(d);
    }

    public void removeProjectable(Projectable p){

        if(projectDrawingMapper.containsKey(p)){
            view.removeDrawable(projectDrawingMapper.get(p));
            projectDrawingMapper.remove(p);
        }
    }

    public void clear(){
        projectDrawingMapper.clear();
        view.clear();
        view.addDrawable(cursor.getDrawable(transformer));
    }

    public void addDragListener(){
        int[] xy = new int[2];
        view.panel.addMouseListener(new MouseAdapter(){
            @Override
            public void mousePressed(MouseEvent e) {
                xy[0] = e.getX();
                xy[1] = e.getY();
            }
        });
        view.panel.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {

                if(!e.isControlDown()){
                    return;
                }
                e.consume();

                int nx = e.getX();
                int ny = e.getY();
                int dx = nx - xy[0];
                int dy = ny - xy[1];

                JViewport port = scroll.getViewport();
                Point pt = port.getViewPosition();

                int vx = (int)pt.getX() - dx;
                if(vx<0){
                    vx = 0;
                }
                int vy = (int)pt.getY() - dy;
                if(vy<0){
                    vy = 0;
                }
                port.setViewPosition(new Point(vx, vy));

                xy[0] = nx;
                xy[1] = ny;
            }

            @Override
            public void mouseMoved(MouseEvent e) {

            }
        });
        view.deactivateWheelZoom();
        view.panel.addMouseWheelListener(evt->{
            evt.consume();
            double originalZoom = view.getZoom();
            Point p = evt.getPoint();
            double zoom;
            int rotation = evt.getWheelRotation();

            if(rotation>0){
                zoom = originalZoom - 0.125;
                if(zoom==0) zoom = 0.125;
            } else if(rotation < 0){
                zoom = originalZoom + 0.125;
            } else{
                zoom = originalZoom;
            }

            if(zoom==originalZoom){
                return;
            }

            double nx = p.getX()*zoom/originalZoom;
            double ny = p.getY()*zoom/originalZoom;

            double dx = nx - p.getX();
            double dy = ny - p.getY();

            view.setZoom(zoom);
            shiftViewport(dx, dy);
        });

    }

    public void shiftViewport(double dx, double dy){

        JViewport port = scroll.getViewport();
        Point pt = port.getViewPosition();
        int vx = (int)(pt.getX()  + dx);
        if(vx<0){
            vx = 0;
        }
        int vy = (int)(pt.getY() + dy);
        if(vy<0){
            vy = 0;
        }
        port.setViewPosition(new Point(vx, vy));


    }

    public void addMouseListener(MouseListener ml){
        view.panel.addMouseListener(ml);
    }

    public double[] getNormalizedCoordinates(double ox, double oy){
        return transformer.getVolumeCoordinates(new double[]{ox, oy});
    }

    public boolean inBounds(double x, double y){
        return bounds.contains(x,y);
    }


    public void addMouseMotionListener(MouseMotionListener initializer) {
        view.panel.addMouseMotionListener(initializer);
    }

    public void setLabel(String label){
        this.label.setText(label);
    }

    public double[] getNormal() {
        return normal;
    }

    public double getScale() {
        return transformer.scale;
    }

    public void addProjectableMesh(ProjectableMesh pm, DeformableMesh3D mesh) {
        Drawable d = new MeshProjection(pm, mesh);
        projectDrawingMapper.put(pm, d);
        view.addDrawable(d);
    }

    class MeshProjection implements Drawable{
        DeformableMesh3D mesh;
        Projectable p;
        MeshProjection(Projectable p, DeformableMesh3D mesh){
            this.p = p;
            this.mesh = mesh;
        }
        @Override
        public void draw(Graphics2D g) {
            if(mesh.isSelected()){
                g.setColor(GuiTools.SELECTED_MESH_COLOR);
            } else{
                g.setColor(mesh.getColor());
            }

            g.draw(p.getProjection(transformer));
        }
    }
}
