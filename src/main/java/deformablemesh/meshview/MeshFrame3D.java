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
import deformablemesh.externalenergies.ExternalEnergy;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Furrow3D;
import deformablemesh.gui.GuiTools;
import deformablemesh.gui.RingController;
import deformablemesh.track.Track;
import deformablemesh.util.Vector3DOps;
import ij.ImagePlus;
import org.jogamp.java3d.*;
import org.jogamp.java3d.utils.picking.PickResult;
import org.jogamp.vecmath.Color3f;
import org.jogamp.vecmath.Point3d;
import org.jogamp.vecmath.Vector3d;
import org.jogamp.vecmath.Vector3f;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
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
public class    MeshFrame3D {
    DataCanvas canvas;

    JFrame frame;
    Axis3D axis;
    Map<Object, DataObject> observedObjects = new HashMap<>();
    List<DataObject> transientObjects = new ArrayList<>();

    public Color getBackgroundColor() {
        return canvas.getCanvasBackgroundColor();
    }

    public VolumeDataObject getVolumeDataObject() {
        return vdo;
    }

    public DataCanvas getCanvas() {
        return canvas;
    }

    @FunctionalInterface
    public static interface HudDisplay{
        void draw(Graphics2D g);
    }
    HudDisplay hud = g->{};
    private SegmentationController segmentationController;

    List<DeformableMesh3D> showing = new ArrayList<>();

    boolean showingVolume = false;
    VolumeDataObject vdo;

    RingController ringController;

    DataObject lights;
    float ambient = 0.6f;
    float directional = 0.25f;

    List<ChannelVolume> channelVolumes = new ArrayList<>();

    public MeshFrame3D(){

    }

    public void addChannelVolume(ChannelVolume cv){
        channelVolumes.add(cv);
        segmentationController.addFrameListener(cv);
        addDataObject(cv.vdo);
    }

    public void removeChannelVolume(ChannelVolume cv){
        channelVolumes.remove(cv);
        segmentationController.removeFrameListener(cv);
        removeDataObject(cv.vdo);
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
    public void createNewChannelVolume(){
        ImagePlus plus = GuiTools.selectOpenImage(frame);
        if(plus == null){
            return;
        }
        Color c = JColorChooser.showDialog(null, "Select Color", Color.WHITE);

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
            ChannelVolume cv = new ChannelVolume(stack, c);
            addChannelVolume(cv);
        }

    }

    public void chooseToremoveChannelVolume(){
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
                setter.setPreviewBackgroundColor(getBackgroundColor());
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
        axis = new Axis3D();
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
     * In case something goes wronge. this is a bit extreme though.
     */
    public void purgeCanvas(){
        clearTransients();
        observedObjects.clear();
        canvas.removeAll();
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
                }
                addDataObject(mesh.data_object);
                showing.add(mesh);
            } else{
                if(mesh==selectedMesh){
                    mesh.data_object.setWireColor(Color.GREEN);
                } else{
                    mesh.data_object.setWireColor(track.getColor());
                }
            }

        }

    }

    /**
     * Backs the volume texture date with the supplied image stack.
     * TODO qualify whether the stack/texture data has changed.
     * @param stack
     */
    public void showVolume(MeshImageStack stack){
        if(stack.getWidthPx()==0 || stack.getHeightPx()==0 || stack.getNSlices()==0){
            //no volume data ignore request.
            return;
        }

        if(showingVolume==false){
            showingVolume=true;
            if(vdo==null){
                vdo = new VolumeDataObject(segmentationController.getVolumeColor());
            }
            vdo.setTextureData(stack);
            addDataObject(vdo);
        } else{
            vdo.setTextureData(stack);
        }


    }



    public void showEnergy(MeshImageStack stack, ExternalEnergy erg) {
        showingVolume = true;
        int d = stack.getNSlices();
        int h = stack.getHeightPx();
        int w = stack.getWidthPx();

        if(vdo==null){
            vdo = new VolumeDataObject(segmentationController.getVolumeColor());
            vdo.setTextureData(stack);
        }

        for(int i = 0; i<d; i++){
            for(int j = 0; j<h; j++){
                for(int k = 0; k<w; k++){

                    //double v = stack.data[i][j][k];
                    double v = erg.getEnergy(stack.getNormalizedCoordinate(new double[]{k,j,i}));
                    vdo.texture_data[k][h-j-1][i] = v;


                }
            }
        }

        vdo.updateVolume();

    }


    public JFrame getJFrame() {
        return frame;
    }

    public void changeVolumeClipping(int minDelta, int maxDelta) {
        if(vdo!=null){
            double[] mnMx = vdo.getMinMax();
            double min = mnMx[0] + minDelta*0.05;
            double max = mnMx[1] + maxDelta*0.05;
            vdo.setMinMaxRange(min, max);
        }
    }

    public void hideVolume() {
        if(vdo!=null){
            removeDataObject(vdo);
            vdo = null;
        }
        showingVolume=false;
    }

    List<ContractileRingDataObject> lines = new ArrayList<>();




    public void updateRingController(){
        RingController rc = segmentationController.getRingController();
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

    public boolean volumeShowing() {
        return showingVolume;
    }


}


