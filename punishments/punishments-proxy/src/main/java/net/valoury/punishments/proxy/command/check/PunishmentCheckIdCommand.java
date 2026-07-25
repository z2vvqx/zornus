package net.valoury.punishments.proxy.command.check;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.valoury.punishments.proxy.PunishmentProxyConstants;
import net.valoury.punishments.proxy.model.Punishment;
import net.valoury.punishments.proxy.service.PunishmentService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public final class PunishmentCheckIdCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentCheckIdCommand.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern(PunishmentProxyConstants.CHECK_DATE_FORMAT)
            .withZone(ZoneId.systemDefault());

    public static LiteralArgumentBuilder<CommandSource> create(PunishmentService punishmentService) {
        return BrigadierCommand
                .literalArgumentBuilder("id")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PunishmentProxyConstants.USAGE_CHECK_ID));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("identifier_code", StringArgumentType.word())
                        .executes(context -> handleCheckByIdentifier(context, punishmentService))
                );
    }

    private static int handleCheckByIdentifier(CommandContext<CommandSource> context, PunishmentService punishmentService) {
        CommandSource source = context.getSource();
        String identifier = StringArgumentType.getString(context, "identifier_code");

        punishmentService.fetchByIdentifier(identifier)
                .thenAccept(punishment -> {
                    if (punishment.isEmpty()) {
                        source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.ERROR_INVALID_IDENTIFIER));
                        return;
                    }
                    sendDetails(source, punishment.get(), punishmentService);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to check punishment {}", identifier, throwable);
                    source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void sendDetails(CommandSource source, Punishment punishment, PunishmentService punishmentService) {
        CompletableFuture<String> targetName = punishmentService.resolveUsername(punishment.punishedPlayerId());
        CompletableFuture<String> imposingName = punishmentService.resolveUsername(punishment.imposingPlayerId());
        CompletableFuture<String> revokingName = punishment.revokingPlayerId() == null
                ? CompletableFuture.completedFuture("")
                : punishmentService.resolveUsername(punishment.revokingPlayerId());

        targetName
                .thenCombine(imposingName, NamePair::new)
                .thenCombine(revokingName, (names, revoker) -> createDetails(punishment, names, revoker))
                .thenAccept(source::sendMessage)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to resolve details for punishment {}", punishment.identifier(), throwable);
                    source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });
    }

    private static Component createDetails(Punishment punishment, NamePair names, String revokerName) {
        TextComponent.Builder message = Component.text()
                .appendNewline()
                .append(detailLine("ID", punishment.identifier().toUpperCase())).appendNewline()
                .append(detailLine("Player", names.targetName())).appendNewline()
                .append(detailLine("Imposer", names.imposingName())).appendNewline()
                .append(detailLine("Type", punishment.type().toString())).appendNewline()
                .append(detailLine("Reason", punishment.reason())).appendNewline();
        if (punishment.presetName() != null && punishment.presetApplicationNumber() != null) {
            message.append(detailLine("Preset", punishment.presetName())).appendNewline()
                    .append(detailLine(
                            "Preset Application",
                            String.valueOf(punishment.presetApplicationNumber()))).appendNewline();
        }
        if (punishment.expiresAt() == null) {
            message.append(detailLine("Expires", PunishmentProxyConstants.PERMANENT)).appendNewline();
        } else {
            String label = punishment.expiresAt().isBefore(java.time.Instant.now()) ? "Expired" : "Expires";
            message.append(detailLine(label, DATE_FORMATTER.format(punishment.expiresAt()))).appendNewline();
        }
        message.append(detailLine("Active", String.valueOf(punishment.active()))).appendNewline();
        if (punishment.revokingPlayerId() != null) {
            message.append(detailLine("Revoked By", revokerName)).appendNewline();
            if (punishment.revokedAt() != null) {
                message.append(detailLine("Revoked At", DATE_FORMATTER.format(punishment.revokedAt()))).appendNewline();
            }
            if (punishment.revocationReason() != null) {
                message.append(detailLine("Revocation Reason", punishment.revocationReason())).appendNewline();
            }
        }
        return message.build();
    }

    private static Component detailLine(String key, String value) {
        return StringUtils.deserialize(
                SharedConstants.BULLET_POINT + PunishmentProxyConstants.UI_CHECK_DETAIL_ENTRY,
                TagResolver.resolver(Placeholder.unparsed("key", key), Placeholder.unparsed("value", value)));
    }

    private record NamePair(String targetName, String imposingName) {
    }
}
