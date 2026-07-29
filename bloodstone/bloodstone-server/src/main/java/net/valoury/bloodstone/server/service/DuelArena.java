package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

final class DuelArena {

    private final DuelPosition sideA;
    private final DuelPosition sideB;

    private DuelArena(DuelPosition sideA, DuelPosition sideB) {
        this.sideA = sideA;
        this.sideB = sideB;
    }

    static @Nullable DuelArena load(Plugin plugin) {
        World world = plugin.getServer().getWorld(BloodstoneServerConstants.WORLD_NAME);
        if (world == null) {
            return null;
        }
        FileConfiguration configuration = plugin.getConfig();
        DuelPosition sideA = new DuelPosition(
                world,
                configuration.getDouble("duel.arena.side-a.x", Double.NaN),
                configuration.getDouble("duel.arena.side-a.y", Double.NaN),
                configuration.getDouble("duel.arena.side-a.z", Double.NaN),
                (float) configuration.getDouble("duel.arena.side-a.yaw", 0.0D),
                (float) configuration.getDouble("duel.arena.side-a.pitch", 0.0D)
        );
        DuelPosition sideB = new DuelPosition(
                world,
                configuration.getDouble("duel.arena.side-b.x", Double.NaN),
                configuration.getDouble("duel.arena.side-b.y", Double.NaN),
                configuration.getDouble("duel.arena.side-b.z", Double.NaN),
                (float) configuration.getDouble("duel.arena.side-b.yaw", 0.0D),
                (float) configuration.getDouble("duel.arena.side-b.pitch", 0.0D)
        );
        if (!sideA.hasFiniteCoordinates()
                || !sideB.hasFiniteCoordinates()
                || sideA.hasSameCoordinates(sideB)) {
            return null;
        }
        return new DuelArena(sideA, sideB);
    }

    @Contract(pure = true)
    DuelPosition sideA() {
        return sideA;
    }

    @Contract(pure = true)
    DuelPosition sideB() {
        return sideB;
    }
}

record DuelPosition(
        World world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {

    @Contract(pure = true)
    static DuelPosition from(Location location) {
        return new DuelPosition(
                location.getWorld(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    @Contract(pure = true)
    Location toLocation() {
        return new Location(world, x, y, z, yaw, pitch);
    }

    @Contract(pure = true)
    boolean hasSameCoordinates(DuelPosition other) {
        return world.equals(other.world)
                && Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0
                && Double.compare(z, other.z) == 0;
    }

    @Contract(pure = true)
    boolean hasFiniteCoordinates() {
        return Double.isFinite(x)
                && Double.isFinite(y)
                && Double.isFinite(z)
                && Float.isFinite(yaw)
                && Float.isFinite(pitch);
    }
}
