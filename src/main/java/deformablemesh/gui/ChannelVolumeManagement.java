package deformablemesh.gui;

import deformablemesh.MeshImageStack;
import deformablemesh.SegmentationController;
import deformablemesh.meshview.ChannelVolume;
import deformablemesh.meshview.MeshFrame3D;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class ChannelVolumeManagement {
    SegmentationController controller;
    ControlFrame controlFrame;
    public ChannelVolumeManagement(SegmentationController sc){
        controller = sc;
    }
    public void buildGui(ControlFrame controlFrame){
        this.controlFrame = controlFrame;
        JFrame parent = controlFrame.getFrame();
        MeshFrame3D mf3d = controller.getMeshFrame3D();
        if(mf3d == null){
            return;
        }
        JDialog channelManager = new JDialog(parent, true);

        JPanel content = new JPanel(new BorderLayout());


        MeshImageStack stack = controller.getMeshImageStack();
        JPanel available = new JPanel();
        available.setLayout(new BoxLayout( available, BoxLayout.PAGE_AXIS) );
        List<JComponent> ac = availableChannels(stack);

        ac.forEach(available::add);

        content.add( available, BorderLayout.CENTER );


        JButton add = new JButton("select open image");
        add.addActionListener(evt->{
            mf3d.createNewChannelVolume();
            channelManager.setVisible(false);
        });

        JButton contrast = new JButton( "contrast channel");
        contrast.addActionListener(
            evt->{
                mf3d.chooseToContrastChannelVolume();
                channelManager.setVisible(false);
            }
        );

        JButton remove = new JButton("remove");
        remove.addActionListener(evt->{
            mf3d.chooseToremoveChannelVolume();
            channelManager.setVisible(false);
        });

        JPanel buttons = new JPanel(new GridLayout(1, 3));
        buttons.add(add);
        buttons.add(contrast);
        buttons.add(remove);
        content.add(buttons, BorderLayout.SOUTH);

        channelManager.setContentPane(content);
        channelManager.pack();
        channelManager.setTitle("Add, Adjust or Remove 3D Volumes.");
        GuiTools.centerComponent(mf3d.getJFrame(), channelManager);
        mf3d.setVisible(true);
        channelManager.setVisible(true);
        System.out.println("set visible");
    }

    private void removeRow(JComponent comp){

    }

    List<JComponent> availableChannels( MeshImageStack stack){
        List<JComponent> comps = new ArrayList<>();
        String s = stack.getShortTitle();
        for(int i = 0; i<stack.getNChannels(); i++){
            final int channel = i;
            JButton color = colorSelector(Color.DARK_GRAY);
            color.addActionListener( evt ->{
                Color c = GuiTools.getColor(controlFrame.getFrame());
                color.setBackground(c);
            });
            JTextField min = new JTextField(4);
            min.setText("0.1");

            JTextField max = new JTextField(4);
            max.setText("0.9");
            JCheckBox labels = new JCheckBox();

            JLabel label = new JLabel(s + "c:" + i);
            JButton add = new JButton("add");
            add.addActionListener(evt ->{
                MeshFrame3D mf3d = controller.getMeshFrame3D();
                MeshImageStack stack2 = controller.getMeshImageStack().duplicate();
                stack2.setChannel(channel);
                ChannelVolume cv = mf3d.createNewChannelVolume(stack2, color.getBackground());
                if(labels.isSelected()){
                    cv.getVolumeDataObject().showAsLabeledVolume();
                } else{
                    cv.getVolumeDataObject().setMinMaxRange(
                            Double.parseDouble(min.getText()),
                            Double.parseDouble(max.getText()) );
                }

            });
            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
            row.add(min);
            row.add(max);
            row.add(color);
            row.add(labels);
            row.add(add);
            row.add(label);
            comps.add(row);
        }
        return comps;
    }

    List<JComponent> displayedChannels(){
        List<JComponent> comps = new ArrayList<>();
        return comps;
    }
    JButton colorSelector( Color current){
        JButton button = new JButton();
        button.setOpaque(true);
        button.setBackground(current);
        return button;
    }

}
