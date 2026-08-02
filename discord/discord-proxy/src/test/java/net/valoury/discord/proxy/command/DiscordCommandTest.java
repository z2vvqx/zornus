package net.valoury.discord.proxy.command;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import net.valoury.discord.api.link.AccountLink;
import net.valoury.discord.api.link.AccountLinkService;
import net.valoury.discord.api.link.AccountLinkStorage;
import net.valoury.discord.api.link.ConsumeLinkCodeResult;
import net.valoury.discord.api.link.LinkCodeReservationResult;
import net.valoury.discord.api.link.UnlinkAccountResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordCommandTest {
    @Test
    void buildsDomainRootWithHelpLinkAndUnlinkSubcommands() {
        AccountLinkService accountLinkService = new AccountLinkService(new NoOpStorage());

        LiteralCommandNode<CommandSource> rootNode = DiscordCommand.create(accountLinkService).getNode();

        assertEquals("discord", rootNode.getName());
        assertEquals(
                Set.of("help", "link", "unlink"),
                rootNode.getChildren().stream()
                        .map(child -> child.getName())
                        .collect(Collectors.toSet())
        );
        assertEquals("link", DiscordLinkCommand.createShortcut(accountLinkService).getNode().getName());
        assertEquals("unlink", DiscordUnlinkCommand.createShortcut(accountLinkService).getNode().getName());
    }

    @Test
    void everyRecognizedCommandPrefixHasAnExecutor() {
        AccountLinkService accountLinkService = new AccountLinkService(new NoOpStorage());
        List<String> prefixesWithoutExecutors = new ArrayList<>();

        collectPrefixesWithoutExecutors(
                DiscordCommand.create(accountLinkService).getNode(),
                "",
                prefixesWithoutExecutors
        );

        assertEquals(List.of(), prefixesWithoutExecutors);
    }

    private static void collectPrefixesWithoutExecutors(
            CommandNode<CommandSource> commandNode,
            String parentPath,
            List<String> prefixesWithoutExecutors
    ) {
        String nodeName = commandNode instanceof ArgumentCommandNode<?, ?>
                ? "<" + commandNode.getName() + ">"
                : commandNode.getName();
        String commandPath = parentPath.isEmpty() ? "/" + nodeName : parentPath + " " + nodeName;

        if (commandNode.getCommand() == null) {
            prefixesWithoutExecutors.add(commandPath);
        }

        for (CommandNode<CommandSource> childNode : commandNode.getChildren()) {
            collectPrefixesWithoutExecutors(childNode, commandPath, prefixesWithoutExecutors);
        }
    }

    private static final class NoOpStorage implements AccountLinkStorage {
        @Override
        public CompletableFuture<LinkCodeReservationResult> reserveLinkCode(
                UUID minecraftUniqueId,
                String minecraftName,
                String codeHash,
                Duration codeLifetime,
                Duration issuanceCooldown
        ) {
            return CompletableFuture.completedFuture(
                    new LinkCodeReservationResult.AlreadyLinked());
        }

        @Override
        public CompletableFuture<ConsumeLinkCodeResult> consumeLinkCode(
                long discordUserId,
                String codeHash,
                int maximumAttempts,
                Duration attemptWindow
        ) {
            return CompletableFuture.completedFuture(
                    new ConsumeLinkCodeResult.InvalidOrExpiredCode());
        }

        @Override
        public CompletableFuture<Optional<AccountLink>> findByMinecraftUniqueId(UUID minecraftUniqueId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Optional<AccountLink>> findByDiscordUserId(long discordUserId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<UnlinkAccountResult> unlinkByMinecraftUniqueId(UUID minecraftUniqueId) {
            return CompletableFuture.completedFuture(new UnlinkAccountResult.NotLinked());
        }

        @Override
        public CompletableFuture<UnlinkAccountResult> unlinkByDiscordUserId(long discordUserId) {
            return CompletableFuture.completedFuture(new UnlinkAccountResult.NotLinked());
        }

        @Override
        public void close() {
        }
    }
}
