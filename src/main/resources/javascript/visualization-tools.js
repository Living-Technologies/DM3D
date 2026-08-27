importer("deformablemesh.meshview.MultiChannelVolumeTexture");
IntArray = Java.type("int[]");
importer("deformablemesh.meshview.VolumeDataObject");

function shinyObject(o){
        o.shininess = 1;
        o.amb  = 1;
        o.emm = 0;
        o.dif = 0;
        o.spec = 1.667;

        o.setShowSurface(false);
        o.setShowSurface(true);
}



function createShinyMeshes(){
    mf3d = controls.getMeshFrame3D();
    mf3d.setBackgroundColor(Color.BLACK);
    tracks = controls.getAllTracks();
    mf3d.setDirectionalBrightness(0.6);
    mf3d.setAmbientBrightness(0.25);
    for(k in tracks){
        trk = tracks[k];
        f = controls.getCurrentFrame();
        dobj = trk.getMesh( f ).data_object;
        shinyObject(dobj);
    }
}


//very shiny
function veryShiny(o){
    o.shininess = 128;
    o.amb  = 1;
    o.emm = 0.1;
    o.dif = 0.5;
    o.spec = 5;
    o.setWireColor( new Color(0, 0, 0, 0) );
    o.setShowSurface(false);
    o.setShowSurface(true);
}

function veryShinyMeshes(){
    mf3d = controls.getMeshFrame3D();
    mf3d.setBackgroundColor(Color.BLACK);
    tracks = controls.getAllTracks();
    mf3d.setDirectionalBrightness(1);
    mf3d.setAmbientBrightness(0.0);
    for(k in tracks){
        trk = tracks[k];
        f = controls.getCurrentFrame();
        dobj = trk.getMesh( f ).data_object;
        veryShiny(dobj);
    }
}

function turnOffWires(){
    tracks = controls.getAllTracks();
    tracks.forEach( function(track){
      f = controls.getCurrentFrame();
      track.setShowSurface(true);
      if(track.containsKey(f)){
        mesh = track.getMesh(f);
        mdo = mesh.data_object;
        mdo.setWireColor( new Color(0, 0, 0, 0));
      }
    });
}

function addSeparateVolumeTexture(stack){
    xyz = new IntArray(3);
    xyz[0] = stack.getWidthPx();
    xyz[1] = stack.getHeightPx();
    xyz[2] = stack.getNSlices();
    mctex = new MultiChannelVolumeTexture(xyz);
    vdo = new VolumeDataObject( Color.CYAN, mctex );
    vdo.setTextureData( stack );
    mf3d.addDataObject( vdo );
}
