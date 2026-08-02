package net.valoury.discord.bot.ticket.listener;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.valoury.discord.bot.DiscordBotConstants;
import net.valoury.discord.bot.interaction.DiscordInteractionResponder;
import net.valoury.discord.bot.ticket.TicketButtonIdentifier;
import net.valoury.discord.bot.ticket.service.TicketManagementService;
import net.valoury.discord.bot.ticket.service.TicketOpeningService;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public final class TicketInteractionListener extends ListenerAdapter {
    private final TicketOpeningService openingService;
    private final TicketManagementService managementService;
    private final DiscordInteractionResponder interactionResponder;

    public TicketInteractionListener(
            TicketOpeningService openingService,
            TicketManagementService managementService,
            DiscordInteractionResponder interactionResponder
    ) {
        this.openingService = Objects.requireNonNull(openingService, "Ticket opening service cannot be null");
        this.managementService = Objects.requireNonNull(
                managementService,
                "Ticket management service cannot be null"
        );
        this.interactionResponder = Objects.requireNonNull(
                interactionResponder,
                "Discord interaction responder cannot be null"
        );
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String componentIdentifier = event.getComponentId();
        if (!TicketButtonIdentifier.isTicketOpenButton(componentIdentifier)) {
            return;
        }

        interactionResponder.respond(
                event,
                "ticket button interaction",
                DiscordBotConstants.TICKET_OPERATION_FAILED,
                () -> openTicket(event, componentIdentifier)
        );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!DiscordBotConstants.TICKET_COMMAND_NAME.equals(event.getName())) {
            return;
        }

        interactionResponder.respond(
                event,
                "ticket command interaction",
                DiscordBotConstants.TICKET_OPERATION_FAILED,
                () -> executeTicketCommand(event)
        );
    }

    private CompletableFuture<String> openTicket(
            ButtonInteractionEvent event,
            String componentIdentifier
    ) {
        OptionalLong staffRoleId = TicketButtonIdentifier.parseStaffRoleId(componentIdentifier);
        Guild guild = event.getGuild();
        if (staffRoleId.isEmpty() || guild == null || !(event.getChannel() instanceof TextChannel channel)) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_INVALID_BUTTON);
        }

        Role staffRole = guild.getRoleById(staffRoleId.getAsLong());
        if (staffRole == null) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_INVALID_BUTTON);
        }
        return openingService.openTicket(channel, event.getUser(), staffRole);
    }

    private CompletableFuture<String> executeTicketCommand(SlashCommandInteractionEvent event) {
        Member administrator = event.getMember();
        if (administrator == null || !administrator.hasPermission(Permission.ADMINISTRATOR)) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_ADMINISTRATOR_ONLY);
        }

        return switch (event.getSubcommandName()) {
            case "panel" -> createPanel(event);
            case "close" -> withTicketThread(event, managementService::closeTicket);
            case "assign" -> withSelectedMember(event, managementService::assignTicket);
            case "add" -> withSelectedMember(event, managementService::addUser);
            case "remove" -> withSelectedMember(event, managementService::removeUser);
            case null, default -> CompletableFuture.completedFuture(DiscordBotConstants.TICKET_OPERATION_FAILED);
        };
    }

    private CompletableFuture<String> createPanel(SlashCommandInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_PANEL_INVALID_CHANNEL);
        }
        OptionMapping staffRoleOption = event.getOption("staff-role");
        if (staffRoleOption == null) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_INVALID_STAFF_ROLE);
        }
        return openingService.createTicketPanel(channel, staffRoleOption.getAsRole());
    }

    private CompletableFuture<String> withTicketThread(
            SlashCommandInteractionEvent event,
            TicketThreadOperation operation
    ) {
        if (!(event.getChannel() instanceof ThreadChannel thread)) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_NOT_RECOGNIZED);
        }
        return operation.apply(thread);
    }

    private CompletableFuture<String> withSelectedMember(
            SlashCommandInteractionEvent event,
            TicketMemberOperation operation
    ) {
        if (!(event.getChannel() instanceof ThreadChannel thread)) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_NOT_RECOGNIZED);
        }
        OptionMapping selectedUserOption = event.getOption("user");
        Member selectedMember = selectedUserOption == null ? null : selectedUserOption.getAsMember();
        if (selectedMember == null) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_MISSING_USER);
        }
        return operation.apply(thread, selectedMember);
    }

    @FunctionalInterface
    private interface TicketThreadOperation {
        CompletableFuture<String> apply(ThreadChannel thread);
    }

    @FunctionalInterface
    private interface TicketMemberOperation {
        CompletableFuture<String> apply(ThreadChannel thread, Member selectedMember);
    }
}
