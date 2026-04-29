package com.zornus.shared.utilities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class StringUtils {

    private static final DateTimeFormatter EXACT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' hh:mm:ss a").withZone(ZoneId.systemDefault());

    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().strict(true).build();

    private StringUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static Component deserialize(String input) {
        return MINI_MESSAGE.deserialize(input);
    }

    public static Component deserialize(String input, TagResolver resolver) {
        return MINI_MESSAGE.deserialize(input, resolver);
    }

    public static Component formatRelativeTime(Instant timestamp) {
        Duration duration = Duration.between(Instant.now(), timestamp);
        boolean isPast = duration.isNegative();

        if (isPast) {
            duration = duration.negated();
        }

        long years = duration.toDays() / 365;
        long months = (duration.toDays() % 365) / 30;
        long days = duration.toDays() % 30;
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.toSeconds() % 60;

        String relativeTime;

        if (years > 0) {
            if (months > 0) {
                relativeTime = String.format("%d %s & %d %s",
                    years, pluralize("year", years),
                    months, pluralize("month", months));
            } else {
                relativeTime = String.format("%d %s",
                    years, pluralize("year", years));
            }
        } else if (months > 0) {
            if (days > 0) {
                relativeTime = String.format("%d %s & %d %s",
                    months, pluralize("month", months),
                    days, pluralize("day", days));
            } else {
                relativeTime = String.format("%d %s",
                    months, pluralize("month", months));
            }
        } else if (days > 0) {
            if (hours > 0) {
                relativeTime = String.format("%d %s & %d %s",
                    days, pluralize("day", days),
                    hours, pluralize("hour", hours));
            } else {
                relativeTime = String.format("%d %s",
                    days, pluralize("day", days));
            }
        } else if (hours > 0) {
            if (minutes > 0) {
                relativeTime = String.format("%d %s & %d %s",
                    hours, pluralize("hour", hours),
                    minutes, pluralize("minute", minutes));
            } else {
                relativeTime = String.format("%d %s",
                    hours, pluralize("hour", hours));
            }
        } else if (minutes > 0) {
            if (seconds > 0) {
                relativeTime = String.format("%d %s & %d %s",
                    minutes, pluralize("minute", minutes),
                    seconds, pluralize("second", seconds));
            } else {
                relativeTime = String.format("%d %s",
                    minutes, pluralize("minute", minutes));
            }
        } else if (seconds > 0) {
            relativeTime = String.format("%d %s",
                seconds, pluralize("second", seconds));
        } else {
            long milliseconds = duration.toMillis() % 1000;
            relativeTime = String.format("%d %s",
                milliseconds, pluralize("millisecond", milliseconds));
        }

        // Handles both past timestamps (e.g., "5 minutes ago") and future timestamps (e.g., "in 2 days")
        if (isPast) {
            relativeTime += " ago";
        } else {
            relativeTime = "in " + relativeTime;
        }

        String exactDate = EXACT_DATE_FORMATTER.format(timestamp);

        return Component.text(relativeTime)
                .hoverEvent(Component.text(exactDate));
    }

    public static Component formatDuration(Duration duration) {
        long years = duration.toDays() / 365;
        long months = (duration.toDays() % 365) / 30;
        long days = duration.toDays() % 30;
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.toSeconds() % 60;

        String durationText;

        if (years > 0) {
            if (months > 0) {
                durationText = String.format("%d %s & %d %s",
                    years, pluralize("year", years),
                    months, pluralize("month", months));
            } else {
                durationText = String.format("%d %s",
                    years, pluralize("year", years));
            }
        } else if (months > 0) {
            if (days > 0) {
                durationText = String.format("%d %s & %d %s",
                    months, pluralize("month", months),
                    days, pluralize("day", days));
            } else {
                durationText = String.format("%d %s",
                    months, pluralize("month", months));
            }
        } else if (days > 0) {
            if (hours > 0) {
                durationText = String.format("%d %s & %d %s",
                    days, pluralize("day", days),
                    hours, pluralize("hour", hours));
            } else {
                durationText = String.format("%d %s",
                    days, pluralize("day", days));
            }
        } else if (hours > 0) {
            if (minutes > 0) {
                durationText = String.format("%d %s & %d %s",
                    hours, pluralize("hour", hours),
                    minutes, pluralize("minute", minutes));
            } else {
                durationText = String.format("%d %s",
                    hours, pluralize("hour", hours));
            }
        } else if (minutes > 0) {
            if (seconds > 0) {
                durationText = String.format("%d %s & %d %s",
                    minutes, pluralize("minute", minutes),
                    seconds, pluralize("second", seconds));
            } else {
                durationText = String.format("%d %s",
                    minutes, pluralize("minute", minutes));
            }
        } else if (seconds > 0) {
            durationText = String.format("%d %s",
                seconds, pluralize("second", seconds));
        } else {
            long milliseconds = duration.toMillis() % 1000;
            durationText = String.format("%d %s",
                milliseconds, pluralize("millisecond", milliseconds));
        }

        return Component.text(durationText);
    }

    private static String pluralize(String word, long count) {
        return count == 1 ? word : word + "s";
    }
}
