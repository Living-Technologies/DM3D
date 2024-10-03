package deformablemesh.geometry.topology;

public class TopologyValidationError {
    final static int UNKNOWN = 0;
    final static int FOLDED_TRIANGLE = 1;
    final static int INTERSECTING_SURFACE = 2;
    final static int DISJOINT_NODE = 3;
    final static int DEGENERATE_TRIANGLE = 4;
    public static final int OPEN_SURFACE = 5;
    final String message;
    final int type;
    public TopologyValidationError(String s){
        this(UNKNOWN, s);
    }
    public TopologyValidationError(int type, String s){
        this.type = type;
        message = s;
    }
    String getMessage(){
        return message;
    }
    @Override
    public String toString(){
        return getClass().getSimpleName() + ":" + message;
    }
    public int getType(){ return type;}
}
