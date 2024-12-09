package deformablemesh.geometry.topology;

import deformablemesh.geometry.Connection3D;
import deformablemesh.geometry.Node3D;

import java.util.ArrayList;
import java.util.List;

public class ChainLink {
    final Connection3D item;
    List<ChainLink> back = new ArrayList<>();
    List<ChainLink> front = new ArrayList<>();
    final Node3D backNode;
    final Node3D frontNode;

    public ChainLink(Connection3D item) {
        this.item = item;
        backNode = item.A;
        frontNode = item.B;
    }

    public Connection3D getConnection() {
        return item;
    }
}
