package deformablemesh.experimental;

import deformablemesh.MeshImageStack;
import deformablemesh.SegmentationController;
import deformablemesh.gimli2b.Dm3dService;
import lightgraph.Graph;
import ome.formats.importer.ImportConfig;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class VolumeCommand implements Command {
    @Parameter
    private Dm3dService service;

    @Override
    public void run() {
        SegmentationController controls = service.getApplicationController();
        MeshImageStack stack = controls.getMeshImageStack();
        int bins = 100;
        long start = System.nanoTime();
        double[] x = new double[100];
        double[] y = new double[100];

        double mn = 0;
        double mx = stack.data.length*0.5;
        double factor = 1.0/(mx - mn);
        for(int i = 0; i<stack.getNFrames(); i++){
            stack.setFrame(i);
            double[] data = stack.data;
            double sum = 0;
            for(double v : data){
                sum+=v;
            }
            int index = (int)((sum - mn)*factor);
            if(index < 0){index=0;} else if(index >= bins){index = bins-1;}
            y[index]++;
        }
        for(int i = 0; i<bins; i++){
            x[i] = (mx - mn)/bins * (i + 0.5) + mn;
        }
        Graph g = new Graph();
        g.addData(x, y);
        g.show(false, "Volume histogram");
        System.out.println((System.nanoTime() - start)*1e-9);
    }

    public static void main(String[] args){
        net.imagej.Main.main(args);
    }
}
