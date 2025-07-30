package deformablemesh.experimental;

import deformablemesh.MeshImageStack;
import deformablemesh.io.SaveImageToZarr;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.FileInfoVirtualStack;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConvertFolderToZarr {

    public static void main(String[] args) throws Exception {
        Path p = Paths.get("D:\\working\\maria\\Jurica\\1-crops");
        Path out = p.getParent().resolve(p.getFileName().toString() + "-masks2.zarr");
        List<Path> images = new ArrayList<>();
        try(DirectoryStream<Path> all = Files.newDirectoryStream(p, "masks-*.tif")){
            all.forEach(images::add);
        }
        Pattern pat = Pattern.compile(".*-(\\d+)\\.tif");

        images.sort(Comparator.comparingInt(path->{
                    Matcher m = pat.matcher(path.getFileName().toString());
                    m.find();
                    return Integer.valueOf(m.group(1));
                }        ));
        for(Path img : images){
            System.out.println("writing: " + img.toString());
            ImagePlus plus = FileInfoVirtualStack.openVirtual(img.toAbsolutePath().toString());
            if (Files.exists(out)) {
                SaveImageToZarr.appendToZarr(plus, out);
            } else{
                SaveImageToZarr.saveToZarr(plus, out);
            }
        }

    }

}
