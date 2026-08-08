package deformablemesh;

import deformablemesh.gui.GuiTools;

import java.util.Queue;
import java.util.concurrent.*;

/**
 * Historical class, that should be replaced, developed because of confusion regarding the way ExecutorServices
 * handle exceptions
 *
 * Executables are submitted and if they're already running on the main thread, then they're short circuited otherwise
 * they're submitted to the main executor service, which puts them in the queue for execution.
 */
public class ExceptionThrowingService implements ETExecutor{

    ExecutorService main, monitor;
    Thread main_thread;
    Queue<Exception> exceptions = new LinkedBlockingDeque<>();
    ExceptionThrowingService(){
        main = Executors.newSingleThreadExecutor();
        monitor = Executors.newSingleThreadExecutor();
        main.submit(() -> {
            main_thread = Thread.currentThread();
            main_thread.setName("My Main Thread");
        });
    }

    private void execute(ETExecutable e){
        try{
            e.execute();
        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }
    }

    @Override
    public void submit(ETExecutable r){

        if(Thread.currentThread()==main_thread){
            execute(r);
            return;
        }

        final Future<?> f = main.submit(()->execute(r));

        monitor.submit(() -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                GuiTools.errorMessage(e.toString() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    public void shutdown(){
        main.shutdown();
        monitor.shutdown();
    }
}
