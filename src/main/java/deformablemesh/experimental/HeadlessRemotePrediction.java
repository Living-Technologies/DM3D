package deformablemesh.experimental;

import deformablemesh.MeshImageStack;
import ij.ImagePlus;
import ij.ImageStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class HeadlessRemotePrediction implements AutoCloseable{
    MeshImageStack source;
    PredictionClient client;
    List<Future<Integer>> sendingFrames = new ArrayList<>();
    ExecutorService writeThread = Executors.newSingleThreadExecutor();

    public HeadlessRemotePrediction(MeshImageStack stack){
        source = stack;
    }

    public void prepareOutput(Path volumeName){
    }

    public void connect(String host, int port) throws IOException {
        client = new PredictionClient(host, port);
    }
    public Integer writeFrame(int f){
        ImagePlus iso = source.getStackIso(f);

        try {
            client.writeImage(iso);
        } catch(Exception e){
            //break everything.
            throw new RuntimeException(e);
        }
        return f;
    }
    public void startSending(){

        for(int i = 0; i<source.getNFrames(); i++){
            final int frame = i;
            sendingFrames.add(writeThread.submit( ()->writeFrame(frame) ));
        }
    }

    public void readPredictions() throws Exception {
        for(Future<Integer> result: sendingFrames){
            int frame = result.get();
            List<ImagePlus> predictions = client.getOutputs();
            for(int i = 0; i<predictions.size(); i++) {
                ImagePlus op = predictions.get(i);
                ImagePlus smaller = op.createImagePlus();
                ImageStack stack = smaller.getStack();
                ImageStack fresh = op.getStack();
                int nc = op.getNChannels();
                int ns = op.getNSlices();
                for(int j = 1; j<=fresh.size(); j++){
                    stack.addSlice(fresh.getProcessor(j).convertToByte(false));
                }
            }
        }
    }
    public void process() throws Exception {
        client.start(source.getNFrames());
        startSending();
        readPredictions();
    }
    public void close() throws Exception {
        writeThread.shutdown();
        client.close();
    }

    public static void main(String[] args) throws Exception {
        Path path = Paths.get(args[0]);
        MeshImageStack stack;
        System.out.println("processing: " + path);
        if(Files.isDirectory(path)){
            stack = MeshImageStack.fromFolder(Paths.get(args[0]));
        }else{
            stack = new MeshImageStack(Paths.get(args[0]));
        }
        System.out.println("loaded");
        Path volumeName = Paths.get(args[1]);

        try(HeadlessRemotePrediction hrp = new HeadlessRemotePrediction(stack)){
            hrp.connect("172.30.138.167", 5050);
            hrp.prepareOutput(volumeName);
            hrp.process();
        }
    }
}
