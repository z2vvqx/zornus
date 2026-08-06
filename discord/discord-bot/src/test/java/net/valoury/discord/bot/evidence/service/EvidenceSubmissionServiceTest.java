package net.valoury.discord.bot.evidence.service;

import net.dv8tion.jda.api.entities.Message;
import net.valoury.discord.api.evidence.EvidenceAttachment;
import net.valoury.discord.bot.evidence.message.PreparedEvidenceFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceSubmissionServiceTest {
    @Test
    void acceptsAndNormalizesHttpsLinks() {
        assertEquals(
                "https://example.com/proof",
                EvidenceSubmissionService.normalizeExternalLink("  HTTPS://example.com/a/../proof  ")
        );
        assertNull(EvidenceSubmissionService.normalizeExternalLink("  "));
    }

    @Test
    void rejectsUnsafeExternalLinks() {
        assertThrows(IllegalArgumentException.class, () ->
                EvidenceSubmissionService.normalizeExternalLink("http://example.com/proof"));
        assertThrows(IllegalArgumentException.class, () ->
                EvidenceSubmissionService.normalizeExternalLink("https://user:password@example.com/proof"));
        assertThrows(IllegalArgumentException.class, () ->
                EvidenceSubmissionService.normalizeExternalLink("not-a-url"));
    }

    @Test
    void mapsComponentsV2ResolvedAttachmentIdentifiersWithoutFilenames() {
        PreparedEvidenceFile preparedFile = new PreparedEvidenceFile(
                Path.of("01-proof.png"),
                "01-proof.png",
                "image/png",
                128L,
                "0".repeat(64)
        );

        var mapped = EvidenceSubmissionService.mapAttachmentIds(List.of(987654321L), List.of(preparedFile));

        assertEquals(987654321L, mapped.getFirst().attachmentId());
        assertEquals("01-proof.png", mapped.getFirst().fileName());
        assertThrows(IllegalStateException.class, () -> EvidenceSubmissionService.mapAttachmentIds(
                List.of(1L, 1L),
                List.of(preparedFile, preparedFile)
        ));
    }

    @Test
    void requiresEveryPreviousAttachmentToBeReuploadedForAnEdit() {
        List<EvidenceAttachment> existingAttachments = List.of(
                new EvidenceAttachment(123L, "01-proof.png", "image/png", 128L, "0".repeat(64)),
                new EvidenceAttachment(124L, "02-proof.png", "image/png", 256L, "1".repeat(64))
        );

        assertTrue(EvidenceSubmissionService.isMissingRequiredReplacementAttachments(
                existingAttachments,
                List.of()
        ));
        assertTrue(EvidenceSubmissionService.isMissingRequiredReplacementAttachments(
                existingAttachments,
                Collections.nCopies(1, (Message.Attachment) null)
        ));
        assertFalse(EvidenceSubmissionService.isMissingRequiredReplacementAttachments(
                existingAttachments,
                Collections.nCopies(2, (Message.Attachment) null)
        ));
        assertFalse(EvidenceSubmissionService.isMissingRequiredReplacementAttachments(List.of(), List.of()));
    }

    @Test
    void editKeepsExistingValuesWhenFieldsAreBlank() {
        assertEquals(
                "Existing answer",
                EvidenceSubmissionService.keepExistingIfBlank("   ", "Existing answer")
        );
        assertEquals(
                "Existing answer",
                EvidenceSubmissionService.keepExistingIfBlank(null, "Existing answer")
        );
        assertEquals(
                "Updated answer",
                EvidenceSubmissionService.keepExistingIfBlank("  Updated answer  ", "Existing answer")
        );
    }

    @Test
    void detectsAnEditWithoutAnyRequestedChanges() {
        assertTrue(EvidenceSubmissionService.hasNoRequestedEdits("", " ", "", null, List.of()));
        assertFalse(EvidenceSubmissionService.hasNoRequestedEdits(
                "Updated incident",
                "",
                "",
                null,
                List.of()
        ));
    }

    @Test
    void normalizesUntouchedOptionalModalFieldsToEmptyText() {
        assertEquals("", EvidenceSubmissionService.normalizeOptionalText(null));
        assertEquals("Provided context", EvidenceSubmissionService.normalizeOptionalText("Provided context"));
    }
}
