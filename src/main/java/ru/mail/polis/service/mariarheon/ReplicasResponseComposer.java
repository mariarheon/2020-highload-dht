package ru.mail.polis.service.mariarheon;

import one.nio.http.Response;

/**
 * This class is used for composing response to client
 * by responses retrieved from replicas.
 */
public class ReplicasResponseComposer {
    private Response requiredResponse;
    private int goodAnswers;
    private int notFoundAnswers;
    private boolean wasRemoved;
    private int ackCount;
    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    /**
     * Add response from replica.
     *
     * @param response - response from replica.
     * @param ackCount - count of required acknowledgements.
     */
    public void addResponse(final Response response, final int ackCount) {
        this.ackCount = ackCount;
        if (response.getStatus() >= 200 && response.getStatus() <= 202) {
            if (requiredResponse == null) {
                requiredResponse = response;
            }
            goodAnswers++;
        }
        if (response.getStatus() == 404) {
            notFoundAnswers++;
        }
        if (response.getStatus() == 310) {
            wasRemoved = true;
        }
    }

    /**
     * Get response for client, combined from responses from replicas.
     *
     * @return - response for client.
     */
    public Response getComposedResponse() {
        var res = requiredResponse;
        final int ackCountRetrieved = goodAnswers + notFoundAnswers;
        if (wasRemoved) {
            res = new ZeroResponse(Response.NOT_FOUND);
        } else if (ackCountRetrieved < this.ackCount) {
            res = new ZeroResponse(NOT_ENOUGH_REPLICAS);
        } else if (goodAnswers == 0 && notFoundAnswers > 0) {
            res = new ZeroResponse(Response.NOT_FOUND);
        }
        return res;
    }
}
