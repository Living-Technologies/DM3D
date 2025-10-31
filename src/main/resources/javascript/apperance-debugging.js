FloatArray = Java.type("float[]");
importer("org.jogamp.java3d.Material");
importer("org.jogamp.vecmath.Color3f");
importer("org.jogamp.java3d.Appearance");
function clamp(f){
        if(f<0){
            return 0.0;
        } else if(f>1){
            return 1.0;
        } else{
            return f;
        }
    }

function flay( x, y, z){
    var arr = new FloatArray(3);
    arr[0] = x;
    arr[1] = y;
    arr[2] = z;
    return arr;
}

function adjust(c,  v){
        if(v<0) return flay(0, 0, 0);
        if(v<1){
            return flay(c[0]*v, c[1]*v, c[2]*v);
        }
        return flay(
                clamp(c[0] + (v-1)),
                clamp(c[1] + (v-1)),
                clamp(c[2] + (v-1))
        );
    }


mesh = controls.getSelectedMesh();
dobj = mesh.data_object;
color = mesh.getColor();
app = new Appearance();
a = new Appearance();
rgb = color.getRGBComponents(new FloatArray(4));

ambient = new Color3f(adjust(rgb, 1));
emmisive = new Color3f(adjust(rgb, 0.0));
diffuse = new Color3f(adjust(rgb, 1));
specular = new Color3f(adjust(rgb, 10));

shininess = 128;

mat = new Material(
                ambient,
                emmisive,
                diffuse,
                specular,
                shininess);
mat.setLightingEnable( true);
mat.setColorTarget( 4 );
a.setMaterial(mat);
dobj.setSurfaceAppearance( a )
