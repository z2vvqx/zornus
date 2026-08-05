package net.valoury.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.Guild;
import net.valoury.guilds.proxy.model.result.GuildInfoResult;
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GuildInfoCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildInfoCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("info")
                .executes(context -> handleGuildInfo(context, guildService, null))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("guild_name", StringArgumentType.word())
                        .executes(context -> handleGuildInfo(
                                context, guildService,
                                StringArgumentType.getString(context, "guild_name")))
                );
    }

    private static int handleGuildInfo(@NonNull CommandContext<CommandSource> context,
                                       GuildService guildService, String guildName) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        var infoFuture = guildName == null
                ? guildService.getGuildInfo(sender)
                : guildService.getGuildInfoByName(guildName);

        infoFuture.thenAccept(result -> {
                    switch (result) {
                        case GuildInfoResult.Found found ->
                                resolveAndDisplayGuildInfo(sender, guildService, found.guild());
                        case GuildInfoResult.NotInGuild ignored ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.INFO_ERROR_NOT_IN_GUILD));
                        case GuildInfoResult.NotFound ignored ->
                                sender.sendMessage(StringUtils.deserialize("<red>Unable to find that guild.</red>"));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get guild information for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void resolveAndDisplayGuildInfo(
            @NonNull Player sender,
            @NonNull GuildService guildService,
            @NonNull Guild guild
    ) {
        guildService.fetchPlayersByUuids(Set.of(guild.leaderId()))
                .thenAccept(players -> displayGuildInfo(sender, guild, players))
                .exceptionally(throwable -> {
                    LOGGER.error(
                            "Failed to resolve guild leader name for player {}",
                            sender.getUniqueId(),
                            throwable
                    );
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });
    }

    private static void displayGuildInfo(
            @NonNull Player sender,
            @NonNull Guild guild,
            @NonNull Map<UUID, PlayerRecord> players
    ) {
        PlayerRecord leader = players.get(guild.leaderId());
        String leaderName = leader == null ? "Unknown" : leader.username();
        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("leader", leaderName),
                Placeholder.component(
                        "creation_date",
                        StringUtils.formatRelativeTime(guild.createdAt())
                ),
                Placeholder.unparsed("member_count", String.valueOf(guild.memberRanks().size())),
                Placeholder.unparsed("maximum_size", String.valueOf(GuildProxyConstants.MAX_GUILD_SIZE))
        );
        TextComponent message = Component.text()
                .appendNewline()
                .append(StringUtils.deserialize(
                        SharedConstants.BULLET_POINT
                                + GuildProxyConstants.UI_INFO_LEADER,
                        resolver
                ))
                .appendNewline()
                .append(StringUtils.deserialize(
                        SharedConstants.BULLET_POINT
                                + GuildProxyConstants.UI_INFO_CREATION_DATE,
                        resolver
                ))
                .appendNewline()
                .append(StringUtils.deserialize(
                        SharedConstants.BULLET_POINT
                                + GuildProxyConstants.UI_INFO_MEMBERS,
                        resolver
                ))
                .appendNewline()
                .build();
        sender.sendMessage(message);
    }
}
