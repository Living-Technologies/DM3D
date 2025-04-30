importer("deformablemesh.experimental.DTTrackPredictor");
importer("deformablemesh.meshview.BoxDO");
importer("deformablemesh.geometry.Box3D");
importer("deformablemesh.geometry.InterpolatingCropper");
importer("deformablemesh.io.SaveImageToZarr");
importer("ij.process.FloatProcessor");

importer("java.lang.System");
importer("java.lang.Thread");

/**
  * This section is for modifying multiple tracks at the
  * same time.
  **/
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

echo("/***************************************/")
echo(" * Hot keys activated: ");
echo(" 5 : addes currently selected mesh to a group of meshes 'selectedTracks'")
echo(" 6 : starts sculpting modification");
echo(" 7 : start translate modification");
echo(" 8 : cancel modifying mesh");
echo(" 9 : accept modifications ")


criteria = {
  minl:0.004, maxl:0.008,iterations:500, maxChange:0.3
};

/**
  * Starting from the current frame, goes through each track in the
  * provided list.
  *
  **/
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
                    echo(track.getName() + " stopping " + (delta*1000) + " ... " + (v0*1000) + " // " + (v1*1000) );
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


function findStrangeOccurances(){
    tracks = controls.getAllTracks();
    for(i = controls.getCurrentFrame(); i<controls.getNFrames() - 1; i++){
        for(k in tracks){
          track = tracks[k];
          if( track.containsKey(i)){
            mesh = track.getMesh( i );
            bb = mesh.getBoundingBox();
            v1 = bb.getVolume();
            best = -1;
            val = 0;

            for( ok in tracks ){

                ot = tracks[ok];
                if( ot.containsKey( i + 1 ) ){
                    bb2 = ot.getMesh( i+1).getBoundingBox();
                    overlap = bb2.getIntersectingBox(bb).getVolume()/v1;
                    if(overlap > val){
                        val = overlap
                        best = ok
                     }
                }
            }
            if( best != k  && track.containsKey(i + 1) ){
              echo( track.getName() + " overlaps " + tracks[best].getName() + " on frame " + (i+1) )
            }
          }
        }
    }
}

PREDICTOR_LEVEL = 4500;

function driftTrackSelectedMesh(){
    old = controls.getSelectedMesh();
    controls.trackMesh();
    predictor = new DTTrackPredictor( controls.getMeshImageStack() );
    predictor.level = PREDICTOR_LEVEL;

    controls.clearTransientObjects();
    mesh = controls.getSelectedMesh();
    echo( old == mesh );
    mbb = mesh.getBoundingBox();
    predictor.trackable( mesh );
    boxes = predictor.getBoxes();
    min = 3;
    mink = -1;
    for( k in boxes ){
         db = boxes[k];
         controls.addTransientObject( new BoxDO(db) );
         d = Vector3DOps.distance(  mbb.getCenter(), db.getCenter() );
         echo( k + ", " + d );
         if( d < min ){
             mink = k
             min = d
         }
    }
    if( mink >= 0 ){
        echo("found best" + mink);
        fb = boxes[mink];
        cb = mesh.getBoundingBox();
        delta = Vector3DOps.difference( fb.getCenter(), cb.getCenter() );
        echo(delta);
        mesh.translate(delta);
        mesh.resetPositions();
    }
}

function driftTrackBackwardsSelectedMesh(){
    controls.trackMeshBackwards();
    predictor = new DTTrackPredictor( controls.getMeshImageStack() );
    predictor.level = PREDICTOR_LEVEL;

    controls.clearTransientObjects();
    mesh = controls.getSelectedMesh();
    mbb = mesh.getBoundingBox();
    predictor.trackable( mesh );
    boxes = predictor.getBoxes();
    min = 3;
    mink = -1;
    for( k in boxes ){
         db = boxes[k];
         controls.addTransientObject( new BoxDO(db) );
         d = Vector3DOps.distance(  mbb.getCenter(), db.getCenter() );
         echo( k + ", " + d );
         if( d < min ){
             mink = k
             min = d
         }
    }
    if( mink >= 0 ){
        echo("found best" + mink);
        fb = boxes[mink];
        cb = mesh.getBoundingBox();
        delta = Vector3DOps.difference( fb.getCenter(), cb.getCenter() );
        echo(delta);
        mesh.translate(delta);
        mesh.resetPositions();
    }
}


// Averages the volume stack over time.
function averageOverTime( mist ){

	plus = mist.createImagePlus();
	stack = new ImageStack( mist.getWidthPx(), mist.getHeightPx());
                             echo("to here");
	for(i = 0; i<mist.getNSlices(); i++){
		stack.addSlice( new FloatProcessor(mist.getWidthPx(), mist.getHeightPx() ) );
	}
                             echo("to here 2")
	for( frame = 0; frame<mist.getNFrames(); frame++){
		mist.setFrame(frame);
		for(z = 0; z<mist.getNSlices(); z++){
			proc = stack.getProcessor(z + 1);
			for(j = 0; j<mist.getHeightPx(); j++){
				for(i = 0; i<mist.getWidthPx(); i++){
					proc.setf( i, j, proc.getf( i, j) + mist.getValue(i, j, z) );
				}
			}
		}
	}
	plus.setStack(stack);
	plus.show();
}

//volume based max projection in time. current channel only.
function maxOverTime( mist ){
	plus = mist.createImagePlus();
	stack = new ImageStack( mist.getWidthPx(), mist.getHeightPx());
	for(i = 0; i<mist.getNSlices(); i++){
		stack.addSlice( new FloatProcessor(mist.getWidthPx(), mist.getHeightPx() ) );
	}
	for( frame = 0; frame<mist.getNFrames(); frame++){
		mist.setFrame(frame);
		for(z = 0; z<mist.getNSlices(); z++){
			proc = stack.getProcessor(z + 1);
			for(j = 0; j<mist.getHeightPx(); j++){
				for(i = 0; i<mist.getWidthPx(); i++){
					old = proc.getf( i, j);
					next = mist.getValue(i, j, z);
					if( old > next ) next = old;
					proc.setf( i, j, next );
				}
			}
		}
	}
	plus.setStack(stack);
	plus.show();
}
