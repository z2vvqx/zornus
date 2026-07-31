package net.valoury.guilds.proxy.model;

import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum GuildRank {
    LEADER("Leader", 4),
    DIRECTOR("Director", 3),
    OFFICER("Officer", 2),
    ASSOCIATE("Associate", 1),
    OUTCAST("Outcast", 0);

    private static final List<GuildRank> HIGHEST_FIRST = List.of(values());

    private final @NonNull String displayName;
    private final int hierarchyLevel;

    GuildRank(@NonNull String displayName, int hierarchyLevel) {
        this.displayName = displayName;
        this.hierarchyLevel = hierarchyLevel;
    }

    public @NonNull String displayName() {
        return displayName;
    }

    public @NonNull Optional<String> chatTagInitial() {
        return this == OUTCAST
                ? Optional.empty()
                : Optional.of(displayName.substring(0, 1));
    }

    public int hierarchyLevel() {
        return hierarchyLevel;
    }

    public boolean isHigherThan(@NonNull GuildRank otherRank) {
        return hierarchyLevel > otherRank.hierarchyLevel;
    }

    public boolean canManageInvitations() {
        return hierarchyLevel >= ASSOCIATE.hierarchyLevel;
    }

    public boolean canKick(@NonNull GuildRank targetRank) {
        return hierarchyLevel >= OFFICER.hierarchyLevel && isHigherThan(targetRank);
    }

    public boolean canChangeRanks() {
        return hierarchyLevel >= DIRECTOR.hierarchyLevel;
    }

    public boolean canPromote(@NonNull GuildRank targetRank) {
        return canChangeRanks()
                && isHigherThan(targetRank)
                && targetRank.nextHigher()
                .filter(promotedRank -> hierarchyLevel > promotedRank.hierarchyLevel)
                .isPresent();
    }

    public boolean canDemote(@NonNull GuildRank targetRank) {
        return canChangeRanks()
                && isHigherThan(targetRank)
                && targetRank.nextLower().isPresent();
    }

    public boolean canUpdateColor() {
        return this == LEADER;
    }

    public @NonNull Optional<GuildRank> nextHigher() {
        return switch (this) {
            case LEADER -> Optional.empty();
            case DIRECTOR -> Optional.of(LEADER);
            case OFFICER -> Optional.of(DIRECTOR);
            case ASSOCIATE -> Optional.of(OFFICER);
            case OUTCAST -> Optional.of(ASSOCIATE);
        };
    }

    public @NonNull Optional<GuildRank> nextLower() {
        return switch (this) {
            case LEADER -> Optional.of(DIRECTOR);
            case DIRECTOR -> Optional.of(OFFICER);
            case OFFICER -> Optional.of(ASSOCIATE);
            case ASSOCIATE -> Optional.of(OUTCAST);
            case OUTCAST -> Optional.empty();
        };
    }

    public static @NonNull List<GuildRank> highestFirst() {
        return HIGHEST_FIRST;
    }

    public static @NonNull GuildRank fromStoredName(@NonNull String storedName) {
        return Arrays.stream(values())
                .filter(rank -> rank.displayName.equals(storedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown guild rank: " + storedName));
    }
}
