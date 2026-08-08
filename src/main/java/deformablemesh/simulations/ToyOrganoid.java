package deformablemesh.simulations;

import deformablemesh.externalenergies.CellCenterAttraction;
import deformablemesh.externalenergies.SofterStericMesh;
import deformablemesh.externalenergies.VolumeConservation;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Node3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.geometry.Sphere;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.util.ColorSuggestions;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ToyOrganoid {
    static class Link{
        int i, j;

    }
    List<DeformableMesh3D> cells = new ArrayList<>();
    List<Link> links = new ArrayList<>();
    List<SofterStericMesh> steric = new ArrayList<>();
    MeshFrame3D frame = new MeshFrame3D();
    public void linkMeshes(){
        cells.forEach(DeformableMesh3D::clearEnergies);
        steric.clear();
        for(Link con: links){
            DeformableMesh3D a = cells.get(con.i);
            DeformableMesh3D b = cells.get(con.j);
            SofterStericMesh ab = new SofterStericMesh(a, b, 1);
            SofterStericMesh ba = new SofterStericMesh(b, a, 1);
            a.addExternalEnergy(ab);
            b.addExternalEnergy(ba);
            steric.add(ab);
            steric.add(ba);
            a.addExternalEnergy(new CellCenterAttraction(a, b, 0.01));
            b.addExternalEnergy(new CellCenterAttraction(b, a, 0.01));
        }
    }
    public void init(){
        DeformableMesh3D mesh = RayCastMesh.sixTriangleSphere();
        RayCastMesh.subDivideMesh(mesh);
        Sphere s = new Sphere(new double[]{0, 0, 0}, 0.25);
        for(Node3D node: mesh.nodes){
            s.moveTo(node.getCoordinates());
            cells.add(RayCastMesh.rayCastMesh(s, node.getCoordinates(), 2));

        }
        frame.showFrame(true);
        frame.addLights();
        frame.setBackgroundColor(Color.DARK_GRAY);
        cells.forEach(cell ->{
            cell.create3DObject();
            cell.setColor(ColorSuggestions.getSuggestion());
            cell.setShowSurface(true);
            cell.data_object.setWireColor(cell.getColor());
            frame.addDataObject(cell.data_object);
            cell.addExternalEnergy(new VolumeConservation(cell, 10));
            cell.ALPHA = 1;
            cell.BETA = 1.0;
            cell.GAMMA = 100;
        });
        List<SofterStericMesh> se = new ArrayList<>();

        while(true){
            se.stream().parallel().forEach(SofterStericMesh::update);
            cells.stream().parallel().forEach(cell ->{
                cell.update();
            });


        }
    }



    public static void main(String[] args){
        ToyOrganoid model = new ToyOrganoid();
        model.init();
    }

}
