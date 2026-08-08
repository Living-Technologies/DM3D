package deformablemesh.examples;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.io.LoadZarr;
import deformablemesh.io.MeshReader;
import deformablemesh.io.SaveImageToZarr;
import deformablemesh.track.Track;
import ij.ImagePlus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class LabelFolderOfMeshes {
    Path meshPath;
    MeshImageStack stack;
    private final Path zarrOut;
    public LabelFolderOfMeshes(MeshImageStack stack, Path meshPath, Path zarrOut){
        this.stack = stack;
        this.meshPath = meshPath;
        this.zarrOut = zarrOut;
    }
    public Integer processFrame(int frame){
        Path p = meshPath.resolve("frame-" + frame + ".bmf");
        if(Files.exists(p)){
            try {
                List<Track> meshes = MeshReader.loadMeshes(p.toFile());
                if(meshes.size() == 0){
                    return frame;
                }
                List<Track> shift = meshes.stream().map(t->{
                    Track next = new Track(t.getName());
                    next.addMesh(0, t.getMesh(frame));
                    return next;
                }).collect(Collectors.toList());
                System.out.println("processing mesh for: " + stack.getNFrames() + ", " + stack.getNSlices() + ", " + shift.size());
                ImagePlus plus = DeformableMesh3DTools.asUniqueLabels(stack, shift);
                synchronized (zarrOut) {
                    SaveImageToZarr.appendToZarrRa(plus, zarrOut, frame);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
        return frame;
    }

    public static void createLabels(String meshFolder, String image) throws IOException {
        Path imagePath = Paths.get(image);
        MeshImageStack stack;
        String ft;
        if(image.endsWith(".zarr")){
            stack = LoadZarr.loadMeshImageStack2(imagePath);
            ft = ".zarr";
        } else{
            ft = ".tif";
            System.out.println("checking: " + imagePath + ", " + Files.exists(imagePath));
            stack = MeshImageStack.fromVirtualTiff(imagePath.toString());
        }
        Path meshPath = Paths.get(meshFolder);

        String name = imagePath.getFileName().toString().replace(ft, "") + "_mesh-labels.zarr";
        Path parent = imagePath.toAbsolutePath().getParent();
        Path zop = parent.resolve(name);


        LabelFolderOfMeshes labeler = new LabelFolderOfMeshes(stack, meshPath, zop);

        int n = stack.getNFrames();
        Reactor<Integer> reactor = new Reactor<>();
        for(int i = 0; i<n; i++){
            final int frame = i;
            reactor.submit(()->labeler.processFrame(frame));
        }
        reactor.run();
    }
    public static void main(String[] args) throws IOException {
        createLabels(args[0], args[1]);
    }


    static class Reactor<T>{
        final int parallel = ForkJoinPool.getCommonPoolParallelism() - 2;
        final ExecutorService service = ForkJoinPool.commonPool();
        Deque<Callable<T>> queue = new ArrayDeque<Callable<T>>();
        List<T> output = new ArrayList<>();
        public void submit(Callable<T> task){
            if( ! queue.add(task) ){
                throw new RuntimeException("Cannot add to task queue");
            };
        }
        public void run(){
            Deque<Future<T>> running = new ArrayDeque<>();
            while(running.size() < parallel && queue.size() > 0){
                running.add( service.submit( queue.pop()));
            }

            while(running.size() > 0){
                try {
                    T item = running.pop().get();
                    output.add(item);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if(queue.size() > 0){
                    running.add(service.submit( queue.pop() ));
                }
            }
        }

    }
}
