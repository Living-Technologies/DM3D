package deformablemesh.io;

import deformablemesh.track.Track;

import java.nio.LongBuffer;
import java.util.Map;
import java.util.Set;

public class VirtualMeshReader {

    static Map<String, Integer> identity;
    static Map<Integer, Set<Integer>> frames;

    Map<Long, Long> addresses;
    public VirtualMeshReader(){

    }

    Track loadTrack(String name){
        int id = identity.get(name);

        return null;
    }




}
