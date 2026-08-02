package net.valoury.discord.api;

import net.valoury.discord.api.link.AccountLinkService;

public interface DiscordApi {
    String PLUGIN_ID = "discord-proxy";

    AccountLinkService accountLinks();
}
