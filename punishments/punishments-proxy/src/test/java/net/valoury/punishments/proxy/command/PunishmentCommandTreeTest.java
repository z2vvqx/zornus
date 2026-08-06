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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentCommandTreeTest {
    @Test
    void requiresThePunishmentPermissionAtTheRoot() {
        CommandNode<CommandSource> root = PunishmentCommand.create(null, null, null).getNode();

        assertRequiresPermission(root, PunishmentProxyConstants.COMMAND_PERMISSION);
    }

    @Test
    void requiresSpecificPermissionsForEveryPunishmentOperation() {
        CommandNode<CommandSource> root = PunishmentCommand.create(null, null, null).getNode();

        assertRequiresPermission(
                node(root, "impose"),
                PunishmentProxyConstants.IMPOSE_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "impose", "ban"),
                PunishmentProxyConstants.IMPOSE_BAN_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "impose", "mute"),
                PunishmentProxyConstants.IMPOSE_MUTE_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "impose", "warn"),
                PunishmentProxyConstants.IMPOSE_WARN_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "impose", "kick"),
                PunishmentProxyConstants.IMPOSE_KICK_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "impose", "preset"),
                PunishmentProxyConstants.IMPOSE_PRESET_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "revoke"),
                PunishmentProxyConstants.REVOKE_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "revoke", "ban"),
                PunishmentProxyConstants.REVOKE_BAN_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "revoke", "mute"),
                PunishmentProxyConstants.REVOKE_MUTE_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "revoke", "id"),
                PunishmentProxyConstants.REVOKE_ID_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "history"),
                PunishmentProxyConstants.HISTORY_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "check"),
                PunishmentProxyConstants.CHECK_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "check", "ban"),
                PunishmentProxyConstants.CHECK_BAN_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "check", "mute"),
                PunishmentProxyConstants.CHECK_MUTE_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                node(root, "check", "id"),
                PunishmentProxyConstants.CHECK_ID_COMMAND_PERMISSION
        );
    }

    @Test
    void everyRecognizedCommandPrefixHasAnExecutor() {
        List<String> prefixesWithoutExecutors = new ArrayList<>();

        collectPrefixesWithoutExecutors(
                PunishmentCommand.create(null, null, null).getNode(),
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

    private static CommandNode<CommandSource> node(
            CommandNode<CommandSource> root,
            String... path
    ) {
        CommandNode<CommandSource> commandNode = root;
        for (String childName : path) {
            commandNode = commandNode.getChild(childName);
            assertNotNull(commandNode);
        }
        return commandNode;
    }

    private static void assertRequiresPermission(
            CommandNode<CommandSource> commandNode,
            String permission
    ) {
        assertTrue(commandNode.canUse(source(permission)));
        assertFalse(commandNode.canUse(source(null)));
    }

    private static CommandSource source(String permittedPermission) {
        return permission -> permission.equals(permittedPermission)
                ? Tristate.TRUE
                : Tristate.FALSE;
    }
}
