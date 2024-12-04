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
package deformablemesh.meshview;

import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import org.jogamp.java3d.Appearance;
import org.jogamp.java3d.Material;
import org.jogamp.java3d.PolygonAttributes;
import org.jogamp.java3d.Shape3D;
import org.jogamp.java3d.TexCoordGeneration;
import org.jogamp.java3d.TransparencyAttributes;
import org.jogamp.vecmath.Vector4f;

import java.awt.Color;

/**
 * A volume
 */
public class TexturedPlaneDataObject extends DeformableMeshDataObject {
    private Color volumeColor = Color.WHITE;
    Appearance texturedAppearance;
    MeshImageStack stack;
    int[] sizes;
    double[] lengths;
    double[] offsets;
    MultiChannelVolumeTexture volume;
    float min = 0;
    float max = 1;
    final int textureIndex;
    public TexturedPlaneDataObject(DeformableMesh3D mesh, MeshImageStack stack){
        super(mesh.nodes, mesh.connections, mesh.triangles, mesh.positions, mesh.connection_index, mesh.triangle_index);
        offsets = new double[]{ stack.offsets[0], stack.offsets[1], stack.offsets[2]};
        texturedAppearance = createTexturedSurface();
        surface_object.setAppearance(texturedAppearance);

        this.stack = stack;
        int[] dims = {stack.getWidthPx(), stack.getHeightPx(), stack.getNSlices()};

        volume = new MultiChannelVolumeTexture(dims);
        textureIndex = volume.addChannel(stack::getValue, min, max, DataCanvas.getComponents(volumeColor));

        surface_object.setAppearance(createTexturedSurface());
        branch_group.removeChild(mesh_object);
    }
    private Appearance hiddenSurface() {
        Appearance a = new Appearance();
        a.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.SCREEN_DOOR, 1f));
        return a;
    }


    private Appearance createTexturedSurface(){
        //MultiChannelVolumeTexture texture = new MultiChannelVolumeTexture(texture_data, min, max, DataCanvas.getComponents(Color.WHITE));

        TexCoordGeneration texCGen = new TexCoordGeneration();
        texCGen.setFormat(TexCoordGeneration.TEXTURE_COORDINATE_3);

        double xf = stack.getWidthPx()*stack.pixel_dimensions[0];
        double yf = stack.getHeightPx()*stack.pixel_dimensions[1];
        double zf = stack.getNSlices()*stack.pixel_dimensions[2];
        double longest = xf > yf ?
                zf > xf ? zf : xf :
                zf > yf ? zf : yf;

        xf = longest/xf;
        yf = longest/yf;
        zf = longest/zf;
        Vector4f xPlane = new Vector4f((float)xf, 0, 0, (float)(offsets[0]*xf));
        Vector4f yPlane = new Vector4f(0, -(float)yf, 0, (float)(offsets[1]*yf));
        Vector4f zPlane = new Vector4f(0, 0, (float) zf, (float)(offsets[2]*zf));

        texCGen.setPlaneS(xPlane);
        texCGen.setPlaneT(yPlane);
        texCGen.setPlaneR(zPlane);
        texCGen.setPlaneQ(new Vector4f(0, 0, 1, 0));

        Appearance appear = new Appearance();

        appear.setCapability(Appearance.ALLOW_TEXTURE_WRITE);

        appear.setTexCoordGeneration(texCGen);

        appear.setTexture(volume);

        PolygonAttributes p = new PolygonAttributes();
        p.setCullFace(PolygonAttributes.CULL_NONE);

        Material material = new Material();
        //material.setAmbientColor(new Color3f(0f,0.3f,0.3f));
        material.setLightingEnable(false);
        appear.setMaterial(material);
        appear.setPolygonAttributes(p);

        return appear;
    }

    public void updateVolume(){
        volume.updateTextureData(textureIndex, stack::getValue, min, max, DataCanvas.getComponents(volumeColor));

    }

    public void setShowSurface(boolean showSurface) {
        if(showSurface){
            surface_object.setAppearance(texturedAppearance);
        } else{
            surface_object.setAppearance(hiddenSurface());
        }
    }


    public  Shape3D getShape() {
        return surface_object;
    }
}
