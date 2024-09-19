/*-
 * #%L
 * Triangulated surface for deforming in 3D.
 * %%
 * Copyright (C) 2013 - 2023 University College London
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package deformablemesh.util.connectedcomponents;

import ij.ImageStack;

import java.util.*;
import java.util.stream.Collectors;

public class RegionGrowing{
    final List<short[]> labelPixels;
    final List<short[]> constraintPixels;
    final int width;
    final int height;
    final int slices;
    List<Region> regions;
    Map<Integer, List<int[]>> frontiers;

    public RegionGrowing(ImageStack labels, ImageStack constraints){
        width = labels.getWidth();
        height = labels.getHeight();
        slices = labels.getSize();
        labelPixels = new ArrayList<>();
        constraintPixels = new ArrayList<>();
        for(int i = 1; i<=labels.size(); i++){
            labelPixels.add((short[]) labels.getProcessor(i).getPixels());
            constraintPixels.add((short[]) constraints.getProcessor(i).getPixels());
        }
    }
    public void erode(){

        for(Region region: regions){
            List<int[]> edges = new ArrayList<>();
            Integer key = region.getLabel();
            List<int[]> points = region.getPoints();
            for(int[] pt: points){
                if(isFrontier(key, pt)){
                    edges.add(pt);
                }
            }
            for(int[] pt: edges){
                //ok because literally the same point.
                points.remove(pt);
                setLabel(pt, 0);
            }
        }
    }
    static class Point{
        final int x, y, z;
        final int[] xyz;
        public Point(int[] xyz){
            x = xyz[0];
            y = xyz[1];
            z = xyz[2];
            this.xyz = xyz;
        }

        @Override
        public boolean equals(Object p){
            Point o = (Point)p;
            return x == o.x && y == o.y && z == o.z;
        }
        @Override
        public int hashCode(){
            return x + y + z;
        }
    }
    public void dilate(){

        for(Region region: regions){
            List<int[]> edges = new ArrayList<>();
            Integer key = region.getLabel();
            List<int[]> points = region.getPoints();
            List<int[]> frontier = new ArrayList<>();
            for(int[] pt: points){
                if(isFrontier(key, pt)){
                    edges.add(pt);
                }
            }
            Set<Point> expand = new HashSet<>();
            for(int[] pt: edges){
                expand.addAll(freePossible(key, pt).stream().map(Point::new).collect(Collectors.toList()));
            }

            for(Point pt: expand){
                points.add(pt.xyz);
                setLabel(pt.xyz, region.label);
            }
        }
    }

    /**
     * Ignores the constraints.
     *
     * @param label
     * @param front
     * @return
     */
    List<int[]> freePossible(int label, int[] front){
        List<int[]> values = new ArrayList<>();
        for(int i = -1; i<=1; i++){
            int z = front[2] + i;
            if(z<0 || z>slices-1){
                continue;
            }
            for(int j = -1; j<=1; j++){
                int y = front[1] + j;
                if(y<0 || y>=height){
                    continue;
                }
                for(int k = -1; k<=1; k++){

                    if(i==0 && j == 0 && k == 0){
                        continue;
                    }

                    int x = front[0] + k;
                    if(x<0 || x>=width){
                        continue;
                    }
                    int l = getLabel(x, y, z);
                    if( l == 0){
                        values.add(new int[]{x, y, z});
                    }

                }
            }
        }
        return values;
    }



    public void setRegions(List<Region> regions){
        this.regions = regions;
        frontiers = new HashMap<>();
        for(Region region: regions){
            Integer key = region.getLabel();
            List<int[]> points = region.getPoints();
            List<int[]> frontier = new ArrayList<>();
            for(int[] pt: points){
                if(isFrontier(key, pt)){
                    frontier.add(pt);
                }
            }
            frontiers.put(key, frontier);
        }
    }

    /**
     * Get the pixels corresponding to slice i.
     *
     * @param i slice number from 1 to stack size.
     * @return
     */
    public short[] getLabelPixels(int i){
        return labelPixels.get(i-1);
    }

    /**
     * Finds the edge pixels of the region with the provided label.
     * @param label
     * @param xyz
     * @return
     */
    boolean isFrontier(int label, int[] xyz){
        for(int i = -1; i<=1; i++){
            int z = xyz[2] + i;
            if(z<0 || z>slices-1){
                continue;
            }
            for(int j = -1; j<=1; j++){
                int y = xyz[1] + j;
                if(y<0 || y>=height){
                    continue;
                }
                for(int k = -1; k<=1; k++){

                    if(i==0 && j == 0 && k == 0){
                        continue;
                    }

                    int x = xyz[0] + k;
                    if(x<0 || x>=width){
                        continue;
                    }
                    int l = getLabel(x, y, z);
                    if( l!= label){
                        return true;
                    }

                }
            }
        }
        return false;
    }

    List<int[]> possible(int label, int[] front){
        List<int[]> values = new ArrayList<>();
        for(int i = -1; i<=1; i++){
            int z = front[2] + i;
            if(z<0 || z>slices-1){
                continue;
            }
            for(int j = -1; j<=1; j++){
                int y = front[1] + j;
                if(y<0 || y>=height){
                    continue;
                }
                for(int k = -1; k<=1; k++){

                    if(i==0 && j == 0 && k == 0){
                        continue;
                    }

                    int x = front[0] + k;
                    if(x<0 || x>=width){
                        continue;
                    }
                    int l = getLabel(x, y, z);
                    if( l == 0 && isValid(x, y, z)){
                        values.add(new int[]{x, y, z});
                    }

                }
            }
        }
        return values;
    }
    public Region getRegion(Integer label){
        for(Region region: regions){
            if(region.getLabel()==label){
                return region;
            }
        }
        return null;
    }
    public void step(){
        for(Integer key: frontiers.keySet()){
            PixelSet set = new PixelSet();
            List<int[]> frontier = frontiers.get(key);
            for(int[] px: frontier){
                List<int[]> candidates = possible(key, px);
                for(int[] n: candidates){
                    set.add(n);
                }
            }
            frontier.clear();
            List<int[]> region = getRegion(key).getPoints();
            for(int[] px: set.original){
                setLabel(px, key);
                frontier.add(px);
                region.add(px);
            }

        }
    }

    public int getFrontierSize(){
        return frontiers.values().stream().mapToInt(List::size).sum();
    }
    public void setLabel(int[] xyz, int label){
        labelPixels.get(xyz[2])[xyz[0] + width*xyz[1]] = (short)label;
    }
    boolean isValid(int x, int y, int z){
        return constraintPixels.get(z)[x + y*width] != 0;
    }

    int getLabel(int x, int y, int z){
        return labelPixels.get(z)[x + y*width];
    }

}
