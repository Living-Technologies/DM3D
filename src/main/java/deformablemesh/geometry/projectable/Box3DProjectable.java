package deformablemesh.geometry.projectable;

import deformablemesh.geometry.Box3D;
import deformablemesh.geometry.Furrow3D;
import deformablemesh.ringdetection.FurrowTransformer;
import deformablemesh.util.Vector3DOps;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * For projecting a box on a 2D slicing plane.
 *
 * TODO: clean up and reuse.
 */
public class Box3DProjectable implements Projectable {
    final Box3D box;
    public Box3DProjectable(Box3D box){
        this.box = box;
    }
    @Override
    public Shape getProjection(FurrowTransformer transformer) {
        double[] low = box.low;
        double[] high = box.high;
        List<double[]> pos = new ArrayList<>();
        List<int[]> connections = new ArrayList<>();

        pos.add(new double[]{low[0], low[1], low[2]});
        pos.add(new double[]{low[0], low[1], high[2]});
        pos.add(new double[]{low[0], high[1], high[2]});
        pos.add(new double[]{low[0], high[1], low[2]});
        pos.add(new double[]{high[0], low[1], low[2]});
        pos.add(new double[]{high[0], low[1], high[2]});
        pos.add(new double[]{high[0], high[1], high[2]});
        pos.add(new double[]{high[0], high[1], low[2]});

        connections.add(new int[]{0, 1});
        connections.add(new int[]{1, 2});
        connections.add(new int[]{2, 3});
        connections.add(new int[]{3, 0});
        connections.add(new int[]{4, 5});
        connections.add(new int[]{5, 6});
        connections.add(new int[]{6, 7});
        connections.add(new int[]{7, 4});
        connections.add(new int[]{0, 4});
        connections.add(new int[]{1, 5});
        connections.add(new int[]{2, 6});
        connections.add(new int[]{3, 7});


        Furrow3D f = transformer.getFurrow();
        List<double[]> intersections = new ArrayList<>();
        double cx = 0;
        double cy = 0;
        for(int[] con: connections){
            double[] a = pos.get(con[0]);
            double[] b = pos.get(con[1]);
            double da = Vector3DOps.dot(f.normal, Vector3DOps.difference(a, f.cm));
            double db = Vector3DOps.dot( f.normal, Vector3DOps.difference(b, f.cm));

            if(da*db < 0){
                double l = da - db;
                double fraction = da/l;
                double[] pa = transformer.getPlaneCoordinates(a);
                double[] pb = transformer.getPlaneCoordinates(b);
                double[] pt = {
                        pb[0] + fraction*(pa[0] - pb[0]),
                        pb[1] + fraction*(pa[1] - pb[1])
                };
                cx += pt[0];
                cy += pt[1];

                intersections.add(pt);

            }
        }

        Path2D path = new Path2D.Double();

        cx = cx/intersections.size();
        cy = cy/intersections.size();
        double[] cm = {cx, cy};

        intersections.sort(Comparator.comparingDouble(p->{
            double dx = p[0] - cm[0];
            double dy = p[1] - cm[1];
            return Math.atan2(dy, dx);
        }));
        if(!intersections.isEmpty()){
            double[] x0 = intersections.get(0);
            path.moveTo(x0[0], x0[1]);
            for(int i = 1; i < intersections.size(); i++){
                double[] xi = intersections.get(i);
                path.lineTo(xi[0], xi[1]);
            }
            path.closePath();
        }
        return path;
    }
}
