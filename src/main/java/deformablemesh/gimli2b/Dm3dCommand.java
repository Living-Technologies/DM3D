package deformablemesh.gimli2b;

import bdv.viewer.Source;
import deformablemesh.MeshImageStack;
import deformablemesh.plugins.Deforming3DMesh_Plugin;
import deformablemesh.SegmentationController;
import ij.ImagePlus;
import net.imagej.Dataset;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.List;

@Plugin(type = Command.class, name="Start DM3D", menuPath="Plugins > DM3D> Experimental > Start DM3D ")

public class Dm3dCommand <T extends NumericType<T>&NativeType<T>&RealType<T>> implements Command {
    @Parameter(required=false)
    private Dataset currentData;

    SegmentationController controller;
    @Parameter
    Context context;
    @Override
    public void run() {
        context.getServiceIndex().forEach(System.out::println);
        if(controller == null){
            controller = Deforming3DMesh_Plugin.createDeformingMeshApplication();
        }
        if(currentData != null){
            //TODO convert a Dataset to a MeshImageStack2 or List<Source<T>>
            System.out.println(currentData);

        }


    }

}
