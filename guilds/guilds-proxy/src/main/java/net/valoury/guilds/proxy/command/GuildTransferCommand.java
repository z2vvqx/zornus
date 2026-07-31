package net.valoury.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.result.GuildResults;
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
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
                .thenAccept(result -> handleTransferResult(sender, result))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to transfer guild leadership to {}", targetName, throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleTransferResult(
            @NonNull Player sender,
            GuildResults.TransferLeadership result
    ) {
        switch (result) {
            case GuildResults.TransferLeadership.Transferred transferred ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.TRANSFER_SUCCESS,
                            Placeholder.unparsed("target", transferred.targetName())));
            case GuildResults.TransferLeadership.ConfirmationRequired confirmationRequired ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.TRANSFER_CONFIRMATION_REQUIRED,
                            Placeholder.unparsed("target", confirmationRequired.targetName())));
            case GuildResults.TransferLeadership.NoConfirmationPending noConfirmationPending ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.TRANSFER_ERROR_NO_CONFIRMATION,
                            Placeholder.unparsed("target", noConfirmationPending.targetName())));
            case GuildResults.TransferLeadership.NotInGuild ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.TRANSFER_ERROR_NOT_IN_GUILD));
            case GuildResults.TransferLeadership.NotLeader ignored ->
                    sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_LEADER));
            case GuildResults.TransferLeadership.PlayerNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
            case GuildResults.TransferLeadership.PlayerNotInGuild playerNotInGuild ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.TRANSFER_ERROR_PLAYER_NOT_IN_GUILD,
                            Placeholder.unparsed("target", playerNotInGuild.targetName())));
            case GuildResults.TransferLeadership.CannotTransferToSelf ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.TRANSFER_ERROR_CANNOT_TRANSFER_SELF));
            case GuildResults.TransferLeadership.GuildNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
        }
    }
}
