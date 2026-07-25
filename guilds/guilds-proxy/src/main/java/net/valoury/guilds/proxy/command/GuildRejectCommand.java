package net.valoury.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildRejectCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildRejectCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("reject")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_REJECT));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("guild_name", StringArgumentType.word())
                        .executes(context -> handleRejectInvitation(context, guildService))
                );
    }

    private static int handleRejectInvitation(@NonNull CommandContext<CommandSource> context,
                                              GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String guildName = StringArgumentType.getString(context, "guild_name");
        guildService.rejectInvitation(sender, guildName)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case INVITATION_REJECTED -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.REJECT_SUCCESS,
                                Placeholder.unparsed("guild_name", guildName)));
                        case NO_INVITATION_FOUND -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.REJECT_ERROR_NO_INVITATION,
                                Placeholder.unparsed("guild_name", guildName)));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to reject guild invitation for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
