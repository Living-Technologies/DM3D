
typeA = {
    function stylizeMeshSurface(o){
        o.shininess = 1;
        o.amb  = 1;
        o.emm = 0;
        o.dif = 0;
        o.spec = 1.667;

        o.setShowSurface(false);
        o.setShowSurface(true);
    }



    function prepareDisplay(){
        mf3d = controls.getMeshFrame3D();
        mf3d.setBackgroundColor(Color.BLACK);
        tracks = controls.getAllTracks();
        mf3d.setDirectionalBrightness(0.6);
        mf3d.setAmbientBrightness(0.25);
        for(k in tracks){
            trk = tracks[k];
            f = controls.getCurrentFrame();
            dobj = trk.getMesh( f ).data_object;
            stylizeMeshSurface(dobj);
        }
    }


    };

prepareDisplay();

//very shiny
function stylizeMeshSurface(o){
    o.shininess = 128;
    o.amb  = 1;
    o.emm = 0.1;
    o.dif = 0.5;
    o.spec = 5;
    o.setWireColor( new Color(0, 0, 0, 0) );
    o.setShowSurface(false);
    o.setShowSurface(true);
}

function prepareDisplay(){
    mf3d = controls.getMeshFrame3D();
    mf3d.setBackgroundColor(Color.BLACK);
    tracks = controls.getAllTracks();
    mf3d.setDirectionalBrightness(1);
    mf3d.setAmbientBrightness(0.0);
    for(k in tracks){
        trk = tracks[k];
        f = controls.getCurrentFrame();
        dobj = trk.getMesh( f ).data_object;
        stylizeMeshSurface(dobj);
    }
}

prepareDisplay();

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
