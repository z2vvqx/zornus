package net.valoury.bloodstone.server.service;

import net.valoury.shared.database.DatabaseDefaults;
import net.valoury.shared.database.DatabaseExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatabaseExecutorTest {

    @Test
    void saturationAndShutdownProduceFailedFutures() throws Exception {
        DatabaseExecutor executor = new DatabaseExecutor("test-database-", 1);
        CompletableFuture<Void> started = new CompletableFuture<>();
        CompletableFuture<Void> release = new CompletableFuture<>();
        CompletableFuture<Void> blocker = executor.run(() -> {
            started.complete(null);
            release.join();
        });
        started.get(2, TimeUnit.SECONDS);

        List<CompletableFuture<Void>> queued = new ArrayList<>();
        for (int index = 0; index < DatabaseDefaults.EXECUTOR_QUEUE_CAPACITY; index++) {
            queued.add(executor.run(() -> {
            }));
        }
        assertRejected(executor.run(() -> {
        }));

        release.complete(null);
        blocker.join();
        CompletableFuture.allOf(queued.toArray(CompletableFuture[]::new)).join();
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertRejected(executor.run(() -> {
        }));
    }

    @Test
    void shutdownDrainsContinuationFromRunningDatabaseTask() throws Exception {
        DatabaseExecutor executor = new DatabaseExecutor("test-database-drain-", 1);
        CompletableFuture<Void> started = new CompletableFuture<>();
        CompletableFuture<Void> release = new CompletableFuture<>();
        CompletableFuture<Integer> initial = executor.supply(() -> {
            started.complete(null);
            release.join();
            return 1;
        });
        CompletableFuture<Integer> chained = initial.thenCompose(value ->
                executor.supply(() -> value + 1));

        started.get(2, TimeUnit.SECONDS);
        executor.shutdown();
        assertRejected(executor.run(() -> {
        }));
        release.complete(null);

        assertEquals(2, chained.get(2, TimeUnit.SECONDS));
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }

    private void assertRejected(CompletableFuture<Void> future) {
        CompletionException completionException =
                org.junit.jupiter.api.Assertions.assertThrows(
                        CompletionException.class,
                        future::join
                );
        assertInstanceOf(RejectedExecutionException.class, completionException.getCause());
    }
}
