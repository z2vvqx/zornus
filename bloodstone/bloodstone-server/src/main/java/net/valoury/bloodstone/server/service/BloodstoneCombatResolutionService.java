package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.model.CombatResolution;
import net.valoury.bloodstone.server.storage.BloodstoneCombatStorage;
import net.valoury.bloodstone.server.storage.CombatResolutionOutcome;
import net.valoury.guilds.api.GuildMembershipService;
import net.valoury.guilds.api.GuildProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BloodstoneCombatResolutionService {

    private static final String RAMPAGE_MESSAGE_FORMAT =
            "<green><text> <white><killer></white>'s rampage! <weapon></green>";
    private static final String RAMPAGE_TITLE =
            "<aqua><bold>RAMPAGE</bold></aqua>";
    private static final int STATISTIC_RESOLUTION_MAXIMUM_ATTEMPTS = 3;

    private final Plugin plugin;
    private final BloodstoneCombatStorage storage;
    private final GuildMembershipService guildMembershipService;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstonePlayerService playerService;
    private final BloodstonePlayerNameService playerNameService;
    private final Logger logger;
    private final DominationTracker dominationTracker =
            new DominationTracker();
    private final Map<UUID, RespawnNotification>
            pendingRespawnNotifications = new HashMap<>();

    public BloodstoneCombatResolutionService(
            Plugin plugin,
            BloodstoneCombatStorage storage,
            GuildMembershipService guildMembershipService,
            BloodstonePresentationService presentationService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            BloodstonePlayerService playerService,
            BloodstonePlayerNameService playerNameService,
            Logger logger
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.guildMembershipService = guildMembershipService;
        this.presentationService = presentationService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.playerService = playerService;
        this.playerNameService = playerNameService;
        this.logger = logger;
    }

    public void handleDeath(
            Player victim,
            Optional<CombatAttribution.Attribution> attributionOptional
    ) {
        if (attributionOptional.isEmpty()) {
            BloodstoneText.sendMessage(
                    victim,
                    BloodstoneServerConstants.UNATTRIBUTED_DEATH_MESSAGE
            );
            resolveUncreditedDeath(
                    UUID.randomUUID(),
                    victim.getUniqueId(),
                    1
            );
            return;
        }

        CombatAttribution.Attribution attribution = attributionOptional.get();
        UUID killerId = Objects.requireNonNull(attribution.killerId());
        @Nullable UUID carryId = attribution.carryId();
        Set<UUID> assistIds = new HashSet<>(attribution.assistIds());

        Player killer = Bukkit.getPlayer(killerId);
        if (killer != null && killer.isOnline()) {
            String killerHealth = formatHealth(killer.getHealth());
            BloodstoneText.sendMessage(
                    killer,
                    BloodstoneServerConstants.KILLER_MESSAGE_FORMAT,
                    Placeholder.component(
                            "victim",
                            playerNameService.resolveOnlinePlayerName(victim)
                    ),
                    Placeholder.unparsed("health", killerHealth)
            );
            BloodstoneText.sendMessage(
                    victim,
                    BloodstoneServerConstants.VICTIM_MESSAGE_FORMAT,
                    Placeholder.component(
                            "killer",
                            playerNameService.resolveOnlinePlayerName(killer)
                    ),
                    Placeholder.unparsed("health", killerHealth)
            );
        }
        healContributors(
                attribution.eligibleContributions(),
                attribution.totalDamage()
        );

        DominationOutcome dominationOutcome = updateDomination(
                killerId,
                victim.getUniqueId()
        );
        resolveStatistics(
                UUID.randomUUID(),
                killerId,
                victim.getUniqueId(),
                carryId,
                assistIds,
                dominationOutcome,
                1
        );
    }

    public void handleQuit(UUID playerId) {
        pendingRespawnNotifications.remove(playerId);
    }

    public void handleRespawn(Player player) {
        RespawnNotification notification =
                pendingRespawnNotifications.remove(player.getUniqueId());
        if (notification == null) {
            return;
        }
        Component otherPlayerName = resolvePlayerName(
                notification.otherPlayerId()
        );
        switch (notification.type()) {
            case DOMINATED -> presentationService.playDominationRespawn(
                    player,
                    otherPlayerName
            );
            case DOMINATION_LOST ->
                    presentationService.playDominationLost(
                            player,
                            otherPlayerName
                    );
        }
    }

    public void tick() {
        playRevengeTargetParticles();
    }

    public void clear() {
        dominationTracker.clear();
        pendingRespawnNotifications.clear();
    }

    private void resolveUncreditedDeath(
            UUID eventId,
            UUID victimId,
            int attempt
    ) {
        guildMembershipService.findGuildByPlayer(victimId)
                .thenCompose(guild -> storage.recordDeath(
                        eventId,
                        victimId,
                        guild.map(GuildProfile::guildId).orElse(null),
                        Instant.now()
                ))
                .thenAcceptAsync(
                        applied -> playerService.refreshOnlineProfiles(
                                Set.of(victimId)
                        ),
                        mainThreadExecutor
                )
                .exceptionally(exception -> {
                    if (attempt < STATISTIC_RESOLUTION_MAXIMUM_ATTEMPTS) {
                        mainThreadExecutor.execute(() ->
                                Bukkit.getScheduler().runTaskLater(
                                        plugin,
                                        () -> resolveUncreditedDeath(
                                                eventId,
                                                victimId,
                                                attempt + 1
                                        ),
                                        attempt * 100L
                                ));
                    } else {
                        logger.log(
                                Level.SEVERE,
                                "Failed to persist uncredited Bloodstone death "
                                        + eventId,
                                exception
                        );
                    }
                    return null;
                });
    }

    private void healContributors(
            List<CombatAttribution.Contribution> activeContributions,
            double totalDamage
    ) {
        if (totalDamage <= 0.0D) {
            return;
        }
        for (CombatAttribution.Contribution contribution
                : activeContributions) {
            Player contributor = Bukkit.getPlayer(contribution.attackerId());
            if (contributor == null
                    || !contributor.isOnline()
                    || !isInBloodstone(contributor)) {
                continue;
            }
            double share = contribution.damage() / totalDamage;
            double healing = CombatAttribution.healing(
                    contribution.damage(),
                    totalDamage
            );
            contributor.setHealth(Math.min(
                    contributor.getMaxHealth(),
                    contributor.getHealth() + healing
            ));
            BloodstoneText.sendActionBar(
                    contributor,
                    BloodstoneServerConstants
                            .CONTRIBUTION_HEAL_ACTION_BAR_FORMAT,
                    Placeholder.unparsed(
                            "share",
                            String.format(Locale.US, "%.1f", share * 100.0D)
                    ),
                    Placeholder.unparsed(
                            "healing",
                            String.format(Locale.US, "%.1f", healing)
                    )
            );
        }
    }

    private DominationOutcome updateDomination(
            UUID killerId,
            UUID victimId
    ) {
        DominationTracker.Outcome outcome = dominationTracker.recordKill(
                killerId,
                victimId
        );
        if (outcome.revengeCredit()) {
            broadcastRevenge(killerId, victimId);
            pendingRespawnNotifications.put(
                    victimId,
                    new RespawnNotification(
                            RespawnNotificationType.DOMINATION_LOST,
                            killerId
                    )
            );
            Player revengePlayer = Bukkit.getPlayer(killerId);
            if (revengePlayer != null && revengePlayer.isOnline()) {
                presentationService.playRevenge(
                        revengePlayer,
                        resolvePlayerName(victimId)
                );
            }
        }
        if (outcome.announceDomination()) {
            broadcastDomination(
                    killerId,
                    victimId,
                    outcome.killCount()
            );
            pendingRespawnNotifications.put(
                    victimId,
                    new RespawnNotification(
                            RespawnNotificationType.DOMINATED,
                            killerId
                    )
            );
            Player dominator = Bukkit.getPlayer(killerId);
            if (dominator != null && dominator.isOnline()) {
                presentationService.playDomination(
                        dominator,
                        resolvePlayerName(victimId)
                );
            }
        }
        return new DominationOutcome(
                outcome.dominationCredit(),
                outcome.revengeCredit()
        );
    }

    private void resolveStatistics(
            UUID eventId,
            UUID killerId,
            UUID victimId,
            @Nullable UUID carryId,
            Set<UUID> assistIds,
            DominationOutcome dominationOutcome,
            int attempt
    ) {
        CompletableFuture<Optional<GuildProfile>> killerGuild =
                guildMembershipService.findGuildByPlayer(killerId);
        CompletableFuture<Optional<GuildProfile>> victimGuild =
                guildMembershipService.findGuildByPlayer(victimId);
        killerGuild.thenCombine(
                        victimGuild,
                        (killerGuildProfile, victimGuildProfile) ->
                                new CombatResolution(
                                        eventId,
                                        killerId,
                                        victimId,
                                        carryId,
                                        assistIds,
                                        killerGuildProfile
                                                .map(GuildProfile::guildId)
                                                .orElse(null),
                                        victimGuildProfile
                                                .map(GuildProfile::guildId)
                                                .orElse(null),
                                        dominationOutcome.domination(),
                                        dominationOutcome.revenge(),
                                        Instant.now()
                                )
                )
                .thenCompose(storage::resolveCombat)
                .thenAcceptAsync(outcome -> handleStatisticOutcome(
                                killerId,
                                victimId,
                                assistIds,
                                carryId,
                                outcome
                        ),
                        mainThreadExecutor
                )
                .exceptionally(exception -> {
                    if (attempt < STATISTIC_RESOLUTION_MAXIMUM_ATTEMPTS) {
                        mainThreadExecutor.execute(() ->
                                Bukkit.getScheduler().runTaskLater(
                                        plugin,
                                        () -> resolveStatistics(
                                                eventId,
                                                killerId,
                                                victimId,
                                                carryId,
                                                assistIds,
                                                dominationOutcome,
                                                attempt + 1
                                        ),
                                        attempt * 100L
                                ));
                    } else {
                        logger.log(
                                Level.SEVERE,
                                "Failed to persist Bloodstone combat event "
                                        + eventId,
                                exception
                        );
                    }
                    return null;
                });
    }

    private void handleStatisticOutcome(
            UUID killerId,
            UUID victimId,
            Set<UUID> assistIds,
            @Nullable UUID carryId,
            CombatResolutionOutcome outcome
    ) {
        Set<UUID> affectedPlayers = new HashSet<>(assistIds);
        affectedPlayers.add(killerId);
        affectedPlayers.add(victimId);
        if (carryId != null) {
            affectedPlayers.add(carryId);
        }
        playerService.refreshOnlineProfiles(affectedPlayers);
        if (!outcome.newlyApplied()) {
            return;
        }

        Player killer = Bukkit.getPlayer(killerId);
        if (killer != null && killer.isOnline()) {
            int rampage = outcome.killerCurrentRampage();
            if (CombatAnnouncementProgression.isRampageMilestone(rampage)) {
                broadcastRampage(killer, rampage);
            }
        }
    }

    private void broadcastDomination(
            UUID killerId,
            UUID victimId,
            int count
    ) {
        broadcastBloodstone(BloodstoneText.deserialize(
                CombatAnnouncements.domination(count),
                playerNameResolvers(killerId, victimId)
        ));
    }

    private void broadcastRevenge(UUID killerId, UUID victimId) {
        broadcastBloodstone(BloodstoneText.deserialize(
                CombatAnnouncements.revenge(),
                playerNameResolvers(killerId, victimId)
        ));
    }

    private void playRevengeTargetParticles() {
        for (DominationTracker.ActiveDomination domination
                : dominationTracker.activeDominations()) {
            Player dominatedPlayer = Bukkit.getPlayer(
                    domination.dominatedPlayerId()
            );
            Player dominator = Bukkit.getPlayer(domination.dominatorId());
            if (dominatedPlayer == null
                    || !dominatedPlayer.isOnline()
                    || dominator == null
                    || !dominator.isOnline()
                    || !isInBloodstone(dominatedPlayer)
                    || !isInBloodstone(dominator)
                    || !dominatedPlayer.getWorld()
                    .equals(dominator.getWorld())) {
                continue;
            }
            presentationService.playRevengeTargetParticles(
                    dominatedPlayer,
                    dominator
            );
        }
    }

    private TagResolver playerNameResolvers(
            UUID killerId,
            UUID victimId
    ) {
        return TagResolver.resolver(
                Placeholder.component("killer", resolvePlayerName(killerId)),
                Placeholder.component("victim", resolvePlayerName(victimId))
        );
    }

    private void broadcastBloodstone(Component message) {
        Component broadcast = BloodstoneText.deserialize(
                BloodstoneServerConstants.COMBAT_BROADCAST_FORMAT,
                Placeholder.component("message", message)
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isInBloodstone(player)) {
                BloodstoneText.sendMessage(player, broadcast);
            }
        }
    }

    private void broadcastRampage(Player killer, int rampage) {
        CombatAnnouncements.RampageAnnouncement message =
                CombatAnnouncements.rampage(rampage);
        Component rampageIndicator = BloodstoneText.deserialize(
                BloodstoneServerConstants.RAMPAGE_INDICATOR_FORMAT,
                Placeholder.unparsed("rampage", Integer.toString(rampage))
        );
        Component broadcastWeapon = rampageIndicator.color(
                message.weaponColor()
        );
        broadcastBloodstone(BloodstoneText.deserialize(
                RAMPAGE_MESSAGE_FORMAT,
                Placeholder.unparsed("text", message.text()),
                Placeholder.component(
                        "killer",
                        playerNameService.resolveOnlinePlayerName(killer)
                ),
                Placeholder.component("weapon", broadcastWeapon)
        ));
        presentationService.playRampageAnnouncement(
                killer,
                BloodstoneText.deserialize(RAMPAGE_TITLE),
                rampageIndicator.color(NamedTextColor.GRAY)
        );
    }

    private static boolean isInBloodstone(Player player) {
        return BloodstoneServerConstants.WORLD_NAME.equals(
                player.getWorld().getName()
        );
    }

    private Component resolvePlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return playerNameService.resolvePlayerName(player, playerId);
    }

    private static String formatHealth(double health) {
        return String.format(Locale.US, "%.1f", health);
    }

    private record DominationOutcome(boolean domination, boolean revenge) {
    }

    private record RespawnNotification(
            RespawnNotificationType type,
            UUID otherPlayerId
    ) {
        private RespawnNotification {
            Objects.requireNonNull(
                    type,
                    "Respawn notification type cannot be null"
            );
            Objects.requireNonNull(
                    otherPlayerId,
                    "Respawn notification player ID cannot be null"
            );
        }
    }

    private enum RespawnNotificationType {
        DOMINATED,
        DOMINATION_LOST
    }
}
