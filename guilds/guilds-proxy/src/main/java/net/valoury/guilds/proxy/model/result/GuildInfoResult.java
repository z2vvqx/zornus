package net.valoury.guilds.proxy.model.result;

import net.valoury.guilds.proxy.model.Guild;
import org.jspecify.annotations.NonNull;

public sealed interface GuildInfoResult {
    record Found(@NonNull Guild guild) implements GuildInfoResult {
    }

    record NotInGuild() implements GuildInfoResult {
    }

    record NotFound() implements GuildInfoResult {
    }
}
