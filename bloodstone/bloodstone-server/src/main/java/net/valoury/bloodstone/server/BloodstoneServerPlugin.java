package net.valoury.bloodstone.server;

import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.util.logging.Level;

public final class BloodstoneServerPlugin extends JavaPlugin {

    private @Nullable BloodstoneServerModule bloodstoneServerModule;

    @Override
    public void onEnable() {
        try {
            getLogger().info("Initializing Bloodstone plugin...");
            saveDefaultConfig();

            this.bloodstoneServerModule = new BloodstoneServerModule(this);
            bloodstoneServerModule.initialize().whenComplete((ignored, exception) -> {
                if (isEnabled()) {
                    getServer().getScheduler().runTask(
                            this,
                            () -> finishInitialization(exception)
                    );
                }
            });
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize Bloodstone plugin", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bloodstoneServerModule != null) {
            bloodstoneServerModule.shutdown();
        }
    }

    private void finishInitialization(Throwable exception) {
        if (!isEnabled()) {
            return;
        }
        if (exception != null) {
            getLogger().log(Level.SEVERE, "Failed to initialize Bloodstone plugin", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            bloodstoneServerModule.register(getServer().getPluginManager())
                    .whenComplete((ignored, registrationException) ->
                            getServer().getScheduler().runTask(
                                    this,
                                    () -> finishRegistration(registrationException)
                            ));
        } catch (Exception registrationException) {
            getLogger().log(Level.SEVERE,
                    "Failed to register Bloodstone plugin", registrationException);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void finishRegistration(Throwable exception) {
        if (!isEnabled()) {
            return;
        }
        if (exception != null) {
            getLogger().log(Level.SEVERE,
                    "Failed to finish Bloodstone readiness", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Bloodstone plugin initialized successfully");
    }
}
