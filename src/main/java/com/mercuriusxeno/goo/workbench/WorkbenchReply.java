package com.mercuriusxeno.goo.workbench;

import java.nio.charset.StandardCharsets;

/**
 * One workbench operation's answer: an HTTP-shaped status, the body's content type and the body, which the loopback
 * page sends as a response and the in-game manager as a reply payload.
 *
 * @param status      the status, 200 for an answer and 4xx or 5xx for a refusal
 * @param contentType the body's content type
 * @param body        the body's bytes
 */
public record WorkbenchReply(int status, String contentType, byte[] body) {
    /** The status of an answer. */
    public static final int OK = 200;
    /** The content type of a JSON body. */
    public static final String JSON = "application/json; charset=utf-8";
    /** The content type of a plain text body, such as a refusal's cause. */
    public static final String TEXT = "text/plain; charset=utf-8";

    /**
     * @param status      the status
     * @param contentType the body's content type
     * @param body        the body's text
     * @return the reply carrying the text as UTF-8
     */
    public static WorkbenchReply of(int status, String contentType, String body) {
        return new WorkbenchReply(status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return the body read as UTF-8
     */
    public String text() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
