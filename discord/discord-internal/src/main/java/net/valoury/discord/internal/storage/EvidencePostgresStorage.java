package net.valoury.discord.internal.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.valoury.discord.api.evidence.EvidenceAttachment;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.api.evidence.EvidenceCaseRequest;
import net.valoury.discord.api.evidence.EvidenceCaseStatus;
import net.valoury.discord.api.evidence.EvidenceReviewDecision;
import net.valoury.discord.api.evidence.EvidenceSettings;
import net.valoury.discord.api.evidence.EvidenceStorage;
import net.valoury.discord.api.evidence.EvidenceSubmission;
import net.valoury.discord.internal.InternalConstants;
import net.valoury.shared.database.DatabaseDefaults;
import net.valoury.shared.database.DatabaseExecutor;
import net.valoury.shared.database.PostgresSchemaVerifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static net.valoury.discord.internal.storage.EvidenceStorageConstants.CASE_COLUMNS;
import static net.valoury.discord.internal.storage.EvidenceStorageConstants.EDITABLE_SUBMISSION_STATUS_SQL;
import static net.valoury.discord.internal.storage.EvidenceStorageConstants.INITIAL_SUBMISSION_STATUS_SQL;

public final class EvidencePostgresStorage implements EvidenceStorage {
    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;

    public EvidencePostgresStorage(String jdbcUrl, String username, String password) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(jdbcUrl);
        configuration.setUsername(username);
        configuration.setPassword(password);
        configuration.setMaximumPoolSize(InternalConstants.DATABASE_CONNECTION_POOL_SIZE);
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
                "discord-evidence-database-",
                InternalConstants.DATABASE_EXECUTOR_POOL_SIZE
        );
        try {
            initializeSchema();
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    @Override
    public CompletableFuture<EvidenceCase> createCase(EvidenceCaseRequest request) {
        return databaseExecutor.supply(() -> {
            UUID caseId = UUID.randomUUID();
            String sql = """
                    INSERT INTO discord_evidence_cases (
                        case_id, punishment_identifier, punished_player_id, punished_player_name,
                        issuing_player_id, issuing_discord_user_id, preset_name,
                        preset_application_number, punishment_type, reason, punishment_created_at,
                        punishment_expires_at, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_THREAD')
                    ON CONFLICT (punishment_identifier) DO NOTHING
                    RETURNING %s
                    """.formatted(CASE_COLUMNS);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, caseId);
                    statement.setString(2, request.punishmentIdentifier());
                    statement.setObject(3, request.punishedPlayerId());
                    statement.setString(4, request.punishedPlayerName());
                    statement.setObject(5, request.issuingPlayerId());
                    statement.setObject(6, request.issuingDiscordUserId());
                    statement.setString(7, request.presetName());
                    statement.setInt(8, request.presetApplicationNumber());
                    statement.setString(9, request.punishmentType());
                    statement.setString(10, request.reason());
                    statement.setTimestamp(11, Timestamp.from(request.punishmentCreatedAt()));
                    setInstant(statement, 12, request.punishmentExpiresAt());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            EvidenceCase createdCase = mapCase(resultSet);
                            insertAuditEvent(
                                    connection,
                                    createdCase.caseId(),
                                    "CASE_CREATED",
                                    createdCase.issuingDiscordUserId(),
                                    "Preset punishment created the evidence case"
                            );
                            connection.commit();
                            return createdCase;
                        }
                    }
                    EvidenceCase existingCase = findCaseByIdentifier(connection, request.punishmentIdentifier())
                            .orElseThrow(() -> new SQLException("Conflicting evidence case disappeared"));
                    connection.commit();
                    return existingCase;
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to create Discord evidence case", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<EvidenceCase>> findCaseByIdentifier(String punishmentIdentifier) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return findCaseByIdentifier(connection, punishmentIdentifier);
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to find Discord evidence case by punishment identifier", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<EvidenceCase>> findCaseByThreadId(long threadId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT " + CASE_COLUMNS + " FROM discord_evidence_cases WHERE thread_id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, threadId);
                return findCase(statement);
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to find Discord evidence case by thread", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<EvidenceSubmission>> findLatestSubmission(UUID caseId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return findLatestSubmission(connection, caseId);
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to find the latest Discord evidence submission", exception);
            }
        });
    }

    @Override
    public CompletableFuture<List<EvidenceCase>> claimPendingCases(
            Instant claimedAt,
            Duration staleClaimAge,
            int maximumCases
    ) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    WITH pending_cases AS (
                        SELECT case_id AS pending_case_id
                        FROM discord_evidence_cases
                        WHERE status = 'PENDING_THREAD'
                           OR (status = 'CREATING_THREAD' AND provisioning_started_at <= ?)
                        ORDER BY created_at
                        FOR UPDATE SKIP LOCKED
                        LIMIT ?
                    )
                    UPDATE discord_evidence_cases AS evidence_case
                    SET status = 'CREATING_THREAD', provisioning_started_at = ?
                    FROM pending_cases
                    WHERE evidence_case.case_id = pending_cases.pending_case_id
                    RETURNING %s
                    """.formatted(CASE_COLUMNS);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(claimedAt.minus(staleClaimAge)));
                statement.setInt(2, maximumCases);
                statement.setTimestamp(3, Timestamp.from(claimedAt));
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<EvidenceCase> cases = new ArrayList<>();
                    while (resultSet.next()) {
                        cases.add(mapCase(resultSet));
                    }
                    return List.copyOf(cases);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to claim pending Discord evidence cases", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<EvidenceCase>> activateCase(
            UUID caseId,
            long guildId,
            long forumChannelId,
            long threadId,
            long starterMessageId
    ) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    UPDATE discord_evidence_cases
                    SET status = 'AWAITING_EVIDENCE', guild_id = ?, forum_channel_id = ?,
                        thread_id = ?, starter_message_id = ?, provisioning_started_at = NULL
                    WHERE case_id = ? AND status = 'CREATING_THREAD'
                    RETURNING %s
                    """.formatted(CASE_COLUMNS);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, guildId);
                    statement.setLong(2, forumChannelId);
                    statement.setLong(3, threadId);
                    statement.setLong(4, starterMessageId);
                    statement.setObject(5, caseId);
                    Optional<EvidenceCase> activatedCase = findCase(statement);
                    if (activatedCase.isPresent()) {
                        insertAuditEvent(
                                connection,
                                caseId,
                                "THREAD_CREATED",
                                null,
                                "Discord forum thread " + threadId + " was created"
                        );
                    }
                    connection.commit();
                    return activatedCase;
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to activate Discord evidence case", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> releaseCaseProvisioning(UUID caseId) {
        return databaseExecutor.run(() -> {
            String sql = """
                    UPDATE discord_evidence_cases
                    SET status = 'PENDING_THREAD', provisioning_started_at = NULL
                    WHERE case_id = ? AND status = 'CREATING_THREAD'
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, caseId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to release Discord evidence case provisioning", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<EvidenceSettings>> findSettings() {
        return databaseExecutor.supply(() -> {
            String sql = """
                    SELECT guild_id, forum_channel_id, reviewer_role_id, updated_at
                    FROM discord_evidence_settings WHERE settings_key = 1
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapSettings(resultSet)) : Optional.empty();
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to find Discord evidence settings", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveSettings(EvidenceSettings settings) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO discord_evidence_settings (
                        settings_key, guild_id, forum_channel_id, reviewer_role_id, updated_at
                    ) VALUES (1, ?, ?, ?, ?)
                    ON CONFLICT (settings_key) DO UPDATE SET
                        guild_id = EXCLUDED.guild_id,
                        forum_channel_id = EXCLUDED.forum_channel_id,
                        reviewer_role_id = EXCLUDED.reviewer_role_id,
                        updated_at = EXCLUDED.updated_at
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, settings.guildId());
                statement.setLong(2, settings.forumChannelId());
                statement.setLong(3, settings.reviewerRoleId());
                statement.setTimestamp(4, Timestamp.from(settings.updatedAt()));
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to save Discord evidence settings", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> beginSubmission(
            UUID caseId,
            long submissionId,
            long submittingDiscordUserId,
            boolean reviewerOverride,
            Instant startedAt,
            Duration staleUploadAge
    ) {
        return beginUpload(
                caseId,
                submissionId,
                submittingDiscordUserId,
                reviewerOverride,
                startedAt,
                staleUploadAge,
                INITIAL_SUBMISSION_STATUS_SQL
        );
    }

    @Override
    public CompletableFuture<Boolean> beginSubmissionEdit(
            UUID caseId,
            long submissionId,
            long submittingDiscordUserId,
            boolean reviewerOverride,
            Instant startedAt,
            Duration staleUploadAge
    ) {
        return beginUpload(
                caseId,
                submissionId,
                submittingDiscordUserId,
                reviewerOverride,
                startedAt,
                staleUploadAge,
                EDITABLE_SUBMISSION_STATUS_SQL
        );
    }

    private CompletableFuture<Boolean> beginUpload(
            UUID caseId,
            long submissionId,
            long submittingDiscordUserId,
            boolean reviewerOverride,
            Instant startedAt,
            Duration staleUploadAge,
            String acceptedStatusesSql
    ) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    UPDATE discord_evidence_cases
                    SET upload_resume_status = CASE
                            WHEN status = 'UPLOADING' THEN upload_resume_status
                            ELSE status
                        END,
                        status = 'UPLOADING', active_submission_id = ?, upload_started_at = ?
                    WHERE case_id = ?
                      AND (issuing_discord_user_id = ? OR ?)
                      AND (
                          status IN %s
                          OR (
                              status = 'UPLOADING'
                              AND upload_resume_status IN %s
                              AND upload_started_at <= ?
                          )
                      )
                    """.formatted(acceptedStatusesSql, acceptedStatusesSql);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, submissionId);
                statement.setTimestamp(2, Timestamp.from(startedAt));
                statement.setObject(3, caseId);
                statement.setLong(4, submittingDiscordUserId);
                statement.setBoolean(5, reviewerOverride);
                statement.setTimestamp(6, Timestamp.from(startedAt.minus(staleUploadAge)));
                return statement.executeUpdate() == 1;
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to begin Discord evidence submission", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<EvidenceCase>> completeSubmission(EvidenceSubmission submission) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    if (submissionExists(connection, submission.submissionId())) {
                        Optional<EvidenceCase> completedCase = findCaseById(connection, submission.caseId());
                        connection.commit();
                        return completedCase;
                    }
                    boolean editingExistingSubmission = caseHasSubmission(connection, submission.caseId());
                    insertSubmission(connection, submission);
                    insertAttachments(connection, submission);
                    Optional<EvidenceCase> completedCase = finishSubmission(connection, submission);
                    if (completedCase.isEmpty()) {
                        throw new SQLException("Evidence case is no longer accepting this submission");
                    }
                    insertAuditEvent(
                            connection,
                            submission.caseId(),
                            editingExistingSubmission ? "EVIDENCE_EDITED" : "EVIDENCE_SUBMITTED",
                            submission.submittedByDiscordUserId(),
                            "Evidence message " + submission.evidenceMessageId()
                                    + (editingExistingSubmission ? " was updated" : " was posted")
                    );
                    connection.commit();
                    return completedCase;
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to complete Discord evidence submission", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> failSubmission(UUID caseId, long submissionId) {
        return databaseExecutor.run(() -> {
            String sql = """
                    UPDATE discord_evidence_cases
                    SET status = COALESCE(upload_resume_status, 'AWAITING_EVIDENCE'),
                        upload_resume_status = NULL,
                        active_submission_id = NULL,
                        upload_started_at = NULL
                    WHERE case_id = ? AND status = 'UPLOADING' AND active_submission_id = ?
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, caseId);
                statement.setLong(2, submissionId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to release Discord evidence submission", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<EvidenceCase>> reviewCase(
            UUID caseId,
            long reviewerDiscordUserId,
            EvidenceReviewDecision decision,
            String reviewReason,
            Instant reviewedAt
    ) {
        return databaseExecutor.supply(() -> {
            String newStatus = decision == EvidenceReviewDecision.ACCEPT ? "ACCEPTED" : "NEEDS_CHANGES";
            String sql = """
                    UPDATE discord_evidence_cases
                    SET status = ?, reviewed_at = ?, reviewed_by_discord_user_id = ?, review_reason = ?,
                        active_change_request_message_id = NULL
                    WHERE case_id = ? AND status = 'AWAITING_REVIEW'
                    RETURNING %s
                    """.formatted(CASE_COLUMNS);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, newStatus);
                    statement.setTimestamp(2, Timestamp.from(reviewedAt));
                    statement.setLong(3, reviewerDiscordUserId);
                    statement.setString(4, reviewReason);
                    statement.setObject(5, caseId);
                    Optional<EvidenceCase> reviewedCase = findCase(statement);
                    if (reviewedCase.isPresent()) {
                        insertAuditEvent(
                                connection,
                                caseId,
                                decision == EvidenceReviewDecision.ACCEPT
                                        ? "EVIDENCE_ACCEPTED"
                                        : "EVIDENCE_CHANGES_REQUESTED",
                                reviewerDiscordUserId,
                                reviewReason
                        );
                    }
                    connection.commit();
                    return reviewedCase;
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to review Discord evidence case", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> recordActiveChangeRequestMessage(UUID caseId, long messageId) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    UPDATE discord_evidence_cases
                    SET active_change_request_message_id = ?
                    WHERE case_id = ? AND status = 'NEEDS_CHANGES'
                      AND active_change_request_message_id IS NULL
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, messageId);
                statement.setObject(2, caseId);
                return statement.executeUpdate() == 1;
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to record Discord evidence change request message", exception);
            }
        });
    }

    @Override
    public CompletableFuture<OptionalLong> findActiveChangeRequestMessageId(UUID caseId) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    SELECT active_change_request_message_id
                    FROM discord_evidence_cases
                    WHERE case_id = ?
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, caseId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return OptionalLong.empty();
                    }
                    Long messageId = resultSet.getObject("active_change_request_message_id", Long.class);
                    return messageId == null ? OptionalLong.empty() : OptionalLong.of(messageId);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to find active Discord evidence change request", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<EvidenceCase>> editChangeRequest(
            UUID caseId,
            long changeRequestMessageId,
            long reviewerDiscordUserId,
            String reviewReason,
            Instant editedAt
    ) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    UPDATE discord_evidence_cases
                    SET reviewed_at = ?, reviewed_by_discord_user_id = ?, review_reason = ?
                    WHERE case_id = ? AND status = 'NEEDS_CHANGES'
                      AND active_change_request_message_id = ?
                    RETURNING %s
                    """.formatted(CASE_COLUMNS);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setTimestamp(1, Timestamp.from(editedAt));
                    statement.setLong(2, reviewerDiscordUserId);
                    statement.setString(3, reviewReason);
                    statement.setObject(4, caseId);
                    statement.setLong(5, changeRequestMessageId);
                    Optional<EvidenceCase> editedCase = findCase(statement);
                    if (editedCase.isPresent()) {
                        insertAuditEvent(
                                connection,
                                caseId,
                                "EVIDENCE_CHANGE_REQUEST_EDITED",
                                reviewerDiscordUserId,
                                reviewReason
                        );
                    }
                    connection.commit();
                    return editedCase;
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to edit Discord evidence change request", exception);
            }
        });
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_evidence_settings (
                        settings_key SMALLINT PRIMARY KEY CHECK (settings_key = 1),
                        guild_id BIGINT NOT NULL CHECK (guild_id > 0),
                        forum_channel_id BIGINT NOT NULL CHECK (forum_channel_id > 0),
                        reviewer_role_id BIGINT NOT NULL CHECK (reviewer_role_id > 0),
                        updated_at TIMESTAMPTZ NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_evidence_cases (
                        case_id UUID PRIMARY KEY,
                        punishment_identifier VARCHAR(4) NOT NULL UNIQUE
                            CHECK (punishment_identifier ~ '^[A-Z0-9]{4}$'),
                        punished_player_id UUID NOT NULL,
                        punished_player_name VARCHAR(16) NOT NULL
                            CHECK (punished_player_name ~ '^[A-Za-z0-9_]{1,16}$'),
                        issuing_player_id UUID,
                        issuing_discord_user_id BIGINT CHECK (issuing_discord_user_id > 0),
                        preset_name VARCHAR(64) NOT NULL,
                        preset_application_number INTEGER NOT NULL CHECK (preset_application_number > 0),
                        punishment_type VARCHAR(8) NOT NULL
                            CHECK (punishment_type IN ('BAN', 'MUTE', 'WARN', 'KICK')),
                        reason TEXT NOT NULL,
                        punishment_created_at TIMESTAMPTZ NOT NULL,
                        punishment_expires_at TIMESTAMPTZ,
                        status VARCHAR(24) NOT NULL CHECK (status IN (
                            'PENDING_THREAD', 'CREATING_THREAD', 'AWAITING_EVIDENCE', 'UPLOADING',
                            'AWAITING_REVIEW', 'ACCEPTED', 'NEEDS_CHANGES'
                        )),
                        guild_id BIGINT CHECK (guild_id > 0),
                        forum_channel_id BIGINT CHECK (forum_channel_id > 0),
                        thread_id BIGINT UNIQUE CHECK (thread_id > 0),
                        starter_message_id BIGINT CHECK (starter_message_id > 0),
                        provisioning_started_at TIMESTAMPTZ,
                        upload_resume_status VARCHAR(24),
                        active_submission_id BIGINT CHECK (active_submission_id > 0),
                        upload_started_at TIMESTAMPTZ,
                        reviewed_at TIMESTAMPTZ,
                        reviewed_by_discord_user_id BIGINT CHECK (reviewed_by_discord_user_id > 0),
                        review_reason TEXT,
                        active_change_request_message_id BIGINT CHECK (active_change_request_message_id > 0),
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            statement.execute("""
                    ALTER TABLE discord_evidence_cases
                    ADD COLUMN IF NOT EXISTS active_change_request_message_id BIGINT
                        CHECK (active_change_request_message_id > 0)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_discord_evidence_cases_pending
                    ON discord_evidence_cases (status, created_at)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_evidence_submissions (
                        submission_id BIGINT PRIMARY KEY CHECK (submission_id > 0),
                        case_id UUID NOT NULL REFERENCES discord_evidence_cases(case_id),
                        submitted_by_discord_user_id BIGINT NOT NULL
                            CHECK (submitted_by_discord_user_id > 0),
                        incident_description TEXT NOT NULL,
                        proof_description TEXT NOT NULL,
                        additional_context TEXT NOT NULL,
                        external_link TEXT,
                        evidence_message_id BIGINT NOT NULL CHECK (evidence_message_id > 0),
                        submitted_at TIMESTAMPTZ NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_discord_evidence_submissions_case
                    ON discord_evidence_submissions (case_id, submitted_at)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_evidence_attachments (
                        submission_id BIGINT NOT NULL
                            REFERENCES discord_evidence_submissions(submission_id) ON DELETE RESTRICT,
                        attachment_id BIGINT NOT NULL CHECK (attachment_id > 0),
                        file_name TEXT NOT NULL,
                        content_type VARCHAR(128) NOT NULL,
                        size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
                        sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
                        PRIMARY KEY (submission_id, attachment_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_evidence_audit_events (
                        event_id UUID PRIMARY KEY,
                        case_id UUID NOT NULL REFERENCES discord_evidence_cases(case_id) ON DELETE RESTRICT,
                        event_type VARCHAR(40) NOT NULL,
                        actor_discord_user_id BIGINT CHECK (actor_discord_user_id > 0),
                        detail TEXT NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_discord_evidence_audit_case
                    ON discord_evidence_audit_events (case_id, created_at)
                    """);
            validateSchema(connection);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize Discord evidence schema", exception);
        }
    }

    private static void validateSchema(Connection connection) throws SQLException {
        PostgresSchemaVerifier.requireRelations(
                connection,
                "discord_evidence_settings",
                "discord_evidence_cases",
                "idx_discord_evidence_cases_pending",
                "discord_evidence_submissions",
                "idx_discord_evidence_submissions_case",
                "discord_evidence_attachments",
                "discord_evidence_audit_events",
                "idx_discord_evidence_audit_case"
        );
        PostgresSchemaVerifier.requireColumns(
                connection,
                "discord_evidence_cases",
                "case_id",
                "punishment_identifier",
                "punished_player_id",
                "issuing_discord_user_id",
                "status",
                "thread_id",
                "active_submission_id",
                "active_change_request_message_id",
                "created_at"
        );
    }

    private static Optional<EvidenceCase> findCaseByIdentifier(
            Connection connection,
            String punishmentIdentifier
    ) throws SQLException {
        String sql = "SELECT " + CASE_COLUMNS
                + " FROM discord_evidence_cases WHERE punishment_identifier = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, punishmentIdentifier);
            return findCase(statement);
        }
    }

    private static Optional<EvidenceCase> findCaseById(Connection connection, UUID caseId) throws SQLException {
        String sql = "SELECT " + CASE_COLUMNS + " FROM discord_evidence_cases WHERE case_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, caseId);
            return findCase(statement);
        }
    }

    private static Optional<EvidenceSubmission> findLatestSubmission(
            Connection connection,
            UUID caseId
    ) throws SQLException {
        String sql = """
                SELECT submission_id, case_id, submitted_by_discord_user_id, incident_description,
                       proof_description, additional_context, external_link, evidence_message_id,
                       submitted_at
                FROM discord_evidence_submissions
                WHERE case_id = ?
                ORDER BY submitted_at DESC, submission_id DESC
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, caseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                long submissionId = resultSet.getLong("submission_id");
                return Optional.of(new EvidenceSubmission(
                        submissionId,
                        resultSet.getObject("case_id", UUID.class),
                        resultSet.getLong("submitted_by_discord_user_id"),
                        resultSet.getString("incident_description"),
                        resultSet.getString("proof_description"),
                        resultSet.getString("additional_context"),
                        resultSet.getString("external_link"),
                        resultSet.getLong("evidence_message_id"),
                        resultSet.getTimestamp("submitted_at").toInstant(),
                        findAttachments(connection, submissionId)
                ));
            }
        }
    }

    private static List<EvidenceAttachment> findAttachments(
            Connection connection,
            long submissionId
    ) throws SQLException {
        String sql = """
                SELECT attachment_id, file_name, content_type, size_bytes, sha256
                FROM discord_evidence_attachments
                WHERE submission_id = ?
                ORDER BY attachment_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<EvidenceAttachment> attachments = new ArrayList<>();
                while (resultSet.next()) {
                    attachments.add(new EvidenceAttachment(
                            resultSet.getLong("attachment_id"),
                            resultSet.getString("file_name"),
                            resultSet.getString("content_type"),
                            resultSet.getLong("size_bytes"),
                            resultSet.getString("sha256")
                    ));
                }
                return List.copyOf(attachments);
            }
        }
    }

    private static Optional<EvidenceCase> findCase(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? Optional.of(mapCase(resultSet)) : Optional.empty();
        }
    }

    private static EvidenceCase mapCase(ResultSet resultSet) throws SQLException {
        Timestamp expiresAt = resultSet.getTimestamp("punishment_expires_at");
        return new EvidenceCase(
                resultSet.getObject("case_id", UUID.class),
                resultSet.getString("punishment_identifier"),
                resultSet.getObject("punished_player_id", UUID.class),
                resultSet.getString("punished_player_name"),
                resultSet.getObject("issuing_player_id", UUID.class),
                resultSet.getObject("issuing_discord_user_id", Long.class),
                resultSet.getString("preset_name"),
                resultSet.getInt("preset_application_number"),
                resultSet.getString("punishment_type"),
                resultSet.getString("reason"),
                resultSet.getTimestamp("punishment_created_at").toInstant(),
                expiresAt == null ? null : expiresAt.toInstant(),
                EvidenceCaseStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("guild_id", Long.class),
                resultSet.getObject("forum_channel_id", Long.class),
                resultSet.getObject("thread_id", Long.class),
                resultSet.getObject("starter_message_id", Long.class),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private static EvidenceSettings mapSettings(ResultSet resultSet) throws SQLException {
        return new EvidenceSettings(
                resultSet.getLong("guild_id"),
                resultSet.getLong("forum_channel_id"),
                resultSet.getLong("reviewer_role_id"),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static boolean submissionExists(Connection connection, long submissionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM discord_evidence_submissions WHERE submission_id = ?")) {
            statement.setLong(1, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean caseHasSubmission(Connection connection, UUID caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM discord_evidence_submissions WHERE case_id = ? LIMIT 1")) {
            statement.setObject(1, caseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void insertSubmission(Connection connection, EvidenceSubmission submission) throws SQLException {
        String sql = """
                INSERT INTO discord_evidence_submissions (
                    submission_id, case_id, submitted_by_discord_user_id, incident_description,
                    proof_description, additional_context, external_link, evidence_message_id,
                    submitted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, submission.submissionId());
            statement.setObject(2, submission.caseId());
            statement.setLong(3, submission.submittedByDiscordUserId());
            statement.setString(4, submission.incidentDescription());
            statement.setString(5, submission.proofDescription());
            statement.setString(6, submission.additionalContext());
            statement.setString(7, submission.externalLink());
            statement.setLong(8, submission.evidenceMessageId());
            statement.setTimestamp(9, Timestamp.from(submission.submittedAt()));
            statement.executeUpdate();
        }
    }

    private static void insertAttachments(Connection connection, EvidenceSubmission submission) throws SQLException {
        String sql = """
                INSERT INTO discord_evidence_attachments (
                    submission_id, attachment_id, file_name, content_type, size_bytes, sha256
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (EvidenceAttachment attachment : submission.attachments()) {
                statement.setLong(1, submission.submissionId());
                statement.setLong(2, attachment.attachmentId());
                statement.setString(3, attachment.fileName());
                statement.setString(4, attachment.contentType());
                statement.setLong(5, attachment.sizeBytes());
                statement.setString(6, attachment.sha256());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Optional<EvidenceCase> finishSubmission(
            Connection connection,
            EvidenceSubmission submission
    ) throws SQLException {
        String sql = """
                UPDATE discord_evidence_cases
                SET status = 'AWAITING_REVIEW', upload_resume_status = NULL,
                    active_submission_id = NULL, upload_started_at = NULL,
                    active_change_request_message_id = NULL
                WHERE case_id = ? AND status = 'UPLOADING' AND active_submission_id = ?
                RETURNING %s
                """.formatted(CASE_COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, submission.caseId());
            statement.setLong(2, submission.submissionId());
            return findCase(statement);
        }
    }

    private static void insertAuditEvent(
            Connection connection,
            UUID caseId,
            String eventType,
            Long actorDiscordUserId,
            String detail
    ) throws SQLException {
        String sql = """
                INSERT INTO discord_evidence_audit_events (
                    event_id, case_id, event_type, actor_discord_user_id, detail
                ) VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, caseId);
            statement.setString(3, eventType);
            statement.setObject(4, actorDiscordUserId);
            statement.setString(5, detail == null ? "" : detail);
            statement.executeUpdate();
        }
    }

    private static void setInstant(PreparedStatement statement, int index, Instant instant) throws SQLException {
        if (instant == null) {
            statement.setTimestamp(index, null);
        } else {
            statement.setTimestamp(index, Timestamp.from(instant));
        }
    }

    private static void rollback(Connection connection, SQLException exception) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }

    @Override
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(
                    InternalConstants.DATABASE_SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            )) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }
}
