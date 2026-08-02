package net.valoury.discord.api.link;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AccountLinkStorage extends AutoCloseable {
    CompletableFuture<LinkCodeReservationResult> reserveLinkCode(
            UUID minecraftUniqueId,
            String minecraftName,
            String codeHash,
            Duration codeLifetime,
            Duration issuanceCooldown
    );

    CompletableFuture<ConsumeLinkCodeResult> consumeLinkCode(
            long discordUserId,
            String codeHash,
            int maximumAttempts,
            Duration attemptWindow
    );

    CompletableFuture<Optional<AccountLink>> findByMinecraftUniqueId(UUID minecraftUniqueId);

    CompletableFuture<Optional<AccountLink>> findByDiscordUserId(long discordUserId);

    CompletableFuture<UnlinkAccountResult> unlinkByMinecraftUniqueId(UUID minecraftUniqueId);

    CompletableFuture<UnlinkAccountResult> unlinkByDiscordUserId(long discordUserId);

    @Override
    void close();
}
