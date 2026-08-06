package net.valoury.discord.bot.evidence.message;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.filedisplay.FileDisplay;
import net.dv8tion.jda.api.components.tree.ComponentTree;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.api.evidence.EvidenceCaseStatus;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;
import net.valoury.discord.bot.evidence.EvidenceButtonIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceMessageFactoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsValidComponentsV2StarterAndSubmissionMessages() throws Exception {
        EvidenceMessageFactory factory = new EvidenceMessageFactory();
        EvidenceCase evidenceCase = evidenceCase();
        Path proofPath = temporaryDirectory.resolve("01-proof.png");
        Files.write(proofPath, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        PreparedEvidenceFile proofFile = new PreparedEvidenceFile(
                proofPath,
                "01-proof.png",
                "image/png",
                Files.size(proofPath),
                "0".repeat(64)
        );

        try (var starter = factory.starter(evidenceCase);
             var submission = factory.submission(
                     evidenceCase,
                     123L,
                     "The player sent prohibited chat content.",
                     "The screenshot shows the complete message and timestamp.",
                     "No additional context.",
                     "https://example.com/proof",
                     List.of(proofFile)
             )) {
            assertTrue(starter.isUsingComponentsV2());
            assertTrue(submission.isUsingComponentsV2());
            assertEquals(1, submission.getAttachments().size());
            assertEquals(
                    EvidenceBotConstants.FILE_COMPONENT_ID_BASE,
                    ComponentTree.forMessage(submission.getComponents())
                            .findAll(FileDisplay.class)
                            .getFirst()
                            .getUniqueId()
            );
        }
    }

    @Test
    void awaitingReviewStarterDisablesSubmitAndOffersEdit() {
        try (var starter = new EvidenceMessageFactory().starter(
                evidenceCase(EvidenceCaseStatus.AWAITING_REVIEW))) {
            List<Button> buttons = ComponentTree.forMessage(starter.getComponents()).findAll(Button.class);

            assertEquals(2, buttons.size());
            assertEquals("Submit Evidence", buttons.getFirst().getLabel());
            assertTrue(buttons.getFirst().isDisabled());
            assertEquals("Edit Evidence", buttons.get(1).getLabel());
            assertTrue(buttons.get(1).isEnabled());
        }
    }

    @Test
    void attachmentFreeEditDoesNotReferencePreviousFiles() {
        EvidenceCase evidenceCase = evidenceCase(EvidenceCaseStatus.AWAITING_REVIEW);
        try (var edit = new EvidenceMessageFactory().editedSubmission(
                evidenceCase,
                456L,
                "The player sent prohibited chat content.",
                "The screenshot shows the complete message and timestamp.",
                "",
                null,
                List.of()
        )) {
            assertEquals(0, edit.getAttachments().size());
            assertEquals(0, edit.getFiles().size());
            assertEquals(0, edit.getAllDistinctFiles().size());
            assertEquals(0, ComponentTree.forMessage(edit.getComponents()).findAll(FileDisplay.class).size());
        }
    }

    @Test
    void changesRequestedNotificationOffersChangeRequestEditing() {
        EvidenceCase evidenceCase = evidenceCase(EvidenceCaseStatus.NEEDS_CHANGES);

        try (var notification = new EvidenceMessageFactory().changesRequested(
                evidenceCase,
                456L,
                "Include the missing surrounding messages."
        )) {
            List<Button> buttons = ComponentTree.forMessage(notification.getComponents()).findAll(Button.class);

            assertEquals(1, buttons.size());
            assertEquals("Edit Change Request", buttons.getFirst().getLabel());
            assertEquals(
                    EvidenceButtonIdentifier.editChangeRequest(evidenceCase.caseId()),
                    buttons.getFirst().getCustomId()
            );
            assertTrue(buttons.getFirst().isEnabled());

            try (var disabledNotification = new EvidenceMessageFactory().disabledChangeRequestEdit(
                    ComponentTree.forMessage(notification.getComponents()),
                    evidenceCase.caseId()
            )) {
                Button disabledButton = ComponentTree.forMessage(disabledNotification.getComponents())
                        .findAll(Button.class)
                        .getFirst();
                assertTrue(disabledButton.isDisabled());
            }
        }
    }

    @Test
    void threadNameCarriesTheUniquePunishmentIdentifier() {
        String threadName = new EvidenceMessageFactory().threadName(evidenceCase());

        assertTrue(threadName.startsWith("#AB12 | Player_1 |"));
        assertTrue(threadName.length() <= 100);
    }

    private static EvidenceCase evidenceCase() {
        return evidenceCase(EvidenceCaseStatus.CREATING_THREAD);
    }

    private static EvidenceCase evidenceCase(EvidenceCaseStatus status) {
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
                status,
                null,
                null,
                null,
                null,
                Instant.parse("2026-08-05T10:00:00Z")
        );
    }
}
