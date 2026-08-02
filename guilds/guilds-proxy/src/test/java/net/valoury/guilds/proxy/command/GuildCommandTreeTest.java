package net.valoury.guilds.proxy.command;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.CommandSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuildCommandTreeTest {
    @Test
    void everyRecognizedCommandPrefixHasAnExecutor() {
        List<String> prefixesWithoutExecutors = new ArrayList<>();

        collectPrefixesWithoutExecutors(
                GuildCommand.create(null, null).getNode(),
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
}
