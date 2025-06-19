package deformablemesh.gui;

import deformablemesh.MeshImageStack;
import deformablemesh.SegmentationController;
import deformablemesh.meshview.ChannelVolume;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.meshview.VolumeDataObject;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

public class ChannelVolumeManagement {
    SegmentationController controller;
    ControlFrame controlFrame;
    public ChannelVolumeManagement(SegmentationController sc){
        controller = sc;
    }

    private void addRow(JComponent layout, GridBagConstraints gbc, List<JComponent> comps){
        gbc.gridx = 0;
        layout.add(comps.get(0), gbc);
        gbc.gridx = 1;
        layout.add(comps.get(1), gbc);
        gbc.gridx = 2;
        layout.add(comps.get(2), gbc);
        gbc.gridx = 3;
        layout.add(comps.get(3), gbc);
        gbc.gridx = 4;
        layout.add(comps.get(4), gbc);
        gbc.gridx = 5;
        layout.add(comps.get(5), gbc);


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
        List<List<JComponent>> ac = availableChannels(stack);
        List<List<JComponent>> existing = showingChannels(controller.getMeshFrame3D());
        GridBagLayout layout = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        available.setLayout(layout);

        List<JComponent> header = new ArrayList<>();
        header.add(new JLabel("name"));
        header.add(new JLabel("low"));
        header.add(new JLabel("high"));
        header.add(new JLabel("color"));
        header.add(new JLabel("is labels"));
        header.add(new JLabel("action"));
        gbc.gridy=1;
        addRow(available, gbc, header);

        for(List<JComponent> row : ac){
            gbc.gridy += 1;
            addRow(available, gbc, row);
        }
        available.setLayout(layout);
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
    List<List<JComponent>> showingChannels( MeshFrame3D mf3d){
        List<List<JComponent>> rows = new ArrayList<>();
        List<ChannelVolume> displayed = mf3d.getChannelVolumes();
        for(ChannelVolume cv : displayed){
            VolumeDataObject vdo = cv.getVolumeDataObject();
            JButton color = colorSelector( vdo.getColor());
            double[] mnmx = vdo.getMinMaxExtents();
            JTextField min = new JTextField(4);
            min.setText("" + mnmx[0]);

            JTextField max = new JTextField(4);
            max.setText("" + mnmx[1]);
            MeshImageStack stack = cv.getMeshImageStack();
            JLabel label = new JLabel(stack.getShortTitle() + "c:" + stack.getChannel());
            JCheckBox asLabels = new JCheckBox();
            asLabels.setSelected(vdo.shownAsLabels());
            JButton remove = new JButton("remove");
            remove.addActionListener(evt->{
                controller.submit( ()->{
                    mf3d.removeChannelVolume( cv );
                });
            });
            List<JComponent> row = new ArrayList<>();

        }

        return rows;
    }
    List<List<JComponent>> availableChannels( MeshImageStack stack){
        List<List<JComponent>> comps = new ArrayList<>();
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
                    cv.getVolumeDataObject().setMinMaxExtents(
                            Double.parseDouble(min.getText()),
                            Double.parseDouble(max.getText()) );
                }
            });


            List<JComponent> row = new ArrayList<>();
            row.add(label);
            row.add(min);
            row.add(max);
            row.add(color);
            row.add(labels);
            row.add(add);
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
