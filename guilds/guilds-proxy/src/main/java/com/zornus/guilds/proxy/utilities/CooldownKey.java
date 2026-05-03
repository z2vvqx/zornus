package com.zornus.guilds.proxy.utilities;

import java.util.UUID;

public final class CooldownKey {
    private CooldownKey() {}

    public static CanonicalKey canonicalize(UUID playerA, UUID playerB) {
        if (playerA.toString().compareTo(playerB.toString()) < 0) {
            return new CanonicalKey(playerA, playerB);
        } else {
            return new CanonicalKey(playerB, playerA);
        }
    }

    public record CanonicalKey(UUID smaller, UUID larger) {}
}
