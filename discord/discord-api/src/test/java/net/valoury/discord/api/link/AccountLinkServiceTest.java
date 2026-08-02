package net.valoury.discord.api.link;

import net.valoury.discord.api.ApiConstants;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountLinkServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final UUID MINECRAFT_UNIQUE_ID =
            UUID.fromString("3e4f72f0-084f-4c94-a764-8ae09c40446f");

    @Test
    void issuesCopyFriendlyCodeWhilePersistingOnlyItsHash() {
        RecordingStorage storage = new RecordingStorage();
        AccountLinkService service = service(storage);

        IssueLinkCodeResult.Issued issued = assertInstanceOf(
                IssueLinkCodeResult.Issued.class,
                service.issueLinkCode(MINECRAFT_UNIQUE_ID, "ValouryPlayer").join()
        );

        assertTrue(issued.code().matches(
                "[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}"
                        + "-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}"
                        + "-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}"));
        assertEquals(NOW.plus(ApiConstants.ACCOUNT_LINK_CODE_LIFETIME), issued.expiresAt());
        assertTrue(storage.reservedCodeHash.matches("[0-9a-f]{64}"));
        assertEquals(ApiConstants.ACCOUNT_LINK_CODE_ISSUANCE_COOLDOWN, storage.issuanceCooldown);

        service.consumeLinkCode(
                100,
                issued.code().replace("-", "").toLowerCase()
        ).join();
        assertEquals(storage.reservedCodeHash, storage.consumedCodeHash);
        assertEquals(ApiConstants.MAXIMUM_ACCOUNT_LINK_ATTEMPTS, storage.maximumAttempts);
        assertEquals(ApiConstants.ACCOUNT_LINK_ATTEMPT_WINDOW, storage.attemptWindow);
    }

    @Test
    void retriesAnExtremelyRareHashCollision() {
        RecordingStorage storage = new RecordingStorage();
        storage.reservationResults.add(new LinkCodeReservationResult.CodeHashCollision());
        storage.reservationResults.add(new LinkCodeReservationResult.Reserved(
                NOW.plus(ApiConstants.ACCOUNT_LINK_CODE_LIFETIME)));

        IssueLinkCodeResult result = service(storage)
                .issueLinkCode(MINECRAFT_UNIQUE_ID, "ValouryPlayer")
                .join();

        assertInstanceOf(IssueLinkCodeResult.Issued.class, result);
        assertEquals(2, storage.reservationCount);
    }

    @Test
    void rejectsMalformedCodesBeforeAccessingStorage() {
        RecordingStorage storage = new RecordingStorage();

        ConsumeLinkCodeResult result = service(storage)
                .consumeLinkCode(100, "0000-0000-0000").join();
        ConsumeLinkCodeResult nullResult = service(storage).consumeLinkCode(100, null).join();

        assertInstanceOf(ConsumeLinkCodeResult.InvalidOrExpiredCode.class, result);
        assertInstanceOf(ConsumeLinkCodeResult.InvalidOrExpiredCode.class, nullResult);
        assertEquals(0, storage.consumeCount);
    }

    @Test
    void validatesAuthenticatedIdentityInputs() {
        RecordingStorage storage = new RecordingStorage();
        AccountLinkService service = service(storage);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.issueLinkCode(MINECRAFT_UNIQUE_ID, "invalid minecraft name")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.consumeLinkCode(0, "AAAA-AAAA-AAAA")
        );
    }

    private static AccountLinkService service(RecordingStorage storage) {
        return new AccountLinkService(
                storage,
                new SecureRandom()
        );
    }

    private static final class RecordingStorage implements AccountLinkStorage {
        private final Queue<LinkCodeReservationResult> reservationResults = new ArrayDeque<>();
        private String reservedCodeHash;
        private String consumedCodeHash;
        private Duration issuanceCooldown;
        private Duration attemptWindow;
        private int maximumAttempts;
        private int reservationCount;
        private int consumeCount;

        @Override
        public CompletableFuture<LinkCodeReservationResult> reserveLinkCode(
                UUID minecraftUniqueId,
                String minecraftName,
                String codeHash,
                Duration codeLifetime,
                Duration issuanceCooldown
        ) {
            reservationCount++;
            reservedCodeHash = codeHash;
            this.issuanceCooldown = issuanceCooldown;
            LinkCodeReservationResult result = reservationResults.poll();
            return CompletableFuture.completedFuture(
                    result == null
                            ? new LinkCodeReservationResult.Reserved(NOW.plus(codeLifetime))
                            : result);
        }

        @Override
        public CompletableFuture<ConsumeLinkCodeResult> consumeLinkCode(
                long discordUserId,
                String codeHash,
                int maximumAttempts,
                Duration attemptWindow
        ) {
            consumeCount++;
            consumedCodeHash = codeHash;
            this.maximumAttempts = maximumAttempts;
            this.attemptWindow = attemptWindow;
            return CompletableFuture.completedFuture(
                    new ConsumeLinkCodeResult.InvalidOrExpiredCode());
        }

        @Override
        public CompletableFuture<UnlinkAccountResult> unlinkByMinecraftUniqueId(UUID minecraftUniqueId) {
            return CompletableFuture.completedFuture(new UnlinkAccountResult.NotLinked());
        }

        @Override
        public CompletableFuture<Optional<AccountLink>> findByMinecraftUniqueId(UUID minecraftUniqueId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Optional<AccountLink>> findByDiscordUserId(long discordUserId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<UnlinkAccountResult> unlinkByDiscordUserId(long discordUserId) {
            return CompletableFuture.completedFuture(new UnlinkAccountResult.NotLinked());
        }

        @Override
        public void close() {
        }
    }
}
