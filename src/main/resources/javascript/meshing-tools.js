importer("deformablemesh.geometry.BinaryMeshGenerator");
importer("deformablemesh.geometry.BinaryRemesher");
importer("java.util.ArrayList");

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
    controls.startNewMeshTracks( ready );
}