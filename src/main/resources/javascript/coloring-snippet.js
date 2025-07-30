/*
 Change this function to modify how the meshes will be color.
*/
function getColor( mesh ){
    value = mesh.calculateVolume();
    min = 1.0e-6
    max = 5.0e-5
    if( value < min ){
      return Color.BLACK;
    }
    if(value > max){
      return Color.WHITE;
    }
    scaled = (value - min)/(max - min);
    return new Color(scaled, scaled, scaled);
}

/*
    Function defined in load bindings, applies get color to
    every mesh in the current frame.
*/
applyToCurrentMeshes(getColor);
