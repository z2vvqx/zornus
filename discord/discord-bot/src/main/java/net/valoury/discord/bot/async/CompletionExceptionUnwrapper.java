package net.valoury.discord.bot.async;

import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class CompletionExceptionUnwrapper {
    private CompletionExceptionUnwrapper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static Throwable unwrap(Throwable exception) {
        Throwable current = Objects.requireNonNull(exception, "Exception cannot be null");
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
