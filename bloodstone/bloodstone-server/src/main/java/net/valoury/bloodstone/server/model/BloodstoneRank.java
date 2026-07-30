package net.valoury.bloodstone.server.model;

import org.bukkit.permissions.Permissible;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public enum BloodstoneRank {
    DEFAULT(null, 3, 0, 36, null),
    LEGATE("valoury.rank.legate", 4, 4, 30, Duration.ofMinutes(10)),
    JUSTICAR("valoury.rank.justicar", 5, 6, 24, Duration.ofMinutes(7).plusSeconds(30)),
    REGENT("valoury.rank.regent", 6, 8, 18, Duration.ofMinutes(5)),
    ARCHON("valoury.rank.archon", 7, 10, 12, Duration.ofMinutes(2).plusSeconds(30));

    private static final List<BloodstoneRank> PERMISSION_ORDER = List.of(
            ARCHON,
            REGENT,
            JUSTICAR,
            LEGATE
    );

    private final @Nullable String permission;
    private final int bloodPerQualifyingHit;
    private final int freeRandomBoxes;
    private final int randomBoxBloodCost;
    private final @Nullable Duration enchanterCooldown;

    BloodstoneRank(
            @Nullable String permission,
            int bloodPerQualifyingHit,
            int freeRandomBoxes,
            int randomBoxBloodCost,
            @Nullable Duration enchanterCooldown
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
        for (BloodstoneRank rank : PERMISSION_ORDER) {
            if (permissionCheck.test(rank.permission)) {
                return rank;
            }
        }
        return DEFAULT;
    }

    public @NonNull Optional<String> permission() {
        return Optional.ofNullable(permission);
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

    public @NonNull Optional<Duration> enchanterCooldown() {
        return Optional.ofNullable(enchanterCooldown);
    }

    public boolean isPaid() {
        return this != DEFAULT;
    }
}
