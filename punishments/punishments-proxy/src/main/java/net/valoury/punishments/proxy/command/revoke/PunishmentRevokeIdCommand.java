package net.valoury.punishments.proxy.command.revoke;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.valoury.punishments.proxy.PunishmentProxyConstants;
import net.valoury.punishments.proxy.model.result.PunishmentRevokeResult;
import net.valoury.punishments.proxy.service.PunishmentService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PunishmentRevokeIdCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentRevokeIdCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(PunishmentService punishmentService) {
        return BrigadierCommand
                .literalArgumentBuilder("id")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PunishmentProxyConstants.USAGE_REVOKE_ID));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("identifier_code", StringArgumentType.word())
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("reason_array", StringArgumentType.greedyString())
                                .executes(context -> handleRevokeByIdentifier(context, punishmentService))
                        )
                );
    }

    private static int handleRevokeByIdentifier(CommandContext<CommandSource> context, PunishmentService punishmentService) {
        CommandSource source = context.getSource();
        String identifier = StringArgumentType.getString(context, "identifier_code");
        String reason = StringArgumentType.getString(context, "reason_array");

        punishmentService.revokeByIdentifier(identifier, source, reason)
                .thenAccept(result -> {
                    switch (result) {
                        case PunishmentRevokeResult.PunishmentNotFound ignored ->
                                source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.ERROR_PUNISHMENT_NOT_FOUND));
                        case PunishmentRevokeResult.Revoked revoked ->
                                punishmentService.resolveUsername(revoked.punishment().punishedPlayerId())
                                        .thenAccept(targetName -> {
                                            TagResolver resolver = TagResolver.resolver(
                                                    Placeholder.unparsed("target", targetName),
                                                    Placeholder.unparsed("punishment_id", revoked.punishment().identifier().toUpperCase())
                                            );
                                            source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.REVOKE_SUCCESS, resolver));
                                        })
                                        .exceptionally(throwable -> {
                                            LOGGER.error("Failed to resolve revoked punishment target {}",
                                                    revoked.punishment().punishedPlayerId(), throwable);
                                            source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                            return null;
                                        });
                        default -> source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to revoke punishment {}", identifier, throwable);
                    source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
