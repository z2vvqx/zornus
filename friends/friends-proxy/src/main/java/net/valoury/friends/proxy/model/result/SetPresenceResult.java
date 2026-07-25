package net.valoury.friends.proxy.model.result;

public sealed interface SetPresenceResult {
    record Updated() implements SetPresenceResult {
    }
}
