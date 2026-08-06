package net.valoury.punishments.proxy.service;

import net.valoury.discord.api.DiscordApi;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.api.evidence.EvidenceCaseRequest;
import net.valoury.discord.api.evidence.EvidenceService;
import net.valoury.discord.api.link.AccountLinkService;
import net.valoury.punishments.proxy.PunishmentProxyConstants;
import net.valoury.punishments.proxy.model.Punishment;
import net.valoury.shared.model.PlayerRecord;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class PresetEvidenceCoordinator {
    private final AccountLinkService accountLinkService;
    private final EvidenceService evidenceService;

    public PresetEvidenceCoordinator(DiscordApi discordApi) {
        Objects.requireNonNull(discordApi, "Discord API cannot be null");
        this.accountLinkService = discordApi.accountLinks();
        this.evidenceService = discordApi.evidence();
    }

    public CompletableFuture<OptionalLong> findIssuingDiscordUser(UUID issuingPlayerId) {
        return accountLinkService.findByMinecraftUniqueId(
                        Objects.requireNonNull(issuingPlayerId, "Issuing player identifier cannot be null")
                )
                .thenApply(link -> link.isPresent()
                        ? OptionalLong.of(link.orElseThrow().discordUserId())
                        : OptionalLong.empty());
    }

    public CompletableFuture<Optional<EvidenceCase>> createCaseAndWaitForThread(
            Punishment punishment,
            PlayerRecord punishedPlayer,
            OptionalLong issuingDiscordUserId
    ) {
        Objects.requireNonNull(punishment, "Punishment cannot be null");
        Objects.requireNonNull(punishedPlayer, "Punished player cannot be null");
        if (punishment.presetName() == null || punishment.presetApplicationNumber() == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Only preset punishments can create evidence cases"));
        }
        EvidenceCaseRequest request = new EvidenceCaseRequest(
                punishment.identifier(),
                punishment.punishedPlayerId(),
                punishedPlayer.username(),
                punishment.imposingPlayerId(),
                issuingDiscordUserId.isPresent() ? issuingDiscordUserId.getAsLong() : null,
                punishment.presetName(),
                punishment.presetApplicationNumber(),
                punishment.type().name(),
                punishment.reason(),
                punishment.createdAt(),
                punishment.expiresAt()
        );
        return evidenceService.createCase(request).thenCompose(evidenceCase -> {
            if (evidenceCase.hasDiscordThread()) {
                return CompletableFuture.completedFuture(Optional.of(evidenceCase));
            }
            Instant deadline = Instant.now().plus(PunishmentProxyConstants.EVIDENCE_THREAD_WAIT_TIMEOUT);
            return waitForThread(punishment.identifier(), deadline);
        });
    }

    private CompletableFuture<Optional<EvidenceCase>> waitForThread(
            String punishmentIdentifier,
            Instant deadline
    ) {
        return evidenceService.findCaseByIdentifier(punishmentIdentifier).thenCompose(evidenceCase -> {
            if (evidenceCase.filter(EvidenceCase::hasDiscordThread).isPresent()) {
                return CompletableFuture.completedFuture(evidenceCase);
            }
            if (!Instant.now().isBefore(deadline)) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            return CompletableFuture.runAsync(
                            () -> {
                            },
                            CompletableFuture.delayedExecutor(
                                    PunishmentProxyConstants.EVIDENCE_THREAD_POLL_INTERVAL.toMillis(),
                                    TimeUnit.MILLISECONDS
                            )
                    )
                    .thenCompose(ignored -> waitForThread(punishmentIdentifier, deadline));
        });
    }
}
