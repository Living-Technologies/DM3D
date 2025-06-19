package deformablemesh.geometry;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import deformablemesh.MeshDetector;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.interceptable.InterceptingMesh3D;
import deformablemesh.io.MeshReader;
import deformablemesh.track.Track;
import deformablemesh.util.Vector3DOps;
import deformablemesh.util.connectedcomponents.Region;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MeshCroppingTool {

    static class PrincipleAxes{
        double[] e0, e1, e2;
        double[] cm;

        public PrincipleAxes(double[] cm, double[] e0, double[] e1, double[] e2) {
            this.cm = cm;
            this.e0 = e0;
            this.e1 = e1;
            this.e2 = e2;
        }
    }

    private PrincipleAxes getPrincipleAxis(Region r, double[] pxSizes){
        double Ixx = 0;
        double Ixy = 0;
        double Ixz = 0;
        double Iyy = 0;
        double Iyz = 0;
        double Izz = 0;

        double IxxCm = 1.0/12.0*(pxSizes[1]*pxSizes[1] + pxSizes[2]*pxSizes[2]);
        double IyyCm = 1.0/12.0*(pxSizes[0]*pxSizes[0] + pxSizes[2]*pxSizes[2]);
        double IzzCm = 1.0/12.0*(pxSizes[1]*pxSizes[1] + pxSizes[0]*pxSizes[0]);
        double[] cm = new double[3];

        int n = r.getPoints().size();
        for(int[] xyz : r.getPoints()){
            cm[0] += xyz[0];
            cm[1] += xyz[1];
            cm[2] += xyz[2];
        }
        cm[0] = cm[0]/n;
        cm[1] = cm[1]/n;
        cm[2] = cm[2]/n;
        for(int[] xyz : r.getPoints()){
            double z = xyz[2];
            double y = xyz[1];
            double x = xyz[0];
            Ixx += (y*y + z*z) + IxxCm;
            Ixy += -x*y;
            Ixz += -x*z;
            Iyy += (x*x + z*z) + IyyCm;
            Iyz += -y*z;
            Izz += (x*x + y*y) + IzzCm;
        }
        double[] I = new double[]{
                Ixx, Ixy, Ixz,
                Ixy, Iyy, Iyz,
                Ixz, Iyz, Izz
        };

        Matrix mat = new Matrix(I, 3);
        EigenvalueDecomposition ed = mat.eig();
        List<double[]> values = new ArrayList<>(4);
        double[] ev = ed.getRealEigenvalues();
        double[] vecs = ed.getV().getColumnPackedCopy();
        for(int i = 0; i<3; i++){
            double[] vec = new double[3];
            for(int j = 0; j<3; j++){
                vec[j] = vecs[3*i + j];
            }
            Vector3DOps.normalize(vec);
            values.add(vec);
        }
        values.add(ev);
        PrincipleAxes pa = new PrincipleAxes(cm, values.get(0), values.get(1), values.get(2));

        return pa;

    }
    int size = 64;

    public MeshImageStack getCroppedMesh(DeformableMesh3D mesh, MeshImageStack stack){
        BinaryMomentsOfInertia bmi = new BinaryMomentsOfInertia(mesh, stack);
        List<double[]> eigen = bmi.getEigenVectors();
        double[] cm = bmi.getCenterOfMass();
        cm[0] = cm[0] - stack.offsets[0];
        cm[1] = cm[1] - stack.offsets[1];
        cm[2] = cm[2] - stack.offsets[2];

        System.out.println("cm: " + Arrays.toString(cm));
        double[] e0 = eigen.get(0);
        double[] e1 = eigen.get(1);
        double[] e2 = eigen.get(2);
        PrincipleAxes pa = new PrincipleAxes(cm, e0, e1, e2);

        ImagePlus plus = crop(pa, new MeshImageStack(bmi.binary), stack, 255);
        MeshImageStack box = new MeshImageStack(plus);
        return box;
    }
    static ImagePlus cropMeshImages(List<Track> tracks, MeshImageStack mist){
        MeshCroppingTool mct = new MeshCroppingTool();
        int count = 0;
        ImageStack stackers = new ImageStack(mct.size, mct.size);
        ImagePlus plus = null;
        for(Track t: tracks){
            for(Integer key : t.getTrack().keySet()){
                DeformableMesh3D mesh = t.getMesh(key);
                MeshImageStack mopd = mct.getCroppedMesh(mesh, mist);
                if(mopd == null){
                    continue;
                }
                ImageStack made = mopd.getOriginalPlus().getStack();
                if(plus == null){
                    plus = mopd.getOriginalPlus();
                }
                for(int i = 1; i <= made.size(); i++){
                    stackers.addSlice(made.getProcessor(i));
                }
                count += 1;
            }
        }
        plus.setStack(stackers, 2, mct.size, count);
        plus.setOpenAsHyperStack(true);
        plus.show();
        return plus;
    }

    ImagePlus crop(PrincipleAxes pa, MeshImageStack lbls, MeshImageStack stack, int label){
        double ds = stack.getMinPx()*2;
        double l = ds*size;
        ImageStack crop = new ImageStack(size, size);
        double[] cm = pa.cm;
        double[] e0 = pa.e0;
        double[] e1 = pa.e1;
        double[] e2 = pa.e2;

        double oz = cm[2] - l/2*e0[2] - l/2*e1[2] - l/2*e2[2];
        double oy = cm[1] - l/2*e0[1] - l/2*e1[1] - l/2*e2[1];
        double ox = cm[0] - l/2*e0[0] - l/2*e1[0] - l/2*e2[0];
        for( int i = 0; i<size; i++){
            ImageProcessor cp = new FloatProcessor(size, size);
            ImageProcessor mp = new ByteProcessor(size, size);
            for(int j = 0; j<size; j++){
                for(int k = 0; k<size; k++){
                    double z = oz + i*ds*e0[2] + j*ds*e1[2]+ k*ds*e2[2];
                    double y = oy + i*ds*e0[1] + j*ds*e1[1]+ k*ds*e2[1];
                    double x = ox + i*ds*e0[0] + j*ds*e1[0]+ k*ds*e2[0];
                    double[] r = {x, y, z};
                    float f = (float)stack.getInterpolatedValue(r);
                    cp.setf(k, j, f);
                    /*double dbgz = ic[2];
                    if(dbgz < stack.getNSlices()){
                        int zz = (int)dbgz;
                        if(zz >= 0){
                            ImageProcessor proc = stack.getProcessor(0,0, zz);
                            int xx = (int)ic[0];
                            int yy = (int)ic[1];
                            if(xx >= 0 && xx<proc.getWidth() && yy >= 0 && yy < proc.getHeight()){
                                proc.set(xx, yy, 0xffff);
                            }

                        }
                    }*/
                    if(stack.contains(r)) {
                        double[] ic = stack.getImageCoordinates(r);
                        int ix = (int) ic[0];
                        int iy = (int) ic[1];
                        int iz = (int) ic[2];
                        double v = lbls.getValue(ix, iy, iz);
                        int px = lbls.getOriginalPlus().getStack().getProcessor(iz+1).get(ix, iy);
                        System.out.println(v + ", " + px);
                        if (v != 0) {
                            mp.set(k, j, 1);
                        }
                    }
                }
            }
            crop.addSlice(cp);
            crop.addSlice(mp);
        }

        ImagePlus plus = new ImagePlus();
        Calibration cal = plus.getCalibration();
        cal.pixelDepth = ds;
        cal.pixelWidth = ds;
        cal.pixelHeight = ds;
        plus.setCalibration(cal);
        plus.setStack(crop, 2, size, 1);
        plus.setOpenAsHyperStack(true);
        return plus;
    }
    public ImagePlus cropLabelledImage(MeshImageStack lbls, MeshImageStack image){
        MeshDetector md = new MeshDetector(lbls);
        List<Region> regions = md.getRegionsFromLabelledImage();
        ImagePlus plus = null;
        for(Region r : regions){
            PrincipleAxes pa = getPrincipleAxis(r, image.pixel_dimensions);
            ImagePlus plus0 = crop(pa, lbls, image, r.getLabel());
            if(plus == null){
                plus = plus0;
            } else{
                ImageStack stack = plus0.getStack();
                ImageStack pop = plus.getStack();
                for(int i = 1; i<=stack.size(); i++){
                    pop.addSlice(stack.getProcessor(i));
                };
            }
        }
        return plus;

    }
    public static void main(String[] args) throws IOException {
        new ImageJ();
        File tf = new File(args[0]);
        System.out.println(tf);
        List<Track> tracks = MeshReader.loadMeshes(tf);
        MeshImageStack mist = new MeshImageStack(Paths.get(args[1]));
        ImagePlus fin = cropMeshImages(tracks, mist);
        fin.show();
    }


}
