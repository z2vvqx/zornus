package net.valoury.discord.bot.evidence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceIdentifierTest {
    @Test
    void usesTheConfiguredEvidenceForumTagIdentifiers() {
        assertEquals(1534503720853831702L, EvidenceBotConstants.AWAITING_EVIDENCE_TAG_ID);
        assertEquals(1534503859702202406L, EvidenceBotConstants.AWAITING_REVIEW_TAG_ID);
        assertEquals(1534503955680333855L, EvidenceBotConstants.ACCEPTED_TAG_ID);
        assertEquals(1534504129655738408L, EvidenceBotConstants.NEEDS_CHANGES_TAG_ID);
    }

    @Test
    void roundTripsEveryEvidenceInteractionIdentifier() {
        UUID caseId = UUID.randomUUID();

        assertEquals(caseId, EvidenceButtonIdentifier.parseSubmit(
                EvidenceButtonIdentifier.submit(caseId)).orElseThrow());
        assertEquals(caseId, EvidenceButtonIdentifier.parseAccept(
                EvidenceButtonIdentifier.accept(caseId)).orElseThrow());
        assertEquals(caseId, EvidenceButtonIdentifier.parseEdit(
                EvidenceButtonIdentifier.edit(caseId)).orElseThrow());
        assertEquals(caseId, EvidenceButtonIdentifier.parseEditChangeRequest(
                EvidenceButtonIdentifier.editChangeRequest(caseId)).orElseThrow());
        assertEquals(caseId, EvidenceButtonIdentifier.parseRequestChanges(
                EvidenceButtonIdentifier.requestChanges(caseId)).orElseThrow());
        assertEquals(caseId, EvidenceModalIdentifier.parseSubmission(
                EvidenceModalIdentifier.submission(caseId)).orElseThrow());
        assertEquals(caseId, EvidenceModalIdentifier.parseRequestChanges(
                EvidenceModalIdentifier.requestChanges(caseId)).orElseThrow());
        assertEquals(caseId, EvidenceModalIdentifier.parseEdit(
                EvidenceModalIdentifier.edit(caseId)).orElseThrow());
        var changeRequestEditTarget = EvidenceModalIdentifier.parseEditChangeRequest(
                EvidenceModalIdentifier.editChangeRequest(caseId, 987654321L)
        ).orElseThrow();
        assertEquals(caseId, changeRequestEditTarget.caseId());
        assertEquals(987654321L, changeRequestEditTarget.messageId());
    }

    @Test
    void rejectsMalformedAndUnrelatedIdentifiers() {
        assertTrue(EvidenceButtonIdentifier.parseSubmit("ticket:open").isEmpty());
        assertTrue(EvidenceButtonIdentifier.parseAccept("evidence:accept:not-a-uuid").isEmpty());
        assertTrue(EvidenceModalIdentifier.parseSubmission("evidence:submission:").isEmpty());
        assertTrue(EvidenceModalIdentifier.parseEditChangeRequest(
                "evidence:edit-changes-modal:not-a-uuid:not-a-message"
        ).isEmpty());
    }
}
