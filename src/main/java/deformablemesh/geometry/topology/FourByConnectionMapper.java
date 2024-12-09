package deformablemesh.geometry.topology;

import deformablemesh.geometry.Connection3D;
import deformablemesh.geometry.Node3D;
import deformablemesh.geometry.Triangle3D;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FourByConnectionMapper {
    //maps lets us determine the type of connection.
    Map<Connection3D, List<TopoCheck.SortedT>> maps = new HashMap<>();
    Map<Node3D, Map<Integer, List<List<Triangle3D>>>> split = new HashMap<>();


}
