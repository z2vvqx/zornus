package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectAxeDefinition;
import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectTarget;
import net.valoury.bloodstone.server.EffectAxeItemDefinition;
import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.valoury.bloodstone.server.model.CombatResolution;
import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import net.valoury.bloodstone.server.storage.CombatResolutionOutcome;
import net.valoury.guilds.api.GuildMembershipService;
import net.valoury.guilds.api.GuildProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BloodstoneCombatService {

    private static final String BLOODSTONE_BROADCAST_FORMAT =
            "<dark_aqua>Bloodstone</dark_aqua> <dark_gray>─</dark_gray> <white><message></white>";
    private static final String RAMPAGE_MESSAGE_FORMAT =
            "<green><text> <white><killer></white>'s rampage! <weapon></green>";
    private static final String RAMPAGE_TITLE =
            "<aqua><bold>RAMPAGE</bold></aqua>";
    private static final String RAMPAGE_INDICATOR_FORMAT = "<bold>ᐃ <rampage></bold>";
    private static final double BLOOD_DROP_CHANCE = 0.5D;
    private static final long COMBAT_DURATION_MILLISECONDS = 15_000L;
    private static final long COMBAT_DURATION_NANOSECONDS =
            TimeUnit.SECONDS.toNanos(15);
    private static final long EFFECT_AXE_COOLDOWN_NANOSECONDS =
            TimeUnit.SECONDS.toNanos(1);
    private static final int STATISTIC_RESOLUTION_MAXIMUM_ATTEMPTS = 3;

    private final BloodstoneStorage storage;
    private final Plugin plugin;
    private final GuildMembershipService guildMembershipService;
    private final BloodstoneItemService itemService;
    private final BloodstoneSpawnProtectionService spawnProtectionService;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstonePlayerService playerService;
    private final Logger logger;

    private final Map<UUID, CombatTag> combatTags = new HashMap<>();
    private final Map<UUID, Map<UUID, DamageContribution>> contributionsByVictim = new HashMap<>();
    private final Map<UUID, UUID> forcedKillerByVictim = new HashMap<>();
    private final DominationTracker dominationTracker = new DominationTracker();
    private final Map<EffectAxeTargetCooldown, Long> effectAxeCooldowns = new HashMap<>();
    private final Map<UUID, RespawnNotification> pendingRespawnNotifications =
            new HashMap<>();
    private final Map<UUID, ExperienceSnapshot> pendingExperienceRestores =
            new HashMap<>();

    public BloodstoneCombatService(
            Plugin plugin,
            BloodstoneStorage storage,
            GuildMembershipService guildMembershipService,
            BloodstoneItemService itemService,
            BloodstoneSpawnProtectionService spawnProtectionService,
            BloodstonePresentationService presentationService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            BloodstonePlayerService playerService,
            Logger logger
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.guildMembershipService = guildMembershipService;
        this.itemService = itemService;
        this.spawnProtectionService = spawnProtectionService;
        this.presentationService = presentationService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.playerService = playerService;
        this.logger = logger;
    }

    public boolean isTagged(UUID playerId) {
        return combatTags.containsKey(playerId);
    }

    public void forceKillAttribution(
            UUID victimId,
            UUID killerId,
            double attributionDamage
    ) {
        if (!Double.isFinite(attributionDamage) || attributionDamage <= 0.0D) {
            throw new IllegalArgumentException("Attribution damage must be positive and finite");
        }
        forcedKillerByVictim.put(victimId, killerId);
        Map<UUID, DamageContribution> forcedContribution = new HashMap<>();
        forcedContribution.put(
                killerId,
                new DamageContribution(
                        attributionDamage,
                        System.currentTimeMillis()
                )
        );
        contributionsByVictim.put(victimId, forcedContribution);
    }

    public void handleDuelEnd(Player player) {
        removeCombatTag(player, true);
        contributionsByVictim.remove(player.getUniqueId());
    }

    public void handleDamage(EntityDamageByEntityEvent event) {
        Player victim = event.getEntity() instanceof Player player ? player : null;
        Player attacker = resolveAttacker(event.getDamager());
        if (victim != null
                && attacker != null
                && isInBloodstone(victim)
                && shouldCancelSelfInflictedBowDamage(
                        event.getDamager() instanceof Arrow,
                        victim.getUniqueId(),
                        attacker.getUniqueId()
                )) {
            event.setCancelled(true);
            return;
        }
        if (victim == null || attacker == null || attacker.equals(victim)
                || !isInBloodstone(victim) || !isInBloodstone(attacker)) {
            return;
        }
        if (attacker.getUniqueId().equals(
                forcedKillerByVictim.get(victim.getUniqueId()))) {
            return;
        }
        if (!playerService.isLoaded(victim.getUniqueId())
                || !playerService.isLoaded(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Thorns reports the original melee roles in reverse, so normalize its fallback activation.
        if (shouldActivateEffectAxeFromDamageEvent(event.getCause())) {
            handleEffectAxeAttack(
                    victim,
                    attacker,
                    victim.getInventory().getHeldItemSlot()
            );
        }

        long nowMilliseconds = System.currentTimeMillis();
        long nowNanoseconds = System.nanoTime();
        recordContribution(
                victim.getUniqueId(),
                attacker.getUniqueId(),
                event.getFinalDamage(),
                nowMilliseconds
        );
        tag(attacker, nowNanoseconds);
        tag(victim, nowNanoseconds);

        if (event.getDamager() instanceof Arrow arrow && arrow.isCritical()) {
            dropBlood(attacker, victim);
            sendArrowFeedback(attacker, victim, arrow, event.getFinalDamage());
        } else if (event.getDamager() instanceof Player) {
            ItemStack heldItem = attacker.getItemInHand();
            if (heldItem != null
                    && (heldItem.getType() == Material.DIAMOND_SWORD
                    || heldItem.getType() == Material.DIAMOND_AXE)) {
                dropBlood(attacker, victim);
            }
        }
    }

    public void handleDeath(Player victim) {
        forcedKillerByVictim.remove(victim.getUniqueId());
        removeCombatTag(victim, false);
        Map<UUID, DamageContribution> victimContributions =
                contributionsByVictim.remove(victim.getUniqueId());
        if (victimContributions == null || victimContributions.isEmpty()) {
            BloodstoneText.sendMessage(
                    victim,
                    BloodstoneServerConstants.UNATTRIBUTED_DEATH_MESSAGE
            );
            resolveUncreditedDeath(UUID.randomUUID(), victim.getUniqueId(), 1);
            return;
        }

        long nowMilliseconds = System.currentTimeMillis();
        CombatAttribution.Attribution attribution = CombatAttribution.resolve(
                victimContributions.entrySet().stream()
                        .map(entry -> new CombatAttribution.Contribution(
                                entry.getKey(),
                                entry.getValue().damage(),
                                entry.getValue().lastContributionAt()
                        ))
                        .toList(),
                nowMilliseconds
        );
        if (!attribution.hasEligibleContributor()) {
            BloodstoneText.sendMessage(
                    victim,
                    BloodstoneServerConstants.UNATTRIBUTED_DEATH_MESSAGE
            );
            resolveUncreditedDeath(UUID.randomUUID(), victim.getUniqueId(), 1);
            return;
        }

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
                            victim.displayName()
                    ),
                    Placeholder.unparsed("health", killerHealth)
            );
            BloodstoneText.sendMessage(
                    victim,
                    BloodstoneServerConstants.VICTIM_MESSAGE_FORMAT,
                    Placeholder.component(
                            "killer",
                            killer.displayName()
                    ),
                    Placeholder.unparsed("health", killerHealth)
            );
        }
        healContributors(attribution.eligibleContributions(), attribution.totalDamage());

        DominationOutcome dominationOutcome = updateDomination(killerId, victim.getUniqueId());
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

    public void preserveExperienceOnDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        CombatTag combatTag = combatTags.get(player.getUniqueId());
        ExperienceSnapshot experienceSnapshot;
        if (combatTag == null) {
            experienceSnapshot = new ExperienceSnapshot(
                    player.getLevel(),
                    player.getExp(),
                    player.getTotalExperience()
            );
        } else {
            experienceSnapshot = combatTag.experienceSnapshot();
        }
        pendingExperienceRestores.put(
                player.getUniqueId(),
                experienceSnapshot
        );
        event.setDroppedExp(0);
        event.setKeepLevel(true);
        restoreExperience(player, experienceSnapshot);
    }

    private void resolveUncreditedDeath(UUID eventId, UUID victimId, int attempt) {
        guildMembershipService.findGuildByPlayer(victimId)
                .thenCompose(guild -> storage.recordDeath(
                        eventId,
                        victimId,
                        guild.map(GuildProfile::guildId).orElse(null),
                        Instant.now()
                ))
                .thenAcceptAsync(applied -> playerService.refreshProfiles(Set.of(victimId)), mainThreadExecutor)
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
                        logger.log(Level.SEVERE,
                                "Failed to persist uncredited Bloodstone death " + eventId, exception);
                    }
                    return null;
                });
    }

    public void handleQuit(Player player) {
        if (isTagged(player.getUniqueId())) {
            player.setHealth(0.0);
        }
        pendingRespawnNotifications.remove(player.getUniqueId());
        effectAxeCooldowns.keySet().removeIf(key ->
                key.attackerId().equals(player.getUniqueId()) || key.victimId().equals(player.getUniqueId()));
    }

    public void handleRespawn(Player player) {
        ExperienceSnapshot experienceSnapshot =
                pendingExperienceRestores.remove(player.getUniqueId());
        if (experienceSnapshot != null) {
            restoreExperience(player, experienceSnapshot);
        }
        RespawnNotification notification =
                pendingRespawnNotifications.remove(player.getUniqueId());
        if (notification == null) {
            return;
        }
        Component otherPlayerName = displayName(notification.otherPlayerId());
        switch (notification.type()) {
            case DOMINATED -> presentationService.playDominationRespawn(
                    player,
                    otherPlayerName
            );
            case DOMINATION_LOST -> presentationService.playDominationLost(
                    player,
                    otherPlayerName
            );
        }
    }

    public void handleEnteredSpawn(Player player) {
        removeCombatTag(player, true);
    }

    public void tick() {
        long nowMilliseconds = System.currentTimeMillis();
        long nowNanoseconds = System.nanoTime();
        List<UUID> expiredTags = new ArrayList<>();
        for (Map.Entry<UUID, CombatTag> entry : combatTags.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            int seconds = remainingCombatSeconds(
                    entry.getValue().expiresAtNanoseconds(),
                    nowNanoseconds
            );
            if (seconds == 0) {
                expiredTags.add(entry.getKey());
                continue;
            }
            player.setLevel(seconds);
            player.setExp(combatProgress(seconds));
        }
        for (UUID playerId : expiredTags) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                removeCombatTag(player, true);
            } else {
                combatTags.remove(playerId);
            }
        }

        contributionsByVictim.values().forEach(contributions ->
                contributions.entrySet().removeIf(entry ->
                        nowMilliseconds - entry.getValue().lastContributionAt()
                                >= COMBAT_DURATION_MILLISECONDS));
        contributionsByVictim.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        effectAxeCooldowns.entrySet().removeIf(entry ->
                nowNanoseconds - entry.getValue()
                        >= EFFECT_AXE_COOLDOWN_NANOSECONDS);
        playRevengeTargetParticles();
    }

    public void shutdown() {
        for (Map.Entry<UUID, CombatTag> entry : new ArrayList<>(combatTags.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                restoreExperience(player, entry.getValue());
            }
        }
        combatTags.clear();
        contributionsByVictim.clear();
        forcedKillerByVictim.clear();
        dominationTracker.clear();
        effectAxeCooldowns.clear();
        pendingRespawnNotifications.clear();
        pendingExperienceRestores.clear();
    }

    private void tag(Player player, long nowNanoseconds) {
        CombatTag existing = combatTags.get(player.getUniqueId());
        if (existing == null) {
            combatTags.put(player.getUniqueId(),
                    new CombatTag(
                            player.getLevel(),
                            player.getExp(),
                            player.getTotalExperience(),
                            nowNanoseconds
                                    + COMBAT_DURATION_NANOSECONDS
                    ));
            BloodstoneText.sendActionBar(
                    player,
                    BloodstoneServerConstants.COMBAT_ENTER_ACTION_BAR
            );
        } else {
            combatTags.put(player.getUniqueId(),
                    new CombatTag(
                            existing.originalLevel(),
                            existing.originalProgress(),
                            existing.originalTotalExperience(),
                            nowNanoseconds
                                    + COMBAT_DURATION_NANOSECONDS
                    ));
        }
        player.setLevel(15);
        player.setExp(0.99F);
    }

    private void removeCombatTag(Player player, boolean notify) {
        CombatTag combatTag = combatTags.remove(player.getUniqueId());
        if (combatTag == null) {
            return;
        }
        restoreExperience(player, combatTag);
        if (notify && player.isOnline()) {
            BloodstoneText.sendActionBar(
                    player,
                    BloodstoneServerConstants.COMBAT_EXIT_ACTION_BAR
            );
        }
    }

    private void restoreExperience(Player player, CombatTag combatTag) {
        restoreExperience(player, combatTag.experienceSnapshot());
    }

    private void restoreExperience(
            Player player,
            ExperienceSnapshot experienceSnapshot
    ) {
        player.setTotalExperience(experienceSnapshot.totalExperience());
        player.setLevel(experienceSnapshot.level());
        player.setExp(experienceSnapshot.progress());
    }

    private void recordContribution(UUID victimId, UUID attackerId, double damage, long now) {
        Map<UUID, DamageContribution> contributions =
                contributionsByVictim.computeIfAbsent(victimId, ignored -> new HashMap<>());
        DamageContribution previous = contributions.get(attackerId);
        double accumulatedDamage = previous == null ? damage : previous.damage() + damage;
        contributions.put(attackerId, new DamageContribution(accumulatedDamage, now));
    }

    private void dropBlood(Player attacker, Player victim) {
        if (!isBloodDropRoll(ThreadLocalRandom.current().nextDouble())) {
            return;
        }
        int amount = BloodstoneRank.resolve(attacker).bloodPerQualifyingHit();
        Item dropped = victim.getWorld().dropItem(
                victim.getLocation().clone().add(0.0, 1.0, 0.0),
                itemService.createBlood(amount)
        );
        dropped.setVelocity(dropped.getVelocity().setY(0.16));
        presentationService.playBloodDropStep(victim.getLocation());
    }

    private void sendArrowFeedback(Player attacker, Player victim, Arrow arrow, double finalDamage) {
        if (!isHeadshot(
                arrow.getLocation().getY(),
                victim.getEyeLocation().getY()
        )) {
            return;
        }
        double remainingHealth = Math.max(0.0, victim.getHealth() - finalDamage);
        BloodstoneText.sendMessage(
                attacker,
                BloodstoneServerConstants.ARROW_FEEDBACK_FORMAT,
                Placeholder.component(
                        "shot",
                        BloodstoneText.deserialize(
                                BloodstoneServerConstants.HEADSHOT_DISPLAY
                        )
                ),
                Placeholder.component(
                        "victim",
                        victim.displayName()
                ),
                Placeholder.unparsed("health", formatHealth(remainingHealth))
        );
    }

    public void handleEffectAxeAttack(Player attacker, Player victim, int heldSlot) {
        if (heldSlot < 0 || heldSlot > 8
                || !isInBloodstone(attacker)
                || !isInBloodstone(victim)
                || !playerService.isLoaded(attacker.getUniqueId())
                || !playerService.isLoaded(victim.getUniqueId())) {
            return;
        }
        ItemStack heldItem = attacker.getInventory().getItem(heldSlot);
        if (heldItem == null || spawnProtectionService.isInsideSpawn(attacker)
                || spawnProtectionService.isInsideSpawn(victim)) {
            return;
        }
        Optional<EffectAxeItemDefinition> definitionOptional =
                itemService.effectAxeDefinition(heldItem);
        if (definitionOptional.isEmpty()) {
            return;
        }
        EffectAxeItemDefinition definition = definitionOptional.get();
        long nowNanoseconds = System.nanoTime();
        EffectAxeTargetCooldown cooldown = new EffectAxeTargetCooldown(
                attacker.getUniqueId(),
                victim.getUniqueId(),
                definition.id()
        );
        Long previousUse = effectAxeCooldowns.get(cooldown);
        if (!isEffectAxeActivationReady(previousUse, nowNanoseconds)) {
            return;
        }
        effectAxeCooldowns.put(cooldown, nowNanoseconds);

        int particleCount = BloodstonePresentationService
                .effectAxeParticleCount(definition.effects().size());
        for (EffectAxeDefinition effect : definition.effects()) {
            Player effectRecipient = effect.target() == EffectTarget.SELF
                    ? attacker
                    : victim;
            effectRecipient.addPotionEffect(effect.createPotionEffect(), true);
            presentationService.playEffectAxeParticles(
                    effectRecipient,
                    effect.particleColor(),
                    particleCount
            );
        }
        presentationService.playEffectAxeSound();
        if (itemService.consumeControlledUse(heldItem)) {
            attacker.getInventory().clear(heldSlot);
            presentationService.playEffectAxeBreak(attacker);
        } else {
            attacker.getInventory().setItem(heldSlot, heldItem);
        }
    }

    private void healContributors(
            List<CombatAttribution.Contribution> activeContributions,
            double totalDamage
    ) {
        if (totalDamage <= 0.0) {
            return;
        }
        for (CombatAttribution.Contribution contribution : activeContributions) {
            Player contributor = Bukkit.getPlayer(contribution.attackerId());
            if (contributor == null || !contributor.isOnline() || !isInBloodstone(contributor)) {
                continue;
            }
            double share = contribution.damage() / totalDamage;
            double healing = CombatAttribution.healing(contribution.damage(), totalDamage);
            contributor.setHealth(Math.min(contributor.getMaxHealth(), contributor.getHealth() + healing));
            BloodstoneText.sendActionBar(
                    contributor,
                    BloodstoneServerConstants.CONTRIBUTION_HEAL_ACTION_BAR_FORMAT,
                    Placeholder.unparsed(
                            "share",
                            String.format(Locale.US, "%.1f", share * 100.0)
                    ),
                    Placeholder.unparsed(
                            "healing",
                            String.format(Locale.US, "%.1f", healing)
                    )
            );
        }
    }

    private DominationOutcome updateDomination(UUID killerId, UUID victimId) {
        DominationTracker.Outcome outcome = dominationTracker.recordKill(killerId, victimId);
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
                        displayName(victimId)
                );
            }
        }
        if (outcome.announceDomination()) {
            broadcastDomination(killerId, victimId, outcome.killCount());
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
                        displayName(victimId)
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
        killerGuild.thenCombine(victimGuild, (killerGuildProfile, victimGuildProfile) ->
                        new CombatResolution(
                                eventId,
                                killerId,
                                victimId,
                                carryId,
                                assistIds,
                                killerGuildProfile.map(GuildProfile::guildId).orElse(null),
                                victimGuildProfile.map(GuildProfile::guildId).orElse(null),
                                dominationOutcome.domination(),
                                dominationOutcome.revenge(),
                                Instant.now()
                        ))
                .thenCompose(storage::resolveCombat)
                .thenAcceptAsync(outcome ->
                                handleStatisticOutcome(killerId, victimId, assistIds, carryId, outcome),
                        mainThreadExecutor)
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
                        logger.log(Level.SEVERE, "Failed to persist Bloodstone combat event " + eventId, exception);
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
        playerService.refreshProfiles(affectedPlayers);
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

    private void broadcastDomination(UUID killerId, UUID victimId, int count) {
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
            Player dominatedPlayer =
                    Bukkit.getPlayer(domination.dominatedPlayerId());
            Player dominator = Bukkit.getPlayer(domination.dominatorId());
            if (dominatedPlayer == null || !dominatedPlayer.isOnline()
                    || dominator == null || !dominator.isOnline()
                    || !isInBloodstone(dominatedPlayer)
                    || !isInBloodstone(dominator)
                    || !dominatedPlayer.getWorld().equals(dominator.getWorld())) {
                continue;
            }
            presentationService.playRevengeTargetParticles(
                    dominatedPlayer,
                    dominator
            );
        }
    }

    private TagResolver playerNameResolvers(UUID killerId, UUID victimId) {
        return TagResolver.resolver(
                Placeholder.component("killer", displayName(killerId)),
                Placeholder.component("victim", displayName(victimId))
        );
    }

    private void broadcastBloodstone(Component message) {
        Component broadcast = BloodstoneText.deserialize(
                BLOODSTONE_BROADCAST_FORMAT,
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
                RAMPAGE_INDICATOR_FORMAT,
                Placeholder.unparsed("rampage", Integer.toString(rampage))
        );
        Component broadcastWeapon = rampageIndicator.color(message.weaponColor());
        Component killerName =
                killer.displayName();
        broadcastBloodstone(BloodstoneText.deserialize(
                RAMPAGE_MESSAGE_FORMAT,
                Placeholder.unparsed("text", message.text()),
                Placeholder.component("killer", killerName),
                Placeholder.component("weapon", broadcastWeapon)
        ));
        presentationService.playRampageAnnouncement(
                killer,
                BloodstoneText.deserialize(RAMPAGE_TITLE),
                rampageIndicator.color(NamedTextColor.GRAY)
        );
    }

    @Contract(pure = true)
    static boolean isEffectAxeActivationReady(
            @Nullable Long previousUseNanoseconds,
            long nowNanoseconds
    ) {
        return previousUseNanoseconds == null
                || nowNanoseconds - previousUseNanoseconds
                >= EFFECT_AXE_COOLDOWN_NANOSECONDS;
    }

    @Contract(pure = true)
    static boolean shouldActivateEffectAxeFromDamageEvent(
            @Nullable DamageCause damageCause
    ) {
        return damageCause == DamageCause.THORNS;
    }

    @Contract(pure = true)
    static boolean shouldCancelSelfInflictedBowDamage(
            boolean causedByArrow,
            @Nullable UUID damagedPlayerId,
            @Nullable UUID shooterId
    ) {
        return causedByArrow
                && damagedPlayerId != null
                && damagedPlayerId.equals(shooterId);
    }

    @Contract(pure = true)
    static int remainingCombatSeconds(
            long expiresAtNanoseconds,
            long nowNanoseconds
    ) {
        long remainingNanoseconds = expiresAtNanoseconds - nowNanoseconds;
        if (remainingNanoseconds <= 0) {
            return 0;
        }
        return Math.toIntExact(
                Math.floorDiv(remainingNanoseconds - 1, 1_000_000_000L) + 1
        );
    }

    @Contract(pure = true)
    static float combatProgress(int secondsRemaining) {
        float elapsedFraction = Math.max(0, Math.min(15, secondsRemaining)) / 15.0F;
        return Math.min(0.99F, elapsedFraction * 0.99F);
    }

    @Contract(pure = true)
    static boolean isBloodDropRoll(double randomValue) {
        if (!Double.isFinite(randomValue)
                || randomValue < 0.0D
                || randomValue >= 1.0D) {
            throw new IllegalArgumentException(
                    "Random value must be in the range [0.0, 1.0)"
            );
        }
        return randomValue < BLOOD_DROP_CHANCE;
    }

    @Contract(pure = true)
    static boolean isHeadshot(double arrowImpactY, double victimEyeY) {
        return arrowImpactY >= victimEyeY;
    }

    private @Nullable Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        return null;
    }

    private boolean isInBloodstone(Player player) {
        return BloodstoneServerConstants.WORLD_NAME.equals(player.getWorld().getName());
    }

    private Component displayName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player == null
                ? Component.text(playerId.toString().substring(0, 8))
                : player.displayName();
    }

    private String formatHealth(double health) {
        return String.format(Locale.US, "%.1f", health);
    }

    private record CombatTag(
            int originalLevel,
            float originalProgress,
            int originalTotalExperience,
            long expiresAtNanoseconds
    ) {
        private ExperienceSnapshot experienceSnapshot() {
            return new ExperienceSnapshot(
                    originalLevel,
                    originalProgress,
                    originalTotalExperience
            );
        }
    }

    private record ExperienceSnapshot(
            int level,
            float progress,
            int totalExperience
    ) {
    }

    private record DamageContribution(double damage, long lastContributionAt) {
    }

    private record DominationOutcome(boolean domination, boolean revenge) {
    }

    private record RespawnNotification(
            RespawnNotificationType type,
            UUID otherPlayerId
    ) {
        private RespawnNotification {
            Objects.requireNonNull(type, "Respawn notification type cannot be null");
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

    private record EffectAxeTargetCooldown(UUID attackerId, UUID victimId, String axeId) {
    }

}
