package deformablemesh.experimental;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.geometry.*;
import deformablemesh.io.MeshReader;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.track.Track;
import deformablemesh.util.ColorSuggestions;
import deformablemesh.util.Vector3DOps;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TopoCheck {
    Map<Node3D, List<Triangle3D>> nodeToTriangle = new HashMap<>();
    Map<Connection3D, List<Triangle3D>> connectionToTriangle = new HashMap<>();
    Map<Triangle3D, List<Connection3D>> triangleToConnection = new HashMap<>();
    List<Connection3D> fourBy = new ArrayList<>();

    /**
     * Populates the node to triangle map and checks for missing connections
     * or triangles with redundant points.
     *
     * @param mesh
     */
    public void populateNodeToTriangle(DeformableMesh3D mesh){
        nodeToTriangle.clear();
        for(Node3D node: mesh.nodes){
            nodeToTriangle.put(node, new ArrayList<>());
        }
        for(Triangle3D t : mesh.triangles){
            nodeToTriangle.get(t.A).add(t);
            nodeToTriangle.get(t.B).add(t);
            nodeToTriangle.get(t.C).add(t);
            if(t.A == t.B || t.A == t.C || t.C == t.B){
                System.out.println("repeated node triangle");
            }
            Connection3D c3d = new Connection3D(t.A, t.B);
            if(!mesh.connections.contains(c3d)){
                System.out.println("missing connection");
            }
            c3d = new Connection3D(t.B, t.C);
            if(!mesh.connections.contains(c3d)){
                System.out.println("missing connection");
            }
            c3d = new Connection3D(t.C, t.A);
            if(!mesh.connections.contains(c3d)){
                System.out.println("missing connection");
            }
        }
    }

    /**
     * Calculates the mappings from the triangles to the edges and the edges to
     * the triangles.
     *
     * This finds triangles that do not share an edge with two unique neighbors.
     *
     * @param mesh
     */
    void populatedConnectionMappings(DeformableMesh3D mesh){
        triangleToConnection.clear();
        connectionToTriangle.clear();
        fourBy.clear();
        for(Triangle3D t3d : mesh.triangles){
            triangleToConnection.put(t3d, new ArrayList<>());
        }
        for(Connection3D con: mesh.connections){
            connectionToTriangle.put(con, new ArrayList<>());
            List<Triangle3D> as = nodeToTriangle.get(con.A);
            List<Triangle3D> bs = nodeToTriangle.get(con.B);

            Set<Triangle3D> union = new HashSet<>();
            union.addAll(as);
            union.addAll(bs);


            List<Triangle3D> shared = as.stream().filter(bs::contains).collect(Collectors.toList());
            for(Triangle3D t: shared){
                connectionToTriangle.get( con ).add(t);
                triangleToConnection.get( t ).add(con);
            }
            Set<Triangle3D> junk = new HashSet<>();
            if(shared.size() < 2){
                junk.addAll(shared);
                System.out.println("Broken connections! " + shared.size());
            } else if(shared.size() > 2){
                fourBy.add(con);
            } else if(shared.size() != 2){
                System.out.println("crazy connections");
            }

            if(mf3d != null && junk.size() > 0 ){
                DeformableMesh3D sm = DeformableMesh3DTools.fromTriangles(mesh.positions, junk.stream().map(Triangle3D::getIndices).collect(Collectors.toList()));
                if(cm == null) {
                    cm = sm.getBoundingBox().getCenter();
                    cm[0] = -cm[0];
                    cm[1] = -cm[1];
                    cm[2] = -cm[2];
                }
                sm.translate(cm);
                sm.create3DObject();
                sm.data_object.setShowSurface(true);
                sm.data_object.setColor(ColorSuggestions.getSuggestion());
                mf3d.addDataObject(sm.data_object);
            }

        }

    }


    static class SortedT{
        double angle;
        boolean cw;
        Triangle3D triangle3D;
        double getAngle(){return angle;};
    }

    static class FourByConnectionMapper{
        //maps lets us determin the type of connection.
        Map<Connection3D, List<SortedT>> maps = new HashMap<>();
        Map<Node3D, Map<Integer, List<List<Triangle3D>>>> split;


    }
    FourByConnectionMapper mapper = new FourByConnectionMapper();
    static DeformableMesh3D debug;



    List<List<Triangle3D>> regroupTriangles(List<List<Triangle3D>> small, List<List<Triangle3D>> big){
        List<List<Triangle3D>> grouped = new ArrayList<>();
        for(List<Triangle3D> part : small ){
            List<Triangle3D> parallel = new ArrayList<>();
            grouped.add(parallel);
            for(List<Triangle3D> incomplete : big){
                if(incomplete.stream().anyMatch(part::contains)){
                    parallel.addAll(incomplete);
                }
            }
        }
        big.clear();
        big.add(grouped.get(0));
        big.add(grouped.get(1));
        return big;
    }
    static double[] cm = null;
    public DeformableMesh3D splitFourByConnections(DeformableMesh3D mesh){
        debug = mesh;
        System.out.println(fourBy.size() + " connections to fix");
        int added = 0;

        NodeSplitting nodeSplitter = new NodeSplitting(mesh);

        for(Connection3D con: fourBy) {
            List<SortedT> st = getSortedTriangles(con);
            mapper.maps.put(con, st);
        }

        List<List<Connection3D>> chainedConnections = ConnectionChain.chainFourByConnections(fourBy);

        boolean broken = false;
        Set<Triangle3D> borked = new HashSet<>();

        List<Connection3D> chainSorted = new ArrayList<>();

        for(List<Connection3D> chain : chainedConnections){
            System.out.print("chain: " );
            for(Connection3D con : chain){
                List<List<Triangle3D>> pa = partitionTriangles(con.A);
                List<List<Triangle3D>> pb = partitionTriangles(con.B);
                boolean na = pa.size() == 2 ? pinched(mapper.maps.get(con), pa): false;
                boolean nb = pb.size() == 2 ? pinched(mapper.maps.get(con), pb): false;

                System.out.print(":" + na + " " + pa.size() + " -- " + nb + " " + pb.size());
            }
            System.out.println("-->");
            Deque<Connection3D> stack = new ArrayDeque<>(chain.size());
            Deque<Connection3D> unfinished = new ArrayDeque<>(chain);
            Set<Connection3D> processed = new HashSet<>();
            stack.add(unfinished.pop());
            boolean processingPinch = true;
            List<Connection3D> visited = new ArrayList<>();
            while(stack.size() > 0){
                Connection3D con = stack.pop();
                if(nodeSplitter.wasSplit(con.A) && nodeSplitter.wasSplit(con.B)){
                    throw new RuntimeException("Unecessary check!");
                }else if(nodeSplitter.wasSplit(con.A) || nodeSplitter.wasSplit(con.B)){
                    //finishing.
                    Node3D toSplit = nodeSplitter.wasSplit(con.A) ? con.B : con.A;
                    List<List<Triangle3D>> parti = partitionTriangles(toSplit);
                    if(parti.size()==2){
                        if( pinched(mapper.maps.get(con), parti) != processingPinch ) {
                            processed.add(con);
                            //leave it!
                            continue;
                        }
                    }
                    Node3D alreadySplit = toSplit == con.A ? con.B : con.A;
                    nodeSplitter.split(toSplit, alreadySplit, parti);
                    processed.add(con);
                    Iterator<Connection3D> citer = unfinished.iterator();
                    while(citer.hasNext()){
                        Connection3D prospect = citer.next();
                        if(prospect.A == toSplit || prospect.B == toSplit){
                            if(! processed.contains(prospect)) {
                                stack.add(prospect);
                                citer.remove();
                            }
                        }
                    }
                } else{
                    List<List<Triangle3D>> parti = partitionTriangles(con.A);
                    if(parti.size() == 2 && pinched(mapper.maps.get(con), parti) == processingPinch ){
                        nodeSplitter.split(con.A, con.B, parti);
                        stack.add(con);
                    } else{
                        List<List<Triangle3D>> parti2 = partitionTriangles(con.B);
                        if(parti2.size() == 2 && pinched(mapper.maps.get(con), parti2) == processingPinch ){
                            nodeSplitter.split(con.B, con.A, parti2);
                            if(!processingPinch) {
                                stack.add(con);
                            }
                        } else{
                            //we cannot split the nodes on this connection, put it back.
                            visited.add(con);
                            unfinished.add(con);
                            Connection3D candidate = unfinished.pop();
                            stack.add(candidate);
                            if(visited.contains(candidate)){
                                if(!processingPinch){
                                    throw new RuntimeException("Looped through 2 times!");
                                }
                                visited.clear();
                                processingPinch = false;
                            }
                        }
                    }

                }

            }
            System.out.println("unfinished: " + unfinished.size() + " processed: " + processed.size() + " starting: " + chain.size());
            //chainSorted.addAll(chain);
        }
        if(mf3d != null) {
            Set<Triangle3D> stationary = new HashSet<>();
            Set<Triangle3D> moved = new HashSet<>();
            for (Node3D node : nodeSplitter.split.keySet()) {
                List<Triangle3D> triangles = nodeSplitter.split.get(node);
                stationary.addAll(nodeToTriangle.get(node).stream().filter(t -> !triangles.contains(t)).collect(Collectors.toList()));
                moved.addAll(triangles);
            }
            List<int[]> tindex0 = stationary.stream().map(Triangle3D::getIndices).collect(Collectors.toList());
            List<int[]> tindex1 = moved.stream().map(Triangle3D::getIndices).collect(Collectors.toList());
            DeformableMesh3D mStat = DeformableMesh3DTools.fromTriangles(debug.positions, tindex0);
            if (cm == null) {
                cm = mStat.getBoundingBox().getCenter();
                cm[0] = -cm[0];
                cm[1] = -cm[1];
                cm[2] = -cm[2];
            }
            mStat.translate(cm);
            mStat.create3DObject();
            mStat.data_object.setWireColor(Color.WHITE);
            mStat.data_object.setColor(new Color(255, 200, 200, 100));
            mStat.data_object.setShowSurface(true);
            mf3d.addTransientObject(mStat.data_object);
            DeformableMesh3D mMove = DeformableMesh3DTools.fromTriangles(debug.positions, tindex1);
            mMove.translate(cm);
            mMove.create3DObject();
            mMove.data_object.setWireColor(Color.WHITE);
            mMove.data_object.setColor(new Color(200, 200, 255, 100));
            mMove.data_object.setShowSurface(true);
            mf3d.addTransientObject(mMove.data_object);
        }


        for(Connection3D con: chainSorted){
            //Two groups they all need to have new connections
            List<List<Triangle3D>> partA = partitionTriangles(con.A);
            List<List<Triangle3D>> partB = partitionTriangles(con.B);
            System.out.println("starting sizes: " + partA.size() + " -- " + partB.size() );
            if(partA.size() > 2 || partB.size() > 2){
                if(partA.size() > 2 && partB.size() > 2){
                    boolean asplit = nodeSplitter.wasSplit(con.A);
                    boolean bsplit = nodeSplitter.wasSplit(con.B);
                    if(asplit && bsplit){
                        System.out.println("both split! finished!");
                        continue;
                    } else if(asplit){
                        List<Triangle3D> modified = nodeSplitter.split.get(con.A);
                        List<Triangle3D> unmodified = partA.stream().flatMap(List::stream).filter(t->!modified.contains(t)).collect(Collectors.toList());
                        partA.clear();
                        partA.add(modified);
                        partA.add(unmodified);

                    } else if(bsplit){
                        List<Triangle3D> modified = nodeSplitter.split.get(con.B);
                        List<Triangle3D> unmodified = partB.stream().flatMap(List::stream).filter(t->!modified.contains(t)).collect(Collectors.toList());
                        partB.clear();
                        partB.add(modified);
                        partB.add(unmodified);
                    } else{
                        System.out.println(nodeSplitter.wasSplit(con.A) + " - " + nodeSplitter.wasSplit(con.B));
                        throw new RuntimeException("Not-implemented!");
                    }

                }
                List<List<Triangle3D>> small = partA.size() == 2 ? partA : partB;
                List<List<Triangle3D>> big = partA.size() > 2 ? partA : partB;
                regroupTriangles(small, big);

            }


            Map<Integer, List<List<Triangle3D>>> regions = new HashMap<>();
            for(List<Triangle3D> ap : partA){
                for(List<Triangle3D> bp : partB){
                    List<Triangle3D> inter = ap.stream().filter(bp::contains).collect(Collectors.toList());
                    System.out.println("x - x : " + inter.size());
                    if(inter.size() == 2){
                        List<List<Triangle3D>> pair = new ArrayList<>();
                        pair.add(ap);
                        pair.add(bp);
                        Integer i = regions.size();
                        regions.put(i, pair);
                    }

                    if(inter.size() == 1 && regions.size() > 0){
                        List<List<Triangle3D>> sA = partitionTriangles(con.A);
                        List<List<Triangle3D>> sB = partitionTriangles(con.B);

                        List<int[]> triangleIndexes = sA.stream().flatMap(
                                List::stream
                        ).map(
                                Triangle3D::getIndices
                        ).collect(Collectors.toList());
                        List<int[]> triangleIndexes2 = sB.stream().flatMap(
                                List::stream
                        ).map(
                                Triangle3D::getIndices
                        ).collect(Collectors.toList());
                        DeformableMesh3D mesh1 = DeformableMesh3DTools.fromTriangles(debug.positions, triangleIndexes);
                        if(cm == null){
                            cm = mesh1.getBoundingBox().getCenter();
                            cm[0] = -cm[0];
                            cm[1] = -cm[1];
                            cm[2] = -cm[2];
                        }
                        mesh1.translate(cm);
                        mesh1.create3DObject();
                        mesh1.data_object.setWireColor(Color.BLACK);
                        mesh1.data_object.setColor(ColorSuggestions.getSuggestion());
                        mesh1.data_object.setShowSurface(true);
                        mf3d.addTransientObject(mesh1.data_object);
                        DeformableMesh3D mesh2 = DeformableMesh3DTools.fromTriangles(debug.positions, triangleIndexes2);
                        mesh2.translate(cm);
                        mesh2.create3DObject();
                        mesh2.data_object.setWireColor(Color.BLACK);
                        mesh2.data_object.setColor(ColorSuggestions.getSuggestion());
                        mesh2.data_object.setShowSurface(true);
                        mf3d.addTransientObject(mesh2.data_object);


                        System.out.println("BOOORKED!");
                    }
                }
            }


            List<SortedT> ordered = mapper.maps.get(con);
            System.out.println(partA.size() + ", " + partB.size() + " sized partitions");

            if(!broken) {
                boolean pa = false;
                try {
                    pa = pinched(ordered, partA);
                } catch(Exception e){


                    throw new RuntimeException(e);
                }
                boolean pb = pinched(ordered, partB);
                System.out.println("pinched-ness: " + pa + ", " + pb);
                if(regions.size() == 2){
                    System.out.println("doubled");
                    int triangleGroup = 0;
                    if(nodeSplitter.wasSplit(con.A)){
                        List<Triangle3D> split = nodeSplitter.split.get(con.A);
                        long count0 = regions.get(0).get(0).stream().filter(split::contains).count();
                        long count1 = regions.get(1).get(0).stream().filter(split::contains).count();
                        System.out.println("skipping remapping A: " + count0 + ", " + count1);
                        int dex = count0 > count1 ? 0 : 1;
                        List<List<Triangle3D>> ab = regions.get(dex);
                        //nodeSplitter.split(con.B, ab.get(1));
                    } else if(nodeSplitter.wasSplit(con.B)){
                        List<Triangle3D> split = nodeSplitter.split.get(con.B);
                        long count0 = regions.get(0).get(0).stream().filter(split::contains).count();
                        long count1 = regions.get(1).get(0).stream().filter(split::contains).count();
                        System.out.println("skipping remapping B: " + count0 + ", " + count1);
                        int dex = count0 > count1 ? 0 : 1;
                        List<List<Triangle3D>> ab = regions.get(dex);
                        //nodeSplitter.split(con.A, ab.get(0));
                    } else{
                        List<List<Triangle3D>> ab = regions.get(0);
                        //nodeSplitter.split(con.A, ab.get(0));
                        //nodeSplitter.split(con.B, ab.get(1));
                    }

                } else {
                    if(pa && !pb){
                        System.out.println("single A");
                        //nodeSplitter.split(con.A, partA.get(0));
                    } else if(pb && !pa){
                        System.out.println("single B");
                        //nodeSplitter.split(con.B, partB.get(0));
                    } else{
                        System.out.println("Windings should be consistent!");
                    }
                }
            }

            }
            if(!broken){
                System.out.println("SUCCCESSS!");
            }

        if(broken){
            DeformableMesh3D ft = DeformableMesh3DTools.fromTriangles(
                    mesh.positions,
                    borked.stream().map(
                            Triangle3D::getIndices
                    ).collect(Collectors.toList()));
            ft.setShowSurface(true);
            if(first) {
                double[] cm2 = ft.getBoundingBox().getCenter();
                ft.translate(new double[]{-cm2[0], -cm2[1], -cm2[2]});
                first=false;
            }
            return ft;
        }
        List<double[]> positions = nodeSplitter.positions;
        List<int[]> triIndexes = nodeSplitter.triIndexes;
        List<int[]> connections = DeformableMesh3DTools.reconnect(triIndexes);

        return new DeformableMesh3D(positions, connections, triIndexes);
    }

    /**
     * The two partitions will be split, either the normals face each other
     * in which case the surfaces are external and touching each other.
     * The other case the meshes have their normals facing out, so they are
     * "pinched".
     *
     *                    a        b
     *                     \ ->   /
     *                      \  <-/
     *                       \  /
     *                        o
     *                      /  \
     *                     /->  \
     *                    /    <-\
     *                   c        d
     *
     * Only consecutive triangles can form the surface. If the partitions
     * hold a, b and b, c then the mesh is pinched. If the triangles hold
     * a,c and b, d then the surfaces kiss each other.
     *
     * @param ordered triangles surrounding the node in question.
     * @param partitions the triangles that have been separated around a node.
     * @see TopoCheck#partitionTriangles(Node3D)
     * @return
     */
    boolean pinched(List<SortedT> ordered, List<List<Triangle3D>> partitions){
        boolean[] pinched = new boolean[2];
        int dex = 0;
        for(List<Triangle3D> part : partitions){
            int cw = -1;
            int ccw = -1;
            for(int i = 0; i<ordered.size(); i++){
                SortedT t = ordered.get(i);

                if(part.contains(t.triangle3D)){
                    if(t.cw){
                        cw = i;
                    } else{
                        ccw = i;
                    }
                    //System.out.printf("cw\t%s\t%f\t", t.cw, t.angle);
                }
            }
            if( cw < ccw){
                int step = ccw - cw;
                if(step == 1){
                    pinched[dex++] = true;
                } else{
                    pinched[dex++] = false;
                }
            } else{
                int step = cw - ccw;
                if(step == 1){
                    pinched[dex++] = false;
                } else{
                    pinched[dex++] = true;
                }
            }
            //System.out.println("  " + pinched[0] + ", " + pinched[1] + " :: " + cw + ", " + ccw);
        }
        if(pinched[0] != pinched[1]){
            DeformableMesh3D mesh1 = DeformableMesh3DTools.fromTriangles(debug.positions, partitions.get(0).stream().map(Triangle3D::getIndices).collect(Collectors.toList()));
            if(cm == null){
                cm = mesh1.getBoundingBox().getCenter();
                cm[0] = -cm[0];
                cm[1] = -cm[1];
                cm[2] = -cm[2];
            }
            mesh1.translate(cm);
            mesh1.create3DObject();
            mesh1.data_object.setWireColor(Color.BLACK);
            mesh1.data_object.setColor(ColorSuggestions.getSuggestion());
            mesh1.data_object.setShowSurface(true);
            mf3d.addTransientObject(mesh1.data_object);
            DeformableMesh3D mesh2 = DeformableMesh3DTools.fromTriangles(debug.positions, partitions.get(1).stream().map(Triangle3D::getIndices).collect(Collectors.toList()));
            mesh2.translate(cm);
            mesh2.create3DObject();
            mesh2.data_object.setWireColor(Color.BLACK);
            mesh2.data_object.setColor(ColorSuggestions.getSuggestion());
            mesh2.data_object.setShowSurface(true);
            mf3d.addTransientObject(mesh2.data_object);

            throw new RuntimeException("Inconsistent partition mapping");
        }
        return pinched[0];
    }
    final static double tolerance = 1e-6;
    /**
     * Finds all of the triangles about this connection, then sorts them by angle
     * and determines if their normal points clockwise or counter clockwise.
     *
     * The connection direction, d, is from con.A to con.B. r is the perpendicular direction
     * from the connection to the center of the triangle.
     *
     *     r dot[ ( n x d ) ] = +/- 1
     *
     *     -1 clockwise
     *     +1 counter clockwise.
     *
     *
     * @param con The connection to be analyzed.
     * @return
     */
    List<SortedT> getSortedTriangles(Connection3D con){
        double[] dir = Vector3DOps.difference(con.B.getCoordinates(), con.A.getCoordinates());
        double[] cm = Vector3DOps.average(con.B.getCoordinates(), con.A.getCoordinates());
        double mag = Vector3DOps.normalize(dir);

        List<Triangle3D> triangles = connectionToTriangle.get(con);
        triangles.forEach(Triangle3D::update);
        List<SortedT> st = new ArrayList<>();
        double[] zero = null;
        for (Triangle3D t3d : triangles) {
            double[] x = Vector3DOps.cross(t3d.normal, dir);
            double[] r = Vector3DOps.difference(t3d.center, cm);

            double[] rper = Vector3DOps.add(r, dir, -Vector3DOps.dot(r, dir));
            double dperp = Vector3DOps.normalize(rper);

            SortedT a = new SortedT();
            a.triangle3D = t3d;
            if (st.size() == 0) {
                a.angle = 0;
                zero = rper;
            } else {
                double dot = Vector3DOps.dot(zero, rper);
                if(dot < -1 ){
                    if(-1 - dot < tolerance){
                        dot = -1;
                    } else{
                        throw new RuntimeException("dot product out of range!");
                    }
                }
                double cross = Vector3DOps.dot(dir, Vector3DOps.cross(rper, zero));
                if (cross >= 0) {
                    a.angle = Math.acos(dot);
                } else {
                    a.angle = 2 * Math.PI - Math.acos(dot);
                }
                if(Double.isNaN(a.angle)){
                    throw new RuntimeException("Sorting angle is NaN!");
                }
            }

            a.cw = Vector3DOps.dot(x, r) < 0;
            st.add(a);
        }



        st.sort(Comparator.comparingDouble(SortedT::getAngle));

        for(int i = 0; i<st.size() - 1; i++){
            if(st.get(i).angle == st.get(i+1).angle){
                if(i > 0){
                    //check previous.
                    if( st.get(i - 1).cw == st.get(i).cw){
                        SortedT a = st.get(i);
                        SortedT b = st.get(i+1);
                        st.set(i, b);
                        st.set(i+1, a);
                    }
                } else{
                    if( st.get(i + 1).cw == st.get(i + 2).cw){
                        SortedT a = st.get(i);
                        SortedT b = st.get(i+1);
                        st.set(i, b);
                        st.set(i+1, a);
                    }
                }
            }
        }

        //This worked! That is amazing.
        System.out.println(st.stream().map(s -> s.cw).collect(Collectors.toList()));
        return st;
    }

    static boolean first = true;

    /**
     * Triangles are expected to share a single node. If we don't
     * remove the provided connection, the the triangles will all be
     * connected.
     * @param node
     * @return
     */
    List<List<Triangle3D>> freePartitionTriangles(Node3D node){
        Deque<Triangle3D> awaiting = new ArrayDeque<>(nodeToTriangle.get(node));
        List<List<Triangle3D>> partitions = new ArrayList<>();
        List<Triangle3D> working = new ArrayList<>();
        Triangle3D tri = awaiting.pop();
        working.add(tri);
        partitions.add(working);
        Deque<Triangle3D> next = new ArrayDeque<>();
        while(awaiting.size() > 0) {
            List<Connection3D> cons = triangleToConnection.get(tri);
            for (Connection3D con : cons) {
                if (con.A == node || con.B == node) {
                    List<Triangle3D> neighbors = connectionToTriangle.get(con);
                    for(Triangle3D neighbor : neighbors){
                        if (!working.contains(neighbor)) {
                            working.add(neighbor);
                            awaiting.remove(neighbor);
                            next.add(neighbor);
                        }
                    }
                }
            }
            if(next.size() == 0){
                working = new ArrayList<>();
                partitions.add(working);
                tri = awaiting.pop();
                working.add(tri);
            } else{
                tri = working.remove(0);
            }


        }


        return partitions;
    }

    /**
     * Triangles are expected to share a single node. If we don't
     * remove the provided connection, the the triangles will all be
     * connected.
     * @param node
     * @return
     */
    List<List<Triangle3D>> partitionTriangles(Node3D node){

        Deque<Triangle3D> awaiting = new ArrayDeque<>(nodeToTriangle.get(node));
        List<List<Triangle3D>> partitions = new ArrayList<>();
        List<Triangle3D> working = new ArrayList<>();
        Triangle3D tri = awaiting.pop();
        working.add(tri);
        partitions.add(working);
        List<Connection3D> crossed = new ArrayList<>();
        Deque<Connection3D> waiting = new ArrayDeque<>();
        while(awaiting.size() > 0) {
            List<Connection3D> cons = triangleToConnection.get(tri);
            for (Connection3D con : cons) {
                if (fourBy.contains(con) || crossed.contains(con)) {
                    continue;
                }
                if (con.A == node || con.B == node) {
                    waiting.add(con);
                }
            }
            if(waiting.size() == 0){
                working = new ArrayList<>();
                partitions.add(working);
                tri = awaiting.pop();
                working.add(tri);
            } else{
                Connection3D link = waiting.pop();
                List<Triangle3D> next = connectionToTriangle.get(link);
                crossed.add(link);
                if (next.size() > 2) {
                    //we're screwed.
                    System.out.println("we're screwed");
                } else {
                    tri = working.contains(next.get(0)) ? next.get(1) : next.get(0);
                    if (working.contains(tri)) {
                        System.out.println("triangle stop");
                        //finished this set!

                    } else {
                        working.add(tri);
                        awaiting.remove(tri);
                    }
                }
            }


        }


        return partitions;
    }
    int getOddIndex(Triangle3D t, int a, int b){
        for(int i: t.getIndices()){
            if( i != a && i != b){
                return i;
            }
        }
        throw new RuntimeException("Triangle does not contain connection!");
    }


    /**
     * all of the folded triangles ( triangles that share all 3 vertexes)
     * have a fourBy connection and can be removed
     *
     * This goes through the fourBy connections, finds the folded triangles,
     * removes them and removes the .
     *
     * @param mesh
     * @return a new mesh without the folded triangles. null if there are
     *          no triangles to be removed.
     */
    DeformableMesh3D removeFoldedTriangles(DeformableMesh3D mesh){
        List<Triangle3D> toRemove = new ArrayList<>();

        Iterator<Connection3D> citer = fourBy.listIterator();
        while(citer.hasNext()){
            Connection3D fb = citer.next();
            List<Triangle3D> triangles = connectionToTriangle.get(fb);
            boolean removed = false;
            for(int i = 0; i<triangles.size() && !removed; i++){
                Triangle3D t0 = triangles.get(i);
                if(toRemove.contains(t0)){
                    continue;
                }
                for(int j = i+1; j<triangles.size() && !removed; j++){
                    Triangle3D t1 = triangles.get(j);
                    if(toRemove.contains(t1)){
                        continue;
                    }
                    if(t1.containsNode(t0.A) && t1.containsNode(t0.B) && t1.containsNode(t0.C)){
                        //folded.
                        toRemove.add(t0);
                        toRemove.add(t1);
                        citer.remove();
                        removed = true;
                    }

                }
            }
        }
        for(Node3D node: mesh.nodes){
            List<Triangle3D> folded = nodeToTriangle.get(node);
            if(folded.size() == 2) {
                folded.forEach(t -> {
                    if (!toRemove.contains(t)) toRemove.add(t);
                });
            }
        }

        if(toRemove.size()>0) {
            List<Triangle3D> triangle3DS = new ArrayList<>(mesh.triangles);
            for (Triangle3D t : toRemove) {
                List<Connection3D> cons = triangleToConnection.get(t);
                for (Connection3D c : cons) {
                    List<Triangle3D> triangles = connectionToTriangle.get(c);
                    if(triangles == null){
                        //broken hashcode/equals implementation?
                        for(Connection3D c2 : connectionToTriangle.keySet()){
                            if(c2.A == c.A && c2.B == c.B){
                                System.out.println("Borked!");
                            } else if( c2.B == c.A && c2.A == c.B ){
                                System.out.println("aslo borked");
                            } else{
                                System.out.println("connection never added!");
                            }
                        }
                    }
                    triangles.remove(t);
                }
                for (Node3D n : new Node3D[]{t.A, t.B, t.C}) {
                    nodeToTriangle.get(n).remove(t);
                }
                triangle3DS.remove(t);
            }
            int[] indexMap = new int[mesh.nodes.size()];
            int dex = 0;
            List<double[]> positions = new ArrayList<>();
            for (Node3D node : mesh.nodes) {
                if (nodeToTriangle.get(node).size() > 0) {
                    positions.add(node.getCoordinates());
                    indexMap[node.index] = dex;
                    dex++;
                }
            }
            List<int[]> conindexes = mesh.connections.stream().filter(
                    c -> connectionToTriangle.get(c).size() > 0
            ).map(
                    c -> new int[]{indexMap[c.A.index], indexMap[c.B.index]}
            ).collect(Collectors.toList());

            List<int[]> tridexes = triangle3DS.stream().map(
                    t -> new int[]{indexMap[t.A.index], indexMap[t.B.index], indexMap[t.C.index]}
            ).collect(Collectors.toList());
            DeformableMesh3D mesh2 = new DeformableMesh3D(positions, conindexes, tridexes);
            return mesh2;
        }
        return null;
    }

    public DeformableMesh3D foldedMeshes2(DeformableMesh3D mesh){
        for(Node3D node : mesh.nodes){
            List<Triangle3D> triangles = nodeToTriangle.get(node);
            if(triangles.size() == 2){
                System.out.println("folded triangle!");
            }
        }
        return mesh;
    }

    public List<DeformableMesh3D> checkMesh(DeformableMesh3D mesh){
        DeformableMesh3D m2 = mesh;
        int iterations = 0;
        while(m2 != null){
            mesh = m2;
            populateNodeToTriangle(mesh);
            populatedConnectionMappings(mesh);
            m2 = removeFoldedTriangles(m2);
            iterations++;
        }


        while(fourBy.size() > 0) {
            mesh = splitFourByConnections(mesh);
            populateNodeToTriangle(mesh);
            populatedConnectionMappings(mesh);
        }



        List<DeformableMesh3D> meshes = Imglib2MeshBenchMark.partition(mesh, Imglib2MeshBenchMark.connectedComponents(mesh.triangles));


        if(mf3d != null) {
            List<DeformableMesh3D> small = meshes.stream().filter(m->m.triangles.size() > 0).collect(Collectors.toList());
            for(DeformableMesh3D sm : small){
                if(cm == null) {
                    cm = sm.getBoundingBox().getCenter();
                    cm[0] = -cm[0];
                    cm[1] = -cm[1];
                    cm[2] = -cm[2];
                }
                sm.translate(cm);
                sm.create3DObject();
                sm.data_object.setShowSurface(false);
                sm.data_object.setWireColor(Color.BLACK);
                mf3d.addDataObject(sm.data_object);
            }
        }
        return meshes.stream().filter(m->m.triangles.size()>20).map(m ->{
            ConnectionRemesher cr = new ConnectionRemesher();
            cr.setMinAndMaxLengths(0.005, 0.01);
            DeformableMesh3D rmed = cr.remesh(m);
            if(cr.isOpenSurface()){
                throw new RuntimeException("No open surfaces!");
            }
            return rmed;
        }).collect(Collectors.toList());
    }
    static MeshFrame3D mf3d;
    public static void main(String[] args) throws IOException {
        List<Track> tracks = MeshReader.loadMeshes(new File("working.bmf"));

        mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.addLights();
        mf3d.setBackgroundColor(new Color(200, 200, 200));
        int skip = 0;
        for(Track t: tracks){
            System.out.println("fixing: " + t.getName());
            TopoCheck tc = new TopoCheck();
            try {
                DeformableMesh3D mesh = t.getMesh(t.getFirstFrame());
                List<DeformableMesh3D> checked = tc.checkMesh(mesh);
            } catch(Exception e){
                e.printStackTrace();
                break;
            }

        }

    }
}
