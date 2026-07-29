package net.valoury.guilds.server;

import net.valoury.guilds.api.GuildMembershipService;
import net.valoury.guilds.api.GuildsApi;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.logging.Level;

public final class GuildsServerPlugin extends JavaPlugin implements GuildsApi {

    private @Nullable GuildsServerModule guildsServerModule;

    @Override
    public void onEnable() {
        try {
            getLogger().info("Initializing Guilds server plugin...");
            this.guildsServerModule = new GuildsServerModule();
            getServer().getServicesManager().register(
                    GuildsApi.class,
                    this,
                    this,
                    ServicePriority.Normal
            );
            guildsServerModule.initialize().whenComplete((ignored, exception) -> {
                if (isEnabled()) {
                    getServer().getScheduler().runTask(
                            this,
                            () -> finishInitialization(exception)
                    );
                }
            });
        } catch (Exception exception) {
            failInitialization(exception);
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (guildsServerModule != null) {
            guildsServerModule.close();
            guildsServerModule = null;
        }
    }

    @Override
    public @NonNull GuildMembershipService memberships() {
        if (guildsServerModule == null) {
            throw new IllegalStateException("Guilds server plugin is not initialized");
        }
        return guildsServerModule.memberships();
    }

    private void finishInitialization(Throwable exception) {
        if (!isEnabled()) {
            return;
        }
        if (exception != null) {
            failInitialization(exception);
            return;
        }
        getLogger().info("Guilds server plugin initialized successfully");
    }

    private void failInitialization(Throwable exception) {
        getLogger().log(Level.SEVERE, "Failed to initialize Guilds server plugin", exception);
        getServer().getPluginManager().disablePlugin(this);
    }
}
