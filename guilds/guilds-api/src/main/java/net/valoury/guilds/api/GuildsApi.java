package net.valoury.guilds.api;

import org.jspecify.annotations.NonNull;

/**
 * Public entry point exposed by the guilds Carbon plugin.
 */
public interface GuildsApi {

    String PLUGIN_ID = "GuildsServer";

    /**
     * Gets the read-only guild membership service.
     *
     * @return guild membership service
     */
    @NonNull GuildMembershipService memberships();
}
