package deformablemesh.experimental;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.*;
import deformablemesh.geometry.topology.TopoCheck;
import deformablemesh.geometry.topology.TopologyValidationError;
import deformablemesh.io.MeshWriter;
import deformablemesh.meshview.MeshFrame3D;
import deformablemesh.track.Track;
import deformablemesh.util.ColorSuggestions;
import deformablemesh.util.connectedcomponents.ConnectedComponents3D;
import deformablemesh.util.connectedcomponents.Region;
import deformablemesh.util.connectedcomponents.RegionGrowing;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.FileInfoVirtualStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.mesh.Mesh;
import net.imglib2.mesh.Triangle;
import net.imglib2.mesh.Triangles;
import net.imglib2.mesh.Vertex;
import net.imglib2.mesh.alg.MarchingCubesRealType;
import net.imglib2.mesh.alg.MeshConnectedComponents;
import net.imglib2.mesh.impl.nio.BufferMesh;
import net.imglib2.type.numeric.integer.UnsignedByteType;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Imglib2Mesh {


    /**
     * This is used for converting image coordinate meshes into
     * normalized coordinate meshes.
     */
    static class ImageSpaceTransformer{
        int ox, oy, oz;
        double iscale;
        double dx, dy, dz;
        double sx, sy, sz;
        int w, h, d;
        public ImageSpaceTransformer(MeshImageStack stack){
            iscale = 1.0/stack.SCALE;
            dx = stack.pixel_dimensions[0];
            dy = stack.pixel_dimensions[1];
            dz = stack.pixel_dimensions[2];
            sx = -stack.offsets[0];
            sy = -stack.offsets[1];
            sz = -stack.offsets[2];

            w = stack.getWidthPx();
            h = stack.getHeightPx();
            d = stack.getNSlices();
        }

        /**
         * Sets the pixel origin of this transformer to be the origin of the
         * pixel based region.
         *
         * @param r the region that pixels will correspond to.
         */
        public void update(Region r){
            ox = (int)r.getLowCorner()[0];
            oy = (int)r.getLowCorner()[1];
            oz = (int)r.getLowCorner()[2];
            w = (int)r.getHighCorner()[0] - ox;
            h = (int)r.getHighCorner()[1] - oy;
            d = (int)r.getHighCorner()[2] - oz;
        }
        double getX(double x){
            return (x + ox + 0.5)*dx * iscale  + sx;
        }
        double getY(double y){
            return (y + oy + 0.5)*dy * iscale + sy;
        }
        double getZ(double z){
            return (z + oz + 0.5)*dz * iscale + sz;
        }

    }

    /**
     * This converts a Mesh that has been derived from a marching cubes algorithm into
     * a DeformableMesh3D which expects the coordinates to be in normalized coordinates.
     *
     * @param mesh
     * @param transformer
     * @return
     */
    static DeformableMesh3D convertMesh(Mesh mesh, ImageSpaceTransformer transformer){
        if(mesh == null || mesh.vertices().size() == 0){
            return null;
        }

        Triangles triangles = mesh.triangles();
        int vertices = mesh.vertices().size();

        double[] positions = new double[3*vertices];
        int dex = 0;
        for(Vertex v: mesh.vertices()){
            positions[ dex*3 ] = transformer.getX(v.x());
            positions[ dex*3 + 1] = transformer.getY(v.y());
            positions[ dex*3 + 2 ] = transformer.getZ(v.z());
            dex++;
        }

        int[] indexes = new int[3*triangles.size()];
        int i = 0;
        Set<DeformableMesh3DTools.Con> connections = new HashSet<>();
        for(Triangle t: triangles){

            int i0 = (int)t.vertex0();
            int i1 = (int)t.vertex1();
            int i2 = (int)t.vertex2();

            indexes[i++] = i0;
            indexes[i++] = i1;
            indexes[i++] = i2;

            connections.add(new DeformableMesh3DTools.Con(i0, i1));
            connections.add(new DeformableMesh3DTools.Con(i1, i2));
            connections.add(new DeformableMesh3DTools.Con(i2, i0));
        }

        int[] cindexes = new int[2*connections.size()];
        i = 0;
        for(DeformableMesh3DTools.Con c: connections){
            cindexes[i++] = c.a;
            cindexes[i++] = c.b;
        }

        DeformableMesh3D dm3d = new DeformableMesh3D(positions, cindexes, indexes);

        return dm3d;
    }

    /**
     * The mesh returned from the marching cubes algorithm, each triangle points to
     * their own vertexes. The vertexes that are at the same location need to be
     * reduced to a single vertex and the associated triangles need to have their
     * indexes updated.
     *
     * @param mesh mesh to be transformed.
     * @param transformer this is only used to describe the extents of the images space
     *                    the vertexes are kept track of with a backing array.
     * @return
     */
    static Mesh removeDuplicateVertices(Mesh mesh, ImageSpaceTransformer transformer){
        Triangles triangles = mesh.triangles();
        int vertices = mesh.vertices().size();

        if(vertices == 0){
            return null;
        }

        double[] positions = new double[3*vertices];

        //List<VertexPosition> check = new ArrayList<>();
        int[][][] space = new int[transformer.d][transformer.h][transformer.w];
        int[] map = new int[vertices];
        int dex = 0;
        for(Vertex v: mesh.vertices()){
            int i = (int)v.index();
            int i2;

            //the location of the vertex
            int sx = (int)v.x();
            int sy = (int)v.y();
            int sz = (int)v.z();
            if( Math.pow(sx - v.x(), 2) + Math.pow(sy - v.y(), 2) + Math.pow(sz - v.z(), 2) > 0 ){
                System.out.println("non-integer vertex: " + v.x() + ", " + v.y() + ", " + v.z());
            }
            i2 = space[sz][sy][sx] - 1;
            if( i2 < 0){
                space[sz][sy][sx] = dex + 1;
                positions[ dex*3 ] = v.x();
                positions[ dex*3 + 1] = v.y();
                positions[ dex*3 + 2 ] = v.z();
                map[i] =  dex;
                dex++;
            } else{
                map[i] = i2;
            }
        }

        BufferMesh fin = new BufferMesh(dex, triangles.size());
        for(int i = 0; i<dex; i++){
            fin.vertices().add(positions[3*i], positions[3*i+1], positions[3*i+2]);
        }
        for(Triangle t: triangles){
            int i0 = map[(int)t.vertex0()];
            int i1 = map[(int)t.vertex1()];
            int i2 = map[(int)t.vertex2()];
            fin.triangles().add(i0, i1, i2);
        }
        return fin;
    }

    /**
     * Creates a mask of the provided region.
     *
     * @param region contains a set of integer points
     * @return ArrayImg holding a binary image of the region.
     */
    public static Img<UnsignedByteType> image(Region region){
        final ImgFactory< UnsignedByteType > factory = new ArrayImgFactory<>(new UnsignedByteType(0));
        ArrayImg<UnsignedByteType, ByteArray> img = (ArrayImg<UnsignedByteType, ByteArray>)factory.create(region.getDimensions());
        RandomAccess<UnsignedByteType> r = img.randomAccess();
        double[] lc = region.getLowCorner();
        int lx = (int)lc[0];
        int ly = (int)lc[1];
        int lz = (int)lc[2];

        for(int[] pt: region.getPoints()){
            UnsignedByteType bt = r.setPositionAndGet(pt[0] - lx, pt[1] - ly, pt[2] - lz);
            bt.set(1);
        }
        return img;
    }

    /**
     * This will separate a labelled image, this will perform a marching cubes algorithm
     * and return the corresponding meshes.
     * @param stack
     * @return
     */
    public static List<DeformableMesh3D> fromLabels(MeshImageStack stack){
        MeshDetector detector = new MeshDetector(stack);
        List<Region> regions = detector.getRegionsFromLabelledImage();
        ImageStack space = new ImageStack(stack.getWidthPx(), stack.getHeightPx());
        for(int i = 0; i<stack.getNSlices(); i++){
            space.addSlice(new ShortProcessor(stack.getWidthPx(), stack.getHeightPx()));
        }
        int label = 1;
        List<Region> shorties = new ArrayList<>();
        for(Region r : regions){
            for(int[] pt : r.getPoints()){
                space.getProcessor(pt[2] + 1 ).set(pt[0], pt[1], label);
            }
            shorties.add(new Region(label++, r.getPoints()));
        }
        new ImagePlus("debug", space).show();
        RegionGrowing rg = new RegionGrowing(space, space);
        rg.setRegions(shorties);

        //Removes topological errors that cannot be handled.
        rg.erode();
        rg.dilate();

        ImageSpaceTransformer ist = new ImageSpaceTransformer(stack);

        List<DeformableMesh3D> meshes = new ArrayList<>();
        for(Region r: shorties){
            r.validate();
            Img<UnsignedByteType> img = image(r);

            ist.update(r);

            Mesh mesh = removeDuplicateVertices(
                    MarchingCubesRealType.calculate(img, 1), ist );

            if(mesh != null) {
                for(Mesh bm : MeshConnectedComponents.iterable(mesh)){
                    DeformableMesh3D dm3d = convertMesh(bm, ist);
                    if(dm3d.calculateVolume() > 0){
                        meshes.add(dm3d);
                    }
                }
            }
        }
        return meshes;
    }

    /**
     * This is currently designed to use from a distance transform. It performs a threshold
     * then a connected components algorithm, then a region growing/watershed.
     *
     * @param mis contains the image and geometry data.
     * @return meshing in normalized coordinates to the provided image.
     */
    public static List<DeformableMesh3D> guessMeshes(MeshImageStack mis){
        ImagePlus frame = mis.getCurrentFrame();
        ImageSpaceTransformer ist = new ImageSpaceTransformer(mis);

        ImageStack old = frame.getStack();
        ImageStack stack = new ImageStack(frame.getWidth(), frame.getHeight());

        List<DeformableMesh3D> meshes = new ArrayList<>();
        for(int j = 1; j<old.size(); j++){
            ImageProcessor p = old.getProcessor(j).convertToShort(false).duplicate();
            p.threshold(3);
            stack.addSlice(p);
        }
        List<Region> regions = ConnectedComponents3D.getRegions(stack);

        ImageStack space = new ImageStack(frame.getWidth(), frame.getHeight());
        for(int j = 1; j<old.size(); j++){
            ImageProcessor p = old.getProcessor(j).convertToShort(false).duplicate();
            p.threshold(0);
            space.addSlice(p);
        }

        RegionGrowing rg = new RegionGrowing(stack, space);
        rg.setRegions(regions);

        while(rg.getFrontierSize()>0){
            rg.step();
        }

        //Removes topological errors that cannot be handled.
        rg.erode();
        rg.dilate();

        for(Region r: regions){
            r.validate();
            Img<UnsignedByteType> img = image(r);

            Mesh mesh = MarchingCubesRealType.calculate(img, 1);

            ist.update(r);

            mesh = removeDuplicateVertices(mesh, ist);
            if(mesh != null) {
                for(Mesh bm : MeshConnectedComponents.iterable(mesh)){
                    DeformableMesh3D dm3d = convertMesh(bm, ist);
                    if(dm3d.calculateVolume() > 0){
                        List<DeformableMesh3D> checked = topoCheck(dm3d);
                        meshes.addAll(checked);
                    }
                }
            }
        }

        return meshes;
    }

    public static List<DeformableMesh3D> topoCheck(DeformableMesh3D mesh){
        TopoCheck tc = new TopoCheck(mesh);
        List<DeformableMesh3D> m2 = new ArrayList<>();
        try {
            m2.addAll(tc.repairMesh());
        } catch(Exception e){

            Track t = new Track("special");
            t.addMesh(0, mesh);
            List<Track> temp = new ArrayList<>();
            temp.add(t);
            try {
                MeshWriter.saveMeshes(new File("topo-exeception.bmf"), temp);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            TopoCheck top2 = new TopoCheck(mesh);
            top2.repairMesh();

            e.printStackTrace();
        }
        return m2;
    }


    public static void main(String[] args) throws IOException {
        new ImageJ();
        ImagePlus plus = FileInfoVirtualStack.openVirtual(new File(args[0]).getAbsolutePath());
        MeshImageStack mis = new MeshImageStack(plus);

        MeshFrame3D mf3d = new MeshFrame3D();
        mf3d.showFrame(true);
        mf3d.addLights();
        mf3d.setBackgroundColor(new Color(200, 200, 200));


        List<Track> broken = new ArrayList<>();
        ImageStack stack = null;

        for(int i = 0; i < mis.getNFrames(); i++){
            mis.setFrame(i);
            long start = System.currentTimeMillis();
            List<DeformableMesh3D> meshes = Imglib2Mesh.guessMeshes(mis);
            mf3d.clearTransients();
            System.out.println("first pass: " + (System.currentTimeMillis() - start));

            for(DeformableMesh3D dm3d : meshes){
                List<TopologyValidationError> errors = TopoCheck.validate(dm3d);
                if(errors.size() > 0){
                    String name = errors.stream().map(Object::toString).collect(Collectors.joining(" "));
                    Track t = new Track(name);
                    t.addMesh(i, dm3d);
                    broken.add(t);
                } else{
                    try {
                        ConnectionRemesher remedy = new ConnectionRemesher();
                        remedy.setMinAndMaxLengths(0.005, 0.01);
                        dm3d = remedy.remesh(dm3d);
                    } catch(Exception e){
                        String name = "Passed TOPOCHECK" + e.getMessage();
                        Track t = new Track(name);
                        t.addMesh(i, dm3d);
                        broken.add(t);
                    }
                }

                Color c = ColorSuggestions.getSuggestion();

                dm3d.setShowSurface(false);
                dm3d.create3DObject();
                dm3d.data_object.setShowSurface(true);
                dm3d.data_object.setColor(c);
                dm3d.data_object.setWireColor(c);
                mf3d.addTransientObject(dm3d.data_object);

            }
            ImageProcessor proc = new ColorProcessor(mf3d.snapShot());
            if(stack == null){
                stack = new ImageStack(proc.getWidth(), proc.getHeight());
            }
            stack.addSlice(proc);
        }

        new ImagePlus("Snapshots", stack).show();


        if(broken.size() > 0){
            System.out.println(broken.size() + " broken meshes saved to broken.bmf");
            MeshWriter.saveMeshes(new File("broken.bmf"), broken);
        } else{
            System.out.println("File successfully converted!");
        }
    }
}
