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
package deformablemesh.gui;


import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * For displaying a slice of the 3D image stack, using a uniform scale.
 *
 * Created by msmith on 10/26/15.
 */
public class Slice3DView{

    Image slice;
    Image binary;
    Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.CYAN, Color.LIGHT_GRAY, Color.MAGENTA};
    double zoom = 1;
    final static Image NO_IMAGE = createNoImage();
    final static Image NULL_IMAGE = createNullImage();

    public final JPanel panel = buildComponent();
    Dimension dimension;
    List<Drawable> drawables = new ArrayList<>();



    public Slice3DView(){
        setSlice(NO_IMAGE);
        setBinary(NULL_IMAGE);
        dimension = new Dimension(
                slice.getWidth(null) + binary.getWidth(null),
                slice.getHeight(null) + binary.getHeight(null)
        );
    }

    public BufferedImage getSnapShot(){
        BufferedImage img = new BufferedImage(
                (int)(zoom*slice.getWidth(null)),
                (int)(zoom*slice.getHeight(null)),
                BufferedImage.TYPE_4BYTE_ABGR );
        Graphics2D g2d = (Graphics2D)img.getGraphics();
        GuiTools.applyRenderingHints(g2d);
        applyZoom(g2d);
        paintBackground(g2d);
        paintGraphics(g2d);
        g2d.dispose();
        return img;
    }
    private void paintGraphics(Graphics2D g2d){

        //g2d.setTransform(t);
        long start = System.currentTimeMillis();
        synchronized(drawables){
            int i = 0;
            for(Drawable drawable: drawables){
                drawable.draw(g2d);
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        if(elapsed > 500){
            System.out.println("projection too slow");
        }
    }

    private void paintBackground(Graphics2D g2d){
        g2d.drawImage(slice, 0, 0, null);
    }

    private void applyZoom(Graphics2D g2d){
        if(zoom != 1){
            AffineTransform t = g2d.getTransform();
            AffineTransform scale = AffineTransform.getScaleInstance(zoom, zoom);
            t.concatenate(scale);
            g2d.setTransform(t);
        }
    }

    JPanel buildComponent(){
        JPanel panel = new JPanel(){
            @Override
            public void paintComponent(Graphics g){
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D)g;
                applyZoom(g2d);
                g2d.drawImage(slice, 0, 0, this);
                g2d.drawImage(binary, slice.getWidth(this), 0, this);
                paintGraphics(g2d);
            }
            @Override
            public Dimension getPreferredSize(){
                return dimension;
            }

            @Override
            public Dimension getMinimumSize(){
                return dimension;
            }

            @Override
            public Dimension getMaximumSize(){
                return dimension;
            }

        };


        panel.addMouseWheelListener(evt->{
            if(evt.isConsumed()){
                return;
            }

            if(evt.getWheelRotation()>0){
                zoom = zoom - 0.125;
                if(zoom==0) zoom = 0.125;
                resize();
            } else if(evt.getWheelRotation()<0){
                zoom = zoom + 0.125;
                resize();
            }

        });


        return panel;
    }
    public double getZoom(){
        return zoom;
    }

    public void setSlice(Image s){
        slice = s;
        resize();
    }

    public Image getSlice(){
        return slice;
    }

    public Point2D getScaledLocation(Point2D p){
        return new Point2D.Double(p.getX()/zoom, p.getY()/zoom);
    }

    public void setBinary(Image b){
        binary = b;
        resize();
    }





    public void addShape(Shape s){
        synchronized(drawables){
            final Color c = colors[drawables.size()%colors.length];
            drawables.add(g->{
                g.setColor(c);
                g.draw(s);
            });
        }
        panel.repaint();
    }
    public void addDrawables(List<Drawable> ds){
        synchronized(drawables){
            drawables.addAll(ds);
        }
        panel.repaint();
    }
    public void addDrawable(Drawable d){
        synchronized(drawables){
            drawables.add(d);
        }
        panel.repaint();
    }

    static public BufferedImage createNoImage(){
        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_BYTE_GRAY);
        return img;
    }
    static public BufferedImage createNullImage(){
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
        return img;
    }

    public void resize(){
        double w = 1;
        double h = 1;
        if(slice!=null){
            w = slice.getWidth(panel)*zoom;
            h = slice.getHeight(panel)*zoom;
        }

        if(binary!=null){
            w += binary.getWidth(panel)*zoom;
        }

        dimension = new Dimension((int)w, (int)h);


        panel.revalidate();
        panel.repaint();

    }



    public static void main(String[] args){
        Slice3DView view = new Slice3DView();

        JFrame f = new JFrame("slice view");

        f.add(new JScrollPane(view.panel));

        f.pack();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);


    }


    public void removeDrawable(Drawable drawable) {
        synchronized(drawables){
            drawables.remove(drawable);
        }
        panel.repaint();
    }

    public void clear(){
        synchronized(drawables){
            drawables.clear();
        }
        panel.repaint();
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
        resize();
    }

    public void deactivateWheelZoom(){
        MouseWheelListener[] ml = panel.getMouseWheelListeners();
        for(MouseWheelListener l: ml){
            panel.removeMouseWheelListener(l);
        }
    }

    public void addMouseAdapter(MouseAdapter adapter){

        panel.addMouseListener(adapter);
        panel.addMouseMotionListener(adapter);
    }

    public void removeMouseAdapter(MouseAdapter adapter){
        panel.removeMouseListener(adapter);
        panel.removeMouseMotionListener(adapter);
    }

    public void repaint() {
        panel.repaint();
    }
}
