package deformablemesh.io;

import bdv.cache.SharedQueue;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import deformablemesh.gimli2b.MeshImageStack2;
import deformablemesh.gimli2b.MeshImageStack2UB;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import org.embl.mobie.io.imagedata.N5ImageData;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5MetadataUtils;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMultiscaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.AxisMetadata;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LoadZarr {
    public static <T extends NumericType<T> & NativeType<T>> MultiscaleImageAdapter<T> load3DZarrFile(String location) throws IOException {
        N5Factory factory = new N5Factory();
        N5Reader reader = factory.openReader(location);
        N5Metadata rootMetadata = N5MetadataUtils.parseMetadata(reader, "/");
        List<Axis> axes = new ArrayList<>();
        List<double[]> scales = new ArrayList<>();
        List<double[]> offsets = new ArrayList<>();

        if(rootMetadata instanceof org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata){
            org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata meta = (org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata) rootMetadata;
            org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata[] o = meta.getChildrenMetadata();
            org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata axis = o[0];
            Axis[] ngff_axis = axis.getAxes();
            for(Axis a : ngff_axis){
                axes.add(a);
            }
            for(org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata ax : o){
                scales.add(ax.getScale());
                offsets.add(ax.getTranslation());
            }
        }
        MultiscaleImageAdapter<T> adapter = new MultiscaleImageAdapter<>(axes);
        String[] sets = reader.deepListDatasets("/");
        for(int i = 0; i<sets.length; i++){
            String s = sets[i];
            CachedCellImg<T, ?> cachedCellImg = N5Utils.open(reader, s);
            int dex = adapter.addResolutionData(cachedCellImg);
            adapter.addDataSetLabel(dex, s);
            adapter.addResolutionTransforms(scales.get(i), offsets.get(i));
        }


        adapter.setTitle(location);
        return adapter;
    }


    public static <T extends NumericType<T> & NativeType<T> & RealType<T> > MeshImageStack2<T> mobieLoad(String location){
        N5ImageData<T> data = new N5ImageData<>(location);
        data.getSourcesAndConverters();

        List<Source<T>>sources = new ArrayList<>(data.getNumDatasets());
        for(int i = 0; i<data.getNumDatasets(); i++){
            Pair<Source<T>, Source<? extends Volatile<T>>> pair = data.getSourcePair(i);
            sources.add(pair.getA());
        }

        Source<T> src = sources.get(0);
        RandomAccessibleInterval<T> rai = src.getSource(0,0);

        long[] dims = rai.dimensionsAsLongArray();
        System.out.println(Arrays.toString(dims));

        MeshImageStack2<T> mist;
        mist = new MeshImageStack2UB<>(sources);
        mist.setShortTitle(location);

        for(int i = 0; i<mist.getNMipMaps(); i++) {
            MeshImageStack2<?> m2 = mist.getUBMipMap(i);
            Calibration ij = m2.getImageJCalibration();
            System.out.println(m2.getWidthPx() + ", " + m2.getHeightPx() + ", " + m2.getNSlices() +
                    ", " + ij + ", " + ij.xOrigin + ", " + ij.yOrigin + ", " + ij.zOrigin);
        }
        return mist;
    }

    public static <T extends NumericType<T> & NativeType<T> & RealType<T> > MeshImageStack2<T> loadMeshImageStack2dep(Path location) throws IOException {
        MeshImageStack2<T> mist;
        try{
            mist = mobieLoad(location.toString());
        } catch(Exception e){
            throw new IOException("Could not load zarr: " + location, e);
        }
        return mist;
    }

    public static <T extends NumericType<T> & NativeType<T> & RealType<T> > MeshImageStack2<T> loadMeshImageStack2(Path location) throws IOException {

        MultiscaleImageAdapter<T> msia = load3DZarrFile(location.toAbsolutePath().toString());
        List<Source<T>> sources = new ArrayList<>();
        for(int i = 0; i<msia.getNChannels(); i++){
            sources.add(msia.getAsBdvSource(i));
        }
        RandomAccessibleInterval<T> rai = msia.images.data.get(0);
        long px = rai.dimension(0)*rai.dimension(1)*rai.dimension(2);
        MeshImageStack2<T> mist;
        if (px < Integer.MAX_VALUE) {
            mist = new MeshImageStack2<>(sources);
            Calibration cb = mist.getImageJCalibration();
            cb.setTimeUnit(msia.getTimeUnit());
            cb.frameInterval = msia.getTimeInterval();
        } else{
            System.out.println("no buffer!");
            mist = new MeshImageStack2UB<>(sources);
        }

        mist.setShortTitle(location.getFileName().toString());
        Calibration ij = mist.getImageJCalibration();
        msia.calibrateUnits(ij);
        return mist;
    }

    public static <T extends NumericType<T> & NativeType<T> & RealType<T>> MeshImageStack2<T> loadMeshImageStack2(URI uri) throws IOException {
        MultiscaleImageAdapter<T> msia = load3DZarrFile(uri.toString());
        List<Source<T>> sources = new ArrayList<>();
        for(int i = 0; i<msia.getNChannels(); i++){
            sources.add(msia.getAsBdvSource(i));
        }
        RandomAccessibleInterval<T> rai = msia.images.data.get(0);
        int n = rai.numDimensions();
        long px = rai.dimension(0)*rai.dimension(1)*rai.dimension(2);

        MeshImageStack2<T> mist;
        if (px < Integer.MAX_VALUE) {
            mist = new MeshImageStack2<>(sources);
        } else{
            System.out.println("no buffer!");
            mist = new MeshImageStack2UB<>(sources);
        }

        mist.setShortTitle(uri.getPath());
        Calibration ij = mist.getImageJCalibration();
        msia.calibrateUnits(ij);
        return mist;
    }

    public static <T extends NumericType<T> & NativeType<T>> List<Source<T>> load3DSource(String location ) throws IOException {
        MultiscaleImageAdapter<T> msia = load3DZarrFile(location);
        List<Source<T>> sources = new ArrayList<>();
        for(int i = 0; i<msia.getNChannels(); i++){
            sources.add(msia.getAsBdvSource(i));
        }
        return sources;

    }

    public static List<ImagePlus> load3DStackFromZarrFile(String location) throws IOException {
        MeshImageStack2<?> mist = loadMeshImageStack2(Paths.get(location));
        List<ImagePlus> pluses = new ArrayList<>();
        for(int i = 0; i<mist.getNMipMaps(); i++){
            pluses.add( mist.getMipMap(i).getOriginalPlus());
        }
        return pluses;
    }


    public static List<ImagePlus> load3DStackFromZarrFileOld( String location ) throws IOException {
        MultiscaleImageAdapter<?> images = load3DZarrFile(location);
        List<ImagePlus> pluses = new ArrayList<>();
        for(int i = 0; i<images.getMipMapLevels(); i++){
            pluses.add(images.getMipMapAsPlus(i));
        }
        return pluses;
    }

    public static void main(String[] args) throws IOException {
        //MultiscaleImageAdapter<?> adapter = load3DZarrFile("../../../working/zeiss-trip-2026-4-15/LLS7/p04-2026-04-16_07-49-01.zarr");
        MeshImageStack2<?> stack = loadMeshImageStack2(Paths.get("../../../working/zeiss-trip-2026-4-15/LLS7/p04-2026-04-16_07-49-01.zarr"));
    }
}
