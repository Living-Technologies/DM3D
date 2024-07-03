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
package deformablemesh.experimental;

import deformablemesh.MeshImageStack;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * This is an experimental class derived from https://github.com/odinsbane/JavaTF2ModelRunner
 *
 * A small server can be started that will let predictions be made remotely. They can also be
 * made locally by specifying "localhost" if the server is started on the local computer.
 *
 */
public class RemotePrediction{
    Socket server;
    MeshImageStack toProcess;
    ProgressDialog progress;
    static class ProgressDialog extends JDialog{
        JLabel status;
        JProgressBar progress;
        ProgressDialog(Window w, int total){
            super(w);
            JPanel panel = new JPanel(new BorderLayout());
            status = new JLabel("preparing " + total + " frames");
            progress = new JProgressBar(0, 2*total);
            panel.add(status, BorderLayout.SOUTH);
            panel.add(progress, BorderLayout.CENTER);
            setContentPane(panel);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        }
        public void updateStatus(String s){
            status.setText(s);
        }
        public void step(){
            EventQueue.invokeLater(()->progress.setValue(progress.getValue() + 1));
        }
    }
    public int setup(MeshImageStack stack) {
        GenericDialog gd = new GenericDialog("Select host");
        gd.addStringField("hostname", "", 30);
        gd.addStringField("port", "5050", 6);
        gd.showDialog();
        String hostname = gd.getNextString();
        int port = Integer.parseInt( gd.getNextString() );
        try {
            server = new Socket(hostname, port);
        } catch (IOException e) {
            System.out.println("Canceling! " + e.getMessage());
            return -1;
        }
        toProcess = stack;
        ImageJ ij = IJ.getInstance();
        progress = new ProgressDialog(ij, toProcess.getNFrames());
        progress.pack();
        progress.setVisible(true);
        if(ij != null) {
            int x = ij.getX();
            int y = ij.getY();
            int h = ij.getHeight();
            progress.setLocation(x, y + h / 2);
        }
        return 0;
    }

    public void run() {

        try {
            process();
            progress.updateStatus("Finished!");

        } catch (IOException e) {
            progress.updateStatus("Failed with IOException: " + e.getMessage());
            e.printStackTrace();
        } catch (ExecutionException e) {
            progress.updateStatus("Failed with ExecutionException: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            progress.updateStatus("Failed with Interrupted: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void process() throws IOException, ExecutionException, InterruptedException {
        ExecutorService sending = Executors.newFixedThreadPool(1);
        List<Future<Integer>> finishing = new ArrayList<>();
        OutputStream os = server.getOutputStream();
        DataOutputStream dos = new DataOutputStream(os);
        final InputStream in = server.getInputStream();
        DataInputStream din = new DataInputStream(in);
        dos.writeInt(toProcess.getNFrames());
        ImagePlus[] cannon = new ImagePlus[1];
        Semaphore limiter = new Semaphore(3);
        for(int i = 0; i<toProcess.getNFrames(); i++){
            final int frame = i;

            Future<Integer> future = sending.submit(()->{
                try {
                    System.out.println("starting frame " + frame);
                    limiter.acquire();
                    final ImagePlus plus = toProcess.getStackIso(frame);
                    if(cannon[0] == null){
                        cannon[0] = plus;
                    }
                    byte[] data = FloatRunner.getImageData(plus);
                    dos.writeInt(plus.getNChannels());
                    dos.writeInt(plus.getWidth());
                    dos.writeInt(plus.getHeight());
                    dos.writeInt(plus.getNSlices());
                    os.write(data);
                    progress.step();
                    return frame;
                } catch(IOException e){
                    throw new RuntimeException(e);
                }
            });
            finishing.add(future);
        }
        progress.updateStatus("Finished preparation awaiting results");
        List<ImagePlus> pluses = new ArrayList<>();
        for(Future<Integer> result: finishing){
            int frame = result.get();
            progress.updateStatus("compiling frame " + frame);
            int outputs = din.readInt();

            for(int i = 0; i<outputs; i++){
                int c = din.readInt();
                int w = din.readInt();
                int h = din.readInt();
                int s = din.readInt();
                byte[] buffer = new byte[c*w*h*s*4];
                int read = 0;
                while(read < buffer.length){
                    int r = din.read(buffer, read, buffer.length - read);
                    if(r<0) break;

                    read += r;
                }
                ImagePlus op = FloatRunner.toImage(buffer, c, w, h, s, cannon[0]);

                if(frame == 0){
                    ImagePlus smaller = op.createImagePlus();
                    ImageStack stack = smaller.getStack();
                    ImageStack fresh = op.getStack();
                    int nc = op.getNChannels();
                    int ns = op.getNSlices();
                    for(int j = 1; j<=fresh.size(); j++){
                        stack.addSlice(fresh.getProcessor(j).convertToByte(false));
                    }
                    smaller.setTitle("op-" + i +" " + "-pred-" + cannon[0].getShortTitle());
                    smaller.setStack(stack, nc, ns, 1);
                    smaller.show();
                    System.out.println("showing");
                    pluses.add(smaller);
                } else{
                    ImagePlus or = pluses.get(i);
                    ImageStack stack = or.getStack();
                    ImageStack fresh = op.getStack();
                    int nc = or.getNChannels();
                    int ns = or.getNSlices();
                    for(int j = 1; j<=fresh.size(); j++){
                        stack.addSlice(fresh.getProcessor(j).convertToByte(false));
                    }
                    or.setStack(stack,nc, ns, (frame + 1));
                    or.setOpenAsHyperStack(true);
                }

            }
            progress.step();
            limiter.release();
            progress.updateStatus("compiled frame " + frame + " with " + outputs + " outputs");

        }
        sending.shutdown();
    }
    public static void main(String... args){
        ImageJ ij = IJ.getInstance();
        ProgressDialog progress = new ProgressDialog(ij, 5);
        progress.pack();
        progress.setVisible(true);
    }
}

class FloatRunner {
    static byte[] getImageData(ImagePlus plus){
        int c = plus.getNChannels();
        int s = plus.getNSlices();
        int w = plus.getWidth();
        int h = plus.getHeight();
        ImageStack stack = plus.getStack();

        byte[] data = new byte[4 * w*h*s*c];
        FloatBuffer buffer = ByteBuffer.wrap(data).asFloatBuffer();
        for(int i = 0; i<c*s; i++){
            FloatProcessor proc = stack.getProcessor( 1 + i ).convertToFloatProcessor();
            buffer.put( (float[])proc.getPixels());
        }

        return data;
    }
    public static ImagePlus toImage(byte[] data, int oc, int ow, int oh, int os, ImagePlus original){
        ImagePlus dup = original.createImagePlus();
        ImageStack stack = new ImageStack(ow, oh);
        int slices = oc*os;
        FloatBuffer buffer = ByteBuffer.wrap(data).asFloatBuffer();
        for(int i = 0; i<slices; i++){
            ImageProcessor proc = new FloatProcessor(ow, oh);
            float[] pixels = new float[ow*oh];
            buffer.get(pixels, 0, pixels.length);
            proc.setPixels(pixels);
            stack.addSlice(proc);
        }

        dup.setStack(stack, oc, os, 1);
        if(dup.getNSlices() != original.getNSlices() || dup.getHeight() != original.getHeight() || dup.getWidth() != original.getWidth()){
            Calibration c0 = original.getCalibration();
            Calibration c1 = dup.getCalibration();
            c1.pixelDepth = c0.pixelDepth*original.getNSlices() / dup.getNSlices();
            c1.pixelWidth = c0.pixelWidth*original.getWidth() / dup.getWidth();
            c1.pixelHeight = c0.pixelHeight*original.getHeight() / dup.getHeight();
            dup.setCalibration(c1);
        }

        return dup;
    }

}
