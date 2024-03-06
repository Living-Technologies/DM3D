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

import org.jogamp.java3d.*;
import org.jogamp.vecmath.Point3f;
import org.jogamp.vecmath.Vector3f;
import org.jogamp.vecmath.Vector4f;

/**
 * Recreation of the 3D surface, such that the scale is set.
 *
 * Created by smithm3 on 23/02/18.
 */
public class Sizeable3DSurface  implements DataObject{

    int[] dimensions;
    //normalized step sizes values
    float XDIM, YDIM, ZDIM;

    //normals for texture generation
    private Vector4f X_PLANE,Y_PLANE,Z_PLANE;

    private BranchGroup BG;


    Shape3D plane;

    TransformGroup tg;

    Vector3f OFFSET;

    Appearance appear;

    BranchGroup surface;

    /**
     *
     * @param texture volume data
     * @param planes number of planes per axis.
     * @param lengths the length each axis represents.
     */
    public Sizeable3DSurface(Texture3D texture, int[] planes, double[] lengths){

        surface = new BranchGroup();
        this.dimensions = planes;
        //pla
        createSurfaces(lengths);

        setTexture(texture);

        OFFSET = getOffset();

        surface.addChild(plane);



    }



    public void createSurfaces(double[] lengths) {

        plane = new Shape3D();
        plane.setPickable(false);

        XDIM = (float)(lengths[0]);
        YDIM = (float)(lengths[1]);
        ZDIM = (float)(lengths[2]);

        X_PLANE = new Vector4f(1/XDIM,0,0,0);
        Y_PLANE = new Vector4f(0,1/YDIM,0,0);
        Z_PLANE = new Vector4f(0,0,1/ZDIM,0);


        prepareAppearance();
        prepareGeometry();

    }

    private void prepareAppearance() {

        PolygonAttributes p = new PolygonAttributes();
        p.setCullFace(PolygonAttributes.CULL_NONE);

        Material m = new Material();
        m.setLightingEnable(false);
        m.setCapability(Material.ALLOW_COMPONENT_WRITE);

        TexCoordGeneration texg = new TexCoordGeneration();
        texg.setFormat(TexCoordGeneration.TEXTURE_COORDINATE_3);
        //texg.setGenMode(TexCoordGeneration.EYE_LINEAR);
        texg.setPlaneS(X_PLANE);
        texg.setPlaneT(Y_PLANE);
        texg.setPlaneR(Z_PLANE);

        Appearance a = new Appearance();

        a.setTexCoordGeneration(texg);
        a.setMaterial(m);
        a.setPolygonAttributes(p);

        a.setTransparencyAttributes(
                new TransparencyAttributes(TransparencyAttributes.NICEST, 1.0f));

        a.setCapability(Appearance.ALLOW_TEXTURE_WRITE);

        appear = a;
        plane.setAppearance(a);
    }






    private void prepareGeometry(){

        plane.removeAllGeometries();

        generateGeometryX();
        generateGeometryY();
        generateGeometryZ();


    }

    /**
     * Generates geometry along X
     *
     * @param num - number of planes to generate
     */
    private void generateGeometryX() {
        float num = dimensions[0];
        float gridSpacing = 1f/num;
        float xs;
        for (float x = 0; x <= 1; x+=gridSpacing) {

            Point3f[] genCoords = new Point3f[4];
            xs = x*XDIM;

            genCoords[0] = new Point3f(xs,	0,	0);
            genCoords[1] = new Point3f(xs,	YDIM,	0);
            genCoords[2] = new Point3f(xs,	YDIM,	ZDIM);
            genCoords[3] = new Point3f(xs,	0,	ZDIM);

            QuadArray genSquare = new QuadArray(4, QuadArray.COORDINATES);
            genSquare.setCoordinates(0, genCoords);

            plane.addGeometry(genSquare);

        }
    }

    /**
     * Generates geometry along Y
     *
     * @param num - number of planes to generate
     */
    private void generateGeometryY() {
        float num = dimensions[1];
        float gridSpacing = 1f/num;
        float ys;
        for (float y = 0; y <= 1; y+=gridSpacing) {
            ys = y*YDIM;
            Point3f[] genCoords = new Point3f[4];

            genCoords[0] = new Point3f(0,	ys,	0);
            genCoords[1] = new Point3f(XDIM,	ys,	0);
            genCoords[2] = new Point3f(XDIM,	ys,	ZDIM);
            genCoords[3] = new Point3f(0,	ys,	ZDIM);

            QuadArray genSquare = new QuadArray(4, QuadArray.COORDINATES);
            genSquare.setCoordinates(0, genCoords);

            plane.addGeometry(genSquare);

        }
    }

    /**
     * Generates geometry along Z
     *
     * @param num - number of planes to generate
     */
    private void generateGeometryZ() {
        float num = dimensions[2];
        float gridSpacing = 1f/num;
        float zs;
        for (float z = 0; z <= 1; z+=gridSpacing) {

            Point3f[] genCoords = new Point3f[4];
            zs = z*ZDIM;
            genCoords[0] = new Point3f(0,	0,	zs);
            genCoords[1] = new Point3f(XDIM,	0,	zs);
            genCoords[2] = new Point3f(XDIM,	YDIM,	zs);
            genCoords[3] = new Point3f(0,	YDIM,	zs);

            QuadArray genSquare = new QuadArray(4, QuadArray.COORDINATES);
            genSquare.setCoordinates(0, genCoords);

            plane.addGeometry(genSquare);

        }
    }


    public Vector3f getOffset(){

        return new Vector3f(-XDIM/2, -YDIM/2, 0);
    }

    public void setTexture(Texture3D tex) {
        appear.setTexture(tex);
    }






    public BranchGroup getBranchGroup(){
        if(BG==null){
            BG = new BranchGroup();
            BG.setCapability(BranchGroup.ALLOW_DETACH);

            tg = new TransformGroup();
            tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
            Transform3D tt = new Transform3D();

            tt.setTranslation(OFFSET);



            tg.setTransform(tt);
            tg.addChild(surface);
            BG.addChild(tg);
        }

        return BG;

    }

}

