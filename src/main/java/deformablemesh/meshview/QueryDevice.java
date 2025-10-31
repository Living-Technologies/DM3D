package deformablemesh.meshview;

import com.jogamp.nativewindow.GraphicsConfigurationFactory;
import com.jogamp.nativewindow.awt.AWTGraphicsDevice;
import com.jogamp.opengl.GLCapabilitiesImmutable;
import jogamp.opengl.GLGraphicsConfigurationFactory;
import org.jogamp.java3d.Canvas3D;
import org.jogamp.java3d.GraphicsConfigTemplate3D;
import org.jogamp.java3d.VirtualUniverse;

import java.awt.*;
import java.util.Map;

public class QueryDevice {
    public static void main(String[] args) {
        VirtualUniverse vu = new VirtualUniverse();
        Map<?, ?> vuMap = VirtualUniverse.getProperties();
        for(Object key : vuMap.keySet()){
            System.out.println(key + ", " + vuMap.get(key));
        }
        System.out.println("version = " + vuMap.get("j3d.version"));
        System.out.println("vendor = " + vuMap.get("j3d.vendor"));
        System.out.println("specification.version = "
                + vuMap.get("j3d.specification.version"));
        System.out.println("specification.vendor = "
                + vuMap.get("j3d.specification.vendor"));
        System.out.println("renderer = " + vuMap.get("j3d.renderer") + "\n");

        GraphicsConfigurationFactory fact = GLGraphicsConfigurationFactory.getFactory(AWTGraphicsDevice.class, GLCapabilitiesImmutable.class);
        GraphicsConfigTemplate3D template = new GraphicsConfigTemplate3D();
        template.setSceneAntialiasing(GraphicsConfigTemplate.REQUIRED);
        /*
         * We need to set this to force choosing a pixel format that support the
         * canvas.
         */
        template.setStereo(GraphicsConfigTemplate3D.PREFERRED);
        template.setSceneAntialiasing(GraphicsConfigTemplate3D.PREFERRED);

        GraphicsConfiguration config = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getBestConfiguration(template);
        GraphicsConfiguration[] possible = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getConfigurations();
        for(GraphicsConfiguration p : possible){
            System.out.println(p + " :: " + (p==config));
        }


        Map<?, ?> c3dMap = new Canvas3D(config).queryProperties();


        for (Object key : c3dMap.keySet()) {
            System.out.println(key + " " + c3dMap.get(key));
        }
    }
}
