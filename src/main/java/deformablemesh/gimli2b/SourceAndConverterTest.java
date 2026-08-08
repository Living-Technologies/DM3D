package deformablemesh.gimli2b;

import bdv.viewer.Source;
import bvb.core.BigVolumeBrowser;
import bvb.shapes.MultiMeshShape;
import bvvpg.core.VolumeViewerFrame;
import bvvpg.core.VolumeViewerPanel;
import bvvpg.vistools.Bvv;
import bvvpg.vistools.BvvHandleFrame;
import bvvpg.vistools.BvvStackSource;
import deformablemesh.MeshImageStack;
import deformablemesh.gui.GuiTools;
import deformablemesh.gui.SwingJSTerm;
import deformablemesh.io.LoadZarr;
import deformablemesh.io.MultiscaleImageAdapter;
import deformablemesh.util.ColorSuggestions;
import ij.IJ;
import ij.ImageJ;
import net.imglib2.mesh.Mesh;

import javax.swing.*;
import java.awt.*;
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
    MeshImageStack mist;
    BigVolumeBrowser bvb;
    public void addImage() throws IOException {

        Path location = GuiTools.getAFile(IJ.getInstance(), "Select Image File or Folder");
        if(location == null){
            return;
        }
        if(Files.isDirectory(location)){
            if(location.getFileName().toString().endsWith(".zarr")){
                MultiscaleImageAdapter<?> msia = LoadZarr.load3DZarrFile(location.toAbsolutePath().toString());
                for(int i = 0; i<msia.getNChannels(); i++){
                    Source<?> source = msia.getAsBdvSource(i);
                    List<BvvStackSource<?>> bvvSources = bvb.addSource(source).getB();
                }
                mist = msia.getMeshImageStack(0);
            }
        } else{
            mist = new MeshImageStack(location);
            bvb.addImagePlus(mist.getOriginalPlus());
        }

    }
    public void loadMeshes() throws IOException {
        Path mesh = GuiTools.getAFile(IJ.getInstance(), "Select Mesh File");
        if(mesh != null){
            MultiMeshShape mmc = new MultiMeshShape();
            if(Files.isDirectory(mesh)){
                try(DirectoryStream<Path> stream = Files.newDirectoryStream(mesh)){
                    for(Path p : stream){
                        List<Map<Integer, Mesh>> tracks = Imglib2MeshReader.loadMeshes(p.toFile());
                        while(tracks.size() > 0){
                            Map<Integer, Mesh> t = tracks.remove(tracks.size() - 1);
                            for(Integer frame : t.keySet()){
                                Mesh m = t.get(frame);
                                Imglib2Mesh.transformFromNormalizedSpaceToImageSpace(m, mist);
                                mmc.addMesh(m, null,  frame, new Color(255, 250, 100, 100));
                            }
                        }
                    }
                }
            } else{
                List<Map<Integer, Mesh>> tracks = Imglib2MeshReader.loadMeshes(mesh.toFile());
                while(tracks.size() > 0){
                    Map<Integer, Mesh> t = tracks.remove(tracks.size() - 1);
                    Color c = ColorSuggestions.getSuggestion();
                    for(Integer frame : t.keySet()){
                        Mesh m = t.get(frame);
                        Imglib2Mesh.transformFromNormalizedSpaceToImageSpace(m, mist);
                        mmc.addMesh(m, null, frame, c);
                    }
                }
            }
            bvb.addShape(mmc);


        }
    }

    static public void buildController(BigVolumeBrowser bvb){
        SourceAndConverterTest test = new SourceAndConverterTest();
        test.bvb = bvb;
        Bvv bvv = bvb.bvv;
        BvvHandleFrame handle = (BvvHandleFrame)bvv.getBvvHandle();
        SwingJSTerm terminal = new SwingJSTerm();
        terminal.addToScriptEngine("bvv", bvv);
        terminal.addToScriptEngine("bvb", bvb);
        terminal.showTerminal();
        VolumeViewerPanel viewer = handle.getBigVolumeViewer().getViewer();
        VolumeViewerFrame frame = handle.getBigVolumeViewer().getViewerFrame();
        JDialog log = new JDialog(frame, "browse volumes", false);

        JPanel panel = new JPanel();
        JButton importMeshes = new JButton("Import Meshfiles");
        importMeshes.addActionListener(evt->{
            try {
                test.loadMeshes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        panel.add(importMeshes);

        JButton loadImage = new JButton("Add Image");
        loadImage.addActionListener(evt->{
            try {
                test.addImage();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        panel.add(loadImage);
        log.setContentPane(panel);
        log.pack();
        log.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        log.setVisible(true);
    }


    public static void main(String[] args) throws IOException {
        new ImageJ();
        BigVolumeBrowser bvb = new BigVolumeBrowser();
        bvb.startBVB("DM3D visualization");
        bvb.settingsDialogBVV();


        buildController(bvb);
    }
    static int getValue(Color c){
        return (c.getRed()<<16) + (c.getGreen()<<8) + c.getBlue();
    }
}
