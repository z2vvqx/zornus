package com.zornus.friends.proxy.model.result;

public sealed interface UpdateFriendSettingResult {
    record Updated() implements UpdateFriendSettingResult {}
    record InvalidSetting() implements UpdateFriendSettingResult {}
}
