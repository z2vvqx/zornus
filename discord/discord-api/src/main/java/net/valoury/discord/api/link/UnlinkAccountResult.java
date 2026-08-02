package net.valoury.discord.api.link;

public sealed interface UnlinkAccountResult {
    record Unlinked() implements UnlinkAccountResult {
    }

    record NotLinked() implements UnlinkAccountResult {
    }
}
