package deformablemesh.gimli2b;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bvb.core.BigVolumeBrowser;
import bvb.shapes.MeshColor;
import bvb.shapes.MultiMeshColor;
import bvvpg.core.VolumeViewerFrame;
import bvvpg.core.VolumeViewerPanel;
import bvvpg.vistools.Bvv;
import bvvpg.vistools.BvvFunctions;
import bvvpg.vistools.BvvHandleFrame;
import bvvpg.vistools.BvvOptions;
import bvvpg.vistools.BvvStackSource;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.gui.GuiTools;
import deformablemesh.gui.SwingJSTerm;
import deformablemesh.io.LoadZarr;
import deformablemesh.io.MeshReader;
import deformablemesh.track.Track;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import net.imglib2.mesh.Mesh;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Trying to create simple examples built on the imglib2 structure
 * "SourceAndConverter"
 */
public class SourceAndConverterTest {
    static SourceAndConverter<UnsignedShortType> build(ImagePlus plus, int channel){
        return null;
    }


    static public void buildController(Bvv bvv){
        BvvHandleFrame handle = (BvvHandleFrame)bvv.getBvvHandle();
        SwingJSTerm terminal = new SwingJSTerm();
        terminal.addToScriptEngine("bvv", bvv);
        terminal.showTerminal();
        VolumeViewerPanel viewer = handle.getBigVolumeViewer().getViewer();
        VolumeViewerFrame frame = handle.getBigVolumeViewer().getViewerFrame();
        JDialog log = new JDialog(frame, "browse volumes", false);

        JPanel panel = new JPanel();
        JButton play = new JButton("play");
        play.addActionListener(evt->{
            play.setEnabled(false);
            new Thread(){
                @Override
                public void run(){
                    ImageStack stack = new ImageStack(viewer.getWidth(), viewer.getHeight());
                    for( int i = 0; i < 360; i++){
                        viewer.setTimepoint(i);
                        try{
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                        BufferedImage img = new BufferedImage(viewer.getWidth(), viewer.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        Graphics g = img.getGraphics();
                        viewer.getDisplay().getComponent().paintAll(g);
                        g.dispose();
                        ImageProcessor proc = new ColorProcessor(img);
                        stack.addSlice(proc);

                    }
                    new ImagePlus("snap shots", stack).show();
                    play.setEnabled(true);
                }
            }.start();
        });
        panel.add(play);
        log.setContentPane(panel);
        log.pack();
        log.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        log.setVisible(true);
    }

    static Bvv buildBvv(){
        double dCam = 2000.;
        double dClipNear = 1000.;
        double dClipFar = 15000.;

        // parameters that require bvv restart,
        // see https://github.com/ekatrukha/BigTrace/wiki/Volume-Render-Settings
        int renderWidth = 800;
        int renderHeight = 600;
        int numDitherSamples = 3;
        int cacheBlockSize = 32;
        int maxCacheSizeInMB = 500;
        int ditherWidth = 3;

        Bvv bvv = BvvFunctions.show( Bvv.options().frameTitle( "DM3D Big Volume Viewer" ).
                dCam(dCam).
                dClipNear(dClipNear).
                dClipFar(dClipFar).
                renderWidth(renderWidth).
                renderHeight(renderHeight).
                numDitherSamples(numDitherSamples ).
                cacheBlockSize(cacheBlockSize ).
                maxCacheSizeInMB(maxCacheSizeInMB ).
                ditherWidth(ditherWidth)
        );
        return bvv;
    }

    public static void main(String[] args) throws IOException {
        new ImageJ();
        BigVolumeBrowser bvb = new BigVolumeBrowser();
        bvb.startBVB("DM3D visualization");
        bvb.settingsDialogBVV();





        File location = GuiTools.getDirectory(IJ.getInstance(), "Select Zarr Folder");
        if(location == null){
            return;
        }
        MeshImageStack2<?> mist = LoadZarr.loadMeshImageStack2(location.toPath());

        Color[] colors = { Color.MAGENTA, Color.CYAN, null, Color.RED, Color.YELLOW};
        for(int i = 0; i<mist.getNChannels(); i++){
            Color c = colors[i];
            if(c == null) continue;
            Source<?> source = mist.sources.get(i);

            List<BvvStackSource<?>> bvvSources = bvb.addSource(source).getB();
            System.out.println(bvvSources.size());
            //BvvStackSource< ? > bvvSource = BvvFunctions.show(source, mist.getNFrames(), new BvvOptions().addTo(bvb.bvv));
            //bvvSource.setColor(new ARGBType(getValue(colors[i])));
            //bvvSource.setDisplayRange(0, 4000);
        }

        Path mesh = GuiTools.getAFile(IJ.getInstance(), "Select Mesh File");
        if(mesh != null){
            MultiMeshColor mmc = new MultiMeshColor();
            if(Files.isDirectory(mesh)){
                try(DirectoryStream<Path> stream = Files.newDirectoryStream(mesh)){
                    for(Path p : stream){
                        List<Map<Integer, Mesh>> tracks = Imglib2MeshReader.loadMeshes(p.toFile());
                        while(tracks.size() > 0){
                            Map<Integer, Mesh> t = tracks.remove(tracks.size() - 1);
                            for(Integer frame : t.keySet()){
                                Mesh m = t.get(frame);
                                Imglib2Mesh.transformFromNormalizedSpaceToImageSpace(m, mist);
                                mmc.addMesh(m, frame, new Color(255, 250, 100, 100));
                            }
                        }
                    }
                }
            } else{
                List<Map<Integer, Mesh>> tracks = Imglib2MeshReader.loadMeshes(mesh.toFile());
                while(tracks.size() > 0){
                    Map<Integer, Mesh> t = tracks.remove(tracks.size() - 1);
                    for(Integer frame : t.keySet()){
                        Mesh m = t.get(frame);
                        Imglib2Mesh.transformFromNormalizedSpaceToImageSpace(m, mist);
                        mmc.addMesh(m, frame, Color.BLUE);
                    }
                }
            }
            bvb.addShape(mmc);


        }

        buildController(bvb.bvv);
    }
    static int getValue(Color c){
        return (c.getRed()<<16) + (c.getGreen()<<8) + c.getBlue();
    }
}
