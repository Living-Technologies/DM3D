package deformablemesh.geometry.modifier;

import deformablemesh.geometry.Box3D;
import deformablemesh.geometry.Node3D;
import deformablemesh.geometry.projectable.Box3DProjectable;
import deformablemesh.geometry.projectable.Projectable;
import deformablemesh.meshview.BoxDO;
import deformablemesh.meshview.MeshFrame3D;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class Translator implements ModificationState {
    MeshFrame3D meshFrame3D;
    final MeshModifier modifier;
    BoxDO cursor;
    double[] center;
    double[] start;
    double[] offset;
    Box3D starting;
    Translator(MeshModifier modifier){
        this.modifier = modifier;

    }
    @Override
    public void register() {
        starting = modifier.getMesh().getBoundingBox();
        center = starting.getCenter();
        cursor = new BoxDO(starting);
        offset = new double[]{0, 0, 0};
        if(modifier.frame != null){
            meshFrame3D = modifier.frame;
            meshFrame3D.addDataObject(cursor);
        }
    }

    @Override
    public void deregister() {
        if(meshFrame3D != null){
            meshFrame3D.removeDataObject(cursor);
            //should this be done here? Yes, because it is removed here.
            meshFrame3D.setCanvasControllerEnabled(true);
        }

    }

    @Override
    public void updatePressed(double[] point, MouseEvent evt) {
        if(point==null){
            return;
        }

        offset[0] = 0;
        offset[1] = 0;
        offset[2] = 0;

        if(meshFrame3D != null){
            meshFrame3D.setCanvasControllerEnabled(false);
        }
        start = point;
    }

    @Override
    public void updateReleased(double[] point, MouseEvent evt) {
        if(offset[0] != 0 || offset[1] != 0 || offset[2] != 0){
            List<Node3D> nodes = modifier.mesh.nodes;
            List<double[]> deltas = new ArrayList<>(nodes.size());
            for(Node3D n: nodes){
                deltas.add(offset);
            }
            modifier.postAction(new DisplaceNodesAction(modifier.mesh, nodes, deltas));
            center[0] = center[0] + offset[0];
            center[1] = center[1] + offset[1];
            center[2] = center[2] + offset[2];
        }
    }

    @Override
    public void updateClicked(double[] point, MouseEvent evt) {

    }

    @Override
    public void updateMoved(double[] point, MouseEvent evt) {

    }

    @Override
    public void updateDragged(double[] point, MouseEvent evt) {
        if(point==null){
            return;
        }
        double sx = point[0] - start[0];
        double sy = point[1] - start[1];
        double sz = point[2] - start[2];
        offset = new double[]{sx, sy, sz};
        double[] shifted = { center[0] + sx, center[1] + sy, center[2] + sz};
        cursor.setCenter(shifted);
        starting.setCenter(shifted);

    }

    @Override
    public Projectable getProjectable() {
        return new Box3DProjectable(starting);
    }
}
