package net.valoury.bloodstone.server.registrar;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.SessionManager;
import com.sk89q.worldguard.session.handler.Handler;
import net.valoury.bloodstone.server.service.BloodstoneCombatService;
import net.valoury.bloodstone.server.service.BloodstoneSpawnProtectionService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;

public final class BloodstoneWorldGuardRegistrar {

    private final SessionManager sessionManager;
    private final SpawnBoundaryHandlerFactory handlerFactory;

    public BloodstoneWorldGuardRegistrar(BloodstoneCombatService combatService) {
        this.sessionManager = WorldGuardPlugin.inst().getSessionManager();
        this.handlerFactory = new SpawnBoundaryHandlerFactory(combatService);
    }

    public void register() {
        if (!sessionManager.registerHandler(handlerFactory, null)) {
            throw new IllegalStateException("Failed to register the Bloodstone WorldGuard handler");
        }
        Bukkit.getOnlinePlayers().forEach(sessionManager::resetState);
    }

    public void unregister() {
        sessionManager.unregisterHandler(handlerFactory);
    }

    private static final class SpawnBoundaryHandlerFactory
            extends Handler.Factory<SpawnBoundaryHandler> {

        private final BloodstoneCombatService combatService;

        private SpawnBoundaryHandlerFactory(BloodstoneCombatService combatService) {
            this.combatService = combatService;
        }

        @Override
        public SpawnBoundaryHandler create(Session session) {
            return new SpawnBoundaryHandler(session, combatService);
        }
    }

    private static final class SpawnBoundaryHandler extends Handler {

        private final BloodstoneCombatService combatService;

        private SpawnBoundaryHandler(Session session, BloodstoneCombatService combatService) {
            super(session);
            this.combatService = combatService;
        }

        @Override
        public boolean onCrossBoundary(
                Player player,
                Location from,
                Location to,
                ApplicableRegionSet toSet,
                Set<ProtectedRegion> entered,
                Set<ProtectedRegion> exited,
                MoveType moveType
        ) {
            if (combatService.isTagged(player.getUniqueId())
                    && entered.stream().anyMatch(region ->
                    BloodstoneSpawnProtectionService.isSpawnRegion(region.getId()))) {
                combatService.handleEnteredSpawn(player);
            }
            return true;
        }
    }
}
