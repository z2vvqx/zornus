package com.zornus.guilds.proxy.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.guilds.proxy.service.GuildService;
import org.jspecify.annotations.NonNull;

public final class GuildCommand {

    private GuildCommand() {
    }

    public static @NonNull BrigadierCommand create(GuildService guildService, ProxyServer proxyServer) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("guild")
                .requires(source -> source instanceof Player)
                .executes(GuildHelpCommand.defaultExecutor())
                .then(GuildHelpCommand.create())
                .then(GuildCreateCommand.create(guildService))
                .then(GuildDeleteCommand.create(guildService))
                .then(GuildInviteCommand.create(guildService, proxyServer))
                .then(GuildAcceptCommand.create(guildService))
                .then(GuildRejectCommand.create(guildService))
                .then(GuildRevokeCommand.create(guildService, proxyServer))
                .then(GuildLeaveCommand.create(guildService))
                .then(GuildKickCommand.create(guildService, proxyServer))
                .then(GuildTransferCommand.create(guildService, proxyServer))
                .then(GuildRenameCommand.create(guildService))
                .then(GuildTagCommand.create(guildService))
                .then(GuildColorCommand.create(guildService))
                .then(GuildListCommand.create(guildService, proxyServer))
                .then(GuildRequestsCommand.create(guildService, proxyServer))
                .then(GuildChatCommand.create(guildService))
                .then(GuildInfoCommand.create(guildService))
                .then(GuildSettingsCommand.create(guildService))
                .build();
        return new BrigadierCommand(node);
    }
}
