package com.mercuriusxeno.goo.network;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Joins a values manager request's parts, refuses a sender lacking the operator level /goo workbench requires, runs
 * the operation off the server thread and sends its reply in parts
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public final class WorkbenchRequests {
    /** The status a refused request answers. */
    static final int FORBIDDEN = 403;
    /**
     * The status a progress part carries: one line a dump run printed, sent before the run's answer under the same
     * request id, since the run takes minutes and the Audit tab shows it as it goes.
     */
    public static final int PROGRESS = 102;
    /** The text a refused request answers. */
    static final String REFUSED = "Only an operator may manage the world's values: /goo workbench needs op level 2.";
    private static final String KEY_SEPARATOR = "#";

    private final WorkbenchPayloadParts joiner = new WorkbenchPayloadParts();
    private final Executor executor;

    /**
     * The player a request came from, as the request handler reads them.
     */
    public interface Sender {

        /**
         * @return a key unique to this sender, under which its parts join
         */
        String key();

        /**
         * @return whether the sender holds the operator level /goo workbench requires
         */
        boolean mayManageValues();

        /**
         * @param part one part of a reply, sent to this sender
         */
        void send(WorkbenchReplyPayload part);
    }

    /**
     * Runs a workbench operation over its posted text.
     */
    @FunctionalInterface
    public interface Runner {

        /**
         * @param operation the operation's name
         * @param text      the posted text
         * @param progress  takes each line a long operation prints before it answers
         * @return the reply's status and text
         */
        Answer answer(String operation, String text, Consumer<String> progress);
    }

    /**
     * An operation's reply as the payload carries it.
     *
     * @param status the reply's status
     * @param text   the reply's text
     */
    public record Answer(int status, String text) {
    }

    /**
     * @param executor runs each joined request, off the server thread, since a dump or a resolve over the inventory
     *                 would stall the tick
     */
    public WorkbenchRequests(Executor executor) {
        this.executor = executor;
    }

    /**
     * Takes one part; once a request's last part arrives, a sender holding the operator level has its operation
     * run and answered, and any other sender is answered with the refusal and runs nothing.
     *
     * @param sender the player the part came from
     * @param part   the part
     * @param runner runs the operation
     */
    public void accept(Sender sender, WorkbenchRequestPayload part, Runner runner) {
        if (!sender.mayManageValues()) {
            joiner.forget(sender.key() + KEY_SEPARATOR);
            if (part.part() == part.parts() - 1) {
                send(sender, part.requestId(), new Answer(FORBIDDEN, REFUSED));
            }
            return;
        }
        joiner.accept(sender.key() + KEY_SEPARATOR + part.requestId(), part.part(), part.parts(), part.text())
                .ifPresent(text -> executor.execute(() -> send(sender, part.requestId(), runner.answer(
                        part.operation(), text, line -> send(sender, part.requestId(), new Answer(PROGRESS, line))))));
    }

    private static void send(Sender sender, int requestId, Answer answer) {
        WorkbenchReplyPayload.parts(requestId, answer.status(), answer.text()).forEach(sender::send);
    }
}
