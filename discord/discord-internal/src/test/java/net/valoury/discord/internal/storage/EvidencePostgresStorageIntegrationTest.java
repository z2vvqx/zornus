package net.valoury.discord.internal.storage;

import net.valoury.discord.api.evidence.EvidenceAttachment;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.api.evidence.EvidenceCaseRequest;
import net.valoury.discord.api.evidence.EvidenceCaseStatus;
import net.valoury.discord.api.evidence.EvidenceReviewDecision;
import net.valoury.discord.api.evidence.EvidenceService;
import net.valoury.discord.api.evidence.EvidenceSettings;
import net.valoury.discord.api.evidence.EvidenceSubmission;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "valoury.evidence.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class EvidencePostgresStorageIntegrationTest {
    private static final String DATABASE_NAME = "discord_evidence_integration";
    private static final String POSTGRESQL_ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String POSTGRESQL_URL = "jdbc:postgresql://localhost:5432/" + DATABASE_NAME;
    private static final String POSTGRESQL_USER = "postgres";
    private static final String POSTGRESQL_PASSWORD = "postword";

    private EvidencePostgresStorage storage;
    private EvidenceService evidenceService;
    private boolean databaseCreated;

    @BeforeAll
    void initializeFreshEvidenceStorage() throws Exception {
        createDisposableDatabase();
        storage = new EvidencePostgresStorage(POSTGRESQL_URL, POSTGRESQL_USER, POSTGRESQL_PASSWORD);
        evidenceService = new EvidenceService(storage);
    }

    @AfterAll
    void closeEvidenceStorage() throws Exception {
        if (storage != null) {
            storage.close();
        }
        if (databaseCreated) {
            dropDisposableDatabase();
        }
    }

    @Test
    void persistsTheCompleteEvidenceLifecycleTransactionally() throws Exception {
        Instant punishmentTime = Instant.parse("2026-08-05T10:00:00Z");
        EvidenceCaseRequest request = new EvidenceCaseRequest(
                "AB12",
                UUID.randomUUID(),
                "Player_1",
                UUID.randomUUID(),
                1001L,
                "profane-language",
                1,
                "MUTE",
                "Profane Language",
                punishmentTime,
                punishmentTime.plus(Duration.ofDays(1))
        );
        EvidenceCase createdCase = evidenceService.createCase(request).join();
        assertEquals(createdCase.caseId(), evidenceService.createCase(request).join().caseId());

        evidenceService.saveSettings(new EvidenceSettings(10L, 20L, 30L, punishmentTime)).join();
        assertEquals(20L, evidenceService.findSettings().join().orElseThrow().forumChannelId());

        EvidenceCase claimedCase = evidenceService.claimPendingCases(
                punishmentTime,
                Duration.ofMinutes(2),
                10
        ).join().getFirst();
        assertEquals(EvidenceCaseStatus.CREATING_THREAD, claimedCase.status());
        EvidenceCase activeCase = evidenceService.activateCase(
                claimedCase.caseId(),
                10L,
                20L,
                40L,
                50L
        ).join().orElseThrow();
        assertEquals(EvidenceCaseStatus.AWAITING_EVIDENCE, activeCase.status());
        assertFalse(evidenceService.beginSubmission(
                activeCase.caseId(),
                60L,
                9999L,
                false,
                punishmentTime.plusSeconds(1),
                Duration.ofMinutes(10)
        ).join());
        assertTrue(evidenceService.beginSubmission(
                activeCase.caseId(),
                61L,
                1001L,
                false,
                punishmentTime.plusSeconds(2),
                Duration.ofMinutes(10)
        ).join());

        EvidenceCase awaitingReview = evidenceService.completeSubmission(new EvidenceSubmission(
                61L,
                activeCase.caseId(),
                1001L,
                "The player sent prohibited chat content.",
                "The external recording shows the full conversation.",
                "",
                "https://example.com/proof",
                70L,
                punishmentTime.plusSeconds(3),
                List.of()
        )).join().orElseThrow();
        assertEquals(EvidenceCaseStatus.AWAITING_REVIEW, awaitingReview.status());
        assertEquals(
                "https://example.com/proof",
                evidenceService.findLatestSubmission(activeCase.caseId()).join().orElseThrow().externalLink()
        );
        assertFalse(evidenceService.beginSubmission(
                activeCase.caseId(),
                62L,
                1001L,
                false,
                punishmentTime.plusSeconds(4),
                Duration.ofMinutes(10)
        ).join());

        EvidenceCase needsChanges = evidenceService.reviewCase(
                activeCase.caseId(),
                2002L,
                EvidenceReviewDecision.REQUEST_CHANGES,
                "Include the message immediately before the violation.",
                punishmentTime.plusSeconds(5)
        ).join().orElseThrow();
        assertEquals(EvidenceCaseStatus.NEEDS_CHANGES, needsChanges.status());
        assertTrue(evidenceService.recordActiveChangeRequestMessage(activeCase.caseId(), 7001L).join());
        assertEquals(
                7001L,
                evidenceService.findActiveChangeRequestMessageId(activeCase.caseId()).join().orElseThrow()
        );
        EvidenceCase editedChangeRequest = evidenceService.editChangeRequest(
                activeCase.caseId(),
                7001L,
                2002L,
                "Include the messages immediately before and after the violation.",
                punishmentTime.plusSeconds(6)
        ).join().orElseThrow();
        assertEquals(EvidenceCaseStatus.NEEDS_CHANGES, editedChangeRequest.status());
        assertEquals(
                "Include the messages immediately before and after the violation.",
                findReviewReason(activeCase.caseId())
        );
        assertFalse(evidenceService.beginSubmission(
                activeCase.caseId(),
                63L,
                2002L,
                true,
                punishmentTime.plusSeconds(7),
                Duration.ofMinutes(10)
        ).join());
        assertTrue(evidenceService.beginSubmissionEdit(
                activeCase.caseId(),
                64L,
                1001L,
                false,
                punishmentTime.plusSeconds(8),
                Duration.ofMinutes(10)
        ).join());

        EvidenceCase revisedReview = evidenceService.completeSubmission(new EvidenceSubmission(
                64L,
                activeCase.caseId(),
                1001L,
                "The player sent prohibited chat content.",
                "The uploaded log includes the requested surrounding messages.",
                "Updated by the issuing staff member.",
                "https://example.com/proof",
                70L,
                punishmentTime.plusSeconds(9),
                List.of()
        )).join().orElseThrow();
        assertEquals(EvidenceCaseStatus.AWAITING_REVIEW, revisedReview.status());
        assertTrue(evidenceService.findActiveChangeRequestMessageId(activeCase.caseId()).join().isEmpty());

        EvidenceCase secondNeedsChanges = evidenceService.reviewCase(
                activeCase.caseId(),
                2002L,
                EvidenceReviewDecision.REQUEST_CHANGES,
                "Add the relevant plain-text log as a preserved attachment.",
                punishmentTime.plusSeconds(10)
        ).join().orElseThrow();
        assertEquals(EvidenceCaseStatus.NEEDS_CHANGES, secondNeedsChanges.status());
        assertTrue(evidenceService.recordActiveChangeRequestMessage(activeCase.caseId(), 7002L).join());
        assertTrue(evidenceService.editChangeRequest(
                activeCase.caseId(),
                7001L,
                2002L,
                "This stale request must not edit the active review.",
                punishmentTime.plusSeconds(11)
        ).join().isEmpty());
        assertTrue(evidenceService.beginSubmissionEdit(
                activeCase.caseId(),
                65L,
                2002L,
                true,
                punishmentTime.plusSeconds(11),
                Duration.ofMinutes(10)
        ).join());

        EvidenceCase reviewerRevisedReview = evidenceService.completeSubmission(new EvidenceSubmission(
                65L,
                activeCase.caseId(),
                2002L,
                "The player sent prohibited chat content.",
                "The uploaded log includes the requested surrounding messages.",
                "The reviewer added the preserved log missed in the change request.",
                null,
                70L,
                punishmentTime.plusSeconds(12),
                List.of(new EvidenceAttachment(
                        72L,
                        "01-chat.log",
                        "text/plain",
                        128L,
                        "0".repeat(64)
                ))
        )).join().orElseThrow();
        assertEquals(EvidenceCaseStatus.AWAITING_REVIEW, reviewerRevisedReview.status());
        EvidenceSubmission latestSubmission = evidenceService.findLatestSubmission(
                activeCase.caseId()).join().orElseThrow();
        assertEquals(70L, latestSubmission.evidenceMessageId());
        assertEquals("01-chat.log", latestSubmission.attachments().getFirst().fileName());

        EvidenceCase acceptedCase = evidenceService.reviewCase(
                activeCase.caseId(),
                2002L,
                EvidenceReviewDecision.ACCEPT,
                "",
                punishmentTime.plusSeconds(13)
        ).join().orElseThrow();
        assertEquals(EvidenceCaseStatus.ACCEPTED, acceptedCase.status());
        assertEquals(9, countAuditEvents(activeCase.caseId()));
    }

    private int countAuditEvents(UUID caseId) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL_URL,
                POSTGRESQL_USER,
                POSTGRESQL_PASSWORD
        ); PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM discord_evidence_audit_events WHERE case_id = ?")) {
            statement.setObject(1, caseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private String findReviewReason(UUID caseId) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL_URL,
                POSTGRESQL_USER,
                POSTGRESQL_PASSWORD
        ); PreparedStatement statement = connection.prepareStatement(
                "SELECT review_reason FROM discord_evidence_cases WHERE case_id = ?")) {
            statement.setObject(1, caseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private void createDisposableDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL_ADMIN_URL,
                POSTGRESQL_USER,
                POSTGRESQL_PASSWORD
        ); PreparedStatement existenceStatement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = ?)")) {
            existenceStatement.setString(1, DATABASE_NAME);
            try (ResultSet resultSet = existenceStatement.executeQuery()) {
                resultSet.next();
                if (resultSet.getBoolean(1)) {
                    throw new IllegalStateException("Refusing to use existing " + DATABASE_NAME + " database");
                }
            }
            try (Statement createStatement = connection.createStatement()) {
                createStatement.execute("""
                        CREATE DATABASE discord_evidence_integration
                        OWNER postgres
                        TEMPLATE template0
                        ENCODING 'UTF8'
                        """);
                databaseCreated = true;
            }
        }
    }

    private void dropDisposableDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL_ADMIN_URL,
                POSTGRESQL_USER,
                POSTGRESQL_PASSWORD
        ); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE discord_evidence_integration WITH (FORCE)");
            databaseCreated = false;
        }
    }
}
