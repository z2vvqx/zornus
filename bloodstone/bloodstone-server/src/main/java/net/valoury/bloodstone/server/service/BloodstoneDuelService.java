package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class BloodstoneDuelService {

    private static final long DUEL_REQUEST_EXPIRATION_TICKS = 30L * 20L;
    private static final long DUEL_COUNTDOWN_INTERVAL_TICKS = 20L;
    private static final double DUEL_FORFEIT_ATTRIBUTION_DAMAGE = 1.0D;
    private static final String DUEL_ARENA_UNAVAILABLE_WARNING =
            "Dueling is unavailable until both duel arena positions are configured";
    private static final String DUEL_RETURN_TELEPORT_WARNING_FORMAT =
            "Failed to return %s after duel %s";

    private final Plugin plugin;
    private final BloodstoneCombatService combatService;
    private final BloodstonePlayerService playerService;
    private final BloodstoneMessageService messageService;
    private final DuelSessionRegistry sessions = new DuelSessionRegistry();
    private final @Nullable DuelArena arena;

    public BloodstoneDuelService(
            Plugin plugin,
            BloodstoneCombatService combatService,
            BloodstonePlayerService playerService,
            BloodstoneMessageService messageService
    ) {
        this.plugin = plugin;
        this.combatService = combatService;
        this.playerService = playerService;
        this.messageService = messageService;
        this.arena = DuelArena.load(plugin);
        if (arena == null) {
            plugin.getLogger().warning(
                    DUEL_ARENA_UNAVAILABLE_WARNING);
        }
    }

    public void challenge(Player challenger, Player challengedPlayer) {
        if (arena == null) {
            messageService.sendUnable(
                    challenger,
                    BloodstoneServerConstants.DUEL_ARENA_UNAVAILABLE
            );
            return;
        }
        if (challenger.equals(challengedPlayer)) {
            messageService.sendUnable(challenger, BloodstoneServerConstants.DUEL_SELF);
            return;
        }
        if (sessions.isBusy(challenger.getUniqueId())) {
            messageService.sendUnable(
                    challenger,
                    BloodstoneServerConstants.DUEL_PLAYER_BUSY
            );
            return;
        }
        if (sessions.isBusy(challengedPlayer.getUniqueId())) {
            messageService.sendUnable(
                    challenger,
                    BloodstoneServerConstants.DUEL_TARGET_BUSY
            );
            return;
        }
        if (!isReady(challenger)) {
            messageService.sendUnable(
                    challenger,
                    BloodstoneServerConstants.DUEL_PLAYER_UNAVAILABLE
            );
            return;
        }
        if (!isReady(challengedPlayer)) {
            messageService.sendUnable(
                    challenger,
                    BloodstoneServerConstants.DUEL_TARGET_UNAVAILABLE
            );
            return;
        }

        DuelRequest request = sessions.createRequest(
                challenger.getUniqueId(),
                challengedPlayer.getUniqueId()
        );
        BloodstoneText.sendMessage(
                challenger,
                BloodstoneServerConstants.DUEL_REQUEST_SENT_FORMAT,
                Placeholder.component(
                        "player",
                        displayName(challengedPlayer, challengedPlayer.getUniqueId())
                )
        );
        BloodstoneText.sendMessage(
                challengedPlayer,
                BloodstoneServerConstants.DUEL_REQUEST_RECEIVED_FORMAT,
                Placeholder.component(
                        "player",
                        displayName(challenger, challenger.getUniqueId())
                )
        );
        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> expire(request),
                DUEL_REQUEST_EXPIRATION_TICKS
        );
    }

    public void accept(Player challengedPlayer) {
        DuelRequest request = sessions.requestFor(challengedPlayer.getUniqueId());
        if (request == null
                || !request.challengedPlayerId().equals(challengedPlayer.getUniqueId())) {
            messageService.sendUnable(
                    challengedPlayer,
                    BloodstoneServerConstants.DUEL_NO_REQUEST
            );
            return;
        }

        Player challenger = Bukkit.getPlayer(request.challengerId());
        if (challenger == null || !isReady(challenger) || !isReady(challengedPlayer)) {
            sessions.remove(request);
            BloodstoneText.sendMessage(
                    challengedPlayer,
                    BloodstoneServerConstants.DUEL_REQUEST_CANCELLED
            );
            notifyOnline(request.challengerId(),
                    BloodstoneServerConstants.DUEL_REQUEST_CANCELLED);
            return;
        }

        sessions.remove(request);
        start(request, challenger, challengedPlayer);
    }

    public void reject(Player challengedPlayer) {
        DuelRequest request = sessions.requestFor(challengedPlayer.getUniqueId());
        if (request == null
                || !request.challengedPlayerId().equals(challengedPlayer.getUniqueId())) {
            messageService.sendUnable(
                    challengedPlayer,
                    BloodstoneServerConstants.DUEL_NO_REQUEST
            );
            return;
        }

        sessions.remove(request);
        BloodstoneText.sendMessage(
                challengedPlayer,
                BloodstoneServerConstants.DUEL_REQUEST_REJECTED
        );
        Player challenger = Bukkit.getPlayer(request.challengerId());
        if (challenger != null && challenger.isOnline()) {
            BloodstoneText.sendMessage(
                    challenger,
                    BloodstoneServerConstants.DUEL_REJECTED_FORMAT,
                    Placeholder.component(
                            "player",
                            displayName(
                                    challengedPlayer,
                                    challengedPlayer.getUniqueId()
                            )
                    )
            );
        }
    }

    @Contract(pure = true)
    public boolean isCountingDown(UUID playerId) {
        ActiveDuel duel = sessions.duelFor(playerId);
        return duel != null && duel.phase() == DuelPhase.COUNTDOWN;
    }

    @Contract(pure = true)
    public boolean isDueling(UUID playerId) {
        return sessions.duelFor(playerId) != null;
    }

    @Contract(pure = true)
    public boolean shouldCancelDamage(EntityDamageEvent event) {
        Player victim = event.getEntity() instanceof Player player ? player : null;
        Player attacker = event instanceof EntityDamageByEntityEvent damageByEntity
                ? resolveAttacker(damageByEntity.getDamager())
                : null;
        ActiveDuel victimDuel = victim == null
                ? null
                : sessions.duelFor(victim.getUniqueId());
        ActiveDuel attackerDuel = attacker == null
                ? null
                : sessions.duelFor(attacker.getUniqueId());
        if (victimDuel == null && attackerDuel == null) {
            return false;
        }
        if ((victimDuel != null && victimDuel.phase() == DuelPhase.COUNTDOWN)
                || (attackerDuel != null && attackerDuel.phase() == DuelPhase.COUNTDOWN)) {
            return true;
        }
        if (!(event instanceof EntityDamageByEntityEvent)
                || victimDuel == null
                || attackerDuel == null) {
            return event instanceof EntityDamageByEntityEvent;
        }
        return !victimDuel.duelId().equals(attackerDuel.duelId());
    }

    public void handleDeath(Player defeatedPlayer) {
        ActiveDuel duel = sessions.removeDuelFor(defeatedPlayer.getUniqueId());
        if (duel == null) {
            return;
        }

        UUID winnerId = duel.opponentOf(defeatedPlayer.getUniqueId());
        Player winner = Bukkit.getPlayer(winnerId);
        BloodstoneText.sendMessage(
                defeatedPlayer,
                BloodstoneServerConstants.DUEL_DEFEAT_FORMAT,
                Placeholder.component("player", displayName(winner, winnerId))
        );
        if (winner == null || !winner.isOnline()) {
            return;
        }

        combatService.handleDuelEnd(winner);
        BloodstoneText.sendMessage(
                winner,
                BloodstoneServerConstants.DUEL_VICTORY_FORMAT,
                Placeholder.component(
                        "player",
                        displayName(
                                defeatedPlayer,
                                defeatedPlayer.getUniqueId()
                        )
                )
        );
        returnPlayer(winner, duel.returnPositionOf(winnerId), duel.duelId());
    }

    public void handleQuit(Player forfeitingPlayer) {
        cancelPendingRequest(forfeitingPlayer.getUniqueId());
        ActiveDuel duel = sessions.removeDuelFor(forfeitingPlayer.getUniqueId());
        if (duel == null) {
            return;
        }

        UUID winnerId = duel.opponentOf(forfeitingPlayer.getUniqueId());
        combatService.forceKillAttribution(
                forfeitingPlayer.getUniqueId(),
                winnerId,
                DUEL_FORFEIT_ATTRIBUTION_DAMAGE
        );
        Player winner = Bukkit.getPlayer(winnerId);
        if (winner != null && winner.isOnline() && !forfeitingPlayer.isDead()) {
            forfeitingPlayer.setNoDamageTicks(0);
            forfeitingPlayer.damage(
                    DUEL_FORFEIT_ATTRIBUTION_DAMAGE,
                    winner
            );
        }
        if (!forfeitingPlayer.isDead()) {
            forfeitingPlayer.setHealth(0.0D);
        }
        if (winner == null || !winner.isOnline()) {
            return;
        }

        combatService.handleDuelEnd(winner);
        BloodstoneText.sendMessage(
                winner,
                BloodstoneServerConstants.DUEL_FORFEIT_VICTORY_FORMAT,
                Placeholder.component(
                        "player",
                        displayName(
                                forfeitingPlayer,
                                forfeitingPlayer.getUniqueId()
                        )
                )
        );
        returnPlayer(winner, duel.returnPositionOf(winnerId), duel.duelId());
    }

    public void shutdown() {
        sessions.clear();
    }

    private void start(
            DuelRequest request,
            Player challenger,
            Player challengedPlayer
    ) {
        DuelArena duelArena = Objects.requireNonNull(arena);
        DuelPosition challengerReturnPosition = DuelPosition.from(
                challenger.getLocation());
        DuelPosition challengedPlayerReturnPosition = DuelPosition.from(
                challengedPlayer.getLocation());
        challenger.closeInventory();
        challengedPlayer.closeInventory();

        if (!challenger.teleport(
                duelArena.sideA().toLocation(),
                PlayerTeleportEvent.TeleportCause.PLUGIN
        )) {
            notifyTeleportFailure(challenger, challengedPlayer);
            return;
        }
        if (!challengedPlayer.teleport(
                duelArena.sideB().toLocation(),
                PlayerTeleportEvent.TeleportCause.PLUGIN
        )) {
            returnPlayer(challenger, challengerReturnPosition, request.requestId());
            notifyTeleportFailure(challenger, challengedPlayer);
            return;
        }

        ActiveDuel duel = sessions.createDuel(
                challenger.getUniqueId(),
                challengedPlayer.getUniqueId(),
                challengerReturnPosition,
                challengedPlayerReturnPosition
        );
        announceCountdown(duel, 3);
        scheduleCountdown(duel, 2, DUEL_COUNTDOWN_INTERVAL_TICKS);
        scheduleCountdown(
                duel,
                1,
                DUEL_COUNTDOWN_INTERVAL_TICKS * 2L
        );
        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> beginFight(duel),
                DUEL_COUNTDOWN_INTERVAL_TICKS * 3L
        );
    }

    private void scheduleCountdown(ActiveDuel duel, int seconds, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> announceCountdown(duel, seconds),
                delayTicks
        );
    }

    private void announceCountdown(ActiveDuel expectedDuel, int seconds) {
        ActiveDuel duel = currentCountdown(expectedDuel);
        if (duel == null) {
            return;
        }
        announceCountdown(Bukkit.getPlayer(duel.challengerId()), seconds);
        announceCountdown(Bukkit.getPlayer(duel.challengedPlayerId()), seconds);
    }

    private void announceCountdown(@Nullable Player player, int seconds) {
        if (player == null || !player.isOnline()) {
            return;
        }
        BloodstoneText.sendActionBar(
                player,
                BloodstoneServerConstants.DUEL_COUNTDOWN_ACTION_BAR_FORMAT,
                Placeholder.unparsed("seconds", Integer.toString(seconds))
        );
        player.playSound(player.getLocation(), Sound.NOTE_PLING, 0.8F, 1.0F);
    }

    private void beginFight(ActiveDuel expectedDuel) {
        ActiveDuel duel = currentCountdown(expectedDuel);
        if (duel == null) {
            return;
        }

        Player challenger = Bukkit.getPlayer(duel.challengerId());
        Player challengedPlayer = Bukkit.getPlayer(duel.challengedPlayerId());
        if (challenger == null || challengedPlayer == null
                || !challenger.isOnline() || !challengedPlayer.isOnline()) {
            return;
        }

        sessions.activate(duel);
        announceFight(challenger);
        announceFight(challengedPlayer);
    }

    private @Nullable ActiveDuel currentCountdown(ActiveDuel expectedDuel) {
        ActiveDuel currentDuel = sessions.duelFor(expectedDuel.challengerId());
        if (currentDuel == null
                || !currentDuel.duelId().equals(expectedDuel.duelId())
                || currentDuel.phase() != DuelPhase.COUNTDOWN) {
            return null;
        }
        return currentDuel;
    }

    private void announceFight(Player player) {
        BloodstoneText.sendActionBar(
                player,
                BloodstoneServerConstants.DUEL_STARTED_ACTION_BAR
        );
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 0.8F, 1.2F);
    }

    private void expire(DuelRequest expectedRequest) {
        DuelRequest currentRequest = sessions.requestFor(expectedRequest.challengerId());
        if (currentRequest == null
                || !currentRequest.requestId().equals(expectedRequest.requestId())) {
            return;
        }

        sessions.remove(currentRequest);
        notifyOnline(currentRequest.challengerId(),
                BloodstoneServerConstants.DUEL_REQUEST_EXPIRED);
        notifyOnline(currentRequest.challengedPlayerId(),
                BloodstoneServerConstants.DUEL_REQUEST_EXPIRED);
    }

    private void cancelPendingRequest(UUID playerId) {
        DuelRequest request = sessions.requestFor(playerId);
        if (request == null) {
            return;
        }

        sessions.remove(request);
        notifyOnline(request.opponentOf(playerId),
                BloodstoneServerConstants.DUEL_REQUEST_CANCELLED);
    }

    @Contract(pure = true)
    private boolean isReady(Player player) {
        return player.isOnline()
                && !player.isDead()
                && BloodstoneServerConstants.WORLD_NAME.equals(player.getWorld().getName())
                && !combatService.isTagged(player.getUniqueId())
                && playerService.isLoaded(player.getUniqueId());
    }

    private void notifyTeleportFailure(Player challenger, Player challengedPlayer) {
        messageService.sendUnable(
                challenger,
                BloodstoneServerConstants.DUEL_TELEPORT_FAILED
        );
        messageService.sendUnable(
                challengedPlayer,
                BloodstoneServerConstants.DUEL_TELEPORT_FAILED
        );
    }

    private void returnPlayer(Player player, DuelPosition returnPosition, UUID duelId) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        if (!player.teleport(
                returnPosition.toLocation(),
                PlayerTeleportEvent.TeleportCause.PLUGIN
        )) {
            plugin.getLogger().warning(String.format(
                    DUEL_RETURN_TELEPORT_WARNING_FORMAT,
                    player.getName(),
                    duelId
            ));
        }
    }

    private void notifyOnline(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            BloodstoneText.sendMessage(player, message);
        }
    }

    @Contract(pure = true)
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

    private Component displayName(@Nullable Player player, UUID playerId) {
        return player == null
                ? Component.text(playerId.toString().substring(0, 8))
                : player.displayName();
    }
}
