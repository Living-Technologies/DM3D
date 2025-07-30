function volumeFilter(mesh){
    minVolume = 5e-6;
    return mesh.calculateVolume() >= minVolume;
}

filterCurrentFrame( volumeFilter );

