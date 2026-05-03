package com.zornus.guilds.proxy.model.result;

import com.zornus.guilds.proxy.model.Guild;
import com.zornus.guilds.proxy.model.GuildResult;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public record GuildInfoResult(
        @NonNull GuildResult result,
        @NonNull Optional<Guild> guild
) {}
