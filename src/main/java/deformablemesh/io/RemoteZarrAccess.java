package deformablemesh.io;

import com.google.gson.GsonBuilder;
import org.janelia.saalfeldlab.n5.HttpKeyValueAccess;
import org.janelia.saalfeldlab.n5.KeyValueAccess;
import org.janelia.saalfeldlab.n5.N5KeyValueReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.StorageFormat;
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueReader;

import java.net.HttpURLConnection;
import java.net.URI;

public class RemoteZarrAccess {

    public static void main(String[] args){

        final String baseUrl = "https://static.webknossos.org/data/l4_sample/color/";
        String url = "http://localhost:8000/cxyzt.zarr";
        final HttpKeyValueAccess kva = new HttpKeyValueAccess(){
            @Override
            public boolean isFile(String p){
                System.out.println(p);
                return super.isFile(p);
            }
        };

        try (final ZarrKeyValueReader zarr = new ZarrKeyValueReader(kva, baseUrl, new GsonBuilder(), false, false, false)) {
            System.out.println(zarr.getVersion());
        }


        N5Factory factory = new N5Factory(){
            @Override
            public KeyValueAccess getKeyValueAccess(final URI uri){
                return kva;
            }

        };

        try( N5Reader reader = factory.openReader(baseUrl) ){

        }
    }
}
