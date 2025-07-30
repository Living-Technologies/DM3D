
function getCurrentMeshes(frame){
    tracks = controls.getAllTracks();
    meshes = tracks.stream().filter(
        function( track){ return track.containsKey(frame); }
    ).map(function( track){ return track.getMesh(frame); }).toList();
    return meshes;
}
function getVolumeHistogram(meshes){
    min=0.0;
    max=0.0001;
    bins = 100;
    dv = (max - min)/bins;
    values = new DoubleArray(bins);
    counts = new DoubleArray(bins);
    for(var i = 0; i<bins; i++){
        values[i] = dv*(0.5 + i) + min;
    }
    for each(mesh in meshes){
        v = mesh.calculateVolume();
        if(v < min || v > max){
            continue;
        } else{
            dex = parseInt((v - min)/dv);
            if(dex > bins - 1 ){
                dex = bins-1
            }
            counts[dex] += 1;
        }
    }

    return [values, counts]
}

g = new Graph();
for( var frame = 0; frame<controls.getNFrames(); frame++){
    meshes = getCurrentMeshes(frame);
    data = getVolumeHistogram(meshes);
    g.addData(data[0], data[1]);

}

g.show(false, "volume histograms");