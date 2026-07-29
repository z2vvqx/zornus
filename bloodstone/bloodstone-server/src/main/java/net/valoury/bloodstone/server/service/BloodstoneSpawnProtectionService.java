package net.valoury.bloodstone.server.service;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class BloodstoneSpawnProtectionService {

    private static final String SPAWN_REGION_NAME = "spawn";

    private final WorldGuardPlugin worldGuard;

    public BloodstoneSpawnProtectionService(Plugin plugin) {
        Objects.requireNonNull(plugin, "Plugin cannot be null");
        Plugin installedPlugin = Objects.requireNonNull(
                plugin.getServer().getPluginManager().getPlugin("WorldGuard"),
                "WorldGuard must be installed"
        );
        if (!(installedPlugin instanceof WorldGuardPlugin worldGuardPlugin)) {
            throw new IllegalStateException("Unsupported WorldGuard installation");
        }
        this.worldGuard = worldGuardPlugin;
    }

    public boolean isInsideSpawn(Player player) {
        return isInsideSpawn(player.getLocation());
    }

    public void validateRuntime() {
        World bloodstoneWorld = Bukkit.getWorld("bloodstone");
        if (bloodstoneWorld == null) {
            throw new IllegalStateException("The Bloodstone world is not loaded");
        }
        isInsideSpawn(bloodstoneWorld.getSpawnLocation());
    }

    public boolean isInsideSpawn(Location location) {
        RegionManager regionManager = worldGuard.getRegionManager(location.getWorld());
        if (regionManager == null) {
            return false;
        }
        for (ProtectedRegion region : regionManager.getApplicableRegions(location)) {
            if (isSpawnRegion(region.getId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSpawnRegion(String regionName) {
        return SPAWN_REGION_NAME.equalsIgnoreCase(regionName);
    }
}
