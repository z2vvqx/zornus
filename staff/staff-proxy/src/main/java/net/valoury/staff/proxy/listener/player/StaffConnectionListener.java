package net.valoury.staff.proxy.listener.player;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.valoury.staff.proxy.service.StaffService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public final class StaffConnectionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaffConnectionListener.class);

    private final @NonNull StaffService staffService;

    public StaffConnectionListener(@NonNull StaffService staffService) {
        this.staffService = staffService;
    }

    @Subscribe
    public @NonNull EventTask onPostLogin(@NonNull PostLoginEvent event) {
        Player player = event.getPlayer();
        CompletableFuture<Void> recording = staffService.recordConnection(player)
                .exceptionally(throwable -> {
                    LOGGER.error(
                            "Failed to record staff connection observation for player {}",
                            player.getUniqueId(),
                            throwable
                    );
                    return null;
                });
        return EventTask.resumeWhenComplete(recording);
    }
}
