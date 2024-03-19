package deformablemesh.geometry.interceptable;

import deformablemesh.geometry.Box3D;
import deformablemesh.geometry.Interceptable;
import deformablemesh.geometry.Intersection;
import deformablemesh.util.Vector3DOps;

import java.util.ArrayList;
import java.util.List;

import static deformablemesh.util.Vector3DOps.TOL;

public class Box3DInterceptable implements Interceptable {
    final Box3D box;
    List<AxisPlane> planes = new ArrayList<>();

    public Box3DInterceptable(Box3D box){
        this.box = box;
        createPlanes();
    }

    @Override
    public List<Intersection> getIntersections(double[] origin, double[] direction) {
        List<Intersection> intersections = new ArrayList<>();
        for(AxisPlane plane: planes){
            for(Intersection section: plane.getIntersections(origin, direction)){
                if(contains(section.location)){
                    intersections.add(section);
                }
            }
        }
        return intersections;
    }

    void createPlanes(){
        double[] low = box.low;
        double[] high = box.high;

        planes.clear();
        AxisPlane plusX = new AxisPlane(
                new double[]{high[0] - TOL, low[1] + high[1], low[2] + high[2]},
                Vector3DOps.xhat
        );
        planes.add(plusX);

        AxisPlane minusX = new AxisPlane(
                new double[]{low[0] + TOL, low[1] + high[1], low[2] + high[2]},
                Vector3DOps.nxhat
        );
        planes.add(minusX);

        AxisPlane plusY = new AxisPlane(
                new double[]{low[0] + high[1], high[1] - TOL, low[2] + high[2]},
                Vector3DOps.yhat
        );
        planes.add(plusY);

        AxisPlane minusY = new AxisPlane(
                new double[]{high[0] + low[0], low[1] + TOL, low[2] + high[2]},
                Vector3DOps.nyhat
        );
        planes.add(minusY);

        AxisPlane plusZ = new AxisPlane(
                new double[]{high[0] + low[0], low[1] + high[1], high[2]- TOL},
                Vector3DOps.zhat
        );
        planes.add(plusZ);

        AxisPlane minusZ = new AxisPlane(
                new double[]{high[0] + low[0], low[1] + high[1], low[2]+ TOL},
                Vector3DOps.nzhat
        );
        planes.add(minusZ);
    }
}
