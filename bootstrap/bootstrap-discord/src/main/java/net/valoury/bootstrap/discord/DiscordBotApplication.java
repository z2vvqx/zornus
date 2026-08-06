package net.valoury.bootstrap.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import net.valoury.discord.bot.DiscordBotConstants;
import net.valoury.discord.bot.DiscordBotModule;

import java.util.Collections;
import java.util.EnumSet;

public final class DiscordBotApplication {
    private DiscordBotApplication() {
    }

    public static void main(String[] arguments) throws InterruptedException {
        MessageRequest.setDefaultMentions(Collections.emptySet());
        DiscordBotModule botModule = new DiscordBotModule();
        JDA discordClient = null;
        try {
            discordClient = JDABuilder.createLight(
                            DiscordBotConstants.TOKEN,
                            EnumSet.noneOf(GatewayIntent.class))
                    .addEventListeners(
                            botModule.ticketInteractionListener(),
                            botModule.linkInteractionListener(),
                            botModule.evidenceInteractionListener()
                    )
                    .build()
                    .awaitReady();
            botModule.initialize(discordClient).join();
            registerShutdownHook(discordClient, botModule);
        } catch (RuntimeException | InterruptedException exception) {
            if (discordClient != null) {
                discordClient.shutdownNow();
            }
            botModule.close();
            throw exception;
        }
    }

    private static void registerShutdownHook(JDA discordClient, DiscordBotModule botModule) {
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform()
                .name("valoury-discord-shutdown")
                .unstarted(() -> {
                    discordClient.shutdown();
                    try {
                        if (!discordClient.awaitShutdown(BootstrapConstants.DISCORD_SHUTDOWN_TIMEOUT)) {
                            discordClient.shutdownNow();
                        }
                    } catch (InterruptedException exception) {
                        discordClient.shutdownNow();
                        Thread.currentThread().interrupt();
                    } finally {
                        botModule.close();
                    }
                }));
    }
}
