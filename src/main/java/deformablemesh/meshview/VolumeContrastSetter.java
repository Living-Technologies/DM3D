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

import deformablemesh.gui.IntensityRanges;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Frame;

public class VolumeContrastSetter{
    IntensityRanges range;
    VolumeDataObject vdo;
    JDialog dialog;
    Color previewBackgroundColor = Color.BLACK;
    Color volumeColor = Color.WHITE;

    public VolumeContrastSetter(VolumeDataObject vdo){
        this.vdo = vdo;
    }

    public void showDialog(Frame parent){
        dialog = new JDialog(parent, "adjust volume contrast");
        dialog.setModal(true);
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Color.BLACK);
        content.setOpaque(true);
        double[] mm = vdo.getMinMaxRange();
        range = new IntensityRanges(vdo.textureProducer, vdo.sizes, mm[0], mm[1]);

        JPanel flow = new JPanel();
        flow.setOpaque(false);
        flow.add(range.getPanel());

        content.add(flow, BorderLayout.NORTH);
        content.add(createButtons(), BorderLayout.SOUTH);

        range.setClipValues(vdo.min, vdo.max);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setVisible(true);
    }

    public JPanel createButtons(){
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
        JButton accept = new JButton("accept");
        accept.addActionListener(evt->{
            //double[] clamped = preview.previewVdo.getClampedMinMax();
            double[] clip = range.getClipValues();
            //using the original vdo we want to find clip values that give the same clamped values.
            dialog.dispose();
            vdo.setMinMaxExtents(clip[0], clip[1]);

        });
        JButton cancel = new JButton("cancel");
        cancel.addActionListener(evt->{
            dialog.dispose();
        });

        panel.add(Box.createHorizontalGlue());
        panel.add(accept);
        panel.add(cancel);
        panel.setOpaque(false);
        return panel;
    }



}
