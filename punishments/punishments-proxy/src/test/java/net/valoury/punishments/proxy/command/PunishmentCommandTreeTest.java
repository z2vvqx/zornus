package net.valoury.punishments.proxy.command;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import net.valoury.punishments.proxy.PunishmentProxyConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentCommandTreeTest {
    @Test
    void requiresThePunishmentManagementPermission() {
        CommandSource permittedSource = permission ->
                PunishmentProxyConstants.COMMAND_PERMISSION.equals(permission)
                        ? Tristate.TRUE
                        : Tristate.FALSE;
        CommandSource unpermittedSource = permission -> Tristate.FALSE;

        CommandNode<CommandSource> commandNode = PunishmentCommand.create(null, null).getNode();

        assertTrue(commandNode.canUse(permittedSource));
        assertFalse(commandNode.canUse(unpermittedSource));
    }

    @Test
    void everyRecognizedCommandPrefixHasAnExecutor() {
        List<String> prefixesWithoutExecutors = new ArrayList<>();

        collectPrefixesWithoutExecutors(
                PunishmentCommand.create(null, null).getNode(),
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
