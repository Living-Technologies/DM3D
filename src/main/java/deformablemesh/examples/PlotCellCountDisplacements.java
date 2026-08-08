package deformablemesh.examples;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.MeshReader;
import deformablemesh.track.Track;
import deformablemesh.util.Vector3DOps;
import lightgraph.DataSet;
import lightgraph.Graph;
import lightgraph.GraphPoints;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlotCellCountDisplacements {
    Graph displacementPlot;
    Graph cellCountPlot;
    double min = 0;
    double max = 10.0;
    int bins = 100;
    double delta = (max - min )/bins;

    double[] x = new double[bins];

    PlotCellCountDisplacements(){
        double delta = (max - min )/bins;
        for(int i = 0; i<x.length; i++){
            x[i] = min + (i + 0.5)*delta;
        }
        displacementPlot = new Graph();
        displacementPlot.setBackground(Color.BLACK);
        displacementPlot.setAxisColor(Color.WHITE);
        displacementPlot.setXTicCount(6);
        displacementPlot.setXRange(0, 10);
        displacementPlot.setXLabel("Displacement per frame.");
        displacementPlot.setYLabel("Displacement per frame.");
        cellCountPlot = new Graph();
        cellCountPlot.setBackground(Color.BLACK);
        cellCountPlot.setAxisColor(Color.WHITE);

    }

    public void plotCellCount(List<Track> tracks, MeshImageStack stack){
        double[] x = new double[ stack.getNFrames()];
        double[] y = new double[ stack.getNFrames()];
        for(int i = 0; i<stack.getNFrames(); i++){
            final int frame = i;
            x[i] = i + 1;
            y[i] = tracks.stream().filter(t->t.containsKey(frame)).count();
        }
        DataSet gp = cellCountPlot.addData(x, y);
        gp.setLabel("" + cellCountPlot.dataSetCount());


    }

    public void plotDisplacementHistogram(List<Track> tracks, MeshImageStack meshImageStack){
        double[] count = new double[bins];
        for(Track t: tracks) {
            if (t.size() < 2) {
                continue;
            }
            List<double[]> di = new ArrayList<>();
            for (int i = t.getFirstFrame(); i <= t.getLastFrame(); i++) {
                if (t.containsKey(i) && t.containsKey(i + 1)) {
                    DeformableMesh3D start = t.getMesh(i);
                    DeformableMesh3D fin = t.getMesh(i + 1);
                    double[] cs = DeformableMesh3DTools.centerAndRadius(start.nodes);
                    double[] cf = DeformableMesh3DTools.centerAndRadius(fin.nodes);
                    double d =
                            Vector3DOps.normalize(
                                    Vector3DOps.difference(cs, cf)
                            ) * meshImageStack.SCALE;

                    di.add(new double[]{i + 0.5, d});
                }
            }
            if (di.size() > 0) {

                for (double[] d : di) {
                    int dex = (int) ((d[1] - min) / delta);
                    if (dex == bins) {
                        dex = dex - 1;
                    }
                    if (dex < 0 || dex > bins) {
                        //throw it away.
                        continue;
                    }
                    count[dex] += 1;
                }

            }
        }

        DataSet histo = displacementPlot.addData(x, count);
        histo.setLabel("Tile " + displacementPlot.dataSetCount());

    }

    public void show(){
        Color[] colors = {
                new Color(255, 100, 100), new Color(100, 255, 255),
                new Color(100, 255, 255), new Color(255, 100, 100),
                new Color(100, 255, 255), new Color(255, 100, 100)
        };
        GraphPoints[] points = {
                GraphPoints.filledTriangles(), GraphPoints.crossX(), GraphPoints.crossPlus(),
                GraphPoints.filledCircles(), GraphPoints.outlinedTriangles(), GraphPoints.hollowCircles()
        };
        for(int z = 0; z<displacementPlot.dataSetCount(); z++){
            DataSet set = displacementPlot.getDataSet(z);
            set.setColor( colors[z]);
            set.setPoints(points[z]);
            DataSet counter = cellCountPlot.getDataSet(z);
            counter.setPoints(points[z]);
            counter.setColor(colors[z]);
        }
        displacementPlot.show(false, "histogram of displacements");
        cellCountPlot.show(false, "number of cells per frame");

    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.US);
        PlotCellCountDisplacements pccd = new PlotCellCountDisplacements();
        for(int i=1; i<=6; i++){
            String dna = "D:/working/dopm/dna-meshes/Tile_" + i + "_dna-aligned.bmf";
            //String dna = "D:/working/dopm/dna-meshes/Tile_" + i + "_dna.bmf";
            String img = "D:/working/dopm/binned-images/Tile_" + i + "_processed_binned-2b.tif";
            MeshImageStack stack = MeshImageStack.fromVirtualTiff(img);
            List<Track> tracks = MeshReader.loadMeshes(new File(dna));
            pccd.plotDisplacementHistogram(tracks, stack);
            pccd.plotCellCount(tracks, stack);
        }
        pccd.show();
    }

}
