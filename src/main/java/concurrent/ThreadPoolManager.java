package concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class ThreadPoolManager {
    private static final Logger LOGGER = Logger.getLogger(ThreadPoolManager.class.getName());
    private final ExecutorService executorService;  // The actual thread pool managing worker threads

    public ThreadPoolManager() {
        // Use available CPU cores for optimal parallelism
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        LOGGER.info("ThreadPool initialized with " + threadPoolSize + " threads");
    }

    public <T> List<T> executeTasksAndCollect(List<Callable<List<T>>> tasks)
            throws InterruptedException, ExecutionException {
        List<Future<List<T>>> futures = new ArrayList<>();

        // Submit all tasks to the thread pool for parallel execution
        for (Callable<List<T>> task : tasks) {
            futures.add(executorService.submit(task));
        }

        // Collect results from completed tasks
        List<T> allResult = new ArrayList<>();
        for (Future<List<T>> future : futures) {
            // Wait for each task to complete and get its results
            allResult.addAll(future.get());
        }
        return allResult;  // Combined results from all parallel tasks
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            // Give running tasks reasonable time to complete
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                // Force shutdown if tasks don't finish in time
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            // Handle interruption during shutdown
            executorService.shutdownNow();
            Thread.currentThread().interrupt();  // Preserve interrupt status
        }
        LOGGER.info("ThreadPool shutdown completed");
    }
}