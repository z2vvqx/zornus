package net.valoury.staff.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.staff.proxy.model.AddressFingerprint;
import net.valoury.staff.proxy.model.ConnectionEdge;
import net.valoury.staff.proxy.model.ConnectionSummary;
import net.valoury.staff.proxy.model.RelatedAccount;
import net.valoury.staff.proxy.model.StaffInspection;
import net.valoury.staff.proxy.security.AddressFingerprintService;
import net.valoury.staff.proxy.storage.StaffStorage;
import org.jspecify.annotations.NonNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class StaffService {
    private final @NonNull StaffStorage storage;
    private final @NonNull ProxyServer proxyServer;
    private final @NonNull AddressFingerprintService addressFingerprintService;

    public StaffService(
            @NonNull StaffStorage storage,
            @NonNull ProxyServer proxyServer,
            @NonNull AddressFingerprintService addressFingerprintService
    ) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.addressFingerprintService = addressFingerprintService;
    }

    public @NonNull CompletableFuture<Void> recordConnection(@NonNull Player player) {
        InetSocketAddress remoteAddress = player.getRemoteAddress();
        InetAddress internetAddress = remoteAddress.getAddress();
        if (internetAddress == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Player remote address is unresolved")
            );
        }
        AddressFingerprint addressFingerprint =
                addressFingerprintService.fingerprint(internetAddress);
        return storage.recordConnection(
                player.getUniqueId(),
                player.getUsername(),
                addressFingerprint
        );
    }

    public @NonNull CompletableFuture<Optional<StaffInspection>> inspect(
            @NonNull String username
    ) {
        return storage.cleanupExpiredConnections()
                .thenCompose(ignored -> resolveTargetPlayer(username))
                .thenCompose(target -> {
                    if (target.isEmpty()) {
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                    PlayerRecord targetRecord = target.get();
                    return storage.fetchConnectedComponent(targetRecord.playerUuid())
                            .thenApply(component -> Optional.of(
                                    createInspection(targetRecord, component)
                            ));
                });
    }

    public @NonNull CompletableFuture<Void> cleanupExpiredConnections() {
        return storage.cleanupExpiredConnections();
    }

    public void close() {
        storage.close();
    }

    private @NonNull CompletableFuture<Optional<PlayerRecord>> resolveTargetPlayer(
            @NonNull String username
    ) {
        Optional<Player> onlinePlayer = proxyServer.getPlayer(username);
        if (onlinePlayer.isPresent()) {
            Player player = onlinePlayer.get();
            return CompletableFuture.completedFuture(Optional.of(
                    new PlayerRecord(player.getUniqueId(), player.getUsername())
            ));
        }
        return storage.fetchPlayerByUsername(username);
    }

    static @NonNull StaffInspection createInspection(
            @NonNull PlayerRecord target,
            @NonNull List<ConnectionEdge> component
    ) {
        Map<UUID, List<ConnectionEdge>> connectionsByPlayer = component.stream()
                .collect(Collectors.groupingBy(
                        ConnectionEdge::playerUuid,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<AddressFingerprint, List<ConnectionEdge>> connectionsByAddress = component.stream()
                .collect(Collectors.groupingBy(
                        ConnectionEdge::addressFingerprint,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<UUID, String> usernamesByPlayer = latestUsernames(connectionsByPlayer);
        usernamesByPlayer.put(target.playerUuid(), target.username());

        List<ConnectionEdge> targetConnections = connectionsByPlayer.getOrDefault(
                target.playerUuid(),
                List.of()
        );
        List<ConnectionSummary> connectionSummaries = targetConnections.stream()
                .map(connection -> new ConnectionSummary(
                        connection.addressFingerprint(),
                        connection.firstSeenAt(),
                        connection.lastSeenAt(),
                        connection.connectionCount(),
                        connectionsByAddress.getOrDefault(
                                        connection.addressFingerprint(),
                                        List.of()
                                ).stream()
                                .map(ConnectionEdge::playerUuid)
                                .collect(Collectors.toSet())
                                .size()
                ))
                .sorted(Comparator
                        .comparing(ConnectionSummary::lastSeenAt)
                        .reversed()
                        .thenComparing(summary -> summary.addressFingerprint().encodedValue()))
                .toList();

        Map<UUID, Integer> connectionDepthByPlayer = new LinkedHashMap<>();
        Map<UUID, UUID> previousPlayerByPlayer = new HashMap<>();
        ArrayDeque<UUID> traversalQueue = new ArrayDeque<>();
        connectionDepthByPlayer.put(target.playerUuid(), 0);
        traversalQueue.add(target.playerUuid());
        while (!traversalQueue.isEmpty()) {
            UUID currentPlayerUuid = traversalQueue.removeFirst();
            int nextConnectionDepth = connectionDepthByPlayer.get(currentPlayerUuid) + 1;
            for (UUID connectedPlayerUuid : connectedPlayers(
                    currentPlayerUuid,
                    connectionsByPlayer,
                    connectionsByAddress,
                    usernamesByPlayer
            )) {
                if (connectionDepthByPlayer.putIfAbsent(
                        connectedPlayerUuid,
                        nextConnectionDepth
                ) == null) {
                    previousPlayerByPlayer.put(connectedPlayerUuid, currentPlayerUuid);
                    traversalQueue.addLast(connectedPlayerUuid);
                }
            }
        }

        Set<AddressFingerprint> targetAddressFingerprints = targetConnections.stream()
                .map(ConnectionEdge::addressFingerprint)
                .collect(Collectors.toSet());
        List<RelatedAccount> relatedAccounts = connectionDepthByPlayer.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(target.playerUuid()))
                .map(entry -> {
                    UUID relatedPlayerUuid = entry.getKey();
                    int connectionDepth = entry.getValue();
                    List<ConnectionEdge> relatedConnections =
                            connectionsByPlayer.getOrDefault(relatedPlayerUuid, List.of());
                    Set<AddressFingerprint> directlySharedAddressFingerprints =
                            connectionDepth == 1
                                    ? relatedConnections.stream()
                                            .map(ConnectionEdge::addressFingerprint)
                                            .filter(targetAddressFingerprints::contains)
                                            .collect(Collectors.toUnmodifiableSet())
                                    : Set.of();
                    UUID previousPlayerUuid = previousPlayerByPlayer.get(relatedPlayerUuid);
                    String connectedThroughUsername = connectionDepth == 1
                            ? null
                            : usernamesByPlayer.getOrDefault(previousPlayerUuid, "Unknown");
                    Instant firstSeenAt = relatedConnections.stream()
                            .map(ConnectionEdge::firstSeenAt)
                            .min(Instant::compareTo)
                            .orElseThrow();
                    Instant lastSeenAt = relatedConnections.stream()
                            .map(ConnectionEdge::lastSeenAt)
                            .max(Instant::compareTo)
                            .orElseThrow();
                    return new RelatedAccount(
                            relatedPlayerUuid,
                            usernamesByPlayer.getOrDefault(relatedPlayerUuid, "Unknown"),
                            connectionDepth,
                            directlySharedAddressFingerprints,
                            connectedThroughUsername,
                            firstSeenAt,
                            lastSeenAt
                    );
                })
                .sorted(Comparator
                        .comparingInt(RelatedAccount::connectionDepth)
                        .thenComparing(RelatedAccount::lastSeenAt, Comparator.reverseOrder())
                        .thenComparing(RelatedAccount::username, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Optional<Instant> firstSeenAt = targetConnections.stream()
                .map(ConnectionEdge::firstSeenAt)
                .min(Instant::compareTo);
        Optional<Instant> lastSeenAt = targetConnections.stream()
                .map(ConnectionEdge::lastSeenAt)
                .max(Instant::compareTo);
        long connectionCount = targetConnections.stream()
                .mapToLong(ConnectionEdge::connectionCount)
                .sum();
        return new StaffInspection(
                target,
                firstSeenAt,
                lastSeenAt,
                connectionCount,
                connectionSummaries,
                relatedAccounts
        );
    }

    private static @NonNull Map<UUID, String> latestUsernames(
            @NonNull Map<UUID, List<ConnectionEdge>> connectionsByPlayer
    ) {
        Map<UUID, String> usernamesByPlayer = new HashMap<>();
        connectionsByPlayer.forEach((playerUuid, connections) -> connections.stream()
                .max(Comparator.comparing(ConnectionEdge::lastSeenAt))
                .ifPresent(connection -> usernamesByPlayer.put(
                        playerUuid,
                        connection.username()
                )));
        return usernamesByPlayer;
    }

    private static @NonNull List<UUID> connectedPlayers(
            @NonNull UUID playerUuid,
            @NonNull Map<UUID, List<ConnectionEdge>> connectionsByPlayer,
            @NonNull Map<AddressFingerprint, List<ConnectionEdge>> connectionsByAddress,
            @NonNull Map<UUID, String> usernamesByPlayer
    ) {
        Set<UUID> connectedPlayers = new LinkedHashSet<>();
        for (ConnectionEdge connection : connectionsByPlayer.getOrDefault(
                playerUuid,
                List.of()
        )) {
            for (ConnectionEdge matchingConnection : connectionsByAddress.getOrDefault(
                    connection.addressFingerprint(),
                    List.of()
            )) {
                if (!matchingConnection.playerUuid().equals(playerUuid)) {
                    connectedPlayers.add(matchingConnection.playerUuid());
                }
            }
        }
        return connectedPlayers.stream()
                .sorted(Comparator.comparing(
                        connectedPlayerUuid -> usernamesByPlayer.getOrDefault(
                                connectedPlayerUuid,
                                "Unknown"
                        ),
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();
    }
}
