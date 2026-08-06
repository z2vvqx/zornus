package net.valoury.discord.bot.evidence.message;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.filedisplay.FileDisplay;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.api.evidence.EvidenceCaseStatus;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;
import net.valoury.discord.bot.evidence.EvidenceButtonIdentifier;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class EvidenceMessageFactory {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    public MessageCreateData starter(EvidenceCase evidenceCase) {
        MessageCreateBuilder builder = new MessageCreateBuilder()
                .setComponents(starterContainer(evidenceCase))
                .useComponentsV2(true);
        if (evidenceCase.issuingDiscordUserId() != null) {
            builder.mentionUsers(Long.toString(evidenceCase.issuingDiscordUserId()));
        }
        return builder.build();
    }

    public MessageEditData updatedStarter(EvidenceCase evidenceCase) {
        return new MessageEditBuilder()
                .setComponents(starterContainer(evidenceCase))
                .useComponentsV2(true)
                .build();
    }

    public MessageCreateData submission(
            EvidenceCase evidenceCase,
            long submittingDiscordUserId,
            String incidentDescription,
            String proofDescription,
            String additionalContext,
            String externalLink,
            List<PreparedEvidenceFile> preparedFiles
    ) {
        List<EvidenceFileSummary> fileSummaries = preparedFiles.stream()
                .map(file -> new EvidenceFileSummary(file.fileName(), file.sha256()))
                .toList();
        List<FileUpload> uploads = preparedFiles.stream()
                .map(PreparedEvidenceFile::createUpload)
                .toList();
        List<FileDisplay> fileDisplays = preparedFiles.stream()
                .map(file -> FileDisplay.fromFileName(file.fileName()))
                .toList();
        return new MessageCreateBuilder()
                .setComponents(submissionContainer(
                        evidenceCase,
                        submittingDiscordUserId,
                        false,
                        incidentDescription,
                        proofDescription,
                        additionalContext,
                        externalLink,
                        fileSummaries,
                        fileDisplays
                ))
                .setFiles(uploads)
                .useComponentsV2(true)
                .build();
    }

    public MessageEditData editedSubmission(
            EvidenceCase evidenceCase,
            long editingDiscordUserId,
            String incidentDescription,
            String proofDescription,
            String additionalContext,
            String externalLink,
            List<PreparedEvidenceFile> replacementFiles
    ) {
        List<EvidenceFileSummary> fileSummaries = replacementFiles.stream()
                .map(file -> new EvidenceFileSummary(file.fileName(), file.sha256()))
                .toList();
        List<FileUpload> attachments = replacementFiles.stream()
                .map(PreparedEvidenceFile::createUpload)
                .toList();
        List<FileDisplay> fileDisplays = replacementFiles.stream()
                .map(file -> FileDisplay.fromFileName(file.fileName()))
                .toList();
        return new MessageEditBuilder()
                .setComponents(submissionContainer(
                        evidenceCase,
                        editingDiscordUserId,
                        true,
                        incidentDescription,
                        proofDescription,
                        additionalContext,
                        externalLink,
                        fileSummaries,
                        fileDisplays
                ))
                .setAttachments(attachments)
                .setReplace(true)
                .useComponentsV2(true)
                .build();
    }

    public MessageEditData disabledChangeRequestEdit(MessageComponentTree components, UUID caseId) {
        String editButtonIdentifier = EvidenceButtonIdentifier.editChangeRequest(caseId);
        MessageComponentTree disabledComponents = components.replace(ComponentReplacer.of(
                Button.class,
                button -> Objects.equals(button.getCustomId(), editButtonIdentifier),
                button -> button.asDisabled()
        ));
        return new MessageEditBuilder()
                .setComponents(disabledComponents.getComponents())
                .useComponentsV2(true)
                .build();
    }

    public MessageCreateData changesRequested(EvidenceCase evidenceCase, long reviewerId, String reason) {
        MessageCreateBuilder builder = new MessageCreateBuilder()
                .setComponents(changeRequestContainer(evidenceCase, reviewerId, reason))
                .useComponentsV2(true);
        if (evidenceCase.issuingDiscordUserId() != null) {
            builder.mentionUsers(Long.toString(evidenceCase.issuingDiscordUserId()));
        }
        return builder.build();
    }

    public MessageEditData editedChangeRequest(EvidenceCase evidenceCase, long reviewerId, String reason) {
        return new MessageEditBuilder()
                .setComponents(changeRequestContainer(evidenceCase, reviewerId, reason))
                .useComponentsV2(true)
                .build();
    }

    private Container changeRequestContainer(EvidenceCase evidenceCase, long reviewerId, String reason) {
        String issuerMention = evidenceCase.issuingDiscordUserId() == null
                ? "The issuing staff member"
                : "<@" + evidenceCase.issuingDiscordUserId() + ">";
        String text = """
                ## Evidence changes requested

                %s must update the evidence for `#%s`.

                **Reviewer:** <@%d>
                **Required changes:** %s
                """.formatted(
                issuerMention,
                evidenceCase.punishmentIdentifier(),
                reviewerId,
                escape(reason)
        ).strip();
        return Container.of(
                TextDisplay.of(text),
                ActionRow.of(Button.secondary(
                        EvidenceButtonIdentifier.editChangeRequest(evidenceCase.caseId()),
                        "Edit Change Request"
                ))
        );
    }

    public String threadName(EvidenceCase evidenceCase) {
        String rawName = "#%s | %s | %s-%d".formatted(
                evidenceCase.punishmentIdentifier(),
                evidenceCase.punishedPlayerName(),
                evidenceCase.presetName(),
                evidenceCase.presetApplicationNumber()
        );
        return rawName.length() <= 100 ? rawName : rawName.substring(0, 100);
    }

    private Container submissionContainer(
            EvidenceCase evidenceCase,
            long actingDiscordUserId,
            boolean edited,
            String incidentDescription,
            String proofDescription,
            String additionalContext,
            String externalLink,
            List<EvidenceFileSummary> files,
            List<FileDisplay> fileDisplays
    ) {
        if (files.size() != fileDisplays.size()) {
            throw new IllegalArgumentException("Evidence file summaries and displays must have equal sizes");
        }
        StringBuilder text = new StringBuilder()
                .append(edited ? "## Evidence updated for #" : "## Evidence submitted for #")
                .append(evidenceCase.punishmentIdentifier())
                .append(edited ? "\n\n**Last updated by:** <@" : "\n\n**Submitted by:** <@")
                .append(actingDiscordUserId)
                .append(">\n\n**What happened**\n")
                .append(escape(incidentDescription))
                .append("\n\n**What the proof demonstrates**\n")
                .append(escape(proofDescription));
        if (!additionalContext.isBlank()) {
            text.append("\n\n**Additional context**\n").append(escape(additionalContext));
        }
        if (externalLink != null) {
            text.append("\n\n**External evidence — not preserved by Valoury**\n<")
                    .append(externalLink)
                    .append('>');
        }
        if (!files.isEmpty()) {
            text.append("\n\n**File integrity**");
            for (EvidenceFileSummary file : files) {
                text.append("\n`")
                        .append(escapeCode(file.fileName()))
                        .append("` — `")
                        .append(file.sha256())
                        .append('`');
            }
        }

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of(text.toString()));
        for (int index = 0; index < fileDisplays.size(); index++) {
            children.add(fileDisplays.get(index)
                    .withUniqueId(EvidenceBotConstants.FILE_COMPONENT_ID_BASE + index));
        }
        children.add(ActionRow.of(
                Button.success(EvidenceButtonIdentifier.accept(evidenceCase.caseId()), "Accept Evidence"),
                Button.danger(EvidenceButtonIdentifier.requestChanges(evidenceCase.caseId()), "Request Changes")
        ));
        return Container.of(children);
    }

    private Container starterContainer(EvidenceCase evidenceCase) {
        String issuer = evidenceCase.issuingDiscordUserId() == null
                ? "Console"
                : "<@" + evidenceCase.issuingDiscordUserId() + ">";
        String expiry = evidenceCase.punishmentExpiresAt() == null
                ? "Permanent"
                : TIME_FORMATTER.format(evidenceCase.punishmentExpiresAt());
        String text = """
                ## Punishment evidence #%s

                **Player:** `%s`
                **Preset:** `%s`
                **Application:** `%d`
                **Action:** `%s`
                **Expires:** `%s`
                **Reason:** %s
                **Issued by:** %s
                **Status:** `%s`

                Explain what happened, identify the relevant portion of the proof, include timestamps,
                and upload files, provide an external HTTPS link, or both.
                """.formatted(
                evidenceCase.punishmentIdentifier(),
                escapeCode(evidenceCase.punishedPlayerName()),
                escapeCode(evidenceCase.presetName()),
                evidenceCase.presetApplicationNumber(),
                escapeCode(evidenceCase.punishmentType()),
                escapeCode(expiry),
                escape(evidenceCase.reason()),
                issuer,
                displayStatus(evidenceCase.status())
        ).strip();
        if (evidenceCase.status() == EvidenceCaseStatus.CREATING_THREAD
                || evidenceCase.status() == EvidenceCaseStatus.AWAITING_EVIDENCE) {
            return Container.of(
                    TextDisplay.of(text),
                    ActionRow.of(Button.primary(
                            EvidenceButtonIdentifier.submit(evidenceCase.caseId()),
                            "Submit Evidence"
                    ))
            );
        }
        if (evidenceCase.status() == EvidenceCaseStatus.AWAITING_REVIEW
                || evidenceCase.status() == EvidenceCaseStatus.NEEDS_CHANGES) {
            return Container.of(
                    TextDisplay.of(text),
                    ActionRow.of(
                            Button.primary(
                                    EvidenceButtonIdentifier.submit(evidenceCase.caseId()),
                                    "Submit Evidence"
                            ).asDisabled(),
                            Button.secondary(
                                    EvidenceButtonIdentifier.edit(evidenceCase.caseId()),
                                    "Edit Evidence"
                            )
                    )
            );
        }
        return Container.of(TextDisplay.of(text));
    }

    private static String displayStatus(EvidenceCaseStatus status) {
        return switch (status) {
            case PENDING_THREAD, CREATING_THREAD -> "Creating Thread";
            case AWAITING_EVIDENCE -> "Awaiting Evidence";
            case UPLOADING -> "Uploading";
            case AWAITING_REVIEW -> "Awaiting Review";
            case ACCEPTED -> "Accepted";
            case NEEDS_CHANGES -> "Needs Changes";
        };
    }

    private static String escape(String value) {
        return MarkdownSanitizer.escape(value.strip());
    }

    private static String escapeCode(String value) {
        return value.replace("`", "'").strip();
    }

    private record EvidenceFileSummary(String fileName, String sha256) {
    }
}
