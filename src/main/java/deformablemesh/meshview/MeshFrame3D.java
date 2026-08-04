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

import deformablemesh.MeshImageStack;
import deformablemesh.SegmentationController;
import deformablemesh.geometry.Box3D;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Furrow3D;
import deformablemesh.gui.FrameListener;
import deformablemesh.gui.FurrowController;
import deformablemesh.gui.GuiTools;
import deformablemesh.track.Track;
import deformablemesh.util.Vector3DOps;
import ij.ImagePlus;
import org.jogamp.java3d.*;
import org.jogamp.java3d.loaders.Scene;
import org.jogamp.java3d.loaders.objectfile.ObjectFile;
import org.jogamp.java3d.utils.picking.PickResult;
import org.jogamp.vecmath.Color3f;
import org.jogamp.vecmath.Point3d;
import org.jogamp.vecmath.Vector3d;
import org.jogamp.vecmath.Vector3f;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Class for creating a frame to view mesh(es).
 *
 * User: msmith
 * Date: 7/2/13
 * Time: 8:45 AM
 */
public class MeshFrame3D {
    DataCanvas canvas;
    JFrame frame;
    Axis3D axis;
    Map<Object, DataObject> observedObjects = new HashMap<>();
    List<DataObject> transientObjects = new ArrayList<>();

    public Color getBackgroundColor() {
        return canvas.getCanvasBackgroundColor();
    }

    public DataCanvas getCanvas() {
        return canvas;
    }
    //MultiChannelVolumeTexture texture;
    VolumeDataObject volumeDataObject;
    MultiChannelVolumeTexture texture;

    @FunctionalInterface
    public static interface HudDisplay{
        void draw(Graphics2D g);
    }
    HudDisplay hud = g->{};
    private SegmentationController segmentationController;

    List<DeformableMesh3D> showing = new ArrayList<>();

    boolean showingVolume = false;

    FurrowController ringController;

    DataObject lights;
    float ambient = 0.75f;
    float directional = 0.25f;

    List<ChannelVolume> channelVolumes = new ArrayList<>();

    public MeshFrame3D(){

    }

    public void addChannelVolume(ChannelVolume cv){
        channelVolumes.add(cv);
    }

    public void removeChannelVolume(ChannelVolume cv){
        channelVolumes.remove(cv);
        cv.unlinkFromTexture();
        if(channelVolumes.isEmpty()) {
            removeDataObject(volumeDataObject);
            volumeDataObject = null;
        }
    }

    public List<ChannelVolume> getChannelVolumes(){
        return Collections.unmodifiableList(channelVolumes);
    }

    public boolean isObject(PickResult result, DeformableMesh3D mesh){
        if(mesh.data_object!=null){
            GeometryArray array = result.getGeometryArray();
            return mesh.data_object.lines==array || mesh.data_object.surface_object.getGeometry()==array;
        }
        return false;
    }

    /**
     * Prompts a user to create a new channel volume by selecting an open image.
     */
    public void createNewChannelVolume(){
        ImagePlus plus = GuiTools.selectOpenImage(frame);
        if(plus == null){
            return;
        }
        Color c = JColorChooser.showDialog(frame, "Select Color", Color.WHITE);

        int channel = 0;
        if(plus.getNChannels()>1){
            Object[] values = IntStream.range(1, plus.getNChannels()+1).boxed().toArray();
            Object option = JOptionPane.showInputDialog(
                    frame,
                    "Select Channel to show:",
                    "Choose Channel",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    values,
                    values[0]
            );
            if(option == null) return;
            channel = (Integer)option - 1;
        }
        if(plus != null && c != null){
            MeshImageStack stack = new MeshImageStack(plus);
            stack.setChannel(channel);
            stack.setFrame(segmentationController.getCurrentFrame());
            createNewChannelVolume(stack, c);
        }

    }


    /**
     * Creates a new channel volume based on the provided mesh image stack. This
     * will add to the existing volume data object for display or create a new one
     * if applicable.
     *
     * @param stack Volume to be displayed
     * @param c color of volume
     * @return the created channel volume
     */
    public ChannelVolume createNewChannelVolume(MeshImageStack stack, Color c){
        ChannelVolume cv;
        //If the volume data object exists, it just returns it.
        if( volumeDataObject != null ){
            int[] sizes = volumeDataObject.sizes;
            if( sizes[0] != stack.getWidthPx() || sizes[1] != stack.getHeightPx() || sizes[2] != stack.getNSlices()){
                MeshImageStack check = null;
                for(ChannelVolume ocv : channelVolumes){
                    if(ocv.getVolumeDataObject() == volumeDataObject){
                        System.out.println("found em");
                        check = ocv.getMeshImageStack();
                    }
                }
                if(check != null) {
                    MeshImageStack geometry = check;
                    TextureProducer tp = new TextureProducer() {
                        @Override
                        public double get(int x, int y, int z) {
                            double[] nc = geometry.getNormalizedCoordinate(new double[]{x, y, z});
                            double[] ic = stack.getImageCoordinates(nc);

                            int x1 = (int)ic[0];
                            int y1 = (int)ic[1];
                            int z1 = (int)ic[2];

                            if( x1 >= 0 && x1 < stack.getWidthPx() && y1 >= 0 && y1 < stack.getHeightPx() && z1 >= 0 && z1 < stack.getNSlices() ){
                                return stack.getValue(x1, y1, z1);
                            } else{
                                return 0;
                            }
                        }
                    };
                    FrameListener fl = stack::setFrame;
                    MeshImageStack db = MeshImageStack.unbufferedStack(tp, fl, geometry);
                    db.setMinValue(stack.getMinValue() );
                    db.setMaxValue( stack.getMaxValue() );

                    cv = new ChannelVolume(db, c, volumeDataObject.volume, volumeDataObject.getGeometry());
                } else{
                    throw new RuntimeException("Cannot add miss-match texture resolutions.");
                }

            } else {
                cv = new ChannelVolume(stack, c, volumeDataObject.volume, volumeDataObject.getGeometry());
            }
        } else {
            //creates a new volume data object and adds it to the group.
            int[] dims = new int[]{stack.getWidthPx(), stack.getHeightPx(), stack.getNSlices()};
            texture = new MultiChannelVolumeTexture(dims);
            cv = new ChannelVolume(stack, c, texture, null);
            addDataObject(cv.getVolumeDataObject());
            volumeDataObject = cv.getVolumeDataObject();
        }
        addChannelVolume(cv);
        return cv;
    }

    public void chooseToRemoveChannelVolume(){
        if(channelVolumes.size() == 0 ) return;
        Object[] choices = channelVolumes.toArray();

        Object option = JOptionPane.showInputDialog(
                frame,
                "Select Channel to Remove:",
                "Choose Channel",
                JOptionPane.QUESTION_MESSAGE,
                null,
                choices,
                choices[0]
        );
        if(option instanceof ChannelVolume) {
            removeChannelVolume((ChannelVolume) option);
        }
    }

    public void chooseToContrastChannelVolume(){
        if(channelVolumes.size() == 0 ) return;

        JDialog dialog = new JDialog(frame,"Select volume to contrast.", true);
        JComboBox<ChannelVolume> channels = new JComboBox<>(channelVolumes.toArray(new ChannelVolume[0]));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(channels, BorderLayout.NORTH);
        JCheckBox box = new JCheckBox("Show volume as labelled image");
        panel.add(box, BorderLayout.CENTER);
        JButton accept = new JButton("adjust");
        JButton cancel = new JButton("cancel");
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
        row.add(Box.createHorizontalGlue());
        row.add(accept);

        accept.addActionListener(evt->{
            dialog.setVisible(false);
            ChannelVolume volume = (ChannelVolume)channels.getSelectedItem();
            if(volume == null) return;
            if(box.isSelected()){
                volume.getVolumeDataObject().showAsLabeledVolume();
            } else{
                VolumeContrastSetter setter = new VolumeContrastSetter(volume.vdo);
                setter.showDialog(getJFrame());
            }
        });

        cancel.addActionListener(evt->{
            dialog.setVisible(false);
        });
        row.add(cancel);
        panel.add(row, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        dialog.pack();
        Point p = frame.getLocation();
        int w = frame.getWidth();
        int h = frame.getHeight();
        int dw = dialog.getWidth();
        int dh = dialog.getHeight();
        dialog.setLocation(
                new Point(p.x + ( w - dw ) / 2, p.y + ( h - dh ) / 3 )
        );
        dialog.setVisible(true);
    }

    /**
     * Tries to look along the normal value provided. As in the normal provided would be pointed towards the user
     * after this function has been called.
     *
     * The x,y,z components will be normalized before calculating.
     *
     * @param x component of normal
     * @param y
     * @param z
     */
    public void lookTowards(double x, double y, double z){
        double m = Math.sqrt(x*x + y*y + z*z);
        if(m==0) throw new RuntimeException("cannot lookTowards zero length vector");
        double[] up = canvas.getUp();
        double[] n = new double[]{x/m, y/m, z/m};
        if(Math.abs(Vector3DOps.dot(up, n))<1e-3){
            up = Vector3DOps.getPerpendicularNormalizedVector(n);
        }
        canvas.lookTowards(n , up);
    }

    /**
     * Tries to look along the normal value provided, with the up axis used for up.
     *
     * @param normal normalized vector that to be looked along.
     * @param up vector that will nearly 'up' when looking along.
     */
    public void lookTowards(double[] normal, double[] up){
        canvas.lookTowards(normal , up);
    }

    /**
     * rotates the data canvas view.
     * @see DataCanvas#rotateView(int, int)
     *
     * @param dx rotation about veritical view.
     * @param dy rotation about horizontal view.
     */
    public void rotateView(int dx, int dy){
        canvas.rotateView(dx, dy);
        syncDirectionalLight();
    }

    public void centerView(double[] center){
        canvas.centerCamera(center);
    }

    /**
     * All of the values necessary to have the same view.
     *
     * @return @see DataCanvas#getViewParameters
     */
    public double[] getViewParameters(){
        return canvas.getViewParameters();
    }
    /**
     * Restores a previous view.
     *
     * @see DataCanvas#setViewParameters
     */
    public void setViewParameters(double[] parameters){
        canvas.setViewParameters(parameters);
    }

    public void showFrame(boolean exit_on_close){
        frame = new JFrame();
        frame.setSize(800, 800);
        if(exit_on_close) {
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }
        Component panel = asJPanel(frame);
        frame.setTitle("DM3D: 3d canvas");
        frame.setIconImage(GuiTools.getIcon());
        frame.add(panel);
        frame.setVisible(true);
        showAxis();


    }

    /**
     * For drawing graphics on the rendered screen.
     *
     * @param hud
     */
    public void setHud(HudDisplay hud){
        this.hud = hud;
        canvas.repaint();
    }

    public void setNoHud(){
        this.hud = g->{};
        canvas.repaint();
    }
    public Component asJPanel(JFrame frame){
        this.frame = frame;
        return asJPanel((Window)frame);
    }

    public Component asJPanel(Window parent){
        if(canvas == null) {
            GraphicsConfiguration gc = DataCanvas.getBestConfigurationOnSameDevice(parent);

            Color3f background = new Color3f(1.0f, 0.0f, 1.0f);
            canvas = new DataCanvas(gc, background) {
                @Override
                public void postRender() {
                    super.postRender();
                    J3DGraphics2D g = getGraphics2D();
                    hud.draw(g);
                    g.flush(false);
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(480, 480);
                }

            };

            canvas.addViewListener(this::syncDirectionalLight);
        }

        return canvas;
    }
    public void removeLights(){
        directionalLightA = null;
        directionalLightB = null;
        removeDataObject(lights);
    }
    public void setAmbientBrightness(float delta){

        ambient = delta;
        if(ambient<0) ambient = 0;
        if(ambient>1) ambient = 1;
        addLights();
    }
    public void setDirectionalBrightness(float delta){

        directional = delta;
        if(directional<0) directional = 0;
        if(directional>1) directional = 1;

        addLights();
    }

    DirectionalLight directionalLightA, directionalLightB;
    AmbientLight ambientLight;

    public void syncDirectionalLight(){
        if(directionalLightA!= null) {
            double[] up = canvas.getUp();
            double[] forward = canvas.getForward();
            double[] camber = Vector3DOps.cross(up, forward);

            double[] tilt = Vector3DOps.add(up, forward, -0.6);

            double[] tA = Vector3DOps.add(tilt, camber, 0.5);
            double[] tB = Vector3DOps.add(tilt, camber, -0.5);

            Vector3DOps.normalize(tA);
            Vector3DOps.normalize(tB);

            directionalLightA.setDirection(-(float) tA[0], -(float) tA[1], -(float) tA[2]);
            directionalLightB.setDirection(-(float) tB[0], -(float) tB[1], -(float) tB[2]);
        }
    }

    public double[] getForward(){
        return canvas.getForward();
    }

    public void addLights(){


        if(lights!=null){
            ambientLight.setColor(new Color3f(new float[]{
                    ambient, ambient, ambient
            }));
            directionalLightA.setColor(new Color3f(directional, directional, directional));
            directionalLightB.setColor(new Color3f(directional, directional, directional));
        } else{
            BranchGroup bg = new BranchGroup();
            BoundingSphere bounds =	new BoundingSphere (new Point3d(0, 0.0, 0.0), 25.0);
            bg.setCapability(BranchGroup.ALLOW_DETACH);
            ambientLight = new AmbientLight(new Color3f(new float[]{
                    ambient, ambient, ambient
            }));
            ambientLight.setInfluencingBounds(bounds);
            ambientLight.setCapability(AmbientLight.ALLOW_COLOR_WRITE);
            bg.addChild(ambientLight);

            double[] up = canvas.getUp();
            Vector3f dir = new Vector3f(-(float)up[0], -(float)up[1], -(float)up[2]);

            directionalLightA = new DirectionalLight(
                    new Color3f(directional, directional, directional),
                    dir);
            directionalLightA.setCapability(DirectionalLight.ALLOW_DIRECTION_WRITE);
            directionalLightA.setCapability(DirectionalLight.ALLOW_COLOR_WRITE);
            directionalLightA.setInfluencingBounds(bounds);
            bg.addChild(directionalLightA);
            directionalLightB = new DirectionalLight(
                    new Color3f(directional, directional, directional),
                    dir);
            directionalLightB.setCapability(DirectionalLight.ALLOW_DIRECTION_WRITE);
            directionalLightB.setCapability(DirectionalLight.ALLOW_COLOR_WRITE);
            directionalLightB.setInfluencingBounds(bounds);
            bg.addChild(directionalLightB);

            lights = () -> bg;
            addDataObject(lights);
        }

    }


    public void showAxis(){
        if(segmentationController != null && segmentationController.getMeshImageStack() != null) {
            Box3D b3d = segmentationController.getMeshImageStack().getBounds();
            if(b3d.getVolume() == 0){
                axis= new Axis3D();
            } else {
                axis = new Axis3D(b3d.low[0], b3d.low[1], b3d.low[2], b3d.high[0], b3d.high[1], b3d.high[2]);
            }
        } else{
            axis = new Axis3D();
        }
        addDataObject(axis);
    }

    public void hideAxis(){
        removeDataObject(axis);
        axis=null;
    }

    public void addDataObject(DataObject obj){
        canvas.addObject(obj);
    }
    HashMap<DataObject, DataObject> transformed = new HashMap<DataObject, DataObject>();

    public void addDataObject(DataObject object, double dx, double dy, double dz){
        final TransformGroup tg = new TransformGroup();

        Transform3D tt = new Transform3D();
        tg.getTransform(tt);

        Vector3d n = new Vector3d(dx, dy, dz);

        tt.setTranslation(n);

        tg.setTransform(tt);
        tg.addChild(object.getBranchGroup());
        final BranchGroup bg = new BranchGroup();
        bg.addChild(tg);
        bg.setCapability(BranchGroup.ALLOW_DETACH);

        DataObject obj = new DataObject() {
            @Override
            public BranchGroup getBranchGroup() {
                return bg;
            }
        };
        transformed.put(object, obj);
        canvas.addObject(obj);

    }

    /**
     * In case something goes wrong. this is a bit extreme though.
     */
    public void purgeCanvas(){
        clearTransients();
        observedObjects.clear();
        canvas.removeAll();
        transformed.clear();
    }

    public void observeObject(Object key, DataObject obj){
        if(observedObjects.containsKey(key)){
            removeDataObject(observedObjects.get(key));
            if(obj==null){
                observedObjects.remove(key);
            }
        }
        if(obj!=null){
            observedObjects.put(key, obj);
            addDataObject(obj);
        }
    }

    public void removeDataObject(DataObject mesh) {
        if(transformed.containsKey(mesh)){
            canvas.removeObject(transformed.get(mesh));
            transformed.remove(mesh);
            return;
        }
        canvas.removeObject(mesh);
    }
    public Axis3D getAxis(){
        return axis;
    }
    public BufferedImage snapShot(){
        return canvas.snapShot();
    }

    public void addTransientObject(DataObject o){
        transientObjects.add(o);
        addDataObject(o);
    }

    public void clearTransients(){
        transientObjects.forEach(canvas::removeObject);
        transientObjects.clear();
    }

    public void addKeyListener(KeyListener kl) {
        canvas.addKeyListener(kl);
    }

    public void removeKeyListener(KeyListener kl){
        canvas.removeKeyListener(kl);
    }

    /**
     * For enabling and disabling the default controller.
     * @param v
     */
    public void setCanvasControllerEnabled(boolean v){
        canvas.setDefaultControllerEnabled(v);
    }


    public void toggleAxis() {
        if(axis==null){
            showAxis();
        } else{
            hideAxis();
        }
    }

    public void recordShot() {
        BufferedImage img = snapShot();
        try {
            ImageIO.write(img, "PNG", new File("snapshot-" + System.currentTimeMillis() + ".png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void setBackgroundColor(Color bg){

        canvas.changeBackgroundColor(bg);
    }

    public void setVisible(boolean t){
        frame.setVisible(t);
    }

    public void addPickListener(CanvasView listener) {
        canvas.addSnakeListener(listener);
    }

    public void removePickListener(CanvasView listener){
        canvas.removeSnakeListener(listener);
    }

    public void removeTransient(DataObject obj) {
        boolean i = transientObjects.remove(obj);
        if(i){
            canvas.removeObject(obj);
        }
    }



    public void setSegmentationController(SegmentationController control){
        segmentationController = control;
        segmentationController.addFrameListener(frame ->{
            if(volumeDataObject != null){
                volumeDataObject.volume.setPaused(true);
                    channelVolumes.forEach( channelVolume ->{
                        channelVolume.frameChanged(frame);
                    } );
                volumeDataObject.volume.setPaused(false);
                volumeDataObject.volume.clamp();
            }
        });
    }

    public void syncMesh(int currentFrame){
        List<Track> tracks = segmentationController.getAllTracks();

        Set<DeformableMesh3D> current = tracks.stream().filter(t->t.containsKey(currentFrame)).map(t->t.getMesh(currentFrame)).collect(Collectors.toSet());
        List<DeformableMesh3D> toRemove = new ArrayList<>(current.size());

        for(DeformableMesh3D mesh: showing){
            if(!current.contains(mesh)){
                toRemove.add(mesh);
            }
        }
        for(DeformableMesh3D mesh: toRemove){
            removeDataObject(mesh.data_object);
            showing.remove(mesh);
        }

        DeformableMesh3D selectedMesh = segmentationController.getSelectedMesh();

        for(Track track: tracks){
            if(!track.containsKey(currentFrame)) continue;
            DeformableMesh3D mesh = track.getMesh(currentFrame);
            if(!showing.contains(mesh)){
                if(mesh.data_object==null){
                    mesh.create3DObject();
                }
                if(mesh==selectedMesh){
                    mesh.data_object.setWireColor(Color.GREEN);
                } else{
                    mesh.data_object.setWireColor(track.getColor());
                    mesh.data_object.setShowWires(track.getShowWires());
                }
                addDataObject(mesh.data_object);
                showing.add(mesh);
            } else{
                if(mesh==selectedMesh){
                    mesh.data_object.setWireColor(Color.GREEN);
                } else{

                    mesh.data_object.setWireColor(track.getColor());
                    mesh.data_object.setShowWires(track.getShowWires());
                }
            }

        }

    }

    public JFrame getJFrame() {
        return frame;
    }

    List<ContractileRingDataObject> lines = new ArrayList<>();




    public void updateRingController(){
        FurrowController rc = segmentationController.getRingController();
        if(rc!=ringController){
            ringController=rc;
            ringController.addFrameListener((i)->{
                updateRingController();
            });
        }

        if(ringController.getFurrow() != null && ringController.isFurrowShowing() ) {
            Furrow3D furrow = ringController.getFurrow();
            if(furrow.getDataObject() == null ){
                if(ringController.isTextureShowing()){
                    furrow.createTexturedPlane3DObject(segmentationController.getMeshImageStack());
                } else{
                    furrow.create3DObject();
                }
            }
            observeObject(ringController, furrow.getDataObject());

        } else{
            observeObject(ringController, null);
        }
    }
    public DataObject addObjectFile(File f){
        ObjectFile file = new ObjectFile();
        try {
            Scene s = file.load(f.getAbsolutePath());
            BranchGroup bg = s.getSceneGroup();
            bg.setPickable(false);
            bg.setCapability(BranchGroup.ALLOW_DETACH);
            Iterator<Node> children = bg.getAllChildren();
            while(children.hasNext() ){
                Node n = children.next();
                System.out.println("modification! " + n + (n instanceof Shape3D) );
                n.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
                n.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
            }

            DataObject obj = s::getSceneGroup;
            addDataObject( s::getSceneGroup );
            return obj;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean volumeShowing() {
        return showingVolume;
    }

    public static void main(String[] args){
        JFrame jframe = new JFrame("what");
        JLabel lbl = new JLabel("waiting");
        jframe.add(lbl);
        jframe.setSize(1024, 1024);
        jframe.setVisible(true);

        MeshFrame3D frame = new MeshFrame3D();
        frame.showFrame(true);
        MeshImageStack stack = new MeshImageStack(Paths.get("quality-sample.tif"));

        MultiChannelVolumeTexture texture = new MultiChannelVolumeTexture(new int[]{stack.getWidthPx(), stack.getHeightPx(), stack.getNFrames()});
        VolumeDataObject vdo = new VolumeDataObject(Color.RED, texture);
        vdo.setTextureData(stack);
        frame.addDataObject(vdo);
        VolumeDataObject vdo2 = new VolumeDataObject(Color.YELLOW, texture);
        vdo2.setTextureData(stack);
        vdo2.showAsLabeledVolume();
        VolumeDataObject tmp;
        for(int j = 0; j<100; j++) {
            for (int i = 0; i < 100; i++) {
                frame.canvas.rotateView(10, 0);
                BufferedImage img = frame.snapShot();
                ImageIcon icon = new ImageIcon(img);
                lbl.setIcon(icon);
            }
            tmp = vdo;
            frame.canvas.destroyOffscreenCanvas();
            frame.removeDataObject(vdo);
            vdo = vdo2;
            vdo2 = tmp;

            frame.addDataObject(vdo);

        }
    }


}


