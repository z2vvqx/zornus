package net.valoury.discord.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.valoury.discord.api.link.AccountLinkService;
import net.valoury.discord.api.link.IssueLinkCodeResult;
import net.valoury.discord.proxy.DiscordProxyConstants;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public final class DiscordLinkCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordLinkCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(AccountLinkService accountLinkService) {
        return createCommand("link", accountLinkService);
    }

    public static @NonNull BrigadierCommand createShortcut(AccountLinkService accountLinkService) {
        return new BrigadierCommand(createCommand("link", accountLinkService)
                .requires(source -> source instanceof Player));
    }

    private static LiteralArgumentBuilder<CommandSource> createCommand(
            String commandName,
            AccountLinkService accountLinkService
    ) {
        return BrigadierCommand
                .literalArgumentBuilder(commandName)
                .executes(context -> handleIssueLinkCode(context, accountLinkService));
    }

    private static int handleIssueLinkCode(
            @NonNull CommandContext<CommandSource> context,
            AccountLinkService accountLinkService
    ) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player player)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        accountLinkService.issueLinkCode(player.getUniqueId(), player.getUsername())
                .thenAccept(result -> sendFeedback(player, result))
                .exceptionally(throwable -> {
                    LOGGER.error(
                            "Failed to issue an account link code for player {}",
                            player.getUniqueId(),
                            throwable
                    );
                    player.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });
        return Command.SINGLE_SUCCESS;
    }

    private static void sendFeedback(Player player, IssueLinkCodeResult result) {
        switch (result) {
            case IssueLinkCodeResult.Issued issued ->
                    player.sendMessage(createIssuedLinkCodeMessage(issued.code()));
            case IssueLinkCodeResult.AlreadyLinked ignored ->
                    player.sendMessage(StringUtils.deserialize(DiscordProxyConstants.LINK_ALREADY_LINKED));
            case IssueLinkCodeResult.RateLimited rateLimited -> player.sendMessage(StringUtils.deserialize(
                    DiscordProxyConstants.LINK_RATE_LIMITED,
                    Placeholder.unparsed("seconds", String.valueOf(ceilSeconds(rateLimited.retryAfter())))
            ));
        }
    }

    static @NonNull Component createIssuedLinkCodeMessage(@NonNull String linkCode) {
        String discordCommand = "/link " + linkCode;
        Component clickableDiscordCommand = Component
                .text(discordCommand, NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand(discordCommand))
                .hoverEvent(Component.text("Click to paste into chat", NamedTextColor.GRAY));

        return StringUtils.deserialize(
                DiscordProxyConstants.LINK_CODE_ISSUED,
                Placeholder.component("discord_command", clickableDiscordCommand)
        );
    }

    private static long ceilSeconds(Duration duration) {
        return Math.max(1, Math.ceilDiv(duration.toMillis(), 1_000));
    }
}
