package com.zornus.shared.utilities;

import com.zornus.shared.SharedConstants;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;

public final class HelpUtils {

    private HelpUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void sendHelpPage(Audience audience, List<String> commands, int page, String paginationTemplate) {
        PaginationResult<String> result = PaginationResult.paginate(commands, page, SharedConstants.ENTRIES_PER_PAGE);

        if (!result.isValidPage()) {
            TagResolver resolver = TagResolver.resolver(Placeholder.unparsed("maximum_pages", String.valueOf(result.maximumPages())));
            audience.sendMessage(StringUtils.deserialize(SharedConstants.INVALID_PAGE, resolver));
            return;
        }

        audience.sendMessage(Component.text(""));

        for (String command : result.items()) {
            audience.sendMessage(StringUtils.deserialize(SharedConstants.BULLET_POINT + command));
        }

        if (result.hasMultiplePages()) {
            audience.sendMessage(Component.text(""));
            TagResolver paginationResolver = TagResolver.resolver(
                    Placeholder.unparsed("current_page", String.valueOf(result.currentPage())),
                    Placeholder.unparsed("maximum_pages", String.valueOf(result.maximumPages()))
            );
            audience.sendMessage(StringUtils.deserialize(paginationTemplate, paginationResolver));
        }

        audience.sendMessage(Component.text(""));
    }
}
