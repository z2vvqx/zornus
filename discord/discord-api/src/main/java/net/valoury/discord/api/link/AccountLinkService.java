package net.valoury.discord.api.link;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.valoury.discord.api.ApiConstants.ACCOUNT_LINK_ATTEMPT_WINDOW;
import static net.valoury.discord.api.ApiConstants.ACCOUNT_LINK_CODE_ALPHABET;
import static net.valoury.discord.api.ApiConstants.ACCOUNT_LINK_CODE_CHARACTER_COUNT;
import static net.valoury.discord.api.ApiConstants.ACCOUNT_LINK_CODE_GROUP_SIZE;
import static net.valoury.discord.api.ApiConstants.ACCOUNT_LINK_CODE_ISSUANCE_COOLDOWN;
import static net.valoury.discord.api.ApiConstants.ACCOUNT_LINK_CODE_LIFETIME;
import static net.valoury.discord.api.ApiConstants.MAXIMUM_ACCOUNT_LINK_ATTEMPTS;
import static net.valoury.discord.api.ApiConstants.MAXIMUM_ACCOUNT_LINK_CODE_GENERATION_ATTEMPTS;
import static net.valoury.discord.api.ApiConstants.MINECRAFT_NAME_PATTERN;

public final class AccountLinkService {
    private final AccountLinkStorage storage;
    private final SecureRandom secureRandom;

    public AccountLinkService(AccountLinkStorage storage) {
        this(storage, new SecureRandom());
    }

    AccountLinkService(AccountLinkStorage storage, SecureRandom secureRandom) {
        this.storage = Objects.requireNonNull(storage, "Account link storage cannot be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "Secure random generator cannot be null");
    }

    public CompletableFuture<IssueLinkCodeResult> issueLinkCode(UUID minecraftUniqueId, String minecraftName) {
        Objects.requireNonNull(minecraftUniqueId, "Minecraft unique identifier cannot be null");
        requireMinecraftName(minecraftName);
        return issueLinkCode(minecraftUniqueId, minecraftName, 1);
    }

    public CompletableFuture<ConsumeLinkCodeResult> consumeLinkCode(long discordUserId, String submittedCode) {
        requireDiscordUserId(discordUserId);
        Optional<String> normalizedCode = normalizeLinkCode(submittedCode);
        if (normalizedCode.isEmpty()) {
            return CompletableFuture.completedFuture(new ConsumeLinkCodeResult.InvalidOrExpiredCode());
        }
        return storage.consumeLinkCode(
                discordUserId,
                hashLinkCode(normalizedCode.orElseThrow()),
                MAXIMUM_ACCOUNT_LINK_ATTEMPTS,
                ACCOUNT_LINK_ATTEMPT_WINDOW
        );
    }

    public CompletableFuture<UnlinkAccountResult> unlinkByMinecraftUniqueId(UUID minecraftUniqueId) {
        Objects.requireNonNull(minecraftUniqueId, "Minecraft unique identifier cannot be null");
        return storage.unlinkByMinecraftUniqueId(minecraftUniqueId);
    }

    public CompletableFuture<Optional<AccountLink>> findByMinecraftUniqueId(UUID minecraftUniqueId) {
        Objects.requireNonNull(minecraftUniqueId, "Minecraft unique identifier cannot be null");
        return storage.findByMinecraftUniqueId(minecraftUniqueId);
    }

    public CompletableFuture<Optional<AccountLink>> findByDiscordUserId(long discordUserId) {
        requireDiscordUserId(discordUserId);
        return storage.findByDiscordUserId(discordUserId);
    }

    public CompletableFuture<UnlinkAccountResult> unlinkByDiscordUserId(long discordUserId) {
        requireDiscordUserId(discordUserId);
        return storage.unlinkByDiscordUserId(discordUserId);
    }

    private CompletableFuture<IssueLinkCodeResult> issueLinkCode(
            UUID minecraftUniqueId,
            String minecraftName,
            int generationAttempt
    ) {
        String code = generateLinkCode();
        return storage.reserveLinkCode(
                        minecraftUniqueId,
                        minecraftName,
                        hashLinkCode(code.replace("-", "")),
                        ACCOUNT_LINK_CODE_LIFETIME,
                        ACCOUNT_LINK_CODE_ISSUANCE_COOLDOWN
                )
                .thenCompose(result -> switch (result) {
                    case LinkCodeReservationResult.Reserved reserved ->
                            CompletableFuture.completedFuture(
                                    new IssueLinkCodeResult.Issued(code, reserved.expiresAt()));
                    case LinkCodeReservationResult.AlreadyLinked ignored ->
                            CompletableFuture.completedFuture(new IssueLinkCodeResult.AlreadyLinked());
                    case LinkCodeReservationResult.RateLimited rateLimited ->
                            CompletableFuture.completedFuture(
                                    new IssueLinkCodeResult.RateLimited(rateLimited.retryAfter()));
                    case LinkCodeReservationResult.CodeHashCollision ignored -> {
                        if (generationAttempt >= MAXIMUM_ACCOUNT_LINK_CODE_GENERATION_ATTEMPTS) {
                            yield CompletableFuture.failedFuture(
                                    new IllegalStateException("Unable to reserve a unique account link code"));
                        }
                        yield issueLinkCode(minecraftUniqueId, minecraftName, generationAttempt + 1);
                    }
                });
    }

    private String generateLinkCode() {
        StringBuilder code = new StringBuilder(
                ACCOUNT_LINK_CODE_CHARACTER_COUNT
                        + ACCOUNT_LINK_CODE_CHARACTER_COUNT / ACCOUNT_LINK_CODE_GROUP_SIZE
                        - 1
        );
        for (int index = 0; index < ACCOUNT_LINK_CODE_CHARACTER_COUNT; index++) {
            if (index > 0 && index % ACCOUNT_LINK_CODE_GROUP_SIZE == 0) {
                code.append('-');
            }
            code.append(ACCOUNT_LINK_CODE_ALPHABET.charAt(
                    secureRandom.nextInt(ACCOUNT_LINK_CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private static Optional<String> normalizeLinkCode(String submittedCode) {
        if (submittedCode == null) {
            return Optional.empty();
        }
        String normalizedCode = submittedCode.toUpperCase(Locale.ROOT).replace("-", "");
        if (normalizedCode.length() != ACCOUNT_LINK_CODE_CHARACTER_COUNT) {
            return Optional.empty();
        }
        for (int index = 0; index < normalizedCode.length(); index++) {
            if (ACCOUNT_LINK_CODE_ALPHABET.indexOf(normalizedCode.charAt(index)) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(normalizedCode);
    }

    private static String hashLinkCode(String normalizedCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    normalizedCode.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is unavailable", exception);
        }
    }

    private static void requireMinecraftName(String minecraftName) {
        Objects.requireNonNull(minecraftName, "Minecraft name cannot be null");
        if (!MINECRAFT_NAME_PATTERN.matcher(minecraftName).matches()) {
            throw new IllegalArgumentException("Minecraft name is invalid");
        }
    }

    private static void requireDiscordUserId(long discordUserId) {
        if (discordUserId <= 0) {
            throw new IllegalArgumentException("Discord user identifier must be positive");
        }
    }

}
