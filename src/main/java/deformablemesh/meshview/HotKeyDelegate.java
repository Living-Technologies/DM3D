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

import deformablemesh.SegmentationController;
import deformablemesh.gui.ControlFrame;
import deformablemesh.gui.GuiTools;

import javax.swing.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HotKeyDelegate {
    JComponent comp;
    ControlFrame accessControl;
    MeshFrame3D display3D;
    boolean hudShowing = false;
    List<ActionMapKey> actions;

    Map<Integer, String> specialCharacters = buildSpecialCharacterMap();
    static private Map<Integer, String> buildSpecialCharacterMap(){
        Map<Integer, String> map = new HashMap<>();
        map.put(KeyEvent.VK_LEFT, "\u2190");
        map.put(KeyEvent.VK_UP, "\u2191");
        map.put(KeyEvent.VK_DOWN, "\u2193");
        map.put(KeyEvent.VK_RIGHT, "\u2192");
        map.put(KeyEvent.VK_BACK_SPACE, "BS");
        map.put(KeyEvent.VK_DELETE, "DEL");


        return map;
    }


    class ActionMapKey{
        KeyStroke k;
        String name;
        String description;
        Action action;
        String activation;
        ActionMapKey(KeyStroke k, String name, String description, Action action){
            this.k = k;
            this.name = name;
            this.description = description;
            this.action = action;
            comp.getInputMap().put(k, name);
            comp.getActionMap().put(name, action);

            activation = createActivationString();
        }


        String createActivationString(){
            String activate = specialCharacters.computeIfAbsent(k.getKeyCode(), k-> "'" + (char)k.intValue() + "'");
            int m = k.getModifiers();
            int c = KeyEvent.CTRL_DOWN_MASK;
            int s = KeyEvent.SHIFT_MASK;

            if( (s & m) != 0){
                activate = "SHIFT+" + activate;
            }

            if( (c & m) != 0){
                activate = "CTRL+" + activate;
            }
            return activate;
        }

        void draw(int x, int y, Graphics2D g){


            g.drawString(activation, x, y);
            g.drawString(description, x+100, y);

        }
    }
    Runnable ifEnabled(Runnable r){
        return () -> {
            if (accessControl.isReady()) {
                r.run();
            }
        };
    }

    public HotKeyDelegate(MeshFrame3D frame, SegmentationController controller, ControlFrame gui){
        this.comp = (JPanel) frame.frame.getContentPane();
        accessControl = gui;
        display3D = frame;
        actions = new ArrayList<>();
        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_N, 0, true),
                "NEXT_MESH",
                "Selects next mesh",
                ifEnabled(controller::selectNextMeshTrack)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_P, 0, true),
                "NEXT_PREVIOUS",
                "Selects previous mesh",
                ifEnabled(controller::selectPreviousMeshTrack)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_O, 0, true),
                "TOGGLE_SURFACE",
                "Show/Hide current mesh surface",
                controller::toggleSurface
            );
        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true),
                "SNAPSHOT",
                "Snapshot of current scene.",
                ifEnabled(controller::takeSnapShot)
            );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true),
                "DEFORM_MESH",
                "Deform/Stop deforming current mesh.",
                ()->accessControl.deformAction(false)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK, true),
                "DEFORM_MESHES",
                "Deform/Stop all meshes.",
                ()->accessControl.deformAction(true)
        );
        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_T, 0, true),
                "LINK_POSSIBLE",
                "Link selected track to next frame track.",
                ifEnabled(controller::linkPossibleTrack)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK, true),
                "TRACK_FORWARD",
                "Track forward by copying current.",
                ifEnabled(accessControl::trackMeshAction)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.SHIFT_DOWN_MASK, true),
                "TRACK_FORWARD_SELECT",
                "Choose track to link in next frame.",
                ifEnabled(accessControl::trackMeshForwardActionChoose)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_DOWN_MASK, true),
                "TRACK_BACKWARD",
                "Track selected mesh backwards by copying.",
                ifEnabled(accessControl::trackMeshBackwardsAction)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_B,  KeyEvent.SHIFT_DOWN_MASK, true),
                "TRACK_BACKWARD_SELECT",
                "Choose track to link backwards.",
                ifEnabled(accessControl::trackMeshBackwardActionChoose)
        );


        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_I, 0, true),
                "INITIALIZE_MESHES",
                "Initialize new meshes",
                ifEnabled(accessControl::initializeMeshAction)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_R, 0, true),
                "REMESH",
                "Raycast selected mesh",
                ifEnabled(accessControl::remeshAction)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_M, 0, true),
                "CONNECTION_REMESH",
                "Remesh Connections of selected mesh.",
                ifEnabled(()->accessControl.connectionRemesh(false))
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK, true),
                "CONNECTION_REMESH_ALL",
                "Remesh Connections of all meshes.",
                ifEnabled(()->accessControl.connectionRemesh(true))
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_MASK, true),
                "UNDO",
                "Undo most recent action.",
                ifEnabled(accessControl::undoAction)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_MASK | KeyEvent.SHIFT_MASK, true),
                "REDO",
                "Redo most recently undone action.",
                ifEnabled(accessControl::redoAction)
        );


        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0, true),
                "CLEAR_MESH",
                "Clear selected mesh from frame. (backspace)",
                ifEnabled(controller::clearSelectedMesh)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, KeyEvent.CTRL_MASK, true),
                "REMOVE_TRACK",
                "Remove selected track.",
                ifEnabled(controller::removeSelectedTrack)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0, true),
                "CLEAR_MESH2",
                "Clear selected mesh from frame. (delete)",
                ifEnabled(controller::clearSelectedMesh)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true),
                "TOGGLE_AXIS",
                "Toggle axis display.",
                frame::toggleAxis
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true),
                "PREVIOUS_FRAME",
                "Previous frame.",
                ifEnabled(accessControl::previousFrameAction)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true),
                "NEXT_FRAME",
                "Next frame.",
                ifEnabled(accessControl::nextFrameAction)
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, 0, true),
                "ADD_VOLUME",
                "add volume channel",
                frame::createNewChannelVolume
        );
        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_X, 0, true),
                "REMOVE_VOLUME",
                "remove volume channel",
                frame::chooseToremoveChannelVolume
        );
        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true),
                "CONTRAST_VOLUME",
                "contrast volume channel",
                frame::chooseToContrastChannelVolume
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, true),
                "CENTER_FURROW",
                "Centers Furrow to Screen or Selected Mesh.",
                controller::centerFurrowOnSelectedMesh
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.SHIFT_DOWN_MASK, true),
                "ORIENT_FURROW",
                "Clicking on selected mesh to orient furrow.",
                controller::startFurrowOrientationListener
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK, true),
                "FLIP_FURROW",
                "Flip furrow direction.",
                controller::flipFurrow
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, true),
                "FURROW_FORWARD",
                "Moves the furrow plane forward.",
                controller::furrowForward
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, true),
                "FURROW_BACKWARDS",
                "Moves the furrow plane backwards.",
                controller::furrowBackward
        );
        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.SHIFT_DOWN_MASK, true),
                "CLEAVE_FURROW",
                "Split the selected mesh with furrow plane.",
                controller::splitMesh
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_H, 0, true),
                "SHOW_HELP_HUD",
                "Show/Hide cheat sheet.",
                this::toggleHud
        );

        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0, true),
                "CENTER_VIEW_SELECT",
                "Center view on selected mesh.",
                controller::centerSelectedMesh
        );
        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_5, 0, true),
                "CUSTOM5",
                "Hot keys 5-9, create with setHotKey(key, runnable).",
                ()->controller.hotKey("5")
        );
        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_6, 0, true),
                "CUSTOM6",
                null,
                ()->controller.hotKey("6")
        );
        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_7, 0, true),
                "CUSTOM7",
                null,
                ()->controller.hotKey("7")
        );
        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_8, 0, true),
                "CUSTOM8",
                null,
                ()->controller.hotKey("8")
        );
        createActionMapKey(
                KeyStroke.getKeyStroke(KeyEvent.VK_9, 0, true),
                "CUSTOM9",
                null,
                ()->controller.hotKey("9")
        );


    }
    public void toggleHud(){
        if(hudShowing){
            display3D.setNoHud();
            hudShowing = false;
        } else {
            display3D.setHud(this::draw);
            hudShowing = true;
        }
    }
    int padding = 15;
    int margin = 25;
    int width = 420;
    int actionHeight = 20;
    public void draw(Graphics2D graphics){
        GuiTools.applyRenderingHints(graphics);
        int w = comp.getWidth();
        int h = comp.getHeight();

        int ox = padding;
        int oy = padding;

        int contentHeight = actionHeight*(actions.size()) + 2*margin;


        graphics.setColor(Color.BLACK);
        graphics.drawRect(ox, oy, width, contentHeight);

        graphics.setColor(new Color(255, 255, 255, 200));
        graphics.fillRect(ox, oy, width, contentHeight);
        int x = margin;
        int y = margin + actionHeight;
        graphics.setColor(Color.BLACK);
        for(ActionMapKey amk: actions){
            if(amk.description==null){
                continue;
            }
            amk.draw(ox + x, oy + y, graphics);
            y += actionHeight;
        }

    }

    ActionMapKey createActionMapKey(KeyStroke k, String name, String description, Runnable run){
        ActionMapKey key = new ActionMapKey(k, name, description, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        });
        actions.add(key);
        return key;
    }

}
