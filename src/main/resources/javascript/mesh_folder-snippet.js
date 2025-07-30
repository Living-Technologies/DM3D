folder = GuiTools.getDirectory(controlFrame.getFrame(), "mesh folder");

frameListener = function( tp ){
    name = "frame-" + tp + ".bmf";
    f = new File(folder, name);
    tracks = MeshReader.loadMeshes( f );
    controls.setMeshTracks( tracks );
    controls.setLastSavedFile( f );
}

frameListener = controls.addFrameListener( frameListener );

//to stop tracking meshes remove frame listener.
//controls.removeFrameListener( frameListener );