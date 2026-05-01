package com.zornus.parties.proxy.model;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record PartySettings(
        @NonNull UUID playerId,
        boolean allowChat,
        boolean allowWarp,
        @NonNull String invitePrivacy
) {

    public PartySettings(@NonNull UUID playerId) {
        this(playerId, true, true, "all");
    }

    public PartySettings withBooleanSetting(@NonNull String settingName, boolean value) {
        return switch (settingName.toLowerCase()) {
            case "chat" -> new PartySettings(playerId, value, allowWarp, invitePrivacy);
            case "warp" -> new PartySettings(playerId, allowChat, value, invitePrivacy);
            default -> throw new IllegalArgumentException("Unknown setting: " + settingName);
        };
    }

    public PartySettings withInvitePrivacy(@NonNull String value) {
        if (!value.equals("all") && !value.equals("friend") && !value.equals("none")) {
            throw new IllegalArgumentException("Invalid invite privacy value: " + value);
        }
        return new PartySettings(playerId, allowChat, allowWarp, value);
    }
}
