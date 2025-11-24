package concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class ThreadPoolManager {
    private static final Logger LOGGER = Logger.getLogger(ThreadPoolManager.class.getName());
    private final ExecutorService executorService;
    private final int threadPoolSize;

    public ThreadPoolManager() {
        this.threadPoolSize = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        LOGGER.info("ThreadPool initialized with " + threadPoolSize + " threads");
    }

    public ThreadPoolManager(int customSize) {
        this.threadPoolSize = customSize;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        LOGGER.info("ThreadPool initialized with " + threadPoolSize + " threads");
    }
    public <T> List<T> executeTasksAndCollect(List<Callable<List<T>>> tasks)
        throws InterruptedException, ExecutionException {
        List<Future<List<T>>> futures = new ArrayList<>();

        for (Callable<List<T>> task : tasks) {
            futures.add(executorService.submit(task));
        }
        List<T> allResult = new ArrayList<>();
        for (Future<List<T>> future : futures) {
            allResult.addAll(future.get());
        }
        return allResult;
    }
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("ThreadPool shutdown completed");
    }
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
}
