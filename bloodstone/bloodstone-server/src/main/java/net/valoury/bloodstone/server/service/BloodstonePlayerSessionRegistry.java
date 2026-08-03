package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.model.PlayerProfile;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BloodstonePlayerSessionRegistry {

    private final Map<UUID, PlayerSessionState> sessions =
            new ConcurrentHashMap<>();

    UUID beginLoading(UUID playerId) {
        UUID generation = UUID.randomUUID();
        sessions.put(playerId, new PlayerSessionState(generation, true, null));
        return generation;
    }

    UUID currentGenerationOrCreate(UUID playerId) {
        return sessions.computeIfAbsent(
                playerId,
                ignored -> new PlayerSessionState(
                        UUID.randomUUID(),
                        false,
                        null
                )
        ).generation();
    }

    Optional<UUID> currentGeneration(UUID playerId) {
        PlayerSessionState state = sessions.get(playerId);
        return state == null
                ? Optional.empty()
                : Optional.of(state.generation());
    }

    boolean isCurrent(UUID playerId, UUID generation) {
        PlayerSessionState state = sessions.get(playerId);
        return state != null && state.generation().equals(generation);
    }

    void storeLoadedProfile(
            UUID playerId,
            UUID generation,
            PlayerProfile profile
    ) {
        sessions.computeIfPresent(playerId, (ignored, state) ->
                state.generation().equals(generation)
                        ? new PlayerSessionState(
                                generation,
                                state.loading(),
                                profile
                        )
                        : state
        );
    }

    void finishLoading(UUID playerId, UUID generation, boolean successful) {
        sessions.computeIfPresent(playerId, (ignored, state) -> {
            if (!state.generation().equals(generation)) {
                return state;
            }
            return new PlayerSessionState(
                    generation,
                    false,
                    successful ? state.profile() : null
            );
        });
    }

    void updateProfileIfCurrent(
            UUID playerId,
            UUID generation,
            PlayerProfile profile
    ) {
        sessions.computeIfPresent(playerId, (ignored, state) -> {
            PlayerProfile currentProfile = state.profile();
            if (!state.generation().equals(generation)
                    || currentProfile == null
                    || profile.version() < currentProfile.version()) {
                return state;
            }
            return new PlayerSessionState(
                    generation,
                    state.loading(),
                    profile
            );
        });
    }

    Optional<PlayerProfile> profile(UUID playerId) {
        PlayerSessionState state = sessions.get(playerId);
        return state == null
                ? Optional.empty()
                : Optional.ofNullable(state.profile());
    }

    boolean isLoaded(UUID playerId) {
        PlayerSessionState state = sessions.get(playerId);
        return state != null && !state.loading() && state.profile() != null;
    }

    void endSession(UUID playerId) {
        sessions.remove(playerId);
    }

    void clear() {
        sessions.clear();
    }

    private record PlayerSessionState(
            UUID generation,
            boolean loading,
            @Nullable PlayerProfile profile
    ) {
    }
}
