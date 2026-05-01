package com.zornus.parties.proxy.utilities;

import java.util.UUID;

public final class CooldownKey {
    private CooldownKey() {}

    public static CanonicalKey canonicalize(UUID playerA, UUID playerB) {
        if (playerA.compareTo(playerB) < 0) {
            return new CanonicalKey(playerA, playerB);
        } else {
            return new CanonicalKey(playerB, playerA);
        }
    }

    public record CanonicalKey(UUID smaller, UUID larger) {}
}
