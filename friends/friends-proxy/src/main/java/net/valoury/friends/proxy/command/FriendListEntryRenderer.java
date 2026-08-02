package net.valoury.friends.proxy.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.valoury.friends.proxy.FriendProxyConstants;
import net.valoury.friends.proxy.model.FriendSettings;
import net.valoury.friends.proxy.model.PresenceState;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

final class FriendListEntryRenderer {

    private static final Pattern MINECRAFT_USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private FriendListEntryRenderer() {
    }

    static @NonNull Component render(
            @NonNull String friendName,
            @NonNull FriendSettings settings,
            @NonNull Optional<Instant> lastSeen,
            boolean currentlyOnline,
            @NonNull Optional<String> currentServerName
    ) {
        boolean visiblyOnline = currentlyOnline && settings.presenceState() != PresenceState.OFFLINE;
        if (visiblyOnline) {
            Optional<String> visibleServerName = settings.showLocation()
                    ? currentServerName.filter(serverName -> !serverName.isBlank())
                    : Optional.empty();
            if (visibleServerName.isPresent()) {
                Component serverComponent = Component.text(visibleServerName.get(), NamedTextColor.YELLOW);
                if (MINECRAFT_USERNAME.matcher(friendName).matches()) {
                    serverComponent = serverComponent.clickEvent(
                            ClickEvent.suggestCommand("/friend jump " + friendName)
                    );
                }
                return StringUtils.deserialize(
                        SharedConstants.BULLET_POINT + FriendProxyConstants.UI_STATUS_ONLINE_WITH_LOCATION,
                        TagResolver.resolver(
                                Placeholder.unparsed("friend", friendName),
                                Placeholder.component("server", serverComponent)
                        )
                );
            }
            return StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + FriendProxyConstants.UI_STATUS_ONLINE,
                    Placeholder.unparsed("friend", friendName)
            );
        }

        if (settings.showLastSeen() && lastSeen.isPresent()) {
            return StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + FriendProxyConstants.UI_STATUS_OFFLINE,
                    TagResolver.resolver(
                            Placeholder.unparsed("friend", friendName),
                            Placeholder.component("timestamp", StringUtils.formatRelativeTime(lastSeen.get()))
                    )
            );
        }
        return StringUtils.deserialize(
                SharedConstants.BULLET_POINT + FriendProxyConstants.UI_STATUS_OFFLINE_NO_DATA,
                Placeholder.unparsed("friend", friendName)
        );
    }
}
