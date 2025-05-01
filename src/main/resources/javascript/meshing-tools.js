importer("deformablemesh.geometry.BinaryMeshGenerator");
importer("deformablemesh.geometry.BinaryRemesher");
importer("java.util.ArrayList");

/*
 * Applies the threshold to find seeds for the resulting meshes, then grows the seeds to
 * the next
 */
function predictVoxelMeshes(downsample, threshold1, threshold2){
	generator = new BinaryMeshGenerator();
	generator.setDownsample(downsample);
	generator.setInitialThreshold(threshold1);
	generator.setSecondThreshold( threshold2 );
	generator.setOpenSteps(1);

	meshes = generator.predictMeshes( controls.getMeshImageStack() );
	echo( meshes.size() + " found");
	ready = new ArrayList();
	for( k in meshes){
	  mesh = meshes[k];
	  repaired = controls.validateTopology( mesh );
	  for( z in repaired ){
	      ready.add(repaired[z])
	  }
	}
    return ready;
}

MESHING_CONSTANTS = {
    'blur' : 2.0,
    'volume_minimum' : 0.00001
};

function binaryRemesher( downsample, threshold ){
	filtered = controls.getMeshImageStack().getCurrentFrame();
	stack = filtered.getStack();
	for( var i=1; i <= stack.size(); i++){
		stack.getProcessor(i).blurGaussian( MESHING_CONSTANTS.blur );
	}
    tracks = controls.getAllTracks();
    original = tracks.stream().filter(
        function( track ){ return track.containsKey(cf);}
        ).map(
            function(track){ return track.getMesh(cf); }
        ).toList();
	remesher = new BinaryRemesher(new MeshImageStack(filtered));
	remesher.setThreshold( threshold );
	remesher.setDownsample(downsample);
	meshes = remesher.remeshMeshes( original );
	echo( meshes.size() + " found");
	next = new ArrayList();

	for( k in meshes){
	  mesh = meshes[k];
	  if( mesh.calculateVolume() > MESHING_CONSTANTS.volume_minimum ){
		   validated = controls.validateTopology( mesh );
		   validated.forEach( function( v) {
			  if( v.calculateVolume() > MESHING_CONSTANTS.volume_minimum ) {
					next.add( v );
			   }
			});
	  }
	}
	controls.startNewMeshTracks( next );
}

function binaryRemeshSelectedMesh(downsample, threshold){
	filtered = controls.getMeshImageStack().getCurrentFrame();
	stack = filtered.getStack();
	for( var i=1; i <= stack.size(); i++){
		stack.getProcessor(i).blurGaussian( MESHING_CONSTANTS.blur );
	}
	cf = controls.getCurrentFrame();
    track = controls.getSelectedMeshTrack();
    if( ! track.containsKey(cf)){
        return;
    }
    mesh = track.getMesh(cf);
    original = new ArrayList<>();
    original.add(mesh);
	remesher = new BinaryRemesher(new MeshImageStack(filtered));
	remesher.setThreshold( threshold );
	remesher.setDownsample(downsample);
	meshes = remesher.remeshMeshes( original );
	echo( meshes.size() + " found");
	next = new ArrayList();

	for( k in meshes){
	  mesh = meshes[k];
	  if( mesh.calculateVolume() > MESHING_CONSTANTS.volume_minimum ){
		   validated = controls.validateTopology( mesh );
		   validated.forEach( function( v) {
			  if( v.calculateVolume() > MESHING_CONSTANTS.volume_minimum ) {
					next.add( v );
			   }
			});
	  }
	}

	controls.startNewMeshTracks( next );

}