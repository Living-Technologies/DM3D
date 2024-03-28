selectedTracks = controls.getEmptyTrackList();
add = function(){
    track = controls.getSelectedMeshTrack();
    if(!selectedTracks.contains(track)){
        selectedTracks.add(track);
    }
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
