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

import deformablemesh.SegmentationController;
import deformablemesh.meshview.MeshFrame3D;

import javax.swing.JFrame;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * For saving constants that the user has entered.
 *
 *
 * Created on 10/03/2017.
 */
public class PropertySaver {
    /* once this fails once, it will stop trying to save. */
    static boolean canSave=true;

    /**
     * Tries to find a user.home and .dmesh3d file for user preferences to reload the last used constants.
     *
     * @param control where the values go.
     * @throws IOException
     */
    static public void loadProperties(SegmentationController control) throws IOException {
        String home = System.getProperty("user.home");
        File props = new File(home, ".dmesh3d");
        if(props.exists()) {
            loadProperties(control, props);
        }
    }
    private static double parseDouble(String value){
        try{
            return Double.parseDouble(value);
        } catch (Exception e){
            return 0.0;
        }
    }

    private static int parseInt(String value){
        try{
            return Integer.parseInt(value);
        } catch (Exception e){
            return 0;
        }
    }
    /**
     * Tries to find a user.home and .dmesh3d file for user preferences to reload the last used constants.
     *
     * @param control where the values go.
     * @param props file properties will be loaded from.
     * @throws IOException
     */
    static public void loadProperties(SegmentationController control, File props) throws IOException {
        //try to read them properties.
        List<String> lines = Files.readAllLines(props.toPath(), StandardCharsets.UTF_8);

        for(String line: lines){
            String[] pair = line.split("\\t");
            String key = pair[0];
            String value = pair.length > 1 ? pair[1] : "";

            try {
                switch (key) {
                    case "gamma":
                        control.setGamma(parseDouble(value));
                        break;
                    case "pressure":
                        control.setPressure(parseDouble(value));
                        break;
                    case "image-weight":
                        control.setWeight(parseDouble(value));
                        break;
                    case "alpha":
                        control.setAlpha(parseDouble(value));
                        break;
                    case "divisions":
                        control.setDivisions(parseInt(value));
                        break;
                    case "beta":
                        control.setBeta(parseDouble(value));
                        break;
                    case "steric-weight":
                        control.setStericNeighborWeight(parseDouble(value));
                        break;
                    case "prediction-host":
                        GuiTools.setPredictionHost(value);
                        break;
                    case "connection-length":
                        control.setMeanConnectionLength(parseDouble(value));
                    default:
                        System.out.println("skipping: " + key);

                }
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Tries to save the currently set properties/constants.
     *
     * @param control
     * @throws IOException
     */
    static public void saveProperties(SegmentationController control) throws IOException {
        if(!canSave) return;
        String home = System.getProperty("user.home");
        File props = new File(home, ".dmesh3d");
        saveProperties(control, props);
    }


    static public void saveProperties(SegmentationController control, File props) throws IOException {
        try(BufferedWriter writer = Files.newBufferedWriter(props.toPath(), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)){
            writer.write(String.format("%s\t%s\n","gamma", Double.toHexString(control.getGamma())));
            writer.write(String.format("%s\t%s\n","pressure", Double.toHexString(control.getPressure())));
            writer.write(String.format("%s\t%s\n","image-weight", Double.toHexString(control.getImageWeight())));
            writer.write(String.format("%s\t%s\n","alpha", Double.toHexString(control.getAlpha())));
            writer.write(String.format("%s\t%s\n","steric-weight", Double.toHexString(control.getStericNeighborWeight())));
            writer.write(String.format("%s\t%d\n","divisions", control.getDivisions()));
            writer.write(String.format("%s\t%s\n","beta", Double.toHexString(control.getBeta())));
            writer.write(String.format("%s\t%s\n", "connection-length", Double.toHexString(control.getMeanConnectionLength())));
            writer.write(String.format("%s\t%s\n", "prediction-host", GuiTools.getPredictionHost()));
        } catch(IOException exc){
            canSave = false;
            throw new IOException(exc);
        }
    }


    /**
     * For setting up the position of the windows. Currently just tries to place them side by side.
     * 
     * @param control
     * @param mf3d
     */
    static public void positionFrames(ControlFrame control, MeshFrame3D mf3d){
        JFrame c = control.getFrame();
        JFrame m = mf3d.getJFrame();
        int x = m.getX();
        int y = m.getY();
        int w = m.getWidth();
        int h = m.getHeight();
        c.setLocation(x+w, y);

    }
}
