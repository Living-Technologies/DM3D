package deformablemesh.geometry.interceptable;

import deformablemesh.geometry.Intersection;
import deformablemesh.util.Vector3DOps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AxisPlane implements Interceptable {
    double[] position;
    double[] normal;

    List<Intersection> sections = new ArrayList<>(1);

    public AxisPlane(double[] pos, double[] norm) {
        position = pos;
        normal = norm;
    }

    @Override
    public List<Intersection> getIntersections(double[] origin, double[] direction) {
        sections.clear();

        double[] r = Vector3DOps.difference(position, origin);
        double dot = Vector3DOps.dot(r, normal);
        double cosTheta = Vector3DOps.dot(direction, normal);
        if (cosTheta != 0) {
            //dot is the distance along the normal.
            double m = dot / cosTheta;

            sections.add(new Intersection(
                    Vector3DOps.add(origin, direction, m),
                    normal
            ));
        } else {
            //parallel
            sections.add(Intersection.inf(normal));
        }

        return Collections.unmodifiableList(sections);
    }

}
