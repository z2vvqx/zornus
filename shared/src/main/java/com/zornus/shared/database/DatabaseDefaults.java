package com.zornus.shared.database;

public final class DatabaseDefaults {

    public static final long CONNECTION_ACQUISITION_TIMEOUT_MILLISECONDS = 2_000;
    public static final long CONNECTION_VALIDATION_TIMEOUT_MILLISECONDS = 1_000;
    public static final int CONNECTION_ESTABLISHMENT_TIMEOUT_SECONDS = 5;
    public static final int SOCKET_READ_TIMEOUT_SECONDS = 30;
    public static final int CANCEL_SIGNAL_TIMEOUT_SECONDS = 2;
    public static final int STATEMENT_TIMEOUT_MILLISECONDS = 5_000;
    public static final int LOCK_TIMEOUT_MILLISECONDS = 1_000;
    public static final int IDLE_TRANSACTION_TIMEOUT_MILLISECONDS = 5_000;
    public static final int TRANSACTION_TIMEOUT_MILLISECONDS = 10_000;
    public static final int EXECUTOR_QUEUE_CAPACITY = 100;

    public static final String POSTGRESQL_SESSION_OPTIONS = String.join(" ",
            "-c statement_timeout=" + STATEMENT_TIMEOUT_MILLISECONDS,
            "-c lock_timeout=" + LOCK_TIMEOUT_MILLISECONDS,
            "-c idle_in_transaction_session_timeout=" + IDLE_TRANSACTION_TIMEOUT_MILLISECONDS,
            "-c transaction_timeout=" + TRANSACTION_TIMEOUT_MILLISECONDS
    );

    private DatabaseDefaults() {
    }
}
