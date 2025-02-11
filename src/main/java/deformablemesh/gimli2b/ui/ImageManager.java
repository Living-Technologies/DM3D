package deformablemesh.gimli2b.ui;

import deformablemesh.MeshImageStack;
import deformablemesh.io.LoadZarr;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import java.awt.Image;
import java.io.IOException;
import java.nio.file.Paths;

public class ImageManager {
    JTree tree;
    JScrollPane view;
    public ImageManager(){
        tree = new JTree();
        view = new JScrollPane(tree);
    }
    public static void main(String[] args) throws IOException {
        MeshImageStack stack = LoadZarr.loadMeshImageStack2(Paths.get(args[0]));

        ImageManager manager = new ImageManager();

        JFrame frame = new JFrame("Loaded Images");

        frame.add(manager.view);


        frame.pack();
        frame.setVisible(true);

    }

}
