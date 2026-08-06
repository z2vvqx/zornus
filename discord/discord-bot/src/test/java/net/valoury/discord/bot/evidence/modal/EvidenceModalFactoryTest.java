package net.valoury.discord.bot.evidence.modal;

import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.api.evidence.EvidenceCaseStatus;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceModalFactoryTest {
    @Test
    void submissionModalUsesAllFiveAvailableComponentSlots() {
        var modal = new EvidenceModalFactory().submission(evidenceCase());

        assertEquals(5, modal.getComponents().size());
        assertEquals("Evidence #AB12", modal.getTitle());
    }

    @Test
    void changeRequestModalContainsTheReasonField() {
        var modal = new EvidenceModalFactory().requestChanges(evidenceCase());

        assertEquals(1, modal.getComponents().size());
    }

    @Test
    void editModalMakesEveryFieldOptionalBecauseBlankMeansUnchanged() {
        var modal = new EvidenceModalFactory().edit(evidenceCase(), 0);

        assertEquals("Edit evidence #AB12", modal.getTitle());
        assertEquals(5, modal.getComponents().size());
        modal.getComponentTree().findAll(TextInput.class)
                .forEach(textInput -> assertFalse(textInput.isRequired()));
        assertFalse(modal.getComponentTree().findAll(AttachmentUpload.class).getFirst().isRequired());
    }

    @Test
    void editModalRequiresEveryPreviousAttachmentToBeReuploaded() {
        var modal = new EvidenceModalFactory().edit(evidenceCase(), 2);

        AttachmentUpload attachmentUpload = modal.getComponentTree()
                .findAll(AttachmentUpload.class)
                .getFirst();
        assertTrue(attachmentUpload.isRequired());
        assertEquals(2, attachmentUpload.getMinValues());
        assertEquals(EvidenceBotConstants.MAXIMUM_ATTACHMENTS, attachmentUpload.getMaxValues());
        assertEquals(
                "Re-upload all 2 current proof files. These uploads replace the previous file set.",
                modal.getComponentTree().findAll(Label.class).stream()
                        .filter(label -> label.getLabel().equals("Replacement proof files"))
                        .findFirst()
                        .orElseThrow()
                        .getDescription()
        );
    }

    @Test
    void editModalRejectsAnUnsupportedPreviousAttachmentCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvidenceModalFactory().edit(
                        evidenceCase(),
                        EvidenceBotConstants.MAXIMUM_ATTACHMENTS + 1
                )
        );
    }

    @Test
    void changeRequestEditModalOnlyEditsTheRequestedChanges() {
        var modal = new EvidenceModalFactory().editChangeRequest(evidenceCase(), 987654321L);

        assertEquals("Edit change request for #AB12", modal.getTitle());
        assertEquals(1, modal.getComponents().size());
        assertEquals(
                987654321L,
                net.valoury.discord.bot.evidence.EvidenceModalIdentifier.parseEditChangeRequest(
                        modal.getId()
                ).orElseThrow().messageId()
        );
    }

    private static EvidenceCase evidenceCase() {
        return new EvidenceCase(
                UUID.randomUUID(),
                "AB12",
                UUID.randomUUID(),
                "Player_1",
                UUID.randomUUID(),
                123L,
                "profane-language",
                1,
                "MUTE",
                "Profane Language",
                Instant.parse("2026-08-05T10:00:00Z"),
                Instant.parse("2026-08-06T10:00:00Z"),
                EvidenceCaseStatus.AWAITING_EVIDENCE,
                1L,
                2L,
                3L,
                4L,
                Instant.parse("2026-08-05T10:00:00Z")
        );
    }
}
