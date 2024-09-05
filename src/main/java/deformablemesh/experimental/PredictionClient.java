package deformablemesh.experimental;

import ij.ImagePlus;
import ij.ImageStack;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class PredictionClient implements AutoCloseable{
    final Socket socket;
    DataInputStream reading;
    DataOutputStream writing;
    ImagePlus cannon;
    public PredictionClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
    }
    public void start(int frames) throws IOException {
        writing = new DataOutputStream(socket.getOutputStream());
        InputStream in = socket.getInputStream();
        DataInputStream din = new DataInputStream(in);
        writing.writeInt(frames);
        reading = new DataInputStream(socket.getInputStream());
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }
    List<ImagePlus> getOutputs() throws IOException {

        int outputs = reading.readInt();
        System.out.println("reading: " + outputs + "outputs");
        List<ImagePlus> pluses = new ArrayList<>();
        for(int i = 0; i<outputs; i++){
            System.out.println("o - " + i);
            int c = reading.readInt();
            int w = reading.readInt();
            int h = reading.readInt();
            int s = reading.readInt();
            byte[] buffer = new byte[c*w*h*s*4];
            int read = 0;
            while(read < buffer.length){
                int r = reading.read(buffer, read, buffer.length - read);
                if(r<0) break;

                read += r;
            }
            ImagePlus op = FloatRunner.toImage(buffer, c, w, h, s, cannon);
            pluses.add(op);
        }
        System.out.println("finished reading");
        return pluses;
    }
    public void writeImage(ImagePlus plus) throws IOException {
        if( cannon == null ) cannon = plus;

        byte[] data = FloatRunner.getImageData(plus);
        writing.writeInt(plus.getNChannels());
        writing.writeInt(plus.getWidth());
        writing.writeInt(plus.getHeight());
        writing.writeInt(plus.getNSlices());
        writing.write(data);
    }
}
