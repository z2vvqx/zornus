package net.valoury.staff.proxy.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.staff.proxy.StaffProxyConstants;
import net.valoury.staff.proxy.service.StaffService;
import org.jspecify.annotations.NonNull;

public final class StaffCommand {
    public static @NonNull BrigadierCommand create(
            StaffService staffService,
            ProxyServer proxyServer
    ) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand
                .literalArgumentBuilder("staff")
                .requires(source -> source.hasPermission(
                        StaffProxyConstants.COMMAND_PERMISSION
                ))
                .executes(StaffHelpCommand.defaultExecutor())
                .then(StaffHelpCommand.create())
                .then(StaffInspectCommand.create(staffService, proxyServer))
                .then(StaffConnectionsCommand.create(staffService, proxyServer))
                .then(StaffRelatedCommand.create(staffService, proxyServer))
                .build();
        return new BrigadierCommand(node);
    }
}
