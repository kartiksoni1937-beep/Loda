package odinclient.util;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ScannerExecutor {
    private static final Object pauseLock = new Object();
    private static volatile boolean paused;
    private static final ExecutorService scannerThread =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "OdinClient-Unified-Scanner");
                        thread.setDaemon(true);
                        thread.setPriority(1);
                        return thread;
                    });
    private static final ExecutorService pausableExecutor = new ScannerExecutor.PausableExecutor();

    private ScannerExecutor() {}

    public static ExecutorService getExecutor() {
        return pausableExecutor;
    }

    public static void setPaused(boolean value) {
        synchronized (pauseLock) {
            paused = value;
            if (!value) {
                pauseLock.notifyAll();
            }
        }
    }

    public static void awaitResume() {
        boolean interrupted = false;
        synchronized (pauseLock) {
            while (paused) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class PausableExecutor extends AbstractExecutorService {
        @Override
        public void execute(Runnable task) {
            ScannerExecutor.scannerThread.execute(
                    () -> {
                        ScannerExecutor.awaitResume();
                        task.run();
                    });
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
        }
    }
}
