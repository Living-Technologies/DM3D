package deformablemesh.examples;

import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.SegmentationController;
import deformablemesh.externalenergies.BallooningEnergy;
import deformablemesh.externalenergies.BrightRegionEnergy;
import deformablemesh.geometry.ConnectionRemesher;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.geometry.interceptable.BinaryInterceptible;
import deformablemesh.gimli2b.Dm3dService;
import deformablemesh.gui.GuiTools;
import deformablemesh.io.LoadZarr;
import deformablemesh.track.Track;
import deformablemesh.util.connectedcomponents.Region;
import ij.IJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class QuickMeshExample implements Command {
    MeshImageStack original;

    @Parameter
    File input;
    @Parameter(style="save")
    File output;
	public DeformableMesh3D process(int frame){

        System.out.println("processing: " + frame);
        long start = System.nanoTime();
        MeshImageStack stack = null;
        synchronized (original){
            original.setFrame(frame);
            stack = new MeshImageStack(original.getCurrentFrame());
        }
        long loaded = System.nanoTime();
        MeshDetector detector = new MeshDetector(stack);
        List<Region> regions = detector.getRegionsFromLabelledImage();

        if(regions.size() == 0){
            DeformableMesh3D mesh = RayCastMesh.sphereRayCastMesh(3);
            for(int i = 0; i<mesh.positions.length; i++){
                mesh.positions[i] = 0;
            }
            System.out.println("empty after" + (System.nanoTime()-start));
            return mesh;
        }
        Region r = regions.get(0);
        for(int i = 1; i<regions.size(); i++){
            r.getPoints().addAll(regions.get(i).getPoints());
        }
        long detected = System.nanoTime();
        List<int[]> points = r.getPoints();
        BinaryInterceptible bi = new BinaryInterceptible(points, stack, r.getLabel());
        DeformableMesh3D mesh = RayCastMesh.rayCastMesh(bi, bi.getCenter(), 3);

        mesh.GAMMA = 1000;
        mesh.ALPHA = 1.0;
        mesh.BETA = 0.2;

        mesh.addExternalEnergy(new BallooningEnergy(bi, mesh, 500));

        for (int i = 0; i < 500; i++) {
            mesh.update();
        }

        mesh.clearEnergies();
        long deformed = System.nanoTime();
        mesh.addExternalEnergy(new BrightRegionEnergy(stack, mesh, 1.0));

        for (int i = 0; i < 500; i++) {
            mesh.update();
        }
        long deformed2 = System.nanoTime();
        System.out.println("loaded, detected, deformed, deformed " +
                (loaded - start)*1e-9 + ", " +
                (detected - loaded)*1e-9 + ", " +
                (deformed - detected)*1e-9 + ", " +
                (deformed2 - deformed)*1e-9 );
        return new DeformableMesh3D(mesh.positions, mesh.connection_index, mesh.triangle_index);

    }
    public void setInput(String input){
        this.input = new File(input);
    }
    public void setOutput( String output){
        this.output = new File(output);
    }
    @Override
    public void run() {
        if(input==null){
            Path p  = GuiTools.getAFile(null, "Select Cropped Image Series.");
            if(p != null){
                input = p.toFile();
            } else{
                return;
            }
        }

        Path path;
        if(output == null){
            path = GuiTools.getSaveFile(IJ.getInstance(), "Select");
            if(path==null) return;
        } else{
            path = output.toPath();
        }

        try{
            original = LoadZarr.loadMeshImageStack2(input.toPath());
        } catch (Exception e){
            System.out.println("cannot load image " + input);
            throw new RuntimeException(e);
        }

        int threads = 28;

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        //ExecutorService executorService = ForkJoinPool.commonPool();
        Queue<Callable<DeformableMesh3D>> tasks = new ArrayDeque<>(original.getNFrames());

        Queue<Future<DeformableMesh3D>> futures = new ArrayDeque<>(threads);

        for(int i = 0; i<original.getNFrames(); i++){
        //for(int i : frames){
            final int frame = i;
            tasks.add(()->process(frame));
        }
        int total = tasks.size();

        int step = total/100;
        if(step == 0) step = 1;

        int concurrent = threads*4/5;

        for(int i = 0; i<concurrent; i++){
            Callable<DeformableMesh3D> task = tasks.poll();
            if(task != null) {
                futures.add(executorService.submit(task));
            } else{
                break;
            }
        }
        System.out.println("submitted!: " + futures.size());
        //Save the positions during generation.
        int count = 0;
        try(DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(
                        Files.newOutputStream( path, StandardOpenOption.CREATE
                        )
                )
        )){
            long start = System.nanoTime();
            for(int i = 0; i<total; i++){

                try {

                    Future<DeformableMesh3D> f = futures.poll();
                    DeformableMesh3D mesh = f.get();

                    if(!tasks.isEmpty()){
                        Callable<DeformableMesh3D> task = tasks.poll();
                        futures.add(executorService.submit(task));
                    }

                    if(i == 0){
                        dos.writeInt(mesh.triangle_index.length);
                        for(int index : mesh.triangle_index){
                            dos.writeInt(index);
                        }
                        dos.writeInt(mesh.connection_index.length);
                        for(int index : mesh.connection_index){
                            dos.writeInt(index);
                        }
                    }
                    dos.writeInt(mesh.positions.length);
                    for(double d: mesh.positions){
                        dos.writeDouble(d);
                    }
                    count++;
                    if(count%step == 0){
                        System.out.println(count + " / " + original.getNFrames() + " after: " + (System.nanoTime() - start)*1e-9);
                        start = System.nanoTime();
                        //System.gc();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }


        }catch(IOException exc){
            throw new RuntimeException(exc);
        }

        executorService.shutdown();
    }

    public static void main(String[] args){
        QuickMeshExample qme = new QuickMeshExample();
        qme.run();
    }
}
