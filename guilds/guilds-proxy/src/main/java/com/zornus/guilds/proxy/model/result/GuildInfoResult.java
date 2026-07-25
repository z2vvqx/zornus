package com.zornus.guilds.proxy.model.result;

import com.zornus.guilds.proxy.model.Guild;
import org.jspecify.annotations.NonNull;

public sealed interface GuildInfoResult {
    record Found(@NonNull Guild guild) implements GuildInfoResult {}
    record NotInGuild() implements GuildInfoResult {}
    record NotFound() implements GuildInfoResult {}
}
