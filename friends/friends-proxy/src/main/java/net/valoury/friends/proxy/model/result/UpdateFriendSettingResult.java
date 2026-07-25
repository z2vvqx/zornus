package net.valoury.friends.proxy.model.result;

public sealed interface UpdateFriendSettingResult {
    record Updated() implements UpdateFriendSettingResult {
    }

    record InvalidSetting() implements UpdateFriendSettingResult {
    }
}
