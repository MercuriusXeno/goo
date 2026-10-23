package com.mercuriusxeno.goo.workbench;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One workbench page server per running game server, opened by /goo workbench and closed by a second one, by
 * /goo workbench stop or by the game server stopping; the page's Stop ends the page's server alone, never the
 * game (decision goo-workbench-command-serves-the-page-from-the-game).
 */
public final class InGameWorkbench {
    private static final Map<Object, ValuesWorkbenchServer> RUNNING = new ConcurrentHashMap<>();

    private InGameWorkbench() {
    }

    /**
     * Starts a page server.
     */
    @FunctionalInterface
    public interface Starter {

        /**
         * @return the started server
         * @throws IOException where the port cannot be bound
         */
        ValuesWorkbenchServer start() throws IOException;
    }

    /**
     * @param owner the game server the page serves
     * @return its page server, where one stands and the page's Stop has not ended it
     */
    public static Optional<ValuesWorkbenchServer> running(Object owner) {
        ValuesWorkbenchServer server = RUNNING.get(owner);
        if (server != null && server.stopped()) {
            RUNNING.remove(owner, server);
            return Optional.empty();
        }
        return Optional.ofNullable(server);
    }

    /**
     * @param owner   the game server the page serves
     * @param starter starts its page server where none runs
     * @return the page server running for it, started now where none ran
     * @throws IOException where the port cannot be bound
     */
    public static synchronized ValuesWorkbenchServer open(Object owner, Starter starter) throws IOException {
        Optional<ValuesWorkbenchServer> standing = running(owner);
        if (standing.isPresent()) {
            return standing.get();
        }
        ValuesWorkbenchServer started = starter.start();
        RUNNING.put(owner, started);
        return started;
    }

    /**
     * Stops the owner's page server, as the game server's stopping hook and /goo workbench stop do.
     *
     * @param owner the game server the page serves
     * @return whether a running page server was stopped
     */
    public static synchronized boolean close(Object owner) {
        Optional<ValuesWorkbenchServer> standing = running(owner);
        RUNNING.remove(owner);
        standing.ifPresent(ValuesWorkbenchServer::stop);
        return standing.isPresent();
    }
}
