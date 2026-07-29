package net.valoury.bloodstone.server.service;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class BloodstoneMainThreadExecutor implements Executor {

    private final Plugin plugin;

    public BloodstoneMainThreadExecutor(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
    }

    @Override
    public void execute(Runnable command) {
        if (!executeIfEnabled(command)) {
            throw new RejectedExecutionException("Bloodstone plugin is disabled");
        }
    }

    public boolean executeIfEnabled(Runnable command) {
        Objects.requireNonNull(command, "Command cannot be null");
        if (!plugin.isEnabled()) {
            return false;
        }
        if (Bukkit.isPrimaryThread()) {
            command.run();
            return true;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, command);
            return true;
        } catch (IllegalPluginAccessException exception) {
            return false;
        }
    }
}
