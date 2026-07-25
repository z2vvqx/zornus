package com.zornus.punishments.proxy.service;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.punishments.proxy.PunishmentProxyConstants;
import com.zornus.punishments.proxy.PunishmentPresets;
import com.zornus.punishments.proxy.PunishmentPresets.PunishmentPreset;
import com.zornus.punishments.proxy.PunishmentPresets.PunishmentPresetStep;
import com.zornus.punishments.proxy.model.Punishment;
import com.zornus.punishments.proxy.model.PunishmentType;
import com.zornus.punishments.proxy.model.result.PunishmentCheckResult;
import com.zornus.punishments.proxy.model.result.PunishmentHistoryResult;
import com.zornus.punishments.proxy.model.result.PunishmentImposeResult;
import com.zornus.punishments.proxy.model.result.PunishmentRevokeResult;
import com.zornus.punishments.proxy.storage.CreatePunishmentOutcome;
import com.zornus.punishments.proxy.storage.PunishmentStorage;
import com.zornus.shared.model.PlayerRecord;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.security.SecureRandom;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PunishmentService {
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhd])");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PunishmentStorage storage;
    private final ProxyServer proxyServer;
    private final PunishmentNotificationService notificationService;

    public PunishmentService(@NonNull PunishmentStorage storage, @NonNull ProxyServer proxyServer) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.notificationService = new PunishmentNotificationService(proxyServer);
    }

    public CompletableFuture<Optional<PlayerRecord>> resolveTargetPlayer(@NonNull String username) {
        Optional<Player> onlinePlayer = proxyServer.getPlayer(username);
        if (onlinePlayer.isPresent()) {
            Player player = onlinePlayer.get();
            return CompletableFuture.completedFuture(Optional.of(
                    new PlayerRecord(player.getUniqueId(), player.getUsername())));
        }
        return storage.fetchPlayerByUsername(username);
    }

    public CompletableFuture<PunishmentImposeResult> impose(
            @NonNull CommandSource source,
            @NonNull PlayerRecord target,
            @NonNull PunishmentType type,
            @Nullable String durationText,
            @NonNull String reason
    ) {
        return imposeConcrete(source, target, type, durationText, reason, null, null);
    }

    public CompletableFuture<PunishmentImposeResult> imposePreset(
            @NonNull CommandSource source,
            @NonNull PlayerRecord target,
            @NonNull String presetName
    ) {
        Optional<PunishmentPreset> presetOptional = PunishmentPresets.find(presetName);
        if (presetOptional.isEmpty()) {
            return CompletableFuture.completedFuture(new PunishmentImposeResult.PresetNotFound());
        }
        PunishmentPreset preset = presetOptional.get();
        if (source instanceof Player player && player.getUniqueId().equals(target.playerUuid())) {
            return CompletableFuture.completedFuture(new PunishmentImposeResult.CannotPunishSelf());
        }
        return storage.fetchNextPresetApplicationNumber(target.playerUuid(), preset.name())
                .thenCompose(applicationNumber -> {
                    PunishmentPresetStep step = preset.stepForApplicationNumber(applicationNumber);
                    return imposeConcrete(
                            source,
                            target,
                            step.type(),
                            step.duration(),
                            preset.reason(),
                            preset.name(),
                            applicationNumber
                    );
                });
    }

    private CompletableFuture<PunishmentImposeResult> imposeConcrete(
            CommandSource source,
            PlayerRecord target,
            PunishmentType type,
            String durationText,
            String reason,
            String presetName,
            Integer presetApplicationNumber
    ) {
        if (source instanceof Player player && player.getUniqueId().equals(target.playerUuid())) {
            return CompletableFuture.completedFuture(new PunishmentImposeResult.CannotPunishSelf());
        }

        Optional<Player> onlineTarget = proxyServer.getPlayer(target.playerUuid());
        if (type == PunishmentType.KICK && onlineTarget.isEmpty()) {
            return CompletableFuture.completedFuture(new PunishmentImposeResult.PlayerNotFound());
        }

        if ((type == PunishmentType.KICK) != (durationText == null)) {
            return CompletableFuture.completedFuture(new PunishmentImposeResult.InvalidDuration());
        }
        Optional<Duration> parsedDuration = parseDuration(durationText);
        if (type != PunishmentType.KICK && parsedDuration.isEmpty() && !isPermanentDuration(durationText)) {
            return CompletableFuture.completedFuture(new PunishmentImposeResult.InvalidDuration());
        }

        Instant createdAt = Instant.now();
        Instant expiresAt;
        try {
            expiresAt = switch (type) {
                case BAN, MUTE, WARN -> parsedDuration.map(createdAt::plus).orElse(null);
                case KICK -> null;
            };
        } catch (DateTimeException | ArithmeticException exception) {
            return CompletableFuture.completedFuture(new PunishmentImposeResult.InvalidDuration());
        }

        boolean active = type != PunishmentType.KICK;
        UUID imposingPlayerId = source instanceof Player player ? player.getUniqueId() : null;
        return createWithUniqueIdentifier(
                source, target, onlineTarget, type, reason, createdAt, expiresAt, active, imposingPlayerId,
                presetName, presetApplicationNumber);
    }

    private CompletableFuture<PunishmentImposeResult> createWithUniqueIdentifier(
            CommandSource source,
            PlayerRecord target,
            Optional<Player> onlineTarget,
            PunishmentType type,
            String reason,
            Instant createdAt,
            Instant expiresAt,
            boolean active,
            UUID imposingPlayerId,
            String presetName,
            Integer presetApplicationNumber
    ) {
        Punishment punishment = new Punishment(
                generateIdentifier(),
                type,
                target.playerUuid(),
                imposingPlayerId,
                reason,
                createdAt,
                expiresAt,
                active,
                null,
                null,
                null,
                false,
                presetName,
                presetApplicationNumber
        );
        return storage.createPunishment(punishment).thenCompose(outcome -> switch (outcome) {
            case CreatePunishmentOutcome.IdentifierCollision ignored -> createWithUniqueIdentifier(
                    source, target, onlineTarget, type, reason, createdAt, expiresAt, active,
                    imposingPlayerId, presetName, presetApplicationNumber);
            case CreatePunishmentOutcome.PresetProgressionConflict ignored -> {
                if (presetName == null) {
                    throw new IllegalStateException("Preset progression conflict without preset metadata");
                }
                yield imposePreset(source, target, presetName);
            }
            case CreatePunishmentOutcome.AlreadyActive ignored -> CompletableFuture.completedFuture(switch (type) {
                case BAN -> new PunishmentImposeResult.AlreadyBanned();
                case MUTE -> new PunishmentImposeResult.AlreadyMuted();
                case WARN -> new PunishmentImposeResult.AlreadyWarnedForReason();
                case KICK -> throw new IllegalStateException("Inactive kicks cannot conflict with active punishments");
            });
            case CreatePunishmentOutcome.Created ignored -> completeImposition(
                    source, target, onlineTarget, punishment);
        });
    }

    private CompletableFuture<PunishmentImposeResult> completeImposition(
            CommandSource source,
            PlayerRecord target,
            Optional<Player> onlineTarget,
            Punishment punishment
    ) {
        if (onlineTarget.isEmpty()) {
            notificationService.broadcastPunishment(source, target, punishment);
            return CompletableFuture.completedFuture(new PunishmentImposeResult.Imposed(punishment));
        }
        notificationService.notifyVictim(onlineTarget.get(), punishment);
        notificationService.broadcastPunishment(source, target, punishment);
        notificationService.enforce(onlineTarget.get(), punishment);
        return storage.markNotificationDelivered(punishment.identifier())
                .thenApply(ignored -> new PunishmentImposeResult.Imposed(punishment));
    }

    public CompletableFuture<PunishmentRevokeResult> revokeByIdentifier(
            @NonNull String identifier,
            @NonNull CommandSource source,
            @NonNull String reason
    ) {
        UUID revokingPlayerId = source instanceof Player player ? player.getUniqueId() : null;
        return storage.revokeByIdentifier(identifier, revokingPlayerId, reason, Instant.now())
                .thenApply(punishment -> punishment
                        .<PunishmentRevokeResult>map(PunishmentRevokeResult.Revoked::new)
                        .orElseGet(PunishmentRevokeResult.PunishmentNotFound::new));
    }

    public CompletableFuture<PunishmentRevokeResult> revokeActive(
            @NonNull PlayerRecord target,
            @NonNull PunishmentType type,
            @NonNull CommandSource source,
            @NonNull String reason
    ) {
        UUID revokingPlayerId = source instanceof Player player ? player.getUniqueId() : null;
        return storage.revokeActive(
                        target.playerUuid(), type, revokingPlayerId, reason, Instant.now())
                .thenApply(punishment -> punishment
                        .<PunishmentRevokeResult>map(PunishmentRevokeResult.Revoked::new)
                        .orElseGet(() -> type == PunishmentType.BAN
                                ? new PunishmentRevokeResult.PlayerNotBanned()
                                : new PunishmentRevokeResult.PlayerNotMuted()));
    }

    public CompletableFuture<PunishmentCheckResult> check(
            @NonNull UUID playerId, @NonNull PunishmentType type) {
        return storage.fetchActive(playerId, type)
                .thenApply(punishment -> punishment
                        .<PunishmentCheckResult>map(PunishmentCheckResult.Found::new)
                        .orElseGet(() -> type == PunishmentType.BAN
                                ? new PunishmentCheckResult.PlayerNotBanned()
                                : new PunishmentCheckResult.PlayerNotMuted()));
    }

    public CompletableFuture<Optional<Punishment>> fetchActive(
            @NonNull UUID playerId, @NonNull PunishmentType type) {
        return storage.fetchActive(playerId, type);
    }

    public CompletableFuture<Optional<Punishment>> fetchByIdentifier(@NonNull String identifier) {
        return storage.fetchByIdentifier(identifier);
    }

    public CompletableFuture<PunishmentHistoryResult> fetchHistory(@NonNull UUID playerId) {
        return storage.fetchHistory(playerId)
                .thenApply(punishments -> punishments.isEmpty()
                        ? new PunishmentHistoryResult.Empty()
                        : new PunishmentHistoryResult.Found(punishments));
    }

    public CompletableFuture<Void> handlePlayerJoin(@NonNull Player player) {
        return storage.upsertPlayer(player.getUniqueId(), player.getUsername())
                .thenCompose(ignored -> storage.claimPendingNotifications(
                        player.getUniqueId(), Instant.now()))
                .thenAccept(punishments -> punishments.stream()
                        .sorted(Comparator.comparing(Punishment::createdAt))
                        .forEach(punishment -> notificationService.notifyDeferred(player, punishment)));
    }

    public CompletableFuture<String> resolveUsername(@Nullable UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(PunishmentProxyConstants.CONSOLE_NAME);
        }
        Optional<Player> onlinePlayer = proxyServer.getPlayer(playerId);
        if (onlinePlayer.isPresent()) {
            return CompletableFuture.completedFuture(onlinePlayer.get().getUsername());
        }
        return storage.fetchPlayer(playerId)
                .thenApply(record -> record.map(PlayerRecord::username)
                        .orElse(PunishmentProxyConstants.UNKNOWN_PLAYER));
    }

    public CompletableFuture<Void> expirePunishments() {
        return storage.expirePunishments(Instant.now());
    }

    public void close() {
        storage.close();
    }

    public static Optional<Duration> parseDuration(@Nullable String durationText) {
        if (durationText == null || durationText.isEmpty() || isPermanentDuration(durationText)) {
            return Optional.empty();
        }
        long totalSeconds = 0;
        Matcher matcher = DURATION_PATTERN.matcher(durationText);
        int matchedCharacters = 0;
        try {
            while (matcher.find()) {
                if (matcher.start() != matchedCharacters) {
                    return Optional.empty();
                }
                long value = Long.parseLong(matcher.group(1));
                long multiplier = switch (matcher.group(2).charAt(0)) {
                    case 'd' -> 86_400L;
                    case 'h' -> 3_600L;
                    case 'm' -> 60L;
                    case 's' -> 1L;
                    default -> 0L;
                };
                totalSeconds = Math.addExact(totalSeconds, Math.multiplyExact(value, multiplier));
                matchedCharacters = matcher.end();
            }
            return totalSeconds > 0 && matchedCharacters == durationText.length()
                    ? Optional.of(Duration.ofSeconds(totalSeconds))
                    : Optional.empty();
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
    }

    static boolean isPermanentDuration(@Nullable String durationText) {
        return "permanent".equalsIgnoreCase(durationText);
    }

    private String generateIdentifier() {
        StringBuilder identifier = new StringBuilder(4);
        for (int index = 0; index < 4; index++) {
            identifier.append(PunishmentProxyConstants.IDENTIFIER_CHARACTERS.charAt(
                    RANDOM.nextInt(PunishmentProxyConstants.IDENTIFIER_CHARACTERS.length())));
        }
        return identifier.toString();
    }
}
