package net.valoury.discord.bot.evidence.modal;

import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;
import net.valoury.discord.bot.evidence.EvidenceModalIdentifier;

public final class EvidenceModalFactory {
    public Modal submission(EvidenceCase evidenceCase) {
        TextInput incident = TextInput.create(EvidenceBotConstants.INCIDENT_FIELD, TextInputStyle.PARAGRAPH)
                .setRequiredRange(20, 1000)
                .setPlaceholder("Describe exactly what happened.")
                .build();
        TextInput proof = TextInput.create(EvidenceBotConstants.PROOF_FIELD, TextInputStyle.PARAGRAPH)
                .setRequiredRange(10, 1000)
                .setPlaceholder("Explain what proves the violation and include timestamps.")
                .build();
        TextInput context = TextInput.create(EvidenceBotConstants.CONTEXT_FIELD, TextInputStyle.PARAGRAPH)
                .setRequired(false)
                .setMaxLength(1000)
                .setPlaceholder("Add any other relevant context.")
                .build();
        TextInput link = TextInput.create(EvidenceBotConstants.LINK_FIELD, TextInputStyle.SHORT)
                .setRequired(false)
                .setMaxLength(1000)
                .setPlaceholder("https://...")
                .build();
        AttachmentUpload files = AttachmentUpload.create(EvidenceBotConstants.FILES_FIELD)
                .setRequired(false)
                .setRequiredRange(0, EvidenceBotConstants.MAXIMUM_ATTACHMENTS)
                .build();
        return Modal.create(
                        EvidenceModalIdentifier.submission(evidenceCase.caseId()),
                        "Evidence #" + evidenceCase.punishmentIdentifier()
                )
                .addComponents(
                        Label.of("What happened?", incident),
                        Label.of("What does the proof demonstrate?", proof),
                        Label.of("Additional context", context),
                        Label.of("External evidence link", link),
                        Label.of("Proof files", "Upload files or provide a link above.", files)
                )
                .build();
    }

    public Modal edit(EvidenceCase evidenceCase, int previousAttachmentCount) {
        if (previousAttachmentCount < 0
                || previousAttachmentCount > EvidenceBotConstants.MAXIMUM_ATTACHMENTS) {
            throw new IllegalArgumentException(
                    "Previous attachment count must be between 0 and "
                            + EvidenceBotConstants.MAXIMUM_ATTACHMENTS
            );
        }
        TextInput incident = optionalTextInput(
                EvidenceBotConstants.INCIDENT_FIELD,
                TextInputStyle.PARAGRAPH,
                20,
                "Leave blank to keep the current incident description."
        );
        TextInput proof = optionalTextInput(
                EvidenceBotConstants.PROOF_FIELD,
                TextInputStyle.PARAGRAPH,
                10,
                "Leave blank to keep the current proof explanation."
        );
        TextInput context = optionalTextInput(
                EvidenceBotConstants.CONTEXT_FIELD,
                TextInputStyle.PARAGRAPH,
                0,
                "Leave blank to keep the current additional context."
        );
        TextInput link = optionalTextInput(
                EvidenceBotConstants.LINK_FIELD,
                TextInputStyle.SHORT,
                0,
                "Leave blank to keep the current HTTPS link."
        );
        boolean requiresAttachmentReupload = previousAttachmentCount > 0;
        AttachmentUpload files = AttachmentUpload.create(EvidenceBotConstants.FILES_FIELD)
                .setRequired(requiresAttachmentReupload)
                .setRequiredRange(previousAttachmentCount, EvidenceBotConstants.MAXIMUM_ATTACHMENTS)
                .build();
        String attachmentNoun = previousAttachmentCount == 1 ? "file" : "files";
        String attachmentInstructions = requiresAttachmentReupload
                ? "Re-upload all " + previousAttachmentCount
                        + " current proof " + attachmentNoun
                        + ". These uploads replace the previous file set."
                : "Optional. Any uploads become the complete proof file set.";
        return Modal.create(
                        EvidenceModalIdentifier.edit(evidenceCase.caseId()),
                        "Edit evidence #" + evidenceCase.punishmentIdentifier()
                )
                .addComponents(
                        Label.of("What happened?", "Blank keeps the current answer.", incident),
                        Label.of("What does the proof demonstrate?", "Blank keeps the current answer.", proof),
                        Label.of("Additional context", "Blank keeps the current answer.", context),
                        Label.of("External evidence link", "Blank keeps the current link.", link),
                        Label.of(
                                "Replacement proof files",
                                attachmentInstructions,
                                files
                        )
                )
                .build();
    }

    public Modal requestChanges(EvidenceCase evidenceCase) {
        TextInput reason = TextInput.create(
                        EvidenceBotConstants.CHANGES_REASON_FIELD,
                        TextInputStyle.PARAGRAPH
                )
                .setRequiredRange(10, 1000)
                .setPlaceholder("Explain exactly what the issuer must correct or add.")
                .build();
        return Modal.create(
                        EvidenceModalIdentifier.requestChanges(evidenceCase.caseId()),
                        "Request changes for #" + evidenceCase.punishmentIdentifier()
                )
                .addComponents(Label.of("Required changes", reason))
                .build();
    }

    public Modal editChangeRequest(EvidenceCase evidenceCase, long messageId) {
        TextInput reason = TextInput.create(
                        EvidenceBotConstants.CHANGES_REASON_FIELD,
                        TextInputStyle.PARAGRAPH
                )
                .setRequiredRange(10, 1000)
                .setPlaceholder("Enter the complete corrected change request.")
                .build();
        return Modal.create(
                        EvidenceModalIdentifier.editChangeRequest(evidenceCase.caseId(), messageId),
                        "Edit change request for #" + evidenceCase.punishmentIdentifier()
                )
                .addComponents(Label.of("Revised required changes", reason))
                .build();
    }

    private static TextInput optionalTextInput(
            String componentIdentifier,
            TextInputStyle style,
            int minimumLength,
            String placeholder
    ) {
        TextInput.Builder builder = TextInput.create(componentIdentifier, style)
                .setRequired(false)
                .setMaxLength(1000)
                .setPlaceholder(placeholder);
        if (minimumLength > 0) {
            builder.setMinLength(minimumLength);
        }
        return builder.build();
    }
}
