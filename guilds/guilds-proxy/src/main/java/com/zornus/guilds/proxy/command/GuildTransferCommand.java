package com.zornus.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.service.GuildService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class GuildTransferCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildTransferCommand.class);

    private static SuggestionProvider<CommandSource> onlinePlayerSuggestions(ProxyServer proxyServer) {
        return (context, builder) -> {
            String remainingInput = builder.getRemainingLowerCase();
            if (remainingInput.isEmpty()) {
                return builder.buildFuture();
            }
            proxyServer.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(username -> username.toLowerCase(Locale.ROOT).startsWith(remainingInput))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("transfer")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_TRANSFER));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("member_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleTransferLeadership(context, guildService, false))
                        .then(BrigadierCommand
                                .literalArgumentBuilder("confirm")
                                .executes(context -> handleTransferLeadership(context, guildService, true))
                        )
                );
    }

    private static int handleTransferLeadership(@NonNull CommandContext<CommandSource> context,
                                                GuildService guildService, boolean isConfirming) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "member_name");
        guildService.transferLeadership(sender, targetName, isConfirming)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case LEADERSHIP_TRANSFERRED -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.TRANSFER_SUCCESS,
                                Placeholder.unparsed("target", targetName)));
                        case TRANSFER_CONFIRMATION_REQUIRED -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.TRANSFER_CONFIRMATION_REQUIRED,
                                Placeholder.unparsed("target", targetName)));
                        case NO_CONFIRMATION_PENDING -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.TRANSFER_ERROR_NO_CONFIRMATION,
                                Placeholder.unparsed("target", targetName)));
                        case NOT_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.TRANSFER_ERROR_NOT_IN_GUILD));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_LEADER));
                        case PLAYER_NOT_FOUND ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                        case PLAYER_NOT_IN_GUILD -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.TRANSFER_ERROR_PLAYER_NOT_IN_GUILD,
                                Placeholder.unparsed("target", targetName)));
                        case CANNOT_TRANSFER_TO_SELF ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.TRANSFER_ERROR_CANNOT_TRANSFER_SELF));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to transfer guild leadership to {}", targetName, throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
