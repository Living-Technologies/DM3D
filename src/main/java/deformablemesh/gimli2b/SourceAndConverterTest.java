package deformablemesh.gimli2b;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bvvpg.core.VolumeViewerFrame;
import bvvpg.core.VolumeViewerPanel;
import bvvpg.vistools.Bvv;
import bvvpg.vistools.BvvFunctions;
import bvvpg.vistools.BvvHandleFrame;
import bvvpg.vistools.BvvOptions;
import bvvpg.vistools.BvvStackSource;
import deformablemesh.io.LoadZarr;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    public static void main(String[] args) throws IOException {
        new ImageJ();
        Path location = Paths.get("D:\\working\\yiteng\\241212_c5.zarr");
        //location = Paths.get("D:/working/sonnen/four2eight-part2/pred-v3.zarr");
        MeshImageStack2<?> mist = LoadZarr.loadMeshImageStack2(location);

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
        Color[] colors = { Color.CYAN, Color.GREEN, Color.BLACK, Color.RED, Color.BLUE};
        for(int i = 0; i<mist.getNChannels(); i++){
            Source<?> source = mist.sources.get(i);
            BvvStackSource< ? > bvvSource = BvvFunctions.show(source, mist.getNFrames(), new BvvOptions().addTo(bvv));
            bvvSource.setColor(new ARGBType(getValue(colors[i])));
            bvvSource.setDisplayRange(0, 4000);
        }

        buildController(bvv);
    }
    static int getValue(Color c){
        return (c.getRed()<<16) + (c.getGreen()<<8) + c.getBlue();
    }
}
