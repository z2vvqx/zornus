package net.valoury.discord.bot.link.listener;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.valoury.discord.api.link.AccountLinkService;
import net.valoury.discord.api.link.ConsumeLinkCodeResult;
import net.valoury.discord.api.link.UnlinkAccountResult;
import net.valoury.discord.bot.DiscordBotConstants;
import net.valoury.discord.bot.interaction.DiscordInteractionResponder;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class LinkInteractionListener extends ListenerAdapter {
    private final AccountLinkService accountLinkService;
    private final DiscordInteractionResponder interactionResponder;

    public LinkInteractionListener(
            AccountLinkService accountLinkService,
            DiscordInteractionResponder interactionResponder
    ) {
        this.accountLinkService = Objects.requireNonNull(
                accountLinkService,
                "Account link service cannot be null"
        );
        this.interactionResponder = Objects.requireNonNull(
                interactionResponder,
                "Discord interaction responder cannot be null"
        );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (DiscordBotConstants.LINK_COMMAND_NAME.equals(event.getName())) {
            interactionResponder.respond(
                    event,
                    "account link command",
                    DiscordBotConstants.LINK_OPERATION_FAILED,
                    () -> linkAccount(event)
            );
        } else if (DiscordBotConstants.UNLINK_COMMAND_NAME.equals(event.getName())) {
            interactionResponder.respond(
                    event,
                    "account unlink command",
                    DiscordBotConstants.LINK_OPERATION_FAILED,
                    () -> unlinkAccount(event)
            );
        }
    }

    private CompletableFuture<String> linkAccount(SlashCommandInteractionEvent event) {
        OptionMapping codeOption = event.getOption("code");
        if (codeOption == null) {
            return CompletableFuture.completedFuture(DiscordBotConstants.LINK_INVALID_OR_EXPIRED_CODE);
        }
        return accountLinkService.consumeLinkCode(event.getUser().getIdLong(), codeOption.getAsString())
                .thenApply(LinkInteractionListener::linkFeedback);
    }

    private CompletableFuture<String> unlinkAccount(SlashCommandInteractionEvent event) {
        return accountLinkService.unlinkByDiscordUserId(event.getUser().getIdLong())
                .thenApply(result -> switch (result) {
                    case UnlinkAccountResult.Unlinked ignored -> DiscordBotConstants.UNLINK_SUCCESS;
                    case UnlinkAccountResult.NotLinked ignored -> DiscordBotConstants.UNLINK_NOT_LINKED;
                });
    }

    private static String linkFeedback(ConsumeLinkCodeResult result) {
        return switch (result) {
            case ConsumeLinkCodeResult.Linked ignored -> DiscordBotConstants.LINK_SUCCESS;
            case ConsumeLinkCodeResult.AlreadyLinked ignored -> DiscordBotConstants.LINK_ALREADY_LINKED;
            case ConsumeLinkCodeResult.MinecraftAccountLinkedElsewhere ignored ->
                    DiscordBotConstants.LINK_MINECRAFT_ALREADY_LINKED;
            case ConsumeLinkCodeResult.DiscordAccountLinkedElsewhere ignored ->
                    DiscordBotConstants.LINK_DISCORD_ALREADY_LINKED;
            case ConsumeLinkCodeResult.InvalidOrExpiredCode ignored ->
                    DiscordBotConstants.LINK_INVALID_OR_EXPIRED_CODE;
            case ConsumeLinkCodeResult.RateLimited rateLimited ->
                    DiscordBotConstants.LINK_RATE_LIMITED.formatted(ceilSeconds(rateLimited.retryAfter()));
        };
    }

    private static long ceilSeconds(Duration duration) {
        return Math.max(1, Math.ceilDiv(duration.toMillis(), 1_000));
    }
}
