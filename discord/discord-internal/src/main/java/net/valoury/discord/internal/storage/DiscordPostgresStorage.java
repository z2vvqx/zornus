package net.valoury.discord.internal.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.valoury.discord.api.link.AccountLink;
import net.valoury.discord.api.link.AccountLinkStorage;
import net.valoury.discord.api.link.ConsumeLinkCodeResult;
import net.valoury.discord.api.link.LinkCodeReservationResult;
import net.valoury.discord.api.link.UnlinkAccountResult;
import net.valoury.discord.api.ticket.AssignTicketResult;
import net.valoury.discord.api.ticket.BeginTicketCloseResult;
import net.valoury.discord.api.ticket.ReserveTicketResult;
import net.valoury.discord.api.ticket.Ticket;
import net.valoury.discord.api.ticket.TicketStatus;
import net.valoury.discord.api.ticket.TicketStorage;
import net.valoury.shared.database.DatabaseDefaults;
import net.valoury.shared.database.DatabaseExecutor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static net.valoury.discord.internal.InternalConstants.ACCOUNT_LINK_COLUMNS;
import static net.valoury.discord.internal.InternalConstants.ACTIVE_TICKET_STATUS_SQL;
import static net.valoury.discord.internal.InternalConstants.DATABASE_CONNECTION_POOL_SIZE;
import static net.valoury.discord.internal.InternalConstants.DATABASE_EXECUTOR_POOL_SIZE;
import static net.valoury.discord.internal.InternalConstants.DATABASE_SHUTDOWN_TIMEOUT_SECONDS;
import static net.valoury.discord.internal.InternalConstants.TICKET_COLUMNS;

public final class DiscordPostgresStorage implements TicketStorage, AccountLinkStorage {
    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;

    public DiscordPostgresStorage(String jdbcUrl, String username, String password) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(jdbcUrl);
        configuration.setUsername(username);
        configuration.setPassword(password);
        configuration.setMaximumPoolSize(DATABASE_CONNECTION_POOL_SIZE);
        configuration.setDriverClassName("org.postgresql.Driver");
        configuration.setConnectionTimeout(DatabaseDefaults.CONNECTION_ACQUISITION_TIMEOUT_MILLISECONDS);
        configuration.setValidationTimeout(DatabaseDefaults.CONNECTION_VALIDATION_TIMEOUT_MILLISECONDS);
        configuration.addDataSourceProperty(
                "connectTimeout", DatabaseDefaults.CONNECTION_ESTABLISHMENT_TIMEOUT_SECONDS);
        configuration.addDataSourceProperty(
                "socketTimeout", DatabaseDefaults.SOCKET_READ_TIMEOUT_SECONDS);
        configuration.addDataSourceProperty(
                "cancelSignalTimeout", DatabaseDefaults.CANCEL_SIGNAL_TIMEOUT_SECONDS);
        configuration.addDataSourceProperty("options", DatabaseDefaults.POSTGRESQL_SESSION_OPTIONS);
        this.dataSource = new HikariDataSource(configuration);
        this.databaseExecutor = new DatabaseExecutor(
                "discord-database-",
                DATABASE_EXECUTOR_POOL_SIZE
        );
        try {
            initializeTicketSchema();
            initializeLinkSchema();
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    @Override
    public CompletableFuture<ReserveTicketResult> reserveTicket(
            long ownerDiscordUserId,
            long guildId,
            long parentChannelId,
            long staffRoleId
    ) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Optional<Ticket> existingTicket = findActiveTicketByOwner(connection, ownerDiscordUserId, false);
                    if (existingTicket.isPresent()) {
                        connection.commit();
                        return new ReserveTicketResult.AlreadyOwnsOpenTicket(existingTicket.orElseThrow());
                    }

                    long ticketNumber = incrementTicketCounter(connection);
                    Ticket reservedTicket = insertTicketReservation(
                            connection,
                            ticketNumber,
                            ownerDiscordUserId,
                            guildId,
                            parentChannelId,
                            staffRoleId
                    );
                    connection.commit();
                    return new ReserveTicketResult.Reserved(reservedTicket);
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    if ("23505".equals(exception.getSQLState())) {
                        Optional<Ticket> existingTicket = findActiveTicketByOwner(
                                connection, ownerDiscordUserId, false);
                        if (existingTicket.isPresent()) {
                            connection.commit();
                            return new ReserveTicketResult.AlreadyOwnsOpenTicket(existingTicket.orElseThrow());
                        }
                    }
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to reserve Discord ticket", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Ticket>> activateTicket(long ticketNumber, long threadId) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    UPDATE discord_tickets
                    SET thread_id = ?, status = 'OPEN'
                    WHERE ticket_number = ? AND status = 'CREATING'
                    RETURNING %s
                    """.formatted(TICKET_COLUMNS);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, threadId);
                statement.setLong(2, ticketNumber);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapTicket(resultSet)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to activate Discord ticket", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> failTicketCreation(long ticketNumber) {
        return databaseExecutor.run(() -> {
            String sql = """
                    UPDATE discord_tickets
                    SET owner_discord_user_id = NULL, status = 'FAILED', closed_at = NOW()
                    WHERE ticket_number = ? AND status IN ('CREATING', 'OPEN')
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, ticketNumber);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to release failed Discord ticket", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Ticket>> findOpenTicketByThread(long threadId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT " + TICKET_COLUMNS + " FROM discord_tickets"
                    + " WHERE thread_id = ? AND status = 'OPEN'";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, threadId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapTicket(resultSet)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to find Discord ticket", exception);
            }
        });
    }

    @Override
    public CompletableFuture<BeginTicketCloseResult> beginTicketClose(long threadId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Optional<Ticket> ticketOptional = findTicketForClose(connection, threadId);
                    if (ticketOptional.isEmpty()) {
                        connection.commit();
                        return new BeginTicketCloseResult.TicketNotFound();
                    }

                    Ticket ticket = ticketOptional.orElseThrow();
                    if (ticket.ownerDiscordUserId().isEmpty()) {
                        connection.commit();
                        return new BeginTicketCloseResult.MissingOwner();
                    }
                    if (ticket.status() == TicketStatus.CLOSING) {
                        connection.commit();
                        return new BeginTicketCloseResult.AlreadyClosing();
                    }

                    Ticket closingTicket = updateTicketStatus(
                            connection, threadId, TicketStatus.OPEN, TicketStatus.CLOSING);
                    connection.commit();
                    return new BeginTicketCloseResult.Ready(closingTicket);
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to begin closing Discord ticket", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> completeTicketClose(long threadId) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    UPDATE discord_tickets
                    SET owner_discord_user_id = NULL, status = 'CLOSED', closed_at = NOW()
                    WHERE thread_id = ? AND status = 'CLOSING' AND owner_discord_user_id IS NOT NULL
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, threadId);
                return statement.executeUpdate() == 1;
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to complete Discord ticket closure", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> restoreOpenTicket(long threadId) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    UPDATE discord_tickets
                    SET status = 'OPEN'
                    WHERE thread_id = ? AND status = 'CLOSING' AND owner_discord_user_id IS NOT NULL
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, threadId);
                return statement.executeUpdate() == 1;
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to restore Discord ticket after close failure", exception);
            }
        });
    }

    @Override
    public CompletableFuture<AssignTicketResult> assignTicket(long threadId, long selectedDiscordUserId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Optional<Ticket> ticketOptional = findOpenTicketForUpdate(connection, threadId);
                    if (ticketOptional.isEmpty()) {
                        connection.commit();
                        return new AssignTicketResult.TicketNotFound();
                    }

                    Ticket ticket = ticketOptional.orElseThrow();
                    if (ticket.ownerDiscordUserId().isEmpty()) {
                        connection.commit();
                        return new AssignTicketResult.MissingOwner();
                    }

                    long previousOwnerDiscordUserId = ticket.ownerDiscordUserId().getAsLong();
                    if (previousOwnerDiscordUserId == selectedDiscordUserId) {
                        connection.commit();
                        return new AssignTicketResult.AlreadyOwner();
                    }

                    Optional<Ticket> selectedUserTicket = findActiveTicketByOwner(
                            connection, selectedDiscordUserId, true);
                    if (selectedUserTicket.isPresent()) {
                        connection.commit();
                        return new AssignTicketResult.SelectedUserAlreadyOwnsOpenTicket(
                                selectedUserTicket.orElseThrow());
                    }

                    Ticket assignedTicket = updateTicketOwner(connection, threadId, selectedDiscordUserId);
                    connection.commit();
                    return new AssignTicketResult.Assigned(previousOwnerDiscordUserId, assignedTicket);
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    if ("23505".equals(exception.getSQLState())) {
                        Optional<Ticket> selectedUserTicket = findActiveTicketByOwner(
                                connection, selectedDiscordUserId, false);
                        if (selectedUserTicket.isPresent()) {
                            connection.commit();
                            return new AssignTicketResult.SelectedUserAlreadyOwnsOpenTicket(
                                    selectedUserTicket.orElseThrow());
                        }
                    }
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to assign Discord ticket", exception);
            }
        });
    }

    @Override
    public CompletableFuture<LinkCodeReservationResult> reserveLinkCode(
            UUID minecraftUniqueId,
            String minecraftName,
            String codeHash,
            Duration codeLifetime,
            Duration issuanceCooldown
    ) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Instant issuedAt = findCurrentDatabaseTime(connection);
                    Instant expiresAt = issuedAt.plus(codeLifetime);
                    if (findAccountLinkByMinecraftUniqueId(connection, minecraftUniqueId).isPresent()) {
                        deletePendingLinkCodes(connection, minecraftUniqueId);
                        connection.commit();
                        return new LinkCodeReservationResult.AlreadyLinked();
                    }

                    Optional<Instant> reservedExpiry = upsertPendingLinkCode(
                            connection,
                            minecraftUniqueId,
                            minecraftName,
                            codeHash,
                            issuedAt,
                            expiresAt,
                            issuanceCooldown
                    );
                    if (reservedExpiry.isEmpty()) {
                        Instant previousIssueTime = findPendingLinkCodeIssueTime(
                                connection, minecraftUniqueId).orElseThrow(
                                () -> new SQLException("Pending account link code disappeared during issuance"));
                        Duration retryAfter = positiveDuration(
                                Duration.between(issuedAt, previousIssueTime.plus(issuanceCooldown)));
                        connection.commit();
                        return new LinkCodeReservationResult.RateLimited(retryAfter);
                    }

                    if (findAccountLinkByMinecraftUniqueId(connection, minecraftUniqueId).isPresent()) {
                        deletePendingLinkCodes(connection, minecraftUniqueId);
                        connection.commit();
                        return new LinkCodeReservationResult.AlreadyLinked();
                    }

                    connection.commit();
                    return new LinkCodeReservationResult.Reserved(reservedExpiry.orElseThrow());
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    if ("23505".equals(exception.getSQLState())) {
                        return new LinkCodeReservationResult.CodeHashCollision();
                    }
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to issue an account link code", exception);
            }
        });
    }

    @Override
    public CompletableFuture<ConsumeLinkCodeResult> consumeLinkCode(
            long discordUserId,
            String codeHash,
            int maximumAttempts,
            Duration attemptWindow
    ) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Instant attemptedAt = findCurrentDatabaseTime(connection);
                    Optional<Duration> retryAfter = recordLinkAttempt(
                            connection, discordUserId, attemptedAt, maximumAttempts, attemptWindow);
                    if (retryAfter.isPresent()) {
                        connection.commit();
                        return new ConsumeLinkCodeResult.RateLimited(retryAfter.orElseThrow());
                    }

                    Optional<PendingLinkCode> pendingCodeOptional = findPendingLinkCodeForUpdate(
                            connection, codeHash);
                    if (pendingCodeOptional.isEmpty()) {
                        connection.commit();
                        return new ConsumeLinkCodeResult.InvalidOrExpiredCode();
                    }

                    PendingLinkCode pendingCode = pendingCodeOptional.orElseThrow();
                    if (!pendingCode.expiresAt().isAfter(attemptedAt)) {
                        deletePendingLinkCodes(connection, pendingCode.minecraftUniqueId());
                        connection.commit();
                        return new ConsumeLinkCodeResult.InvalidOrExpiredCode();
                    }

                    Optional<AccountLink> minecraftAccountLink = findAccountLinkByMinecraftUniqueId(
                            connection, pendingCode.minecraftUniqueId());
                    Optional<AccountLink> discordAccountLink = findAccountLinkByDiscordUserId(
                            connection, discordUserId);
                    Optional<ConsumeLinkCodeResult> existingLinkResult = classifyExistingLinks(
                            minecraftAccountLink, discordAccountLink, pendingCode, discordUserId);
                    if (existingLinkResult.isPresent()) {
                        if (minecraftAccountLink.isPresent()) {
                            deletePendingLinkCodes(connection, pendingCode.minecraftUniqueId());
                        }
                        ConsumeLinkCodeResult result = existingLinkResult.orElseThrow();
                        if (result instanceof ConsumeLinkCodeResult.AlreadyLinked) {
                            deleteLinkAttempts(connection, discordUserId);
                        }
                        connection.commit();
                        return result;
                    }

                    Optional<AccountLink> insertedLink = insertAccountLink(
                            connection, pendingCode, discordUserId, attemptedAt);
                    if (insertedLink.isPresent()) {
                        deletePendingLinkCodes(connection, pendingCode.minecraftUniqueId());
                        deleteLinkAttempts(connection, discordUserId);
                        connection.commit();
                        return new ConsumeLinkCodeResult.Linked(insertedLink.orElseThrow());
                    }

                    minecraftAccountLink = findAccountLinkByMinecraftUniqueId(
                            connection, pendingCode.minecraftUniqueId());
                    discordAccountLink = findAccountLinkByDiscordUserId(connection, discordUserId);
                    Optional<ConsumeLinkCodeResult> concurrentLinkResult = classifyExistingLinks(
                            minecraftAccountLink, discordAccountLink, pendingCode, discordUserId);
                    if (concurrentLinkResult.isEmpty()) {
                        throw new SQLException("Account link conflict could not be resolved");
                    }
                    if (minecraftAccountLink.isPresent()) {
                        deletePendingLinkCodes(connection, pendingCode.minecraftUniqueId());
                    }
                    ConsumeLinkCodeResult result = concurrentLinkResult.orElseThrow();
                    if (result instanceof ConsumeLinkCodeResult.AlreadyLinked) {
                        deleteLinkAttempts(connection, discordUserId);
                    }
                    connection.commit();
                    return result;
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to consume an account link code", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<AccountLink>> findByMinecraftUniqueId(UUID minecraftUniqueId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return findAccountLinkByMinecraftUniqueId(connection, minecraftUniqueId);
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to find an account link by Minecraft identity", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<AccountLink>> findByDiscordUserId(long discordUserId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return findAccountLinkByDiscordUserId(connection, discordUserId);
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to find an account link by Discord identity", exception);
            }
        });
    }

    @Override
    public CompletableFuture<UnlinkAccountResult> unlinkByMinecraftUniqueId(UUID minecraftUniqueId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    deletePendingLinkCodes(connection, minecraftUniqueId);
                    Optional<AccountLink> accountLink = findAccountLinkByMinecraftUniqueId(
                            connection, minecraftUniqueId);
                    boolean unlinked = deleteAccountLinkByMinecraftUniqueId(connection, minecraftUniqueId);
                    if (accountLink.isPresent()) {
                        deleteLinkAttempts(connection, accountLink.orElseThrow().discordUserId());
                    }
                    connection.commit();
                    return unlinked
                            ? new UnlinkAccountResult.Unlinked()
                            : new UnlinkAccountResult.NotLinked();
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to unlink a Minecraft account", exception);
            }
        });
    }

    @Override
    public CompletableFuture<UnlinkAccountResult> unlinkByDiscordUserId(long discordUserId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    deleteLinkAttempts(connection, discordUserId);
                    Optional<AccountLink> accountLink = findAccountLinkByDiscordUserId(
                            connection, discordUserId);
                    boolean unlinked = deleteAccountLinkByDiscordUserId(connection, discordUserId);
                    if (accountLink.isPresent()) {
                        deletePendingLinkCodes(
                                connection, accountLink.orElseThrow().minecraftUniqueId());
                    }
                    connection.commit();
                    return unlinked
                            ? new UnlinkAccountResult.Unlinked()
                            : new UnlinkAccountResult.NotLinked();
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to unlink a Discord account", exception);
            }
        });
    }

    private void initializeTicketSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            if (schemaExists(connection, "discord_tickets")) {
                return;
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_ticket_counter (
                        counter_key SMALLINT PRIMARY KEY CHECK (counter_key = 1),
                        last_ticket_number BIGINT NOT NULL CHECK (last_ticket_number >= 0)
                    )
                    """);
            statement.execute("""
                    INSERT INTO discord_ticket_counter (counter_key, last_ticket_number)
                    VALUES (1, 0)
                    ON CONFLICT (counter_key) DO NOTHING
                    """);
            statement.execute("""
                    CREATE TABLE discord_tickets (
                        ticket_number BIGINT PRIMARY KEY CHECK (ticket_number > 0),
                        thread_id BIGINT UNIQUE,
                        owner_discord_user_id BIGINT,
                        guild_id BIGINT NOT NULL,
                        parent_channel_id BIGINT NOT NULL,
                        staff_role_id BIGINT NOT NULL,
                        status VARCHAR(16) NOT NULL
                            CHECK (status IN ('CREATING', 'OPEN', 'CLOSING', 'CLOSED', 'FAILED')),
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        closed_at TIMESTAMPTZ,
                        CONSTRAINT chk_discord_ticket_owner_state CHECK (
                            (owner_discord_user_id IS NOT NULL AND status IN ('CREATING', 'OPEN', 'CLOSING'))
                            OR (owner_discord_user_id IS NULL AND status IN ('CLOSED', 'FAILED'))
                        ),
                        CONSTRAINT chk_discord_ticket_thread_state CHECK (
                            (status = 'CREATING' AND thread_id IS NULL)
                            OR (status IN ('OPEN', 'CLOSING', 'CLOSED') AND thread_id IS NOT NULL)
                            OR status = 'FAILED'
                        )
                    )
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX idx_discord_tickets_one_open_owner
                    ON discord_tickets (owner_discord_user_id)
                    WHERE owner_discord_user_id IS NOT NULL
                      AND status IN ('CREATING', 'OPEN', 'CLOSING')
                    """);
            statement.execute("""
                    CREATE INDEX idx_discord_tickets_active_thread
                    ON discord_tickets (thread_id)
                    WHERE status IN ('OPEN', 'CLOSING')
                    """);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize Discord ticket database schema", exception);
        }
    }

    private void initializeLinkSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_account_links (
                        minecraft_uuid UUID PRIMARY KEY,
                        minecraft_name VARCHAR(16) NOT NULL
                            CHECK (minecraft_name ~ '^[A-Za-z0-9_]{1,16}$'),
                        discord_user_id BIGINT NOT NULL UNIQUE CHECK (discord_user_id > 0),
                        linked_at TIMESTAMPTZ NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_pending_link_codes (
                        minecraft_uuid UUID PRIMARY KEY,
                        minecraft_name VARCHAR(16) NOT NULL
                            CHECK (minecraft_name ~ '^[A-Za-z0-9_]{1,16}$'),
                        code_hash CHAR(64) NOT NULL UNIQUE
                            CHECK (code_hash ~ '^[0-9a-f]{64}$'),
                        issued_at TIMESTAMPTZ NOT NULL,
                        expires_at TIMESTAMPTZ NOT NULL,
                        CHECK (expires_at > issued_at)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_discord_pending_link_codes_expiry
                    ON discord_pending_link_codes (expires_at)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_account_link_attempts (
                        discord_user_id BIGINT PRIMARY KEY CHECK (discord_user_id > 0),
                        window_started_at TIMESTAMPTZ NOT NULL,
                        attempt_count INTEGER NOT NULL CHECK (attempt_count > 0)
                    )
                    """);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize account link database schema", exception);
        }
    }

    private static boolean schemaExists(Connection connection, String rootTable) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, rootTable);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static long incrementTicketCounter(Connection connection) throws SQLException {
        String sql = """
                UPDATE discord_ticket_counter
                SET last_ticket_number = last_ticket_number + 1
                WHERE counter_key = 1
                RETURNING last_ticket_number
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Discord ticket counter row is missing");
            }
            return resultSet.getLong(1);
        }
    }

    private static Ticket insertTicketReservation(
            Connection connection,
            long ticketNumber,
            long ownerDiscordUserId,
            long guildId,
            long parentChannelId,
            long staffRoleId
    ) throws SQLException {
        String sql = """
                INSERT INTO discord_tickets (
                    ticket_number, owner_discord_user_id, guild_id, parent_channel_id,
                    staff_role_id, status
                ) VALUES (?, ?, ?, ?, ?, 'CREATING')
                RETURNING %s
                """.formatted(TICKET_COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ticketNumber);
            statement.setLong(2, ownerDiscordUserId);
            statement.setLong(3, guildId);
            statement.setLong(4, parentChannelId);
            statement.setLong(5, staffRoleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return mapTicket(resultSet);
            }
        }
    }

    private static Optional<Ticket> findActiveTicketByOwner(
            Connection connection,
            long ownerDiscordUserId,
            boolean lockRow
    ) throws SQLException {
        String sql = "SELECT " + TICKET_COLUMNS + " FROM discord_tickets"
                + " WHERE owner_discord_user_id = ? AND status IN " + ACTIVE_TICKET_STATUS_SQL
                + (lockRow ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerDiscordUserId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapTicket(resultSet)) : Optional.empty();
            }
        }
    }

    private static Optional<Ticket> findTicketForClose(Connection connection, long threadId) throws SQLException {
        String sql = "SELECT " + TICKET_COLUMNS + " FROM discord_tickets"
                + " WHERE thread_id = ? AND status IN ('OPEN', 'CLOSING') FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, threadId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapTicket(resultSet)) : Optional.empty();
            }
        }
    }

    private static Optional<Ticket> findOpenTicketForUpdate(Connection connection, long threadId) throws SQLException {
        String sql = "SELECT " + TICKET_COLUMNS + " FROM discord_tickets"
                + " WHERE thread_id = ? AND status = 'OPEN' FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, threadId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapTicket(resultSet)) : Optional.empty();
            }
        }
    }

    private static Ticket updateTicketStatus(
            Connection connection,
            long threadId,
            TicketStatus expectedStatus,
            TicketStatus newStatus
    ) throws SQLException {
        String sql = "UPDATE discord_tickets SET status = ? WHERE thread_id = ? AND status = ? RETURNING "
                + TICKET_COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newStatus.name());
            statement.setLong(2, threadId);
            statement.setString(3, expectedStatus.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Discord ticket state changed during update");
                }
                return mapTicket(resultSet);
            }
        }
    }

    private static Ticket updateTicketOwner(
            Connection connection,
            long threadId,
            long selectedDiscordUserId
    ) throws SQLException {
        String sql = """
                UPDATE discord_tickets
                SET owner_discord_user_id = ?
                WHERE thread_id = ? AND status = 'OPEN'
                RETURNING %s
                """.formatted(TICKET_COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, selectedDiscordUserId);
            statement.setLong(2, threadId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Discord ticket state changed during assignment");
                }
                return mapTicket(resultSet);
            }
        }
    }

    private static Ticket mapTicket(ResultSet resultSet) throws SQLException {
        Long threadId = resultSet.getObject("thread_id", Long.class);
        Long ownerDiscordUserId = resultSet.getObject("owner_discord_user_id", Long.class);
        Timestamp closedAt = resultSet.getTimestamp("closed_at");
        return new Ticket(
                resultSet.getLong("ticket_number"),
                threadId == null ? OptionalLong.empty() : OptionalLong.of(threadId),
                ownerDiscordUserId == null ? OptionalLong.empty() : OptionalLong.of(ownerDiscordUserId),
                resultSet.getLong("guild_id"),
                resultSet.getLong("parent_channel_id"),
                resultSet.getLong("staff_role_id"),
                TicketStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                closedAt == null ? Optional.empty() : Optional.of(closedAt.toInstant())
        );
    }

    private static void rollback(Connection connection, SQLException originalException) throws SQLException {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
            throw originalException;
        }
    }

    private static Instant findCurrentDatabaseTime(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getTimestamp(1).toInstant();
        }
    }

    private static Optional<Instant> upsertPendingLinkCode(
            Connection connection,
            UUID minecraftUniqueId,
            String minecraftName,
            String codeHash,
            Instant issuedAt,
            Instant expiresAt,
            Duration issuanceCooldown
    ) throws SQLException {
        String sql = """
                INSERT INTO discord_pending_link_codes (
                    minecraft_uuid, minecraft_name, code_hash, issued_at, expires_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (minecraft_uuid) DO UPDATE SET
                    minecraft_name = EXCLUDED.minecraft_name,
                    code_hash = EXCLUDED.code_hash,
                    issued_at = EXCLUDED.issued_at,
                    expires_at = EXCLUDED.expires_at
                WHERE discord_pending_link_codes.issued_at <= ?
                RETURNING expires_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUniqueId);
            statement.setString(2, minecraftName);
            statement.setString(3, codeHash);
            statement.setTimestamp(4, Timestamp.from(issuedAt));
            statement.setTimestamp(5, Timestamp.from(expiresAt));
            statement.setTimestamp(6, Timestamp.from(issuedAt.minus(issuanceCooldown)));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(resultSet.getTimestamp("expires_at").toInstant())
                        : Optional.empty();
            }
        }
    }

    private static Optional<Instant> findPendingLinkCodeIssueTime(
            Connection connection,
            UUID minecraftUniqueId
    ) throws SQLException {
        String sql = "SELECT issued_at FROM discord_pending_link_codes WHERE minecraft_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUniqueId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(resultSet.getTimestamp("issued_at").toInstant())
                        : Optional.empty();
            }
        }
    }

    private static Optional<Duration> recordLinkAttempt(
            Connection connection,
            long discordUserId,
            Instant attemptedAt,
            int maximumAttempts,
            Duration attemptWindow
    ) throws SQLException {
        String sql = """
                INSERT INTO discord_account_link_attempts (
                    discord_user_id, window_started_at, attempt_count
                ) VALUES (?, ?, 1)
                ON CONFLICT (discord_user_id) DO UPDATE SET
                    window_started_at = CASE
                        WHEN discord_account_link_attempts.window_started_at <= ?
                            THEN EXCLUDED.window_started_at
                        ELSE discord_account_link_attempts.window_started_at
                    END,
                    attempt_count = CASE
                        WHEN discord_account_link_attempts.window_started_at <= ? THEN 1
                        ELSE LEAST(discord_account_link_attempts.attempt_count + 1, ?)
                    END
                RETURNING window_started_at, attempt_count
                """;
        Instant resetCutoff = attemptedAt.minus(attemptWindow);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, discordUserId);
            statement.setTimestamp(2, Timestamp.from(attemptedAt));
            statement.setTimestamp(3, Timestamp.from(resetCutoff));
            statement.setTimestamp(4, Timestamp.from(resetCutoff));
            statement.setInt(5, maximumAttempts + 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt("attempt_count") <= maximumAttempts) {
                    return Optional.empty();
                }
                Instant windowStartedAt = resultSet.getTimestamp("window_started_at").toInstant();
                return Optional.of(positiveDuration(
                        Duration.between(attemptedAt, windowStartedAt.plus(attemptWindow))));
            }
        }
    }

    private static Optional<PendingLinkCode> findPendingLinkCodeForUpdate(
            Connection connection,
            String codeHash
    ) throws SQLException {
        String sql = """
                SELECT minecraft_uuid, minecraft_name, expires_at
                FROM discord_pending_link_codes
                WHERE code_hash = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, codeHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PendingLinkCode(
                        resultSet.getObject("minecraft_uuid", UUID.class),
                        resultSet.getString("minecraft_name"),
                        resultSet.getTimestamp("expires_at").toInstant()
                ));
            }
        }
    }

    private static Optional<AccountLink> findAccountLinkByMinecraftUniqueId(
            Connection connection,
            UUID minecraftUniqueId
    ) throws SQLException {
        String sql = "SELECT " + ACCOUNT_LINK_COLUMNS
                + " FROM discord_account_links WHERE minecraft_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUniqueId);
            return findAccountLink(statement);
        }
    }

    private static Optional<AccountLink> findAccountLinkByDiscordUserId(
            Connection connection,
            long discordUserId
    ) throws SQLException {
        String sql = "SELECT " + ACCOUNT_LINK_COLUMNS
                + " FROM discord_account_links WHERE discord_user_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, discordUserId);
            return findAccountLink(statement);
        }
    }

    private static Optional<AccountLink> insertAccountLink(
            Connection connection,
            PendingLinkCode pendingCode,
            long discordUserId,
            Instant linkedAt
    ) throws SQLException {
        String sql = """
                INSERT INTO discord_account_links (
                    minecraft_uuid, minecraft_name, discord_user_id, linked_at
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                RETURNING %s
                """.formatted(ACCOUNT_LINK_COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, pendingCode.minecraftUniqueId());
            statement.setString(2, pendingCode.minecraftName());
            statement.setLong(3, discordUserId);
            statement.setTimestamp(4, Timestamp.from(linkedAt));
            return findAccountLink(statement);
        }
    }

    private static Optional<ConsumeLinkCodeResult> classifyExistingLinks(
            Optional<AccountLink> minecraftAccountLink,
            Optional<AccountLink> discordAccountLink,
            PendingLinkCode pendingCode,
            long discordUserId
    ) {
        if (minecraftAccountLink.isPresent()) {
            AccountLink accountLink = minecraftAccountLink.orElseThrow();
            if (accountLink.discordUserId() == discordUserId) {
                return Optional.of(new ConsumeLinkCodeResult.AlreadyLinked());
            }
            return Optional.of(new ConsumeLinkCodeResult.MinecraftAccountLinkedElsewhere());
        }
        if (discordAccountLink.isPresent()) {
            AccountLink accountLink = discordAccountLink.orElseThrow();
            if (accountLink.minecraftUniqueId().equals(pendingCode.minecraftUniqueId())) {
                return Optional.of(new ConsumeLinkCodeResult.AlreadyLinked());
            }
            return Optional.of(new ConsumeLinkCodeResult.DiscordAccountLinkedElsewhere());
        }
        return Optional.empty();
    }

    private static boolean deleteAccountLinkByMinecraftUniqueId(
            Connection connection,
            UUID minecraftUniqueId
    ) throws SQLException {
        String sql = "DELETE FROM discord_account_links WHERE minecraft_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUniqueId);
            return statement.executeUpdate() == 1;
        }
    }

    private static boolean deleteAccountLinkByDiscordUserId(
            Connection connection,
            long discordUserId
    ) throws SQLException {
        String sql = "DELETE FROM discord_account_links WHERE discord_user_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, discordUserId);
            return statement.executeUpdate() == 1;
        }
    }

    private static void deletePendingLinkCodes(Connection connection, UUID minecraftUniqueId) throws SQLException {
        String sql = "DELETE FROM discord_pending_link_codes WHERE minecraft_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUniqueId);
            statement.executeUpdate();
        }
    }

    private static void deleteLinkAttempts(Connection connection, long discordUserId) throws SQLException {
        String sql = "DELETE FROM discord_account_link_attempts WHERE discord_user_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, discordUserId);
            statement.executeUpdate();
        }
    }

    private static Duration positiveDuration(Duration duration) {
        return duration.isNegative() || duration.isZero() ? Duration.ofMillis(1) : duration;
    }

    private static Optional<AccountLink> findAccountLink(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? Optional.of(mapAccountLink(resultSet)) : Optional.empty();
        }
    }

    private static AccountLink mapAccountLink(ResultSet resultSet) throws SQLException {
        return new AccountLink(
                resultSet.getObject("minecraft_uuid", UUID.class),
                resultSet.getString("minecraft_name"),
                resultSet.getLong("discord_user_id"),
                resultSet.getTimestamp("linked_at").toInstant()
        );
    }

    @Override
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(
                    DATABASE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }

}
