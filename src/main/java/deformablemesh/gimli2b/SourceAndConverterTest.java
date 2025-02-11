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
import ij.ImagePlus;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
/**
 * Trying to create simple examples built on the imglib2 structure
 * "SourceAndConverter"
 */
public class SourceAndConverterTest {
    static <T> SourceAndConverter<T> build(ImagePlus plus){
        return null;
    }
    public static void main(String[] args) throws IOException {
        Path location = Paths.get("D:\\working\\yiteng\\241212_c5.zarr");
        MeshImageStack2<UnsignedShortType> mist = (MeshImageStack2<UnsignedShortType>)LoadZarr.loadMeshImageStack2(location);

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
        BvvHandleFrame handle = (BvvHandleFrame)bvv.getBvvHandle();
        for(int i = 0; i<mist.getNChannels(); i++){
            Source<UnsignedShortType> source = mist.sources.get(i);
            BvvStackSource< ? > bvvSource = BvvFunctions.show(source, mist.getNFrames(), new BvvOptions().addTo(bvv));
        }
        VolumeViewerPanel viewer = handle.getBigVolumeViewer().getViewer();
        VolumeViewerFrame frame = handle.getBigVolumeViewer().getViewerFrame();
        Action action = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("did something");
            }
        };

        for(Object o : frame.getKeybindings().getConcatenatedActionMap().keys() ){
            System.out.println(o);
            System.out.println(frame.getKeybindings().getConcatenatedActionMap().get(o));
        };

        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true);
        viewer.getInputMap().put(stroke, "dm3d");
        viewer.getActionMap().put("dm3d", action);
        ActionMap map = new ActionMap();
        map.put(stroke, action);
        handle.getKeybindings().addActionMap("dm3d", map);

        viewer.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                System.out.println(e);
                new Thread(){
                    @Override
                    public void run(){
                        int stop = 5000;
                        for( int i = 0; i < 360; i++){
                            viewer.setTimepoint(i);
                            try{
                                Thread.sleep(stop);
                                stop = 100;
                            } catch(Exception e){
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }.start();
            }
        });


    }

}
