package com.bigchaindb.smartchaindb.driver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class Promise {

    private static final ExecutorService SHARED_EXECUTOR = Executors.newCachedThreadPool();

    public static <T> CompletableFuture<List<Settlement<T>>> allSettled(Collection<Supplier<T>> tasks) {
        List<CompletableFuture<Settlement<T>>> futures = new ArrayList<>();
        for (Supplier<T> task : tasks) {
            futures.add(CompletableFuture.supplyAsync(task, SHARED_EXECUTOR)
                    .thenApply(Settlement::fulfilled)
                    .exceptionally(Settlement::rejected));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<Settlement<T>> settlements = new ArrayList<>();
                    for (CompletableFuture<Settlement<T>> f : futures) {
                        settlements.add(f.join());
                    }
                    return settlements;
                });
    }

    /**
     * Call this method after all asynchronous tasks have completed to terminate the executor.
     */
    public static void shutdown() {
        SHARED_EXECUTOR.shutdown();
        
        try {
            if (!SHARED_EXECUTOR.awaitTermination(60, TimeUnit.SECONDS)) {
                SHARED_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            SHARED_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
