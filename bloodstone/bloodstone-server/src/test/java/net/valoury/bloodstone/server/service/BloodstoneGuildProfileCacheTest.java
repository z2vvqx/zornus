package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.valoury.guilds.api.GuildMembershipService;
import net.valoury.guilds.api.GuildProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BloodstoneGuildProfileCacheTest {

    @Test
    void refreshReplacesPlaceholderFallbackWithCurrentGuildTag() {
        UUID playerId = new UUID(0L, 1L);
        UUID guildId = new UUID(0L, 2L);
        AtomicReference<Optional<GuildProfile>> guild = new AtomicReference<>(
                Optional.empty()
        );
        GuildMembershipService memberships = new GuildMembershipService() {
            @Override
            public CompletableFuture<Optional<GuildProfile>> findGuildByPlayer(
                    UUID ignored
            ) {
                return CompletableFuture.completedFuture(guild.get());
            }

            @Override
            public CompletableFuture<Optional<GuildProfile>> findGuild(UUID ignored) {
                return CompletableFuture.completedFuture(guild.get());
            }
        };
        BloodstoneGuildProfileCache cache = new BloodstoneGuildProfileCache(
                memberships,
                Logger.getLogger(BloodstoneGuildProfileCacheTest.class.getName())
        );

        cache.refresh(playerId).join();
        assertEquals(Component.empty(), cache.tag(playerId));

        guild.set(Optional.of(
                new GuildProfile(guildId, "Guild One", "ONE", "<gold>")
        ));
        cache.refresh(playerId).join();
        assertEquals(
                Component.text("[ONE]", NamedTextColor.GOLD),
                cache.tag(playerId)
        );

        guild.set(Optional.of(
                new GuildProfile(guildId, "Guild One", "ONE", "<yellow>")
        ));
        cache.refresh(playerId).join();
        assertEquals(
                Component.text("[ONE]", NamedTextColor.YELLOW),
                cache.tag(playerId)
        );
    }

    @Test
    void refreshAllUpdatesCachedAppearanceWithoutPlaceholderAccess() {
        UUID playerId = new UUID(0L, 3L);
        UUID guildId = new UUID(0L, 4L);
        AtomicReference<GuildProfile> guild = new AtomicReference<>(
                new GuildProfile(guildId, "Guild Two", "TWO", "<gold>")
        );
        GuildMembershipService memberships = new GuildMembershipService() {
            @Override
            public CompletableFuture<Optional<GuildProfile>> findGuildByPlayer(
                    UUID ignored
            ) {
                return CompletableFuture.completedFuture(Optional.of(guild.get()));
            }

            @Override
            public CompletableFuture<Optional<GuildProfile>> findGuild(UUID ignored) {
                return CompletableFuture.completedFuture(Optional.of(guild.get()));
            }
        };
        BloodstoneGuildProfileCache cache = new BloodstoneGuildProfileCache(
                memberships,
                Logger.getLogger(BloodstoneGuildProfileCacheTest.class.getName())
        );

        cache.refreshAll(List.of(playerId)).join();
        assertEquals(
                Component.text("[TWO]", NamedTextColor.GOLD),
                cache.tag(playerId)
        );

        guild.set(new GuildProfile(guildId, "Guild Two", "NEW", "<yellow>"));
        cache.refreshAll(List.of(playerId)).join();

        assertEquals(
                Component.text("[NEW]", NamedTextColor.YELLOW),
                cache.tag(playerId)
        );
    }
}
