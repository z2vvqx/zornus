package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.guilds.api.GuildMembershipService;
import net.valoury.guilds.api.GuildProfile;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BloodstoneGuildProfileCache {

    private static final long REFRESH_INTERVAL_NANOSECONDS =
            TimeUnit.SECONDS.toNanos(5);

    private final GuildMembershipService guildMembershipService;
    private final Logger logger;
    private final Map<UUID, Component> cachedTags = new ConcurrentHashMap<>();
    private final Map<UUID, Long> refreshDueNanoseconds = new ConcurrentHashMap<>();

    public BloodstoneGuildProfileCache(
            GuildMembershipService guildMembershipService,
            Logger logger
    ) {
        this.guildMembershipService = guildMembershipService;
        this.logger = logger;
    }

    public CompletableFuture<Void> refresh(UUID playerId) {
        refreshDueNanoseconds.put(
                playerId,
                System.nanoTime() + REFRESH_INTERVAL_NANOSECONDS
        );
        return fetch(playerId);
    }

    public CompletableFuture<Void> refreshAll(Collection<UUID> playerIds) {
        Objects.requireNonNull(playerIds, "Player identifiers cannot be null");
        CompletableFuture<?>[] refreshes = playerIds.stream()
                .distinct()
                .map(this::refresh)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(refreshes);
    }

    private CompletableFuture<Void> fetch(UUID playerId) {
        return guildMembershipService.findGuildByPlayer(playerId)
                .thenAccept(guild -> {
                    if (guild.isPresent()) {
                        cachedTags.put(playerId, formatTag(guild.get()));
                    } else {
                        cachedTags.remove(playerId);
                    }
                })
                .exceptionally(exception -> {
                    logger.log(
                            Level.WARNING,
                            "Failed to refresh the guild tag for " + playerId,
                            exception
                    );
                    return null;
                });
    }

    public Component tag(UUID playerId) {
        refreshIfDue(playerId);
        return cachedTags.getOrDefault(playerId, Component.empty());
    }

    public String legacyTag(UUID playerId) {
        return BloodstoneText.legacy(tag(playerId));
    }

    private void refreshIfDue(UUID playerId) {
        long nowNanoseconds = System.nanoTime();
        Long refreshDue = refreshDueNanoseconds.get(playerId);
        if (refreshDue != null && refreshDue > nowNanoseconds) {
            return;
        }
        long nextRefresh = nowNanoseconds
                + REFRESH_INTERVAL_NANOSECONDS;
        boolean claimed = refreshDue == null
                ? refreshDueNanoseconds.putIfAbsent(playerId, nextRefresh) == null
                : refreshDueNanoseconds.replace(playerId, refreshDue, nextRefresh);
        if (claimed) {
            fetch(playerId);
        }
    }

    public void remove(UUID playerId) {
        cachedTags.remove(playerId);
        refreshDueNanoseconds.remove(playerId);
    }

    public void clear() {
        cachedTags.clear();
        refreshDueNanoseconds.clear();
    }

    private Component formatTag(GuildProfile guildProfile) {
        return BloodstoneGuildText.tag(guildProfile);
    }
}
