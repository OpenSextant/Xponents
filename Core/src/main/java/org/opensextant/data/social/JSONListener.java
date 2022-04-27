package org.opensextant.data.social;

import jodd.json.JsonObject;

public interface JSONListener {

    /**
     * If listener isDone, then caller should exit
     */
    boolean isDone();

    /**
     * implementation should advertise if it prefers JSON or String.
     *
     * @return
     */
    boolean preferJSON();

    void readObject(JsonObject obj) throws MessageParseException;

    /**
     * API method to allow implementation to read string, e.g., TW4J factory uses
     * strictly String args in a JSON context.
     *
     * @param obj
     * @throws MessageParseException
     */
    void readObject(String obj) throws MessageParseException;
}
