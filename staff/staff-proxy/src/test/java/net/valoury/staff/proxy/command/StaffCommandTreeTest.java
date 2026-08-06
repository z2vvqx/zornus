package net.valoury.staff.proxy.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.CommandSource;
import net.valoury.staff.proxy.StaffProxyConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffCommandTreeTest {
    @Test
    void exposesOnlyTheThreeStaffOperationsAndHelp() {
        CommandNode<CommandSource> root = StaffCommand.create(null, null).getNode();

        assertEquals(
                List.of("connections", "help", "inspect", "related"),
                root.getChildren().stream()
                        .map(CommandNode::getName)
                        .sorted()
                        .toList()
        );
    }

    @Test
    void everyRecognizedCommandPrefixHasAnExecutor() {
        List<String> prefixesWithoutExecutors = new ArrayList<>();

        collectPrefixesWithoutExecutors(
                StaffCommand.create(null, null).getNode(),
                "",
                prefixesWithoutExecutors
        );

        assertEquals(List.of(), prefixesWithoutExecutors);
    }

    @Test
    void requiresTheStaffPermissionAtTheRoot() {
        CommandNode<CommandSource> root = StaffCommand.create(null, null).getNode();

        assertRequiresPermission(root, StaffProxyConstants.COMMAND_PERMISSION);
    }

    @Test
    void requiresSpecificPermissionsForEveryStaffOperation() {
        CommandNode<CommandSource> root = StaffCommand.create(null, null).getNode();

        assertRequiresPermission(
                child(root, "inspect"),
                StaffProxyConstants.INSPECT_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                child(root, "connections"),
                StaffProxyConstants.CONNECTIONS_COMMAND_PERMISSION
        );
        assertRequiresPermission(
                child(root, "related"),
                StaffProxyConstants.RELATED_COMMAND_PERMISSION
        );
    }

    @Test
    void distinguishesRelatedPagesFromAddressIdentifiers() throws Exception {
        CommandNode<CommandSource> root = StaffCommand.create(null, null).getNode();
        CommandNode<CommandSource> playerName = child(
                child(root, "related"),
                "player_name"
        );
        ArgumentCommandNode<CommandSource, ?> page = argumentChild(playerName, "page");
        ArgumentCommandNode<CommandSource, ?> addressIdentifier = argumentChild(
                playerName,
                "address_identifier"
        );

        assertEquals(2, page.getType().parse(new StringReader("2")));
        assertThrows(
                CommandSyntaxException.class,
                () -> addressIdentifier.getType().parse(new StringReader("2"))
        );
        assertEquals(
                "IP-1234-5678-9ABC",
                addressIdentifier.getType().parse(
                        new StringReader("ip-1234-5678-9abc")
                )
        );
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCommandNode<CommandSource, ?> argumentChild(
            CommandNode<CommandSource> parent,
            String childName
    ) {
        CommandNode<CommandSource> child = child(parent, childName);
        assertTrue(child instanceof ArgumentCommandNode<?, ?>);
        return (ArgumentCommandNode<CommandSource, ?>) child;
    }

    private static void collectPrefixesWithoutExecutors(
            CommandNode<CommandSource> commandNode,
            String parentPath,
            List<String> prefixesWithoutExecutors
    ) {
        String nodeName = commandNode instanceof ArgumentCommandNode<?, ?>
                ? "<" + commandNode.getName() + ">"
                : commandNode.getName();
        String commandPath = parentPath.isEmpty()
                ? "/" + nodeName
                : parentPath + " " + nodeName;
        if (commandNode.getCommand() == null) {
            prefixesWithoutExecutors.add(commandPath);
        }
        for (CommandNode<CommandSource> childNode : commandNode.getChildren()) {
            collectPrefixesWithoutExecutors(
                    childNode,
                    commandPath,
                    prefixesWithoutExecutors
            );
        }
    }

    private static CommandNode<CommandSource> child(
            CommandNode<CommandSource> parent,
            String childName
    ) {
        CommandNode<CommandSource> child = parent.getChild(childName);
        assertNotNull(child);
        return child;
    }

    private static void assertRequiresPermission(
            CommandNode<CommandSource> commandNode,
            String permission
    ) {
        assertTrue(commandNode.canUse(source(permission)));
        assertFalse(commandNode.canUse(source(null)));
    }

    private static CommandSource source(String permittedPermission) {
        return (CommandSource) Proxy.newProxyInstance(
                CommandSource.class.getClassLoader(),
                new Class<?>[]{CommandSource.class},
                (instance, method, arguments) -> {
                    if (method.getName().equals("hasPermission")) {
                        return arguments[0].equals(permittedPermission);
                    }
                    throw new AssertionError("Unexpected command source call: " + method.getName());
                }
        );
    }
}
