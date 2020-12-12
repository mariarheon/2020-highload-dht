package ru.mail.polis.service.mariarheon;

import one.nio.http.Request;
import one.nio.http.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RendezvousSharding {
    private static final Logger logger = LoggerFactory.getLogger(AsyncServiceImpl.class);

    private final List<String> nodes;
    private final String currentNode;
    private final Map<String, HttpClient> clients;
    private static final Duration HTTP_CLIENT_TIMEOUT = Duration.ofSeconds(40);

    /**
     * Constructor for RendezvousSharding.
     *
     * @param topology - topology.
     * @param currentNode - current node.
     */
    public RendezvousSharding(@NotNull final Set<String> topology,
                              @NotNull final String currentNode) {
        this.nodes = Util.asSortedList(topology);
        this.currentNode = currentNode;
        clients = new HashMap<>();
        final var httpClientBuilder = HttpClient.newBuilder()
                .connectTimeout(HTTP_CLIENT_TIMEOUT);
        for (final String node : nodes) {
            if (!node.equals(currentNode)) {
                final var httpClient = httpClientBuilder.build();
                clients.put(node, httpClient);
            }
        }
    }

    /**
     * get responsible node.
     * @param key - key.
     * @return reuired node.
     */
    private int getResponsibleNodeIndex(@NotNull final String key) {
        int requiredNodeIndex = -1;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            final int hashCode = Objects.hash(key, nodes.get(i));
            if (hashCode > max) {
                max = hashCode;
                requiredNodeIndex = i;
            }
        }
        return requiredNodeIndex;
    }

    /**
     * Get all nodes, which should be used to pass the request on.
     *
     * @param key - id-param.
     * @param replicas - ack/from info.
     * @return - list of nodes, which should be used to pass the request on.
     */
    public List<String> getResponsibleNodes(@NotNull final String key,
                                    @NotNull final Replicas replicas) {
        final int startIndex = getResponsibleNodeIndex(key);
        final var res = new ArrayList<String>();
        for (int i = 0; i < replicas.getTotalNodes(); i++) {
            final int current = (startIndex + i) % getNodesCount();
            res.add(nodes.get(current));
        }
        return res;
    }

    /**
     * Returns true if passed node is the current one.
     *
     * @param node - some node url.
     * @return - true if passed node is the current one; false, otherwise.
     */
    public boolean isMe(final String node) {
        return node.equals(currentNode);
    }

    public String getMe() {
        return currentNode;
    }

    /**
     * Pass request to another node.
     * @param request the request.
     * @return response from node.
     */
    public CompletableFuture<Response> passOn(final HttpRequest request) {
        final var httpClient = clients.get(request.uri().getScheme() + "://" +
                request.uri().getHost() + ":" + request.uri().getPort());
        final var bodyHandler = HttpResponse.BodyHandlers.ofByteArray();
        return httpClient.sendAsync(request, bodyHandler)
                .thenApply(ResponseConverter::convert)
                .exceptionally(ex -> {
                    logger.error("Exception occured when passing on", ex);
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                });
    }

    /**
     * Returns nodes count for the topology.
     *
     * @return - nodes count for the topology.
     */
    public int getNodesCount() {
        return nodes.size();
    }
}
