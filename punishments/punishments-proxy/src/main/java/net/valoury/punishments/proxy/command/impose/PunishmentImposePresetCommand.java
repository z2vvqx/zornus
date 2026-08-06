package net.valoury.punishments.proxy.command.impose;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.punishments.proxy.PunishmentPresets;
import net.valoury.punishments.proxy.PunishmentProxyConstants;
import net.valoury.punishments.proxy.model.result.PunishmentImposeResult;
import net.valoury.punishments.proxy.service.PunishmentService;
import net.valoury.punishments.proxy.service.PresetEvidenceCoordinator;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public final class PunishmentImposePresetCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentImposePresetCommand.class);

    private static final SuggestionProvider<CommandSource> PRESET_SUGGESTIONS = (context, builder) -> {
        String remainingInput = builder.getRemainingLowerCase();
        PunishmentPresets.names().stream()
                .filter(presetName -> context.getSource().hasPermission(
                        PunishmentProxyConstants.imposePresetPermission(presetName)
                ))
                .filter(presetName -> presetName.startsWith(remainingInput))
                .forEach(builder::suggest);
        return builder.buildFuture();
    };

    private PunishmentImposePresetCommand() {
    }

    private static SuggestionProvider<CommandSource> onlinePlayerSuggestions(ProxyServer proxyServer) {
        return (context, builder) -> {
            String remainingInput = builder.getRemainingLowerCase();
            if (remainingInput.isEmpty()) {
                return builder.buildFuture();
            }
            proxyServer.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(username -> username.toLowerCase(Locale.ROOT).startsWith(remainingInput))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    public static LiteralArgumentBuilder<CommandSource> create(
            PunishmentService punishmentService,
            ProxyServer proxyServer,
            PresetEvidenceCoordinator evidenceCoordinator
    ) {
        return BrigadierCommand
                .literalArgumentBuilder("preset")
                .requires(source -> source.hasPermission(
                        PunishmentProxyConstants.IMPOSE_PRESET_COMMAND_PERMISSION
                ))
                .executes(PunishmentImposePresetCommand::sendUsage)
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(PunishmentImposePresetCommand::sendUsage)
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("preset_name", StringArgumentType.word())
                                .suggests(PRESET_SUGGESTIONS)
                                .executes(context -> handleImposePreset(
                                        context,
                                        punishmentService,
                                        evidenceCoordinator
                                ))
                        )
                );
    }

    private static int sendUsage(@NonNull CommandContext<CommandSource> context) {
        context.getSource().sendMessage(StringUtils.deserialize(PunishmentProxyConstants.USAGE_IMPOSE_PRESET));
        return Command.SINGLE_SUCCESS;
    }

    private static int handleImposePreset(
            @NonNull CommandContext<CommandSource> context,
            PunishmentService punishmentService,
            PresetEvidenceCoordinator evidenceCoordinator
    ) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        String requestedPresetName = StringArgumentType.getString(context, "preset_name");
        String targetName = StringArgumentType.getString(context, "player_name");

        PunishmentPresets.PunishmentPreset preset = PunishmentPresets.find(requestedPresetName)
                .orElse(null);
        if (preset == null) {
            source.sendMessage(StringUtils.deserialize(
                    PunishmentProxyConstants.ERROR_PRESET_NOT_FOUND,
                    Placeholder.unparsed("preset", requestedPresetName)
            ));
            return Command.SINGLE_SUCCESS;
        }
        if (!source.hasPermission(PunishmentProxyConstants.imposePresetPermission(preset.name()))) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
        }

        resolveIssuingDiscordUser(source, evidenceCoordinator)
                .thenAccept(issuingDiscordUserId -> {
                    if (source instanceof Player && issuingDiscordUserId.isEmpty()) {
                        source.sendMessage(StringUtils.deserialize(
                                PunishmentProxyConstants.ERROR_EVIDENCE_ACCOUNT_LINK_REQUIRED));
                        return;
                    }
                    resolveTargetAndImpose(
                            source,
                            targetName,
                            preset.name(),
                            punishmentService,
                            evidenceCoordinator,
                            issuingDiscordUserId
                    );
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to resolve Discord evidence identity for punishment issuer", throwable);
                    source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<OptionalLong> resolveIssuingDiscordUser(
            CommandSource source,
            PresetEvidenceCoordinator evidenceCoordinator
    ) {
        if (source instanceof Player player) {
            return evidenceCoordinator.findIssuingDiscordUser(player.getUniqueId());
        }
        return CompletableFuture.completedFuture(OptionalLong.empty());
    }

    private static void resolveTargetAndImpose(
            CommandSource source,
            String targetName,
            String presetName,
            PunishmentService punishmentService,
            PresetEvidenceCoordinator evidenceCoordinator,
            OptionalLong issuingDiscordUserId
    ) {
        punishmentService.resolveTargetPlayer(targetName)
                .thenAccept(targetOptional -> {
                    if (targetOptional.isEmpty()) {
                        source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                        return;
                    }
                    PlayerRecord target = targetOptional.get();
                    punishmentService.imposePreset(source, target, presetName)
                            .thenAccept(result -> sendResult(
                                    source,
                                    target,
                                    presetName,
                                    result,
                                    evidenceCoordinator,
                                    issuingDiscordUserId
                            ))
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to apply punishment preset {} to {} ({})",
                                        presetName, target.username(), target.playerUuid(), throwable);
                                source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return null;
                            });
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to resolve punishment preset target {}", targetName, throwable);
                    source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });
    }

    private static void sendResult(
            CommandSource source,
            PlayerRecord target,
            String requestedPresetName,
            PunishmentImposeResult result,
            PresetEvidenceCoordinator evidenceCoordinator,
            OptionalLong issuingDiscordUserId
    ) {
        switch (result) {
            case PunishmentImposeResult.Imposed imposed -> {
                TagResolver resolver = TagResolver.resolver(
                        Placeholder.unparsed("target", target.username()),
                        Placeholder.unparsed("preset", imposed.punishment().presetName()),
                        Placeholder.unparsed(
                                "step", String.valueOf(imposed.punishment().presetApplicationNumber())),
                        Placeholder.unparsed(
                                "type", imposed.punishment().type().name().toLowerCase(Locale.ROOT)),
                        Placeholder.unparsed("id", imposed.punishment().identifier().toUpperCase())
                );
                source.sendMessage(StringUtils.deserialize(
                        PunishmentProxyConstants.IMPOSE_SUCCESS_PRESET, resolver));
                createEvidenceCase(
                        source,
                        target,
                        imposed,
                        evidenceCoordinator,
                        issuingDiscordUserId
                );
            }
            case PunishmentImposeResult.AlreadyBanned ignored -> source.sendMessage(StringUtils.deserialize(
                    PunishmentProxyConstants.ERROR_PLAYER_ALREADY_BANNED,
                    Placeholder.unparsed("target", target.username())));
            case PunishmentImposeResult.AlreadyMuted ignored -> source.sendMessage(StringUtils.deserialize(
                    PunishmentProxyConstants.ERROR_PLAYER_ALREADY_MUTED,
                    Placeholder.unparsed("target", target.username())));
            case PunishmentImposeResult.AlreadyWarnedForReason ignored -> {
                String reason = PunishmentPresets.find(requestedPresetName)
                        .map(PunishmentPresets.PunishmentPreset::reason)
                        .orElse(requestedPresetName);
                TagResolver resolver = TagResolver.resolver(
                        Placeholder.unparsed("target", target.username()),
                        Placeholder.unparsed("reason", reason)
                );
                source.sendMessage(StringUtils.deserialize(
                        PunishmentProxyConstants.ERROR_PLAYER_ALREADY_WARNED_FOR_REASON, resolver));
            }
            case PunishmentImposeResult.CannotPunishSelf ignored ->
                    source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.ERROR_CANNOT_PUNISH_SELF));
            case PunishmentImposeResult.PresetNotFound ignored -> source.sendMessage(StringUtils.deserialize(
                    PunishmentProxyConstants.ERROR_PRESET_NOT_FOUND,
                    Placeholder.unparsed("preset", requestedPresetName)));
            case PunishmentImposeResult.PlayerNotFound ignored ->
                    source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
            case PunishmentImposeResult.InvalidDuration ignored ->
                    source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.ERROR_INVALID_DURATION));
        }
    }

    private static void createEvidenceCase(
            CommandSource source,
            PlayerRecord target,
            PunishmentImposeResult.Imposed imposed,
            PresetEvidenceCoordinator evidenceCoordinator,
            OptionalLong issuingDiscordUserId
    ) {
        evidenceCoordinator.createCaseAndWaitForThread(
                        imposed.punishment(),
                        target,
                        issuingDiscordUserId
                )
                .thenAccept(evidenceCase -> {
                    String punishmentIdentifier = imposed.punishment().identifier().toUpperCase(Locale.ROOT);
                    if (evidenceCase.isEmpty()) {
                        source.sendMessage(StringUtils.deserialize(
                                PunishmentProxyConstants.EVIDENCE_THREAD_QUEUED,
                                Placeholder.unparsed("id", punishmentIdentifier)
                        ));
                        return;
                    }
                    String threadJumpUrl = evidenceCase.orElseThrow().threadJumpUrl();
                    if (threadJumpUrl == null) {
                        throw new IllegalStateException("Provisioned evidence case has no Discord thread URL");
                    }
                    Component evidenceLink = Component.text("Open the evidence thread", NamedTextColor.AQUA)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(threadJumpUrl));
                    source.sendMessage(StringUtils.deserialize(
                            PunishmentProxyConstants.EVIDENCE_THREAD_READY,
                            TagResolver.resolver(
                                    Placeholder.unparsed("id", punishmentIdentifier),
                                    Placeholder.component("evidence_link", evidenceLink)
                            )
                    ));
                })
                .exceptionally(throwable -> {
                    LOGGER.error(
                            "Punishment {} succeeded but its evidence case could not be created",
                            imposed.punishment().identifier(),
                            throwable
                    );
                    source.sendMessage(StringUtils.deserialize(
                            PunishmentProxyConstants.EVIDENCE_CASE_FAILED,
                            Placeholder.unparsed(
                                    "id",
                                    imposed.punishment().identifier().toUpperCase(Locale.ROOT)
                            )
                    ));
                    return null;
                });
    }
}
