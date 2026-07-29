package net.valoury.bloodstone.server.listener.player;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.bloodstone.server.service.BloodstoneMainThreadExecutor;
import net.valoury.bloodstone.server.service.BloodstoneMessageService;
import net.valoury.bloodstone.server.service.BloodstonePlayerService;
import net.luckperms.api.LuckPerms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class BloodstoneChatListener implements Listener {

    private final BloodstonePlayerService playerService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstoneMessageService messageService;
    private final @Nullable LuckPerms luckPerms;

    public BloodstoneChatListener(
            BloodstonePlayerService playerService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            BloodstoneMessageService messageService,
            @Nullable LuckPerms luckPerms
    ) {
        this.playerService = playerService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.messageService = messageService;
        this.luckPerms = luckPerms;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChat(@NonNull AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        if (!BloodstoneServerConstants.WORLD_NAME.equals(sender.getWorld().getName())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        if (message.startsWith(">")) {
            mainThreadExecutor.execute(() -> messageService.sendError(
                    sender,
                    BloodstoneServerConstants.CHAT_DELIVERY_FAILED
            ));
            return;
        }
        mainThreadExecutor.execute(() -> {
            if (!sender.isOnline()
                    || !BloodstoneServerConstants.WORLD_NAME.equals(
                            sender.getWorld().getName()
                    )) {
                return;
            }
            double ratio = playerService.profile(sender.getUniqueId())
                    .map(PlayerProfile::ratio)
                    .orElse(0.0);
            Component formatted = BloodstoneText.deserialize(
                    BloodstoneServerConstants.CHAT_FORMAT,
                    TagResolver.resolver(
                            Placeholder.unparsed(
                                    "ratio",
                                    String.format(Locale.US, "%.2f", ratio)
                            ),
                            Placeholder.component("suffix", suffix(sender)),
                            Placeholder.component(
                                    "player",
                                    sender.displayName()
                            ),
                            Placeholder.unparsed("message", message)
                    )
            );
            for (Player recipient : Bukkit.getOnlinePlayers()) {
                if (BloodstoneServerConstants.WORLD_NAME.equals(
                        recipient.getWorld().getName()
                )) {
                    BloodstoneText.sendMessage(recipient, formatted);
                }
            }
        });
    }

    private Component suffix(Player player) {
        if (luckPerms == null) {
            return Component.empty();
        }
        String suffix = luckPerms.getPlayerAdapter(Player.class)
                .getMetaData(player)
                .getSuffix();
        return suffix == null
                ? Component.empty()
                : BloodstoneText.ampersandComponent(suffix);
    }
}
