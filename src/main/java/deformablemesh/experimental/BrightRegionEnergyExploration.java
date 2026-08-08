package deformablemesh.experimental;

import deformablemesh.MeshImageStack;
import deformablemesh.externalenergies.BrightRegionEnergy;
import deformablemesh.geometry.CurvatureCalculator;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Node3D;
import deformablemesh.io.MeshReader;
import deformablemesh.meshview.ChannelVolume;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.track.Track;
import deformablemesh.util.Vector3DOps;
import lightgraph.DataSet;
import lightgraph.Graph;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * Given the bright region energy, there should be a maximum point. Where
 * if the point is a long way away from the edge, it is neutral. If it is near
 * the edge but out side, it is attracted to the edge. If it is inside of the edge
 * it is pushed out. If it is to far inside of the bright region, the force should
 * be zero again because there isn't a gradient so it doesnt see a bright region.
 */
public class BrightRegionEnergyExploration {

    /**
     * Each node is checked for inbalanced forces. Appropriate conditions:
     * f(-1) > 0; f(0) = 0; f(1) < 0
     *
     * f(-1) - f(1) + | f(0) |
     *
     * @param mist
     * @param mesh
     * @return
     */
    public static double stability(MeshImageStack mist, DeformableMesh3D mesh){
        BrightRegionEnergy bre = new BrightRegionEnergy(mist, mesh, 1);
        CurvatureCalculator cc = new CurvatureCalculator(mesh);
        double sum = 0;
        double ds = mist.getMinPx();

        for(Node3D node: mesh.nodes) {
            double[] c = node.getCoordinates();
            double[] n = cc.getNormal(node.getIndex());
            double[] pos = Vector3DOps.add(c, n, -ds);
            double f0 = bre.getForce(pos[0], pos[1], pos[2], n);
            pos = c;
            double f1 = bre.getForce(pos[0], pos[1], pos[2], n);
            pos = Vector3DOps.add(c, n, ds);
            double f2 = bre.getForce(pos[0], pos[1], pos[2], n);
            sum += (f0 - f2) + Math.abs(f1);
        }

        return sum/mesh.nodes.size();
    }


    public static void main(String[] args) throws IOException {
        MeshImageStack op = new MeshImageStack(Paths.get("sample.tif"));
        MeshImageStack mist = new MeshImageStack(op.getCurrentFrameIso());
        mist.gaussianBlur(2.0);
        mist.copyValues();
        List<Track> tracks = MeshReader.loadMeshes(new File("sample.bmf"));
        DeformableMesh3D mesh = tracks.get(0).getMesh(0);
        stability(mist, mesh);
        MeshImageStack two = mist.duplicate();
        MeshImageStack three = mist.duplicate();
        BrightRegionEnergy bre = new BrightRegionEnergy(mist, mesh, 1);
        double mn = Double.MAX_VALUE;
        double mx = -mn;
        for(int z = 0; z<mist.getNSlices(); z++){
            for(int j = 0; j<mist.getHeightPx(); j++){
                for(int i = 0; i<mist.getWidthPx(); i++){
                    double[] pt = mist.getNormalizedCoordinate(new double[]{i, j, z});
                    double erg = bre.getEnergy(pt);
                    if(erg < mn){
                        mn = erg;
                    }
                    if(erg > mx){
                        mx = erg;
                    }
                    double p = 0;
                    double n = 0;
                    if(erg < 0){
                        n = -erg;
                    } else{
                        p = erg;
                    }
                    two.data[z*mist.getWidthPx()*mist.getHeightPx() + j*mist.getWidthPx() + i] =  p;
                    three.data[z*mist.getWidthPx()*mist.getHeightPx() + j*mist.getWidthPx() + i] =  n;




                }
            }
        }


        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.setBackgroundColor(Color.BLACK);
        mf3d.addLights();
        two.setMinValue(0);
        two.setMaxValue(mx);
        three.setMinValue(0);
        three.setMaxValue(-mn);
        ChannelVolume cv = mf3d.createNewChannelVolume(three, Color.BLUE);
        ChannelVolume cv2 = mf3d.createNewChannelVolume(two, Color.YELLOW);
        mesh.create3DObject();
        mf3d.addDataObject(mesh.data_object);
        CurvatureCalculator cc = new CurvatureCalculator(mesh);
        Graph g = new Graph();
        Graph g2 = new Graph();
        double ds = mist.getMinPx();
        for(Node3D node: mesh.nodes){

            //Arrow a = new Arrow(0.1, 0.006125);
            //a.setColor(new Color(1f, 0.8f, 0.2f));
            double[] c = node.getCoordinates();
            double[] n = cc.getNormal(node.getIndex());
            //a.moveTo(c[0], c[1], c[2]);
            //a.pointAlong(n);
            //mf3d.addDataObject(a);
            int steps = 25;
            double offset = -steps/2 * mist.getMinPx();
            double[] x = new double[steps];
            double[] y = new double[steps];
            double[] z = new double[steps];
            double sum = 0;
            for (int i = 0; i < steps; i++) {
                double s = offset + i * ds;
                double[] pos = Vector3DOps.add(c, n, s);
                double v = mist.getInterpolatedValue(pos);
                double f = bre.getForce(pos[0], pos[1], pos[2], n);
                sum += f;
                x[i] = s;
                y[i] = v;
                z[i] = f;
            }
            DataSet set =  g.addData(x, y);
            set.setColor(new Color(1.0f, 0.0f, (float)Math.abs(n[2])));
            set =  g2.addData(x, z);
            set.setColor(new Color(1.0f, 0.0f, (float)Math.abs(n[2])));

        }
        g.show(false, "intensity scans");
        g2.show(false, "force scans");
        mf3d.addKeyListener(new KeyAdapter(){
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_A) {
                    mf3d.chooseToContrastChannelVolume();
                }
            }
        });
    }

}
