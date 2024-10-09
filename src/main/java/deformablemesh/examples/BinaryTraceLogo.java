package deformablemesh.examples;

import deformablemesh.MeshImageStack;
import deformablemesh.externalenergies.PressureForce;
import deformablemesh.geometry.BinaryMeshGenerator;
import deformablemesh.geometry.ConnectionRemesher;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.meshview.MeshFrame3D;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class BinaryTraceLogo {

    DeformableMesh3D mesh;
    MeshFrame3D mf3d;
    double pressure = 0;
    public BinaryTraceLogo(MeshFrame3D mf3d){
        this.mf3d = mf3d;
    }

    public static ImagePlus space(){
        ImagePlus plus = new ImagePlus();
        ImageStack stack = new ImageStack(32, 32);

        for(int i = 0; i<32; i++){
            stack.addSlice(new ByteProcessor(32, 32));

        }
        plus.setStack(stack, 1, 32, 1);
        return plus;
    }

    public static void drawLogo(ImagePlus plus){
        ImageStack stack = plus.getStack();


        stack.getProcessor(16).set(12, 16, 1);
        stack.getProcessor(16).set(13, 16, 1);
        stack.getProcessor(16).set(14, 16, 1);
        stack.getProcessor(16).set(15, 16, 1);
        stack.getProcessor(16).set(16, 16, 1);
        stack.getProcessor(16).set(17, 16, 1);
        stack.getProcessor(16).set(18, 16, 1);

        stack.getProcessor(16).set(18, 16, 1);
        stack.getProcessor(17).set(18, 16, 1);
        stack.getProcessor(18).set(18, 16, 1);
        stack.getProcessor(19).set(18, 16, 1);
        stack.getProcessor(20).set(18, 16, 1);

        stack.getProcessor(20).set(18, 15, 1);
        stack.getProcessor(20).set(18, 14, 1);

        stack.getProcessor(20).set(17, 14, 1);
        stack.getProcessor(20).set(16, 14, 1);
        stack.getProcessor(20).set(15, 14, 1);
        stack.getProcessor(20).set(14, 14, 1);

        stack.getProcessor(19).set(14, 14, 1);
        stack.getProcessor(18).set(14, 14, 1);
        stack.getProcessor(17).set(14, 14, 1);
        stack.getProcessor(16).set(14, 14, 1);
        stack.getProcessor(15).set(14, 14, 1);
        stack.getProcessor(14).set(14, 14, 1);

        stack.getProcessor(14).set(14, 14, 1);
        stack.getProcessor(14).set(14, 15, 1);
        stack.getProcessor(14).set(14, 16, 1);
        stack.getProcessor(14).set(14, 17, 1);
        stack.getProcessor(14).set(14, 18, 1);

        stack.getProcessor(14).set(15, 18, 1);
        stack.getProcessor(14).set(16, 18, 1);

        stack.getProcessor(15).set(16, 18, 1);
        stack.getProcessor(16).set(16, 18, 1);
        stack.getProcessor(17).set(16, 18, 1);
        stack.getProcessor(18).set(16, 18, 1);

        stack.getProcessor(18).set(16, 17, 1);
        stack.getProcessor(18).set(16, 16, 1);
        stack.getProcessor(18).set(16, 15, 1);
        stack.getProcessor(18).set(16, 14, 1);
        stack.getProcessor(18).set(16, 13, 1);
        stack.getProcessor(18).set(16, 12, 1);

        stack.getProcessor(18).set(15, 12, 1);
        stack.getProcessor(18).set(14, 12, 1);
        stack.getProcessor(18).set(13, 12, 1);
        stack.getProcessor(18).set(12, 12, 1);

        stack.getProcessor(17).set(12, 12, 1);
        stack.getProcessor(16).set(12, 12, 1);

        stack.getProcessor(16).set(12, 13, 1);
        stack.getProcessor(16).set(12, 14, 1);
        stack.getProcessor(16).set(12, 15, 1);

    }

    void smooth(){
        mesh.ALPHA = 1;
        mesh.BETA = 0.5;
        mesh.GAMMA = 100;
        mesh.clearEnergies();
        mesh.addExternalEnergy(new PressureForce(mesh, pressure));
        for(int i = 0; i<10; i++){
            mesh.update();
        }
    }

    public void buildGui(){
        JFrame two = new JFrame("step");
        JPanel panel = new JPanel();
        JButton button = new JButton("smooth");
        button.addActionListener(evt->{
            smooth();
        });

        JButton b2 = new JButton("remesh");
        b2.addActionListener(evt->{
            remesh();
        });

        panel.add(button);
        panel.add(b2);
        JSlider slider = new JSlider(JSlider.VERTICAL);
        slider.setValue(5);
        slider.addChangeListener(evt->{
            pressure = slider.getValue();
        });
        panel.add(slider);
        two.setContentPane(panel);
        two.pack();
        two.setVisible(true);
        two.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);



    }

    public void setMesh(DeformableMesh3D m){
        if(mesh != null){
            mf3d.removeDataObject(mesh.data_object);
        }
        mesh = m;
        mesh.create3DObject();
        mesh.data_object.setWireColor(new Color(100, 200, 255));
        mesh.data_object.setColor(new Color(100, 200, 255, 128));
        mesh.setShowSurface(true);
        mf3d.addDataObject(mesh.data_object);

    }

    public void remesh(){
        mf3d.removeDataObject(mesh.data_object);

        ConnectionRemesher mesher = new ConnectionRemesher();
        mesher.setMinAndMaxLengths(0.005, 0.01);

        setMesh(mesher.remesh(mesh));
    }

    public static void main(String[] args){
        MeshFrame3D mf3d = new MeshFrame3D();

        mf3d.showFrame(true);
        mf3d.setBackgroundColor(Color.LIGHT_GRAY);
        mf3d.addLights();

        ImagePlus plus = space();
        drawLogo(plus);

        BinaryTraceLogo btl  = new BinaryTraceLogo(mf3d);
        btl.buildGui();

        DeformableMesh3D start = BinaryMeshGenerator.meshesFromLabels(new MeshImageStack(plus)).get(0);
        btl.setMesh(start);



    }

}
