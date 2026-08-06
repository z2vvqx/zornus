package net.valoury.bloodstone.server.model;

import org.bukkit.permissions.Permissible;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

public enum BloodstoneRank {
    VALORIAN("valoury.rank.valorian", 7, 10, 12, Duration.ofMinutes(2).plusSeconds(30)),
    ARCHON("valoury.rank.archon", 6, 8, 18, Duration.ofMinutes(5)),
    CAVALIER("valoury.rank.cavalier", 5, 6, 24, Duration.ofMinutes(7).plusSeconds(30)),
    LEGATE("valoury.rank.legate", 4, 4, 30, Duration.ofMinutes(10));

    private final String permission;
    private final int bloodPerQualifyingHit;
    private final int freeRandomBoxes;
    private final int randomBoxBloodCost;
    private final Duration enchanterCooldown;

    BloodstoneRank(
            String permission,
            int bloodPerQualifyingHit,
            int freeRandomBoxes,
            int randomBoxBloodCost,
            Duration enchanterCooldown
    ) {
        this.permission = permission;
        this.bloodPerQualifyingHit = bloodPerQualifyingHit;
        this.freeRandomBoxes = freeRandomBoxes;
        this.randomBoxBloodCost = randomBoxBloodCost;
        this.enchanterCooldown = enchanterCooldown;
    }

    public static @NonNull BloodstoneRank resolve(@NonNull Permissible permissible) {
        return resolvePermissions(permissible::hasPermission);
    }

    public static @NonNull BloodstoneRank resolvePermissions(
            @NonNull Predicate<String> permissionCheck
    ) {
        for (BloodstoneRank rank : values()) {
            if (permissionCheck.test(rank.permission)) {
                return rank;
            }
        }
        return LEGATE;
    }

    public int bloodPerQualifyingHit() {
        return bloodPerQualifyingHit;
    }

    public int freeRandomBoxes() {
        return freeRandomBoxes;
    }

    public int randomBoxBloodCost() {
        return randomBoxBloodCost;
    }

    public @NonNull Duration enchanterCooldown() {
        return enchanterCooldown;
    }

    public boolean isAtLeast(@NonNull BloodstoneRank requiredRank) {
        Objects.requireNonNull(requiredRank, "Required Bloodstone rank cannot be null");
        return ordinal() <= requiredRank.ordinal();
    }
}
