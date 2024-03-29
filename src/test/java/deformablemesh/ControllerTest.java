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
package deformablemesh;

import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.RayCastMesh;
import deformablemesh.track.Track;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Created by msmith on 3/23/16.
 */
public class ControllerTest {
    @Test
    public void testAddingAndRemovingMeshes(){
        SegmentationModel model = new SegmentationModel();
        SegmentationController controls = new SegmentationController(model);
        DeformableMesh3D sphere = RayCastMesh.fiveTriangleSphere();
        controls.addMesh(sphere);

        waitFor(controls);

        Assert.assertTrue(model.hasSelectedMesh());
        Assert.assertNotNull(model.getSelectedMesh(model.getCurrentFrame()));
        Assert.assertNotNull(model.getSelectedTrack());
        Track track = model.getSelectedTrack();

        Assert.assertTrue(controls.canUndo());
        controls.undo();

        waitFor(controls);

        Assert.assertFalse(controls.canUndo());
        Assert.assertFalse(model.hasSelectedMesh());
        Assert.assertNull(model.getSelectedMesh(model.getCurrentFrame()));
        Assert.assertNull(model.getSelectedTrack());

        Assert.assertTrue(controls.canRedo());
        controls.redo();
        waitFor(controls);
        Assert.assertTrue(model.hasSelectedMesh());
        Assert.assertNotNull(model.getSelectedMesh(model.getCurrentFrame()));
        Assert.assertNotNull(model.getSelectedTrack());
        Assert.assertEquals(track, model.getSelectedTrack());

        DeformableMesh3D mesh2 = RayCastMesh.fiveTriangleSphere();
        DeformableMesh3D mesh3 = RayCastMesh.fiveTriangleSphere();

        controls.addMesh(1, mesh2);
        controls.startNewMeshTrack(1, mesh3);
        waitFor(controls);
        model.selectTrackWithMesh(mesh3);
        Track track2 = controls.model.getSelectedTrack();
        waitFor(controls);
        Assert.assertNotEquals(track, track2);

        List<Track> meshes = model.getAllTracks();
        int sum = 0;
        Assert.assertEquals(2, meshes.size());
        for(Track tk: meshes){

            sum += tk.getTrack().size();
        }
        Assert.assertEquals(3, sum);

        Assert.assertTrue(controls.canUndo());
        Assert.assertFalse(controls.canRedo());

        controls.undo();
        waitFor(controls);

        meshes = model.getAllTracks();
        sum = 0;
        Assert.assertEquals(1, meshes.size());
        for(Track t: meshes){
            sum += t.getTrack().size();
        }
        Assert.assertEquals(2, sum);

        Assert.assertTrue(controls.canUndo());
        Assert.assertTrue(controls.canRedo());
        controls.redo();
        waitFor(controls);

        meshes = model.getAllTracks();
        sum = 0;
        Assert.assertEquals(2, meshes.size());
        for(Track t: meshes){
            sum += t.getTrack().size();
        }
        Assert.assertEquals(3, sum);

        controls.undo();
        controls.undo();
        waitFor(controls);

        meshes = model.getAllTracks();
        sum = 0;
        Assert.assertEquals(1, meshes.size());
        for(Track t: meshes){
            sum += t.getTrack().size();
        }
        Assert.assertEquals(1, sum);

        controls.undo();
        Assert.assertFalse(controls.canUndo());
        Assert.assertTrue(controls.canRedo());

    }

    private List<Exception> waitFor(SegmentationController controller){
        synchronized (controller){
            controller.submit(()->{
                synchronized(controller){
                    controller.notifyAll();
                }
            });

            try {
                controller.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return Collections.emptyList();
            }

        }
        return Collections.emptyList();
    }
    @Test
    public void setValues(){
        SegmentationModel model = new SegmentationModel();
        SegmentationController controller = new SegmentationController(model);
        double a = -1;
        double b = -1;
        double g = -1;
        controller.setAlpha(a);
        controller.setBeta(b);
        controller.setGamma(g);
        waitFor(controller);
        Assert.assertEquals(a, model.ALPHA, 0);
        Assert.assertEquals(b, model.BETA, 0);
        Assert.assertEquals(g, model.GAMMA, 0);
        controller.stopRunning();

    }

    @Test
    public void remesh(){
        SegmentationModel model = new SegmentationModel();
        SegmentationController controller = new SegmentationController(model);
        DeformableMesh3D sphere = RayCastMesh.fiveTriangleSphere();
        controller.startNewMeshTrack(0, sphere);
        controller.reMesh();
        Assert.assertEquals(0, waitFor(controller).size());
        Assert.assertEquals(1, model.getAllTracks().size());

        controller.stopRunning();
    }
}
