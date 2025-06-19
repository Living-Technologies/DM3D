/*-
 * #%L
 * Triangulated surface for deforming in 3D.
 * %%
 * Copyright (C) 2013 - 2023 University College London
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

 function importer( fullName ){
 	tokens = fullName.split(".");
 	className = tokens[ tokens.length - 1];
 	eval(className + " = Java.type('" + fullName + "');");
 	echo(className + " imported");
 }

DoubleArray = Java.type("double[]");
DeformableMesh3D = Java.type("deformablemesh.geometry.DeformableMesh3D");
Track = Java.type("deformablemesh.track.Track");
RayCastMesh = Java.type("deformablemesh.geometry.RayCastMesh");
MeshImageStack = Java.type("deformablemesh.MeshImageStack");
GuiTools = Java.type("deformablemesh.gui.GuiTools");
Vector3DOps = Java.type("deformablemesh.util.Vector3DOps");
SnapShotRecorder = Java.type("deformablemesh.util.SnapShotRecorder");
ImageStack = Java.type("ij.ImageStack");
ColorProcessor = Java.type("ij.process.ColorProcessor");
ImagePlus = Java.type("ij.ImagePlus");
IJ = Java.type("ij.IJ");
FileInfoVirtualStack = Java.type("ij.plugin.FileInfoVirtualStack");

Color = Java.type("java.awt.Color");
ArrayList = Java.type("java.util.ArrayList");
File = Java.type("java.io.File");
Files = Java.type("java.nio.file.Files");
Paths = Java.type("java.nio.file.Paths");
FolderOpener = Java.type("ij.plugin.FolderOpener");


MeshAnalysis = Java.type("deformablemesh.util.MeshAnalysis");
GroupDynamics = Java.type("deformablemesh.util.GroupDynamics");





function echo(obj){
    terminal.echo(obj);
}

//Sets the color of the outline for drawing the selected mesh in 2D
function setSelectedMeshColor(color){
  if( color instanceof Color){
      GuiTools.SELECTED_MESH_COLOR = color;
  }
}

function snapshotsThreeSixty(steps){
    total = 1260
    mf3d = controls.getMeshFrame3D();
    stack = 0;
    perStep = 1260/steps;
    for(var i = 0; i<steps; i++){

        mf3d.rotateView(perStep, 0);
        img = mf3d.snapShot();
        proc = new ColorProcessor(img);
        if(stack==0){
            stack = new ImageStack(proc.getWidth(), proc.getHeight());
        }
        stack.addSlice(proc);
    }
    plus = new ImagePlus();
    plus.setStack(stack);
    plus.setTitle("rotating snapshot");
    plus.show();
}

function meshToNewTrack(){
  track = controls.getSelectedMeshTrack();
  mesh = controls.getSelectedMesh();
  frame = controls.getCurrentFrame();
  controls.clearMeshFromTrack(track, frame);
  controls.startNewMeshTrack(frame, mesh);
  controls.selectMesh(mesh);
}


function restartOffscreenCanvas(){
  mf3d = controls.getMeshFrame3D();
  can = mf3d.getCanvas();
  can.destroyOffscreenCanvas();
}

function normalizeColors(){
  tracks = controls.getAllTracks();

  for(i = 0; i<tracks.size(); i++){
      track = tracks.get(i);
      track.setColor( new Color(i+1));
  }
}

function showPreviousMeshes(){
    controls.clearTransientObjects();
    alpha = 100;
    mesh = controls.getSelectedMesh();
    tracks = controls.getAllTracks();
    frame = controls.getCurrentFrame();
    for( key in tracks){
        track = tracks[key];
        if(track.containsKey(frame - 1)){
            track.setShowSurface(true);
            mesh = track.getMesh(frame - 1);
            dobj =  new DeformableMeshDataObject( mesh.nodes, mesh.connections, mesh.triangles, mesh.positions, mesh.connection_index, mesh.triangle_index );
            c = track.getColor();
            r = c.getRed();
            g = c.getGreen();
            b= c.getBlue();
            dobj.setColor(new Color(r, g, b, alpha));
            dobj.setWireColor( new Color(r, g, b, alpha));
            controls.addTransientObject( dobj );
        }
    }

}

