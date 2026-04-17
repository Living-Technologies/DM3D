package deformablemesh.experimental;

import deformablemesh.gimli2b.MeshImageStack2;
import deformablemesh.io.LoadZarr;

import java.io.IOException;
import java.nio.file.Paths;

public class DeformBenchMark {

    public static void main(String[] args) throws IOException {
        MeshImageStack2<?> mist = LoadZarr.loadMeshImageStack2(Paths.get("/Users/msmith5/working/zeiss-trip-2026-4-15/LLS7-16-07-52/LLS7-16-07-52.zarr"));

    }
}
