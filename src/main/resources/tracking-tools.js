selectedTracks = controls.getEmptyTrackList();
add = function(){
    track = controls.getSelectedMeshTrack();
    if(!selectedTracks.contains(track)){
        selectedTracks.add(track);
    }
    echo(selectedTracks.size() + " meshes selected");
}
controls.setHotKey("5", add);

controls.setHotKey("6", function(){
    rc = controls.getRingController();
    rc.sculptClicked();
});
controls.setHotKey("7", function(){
    rc = controls.getRingController();
    rc.translateClicked();
});
controls.setHotKey("8", function(){
    rc = controls.getRingController();
    rc.cancel();
});
controls.setHotKey("9", function(){
    rc = controls.getRingController();
    rc.finishedClicked();
});
criteria = {
  minl:0.004, maxl:0.008,iterations:500, maxChange:0.3
};
function copyTrackMeshTracksBackwards(tracks, steps){

    c = controls.getCurrentFrame();
    minl = criteria.minl;
    maxl = criteria.maxl;
    iterations = criteria.iterations;
    stop = false;
    maxChange = criteria.maxChange;
    volumes = {};
    for(k in tracks){
        volumes[k] = tracks[k].getMesh(c).calculateVolume();
    }
    mn = c - steps;
    if(mn < 0){
        mn = 0;
    }
    for(i = c; i >= mn ; i--){
        current = i;
        next = i - 1;
        controls.toFrame(current);
        for(k in tracks){
            track = tracks[k];
            if(! track.containsKey(next)){
                controls.trackMeshBackWards(track, i);
            }
        }
        controls.toFrame(next);
        for(k in tracks){
            track = tracks[k];
            if( track.containsKey(next)){
                controls.selectMeshTrack(track);
                m = controls.getSelectedMesh();
                controls.deformMesh(iterations);
                controls.reMeshConnections(minl, maxl);
                controls.deformMesh(iterations);
                controls.reMeshConnections(minl, maxl);
                controls.deformMesh(iterations);
                controls.reMeshConnections(minl, maxl);
                v1 = m.calculateVolume();

                v0 = volumes[k];
                delta = v1 - v0;
                if(delta < 0) delta = -delta;
                if( delta/v0 > maxChange ){
                    echo("stopping " + (delta*1000) + " ... " + (v0*1000) + " // " + (v1*1000) );
                    stop = true;
                }
            }
        }
        if(stop){
          break;
        }
    }
}

function copyTrackMeshTracks(tracks, steps){

    c = controls.getCurrentFrame();
    minl = criteria.minl;
    maxl = criteria.maxl;
    maxChange = criteria.maxChange;
    iterations = criteria.iterations;
    stop = false;
    volumes = {};
    for(k in tracks){
        volumes[k] = tracks[k].getMesh(c).calculateVolume();
    }
    last = c + steps

    if(last > controls.getNFrames()-1){
        last = controls.getNFrames()-1;
    }
    for(i = c; i < last ; i++){
        current = i;
        next = i + 1;
        controls.toFrame(current);
        for(k in tracks){
            track = tracks[k];
            if(! track.containsKey(next)){
                controls.trackMesh(track, i);
            }
        }
        controls.toFrame(next);
        for(k in tracks){
            track = tracks[k];
            if( track.containsKey(next)){
                controls.selectMeshTrack(track);
                m = controls.getSelectedMesh();
                controls.deformMesh(iterations);
                controls.reMeshConnections(minl, maxl);
                controls.deformMesh(iterations);
                controls.reMeshConnections(minl, maxl);
                controls.deformMesh(iterations);
                controls.reMeshConnections(minl, maxl);
                v1 = m.calculateVolume();

                v0 = volumes[k];
                delta = v1 - v0;
                if(delta < 0) delta = -delta;
                if( delta/v0 > maxChange ){
                    echo(track.getName() + " stopping " + delta + " ... " + (v0*1000) + " // " + (v1*1000) );
                    stop = true;
                }
            }
        }
        if(stop){
          break;
        }
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