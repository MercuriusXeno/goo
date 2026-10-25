package com.mercuriusxeno.goo.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Splits a workbench text into numbered parts small enough for one custom payload, and joins them back: vanilla
 * caps a serverbound custom payload at 32767 bytes and a clientbound one at 1048576
 * (ServerboundCustomPayloadPacket and ClientboundCustomPayloadPacket's MAX_PAYLOAD_SIZE), and a draft or an inventory
 * runs past the first (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public final class WorkbenchPayloadParts {
    /**
     * The characters one part carries: a UTF-8 character takes at most three bytes, so a part stays under the
     * serverbound cap with room for the payload's other fields, and under FriendlyByteBuf.writeUtf's 32767
     * character default.
     */
    static final int CHARS_PER_PART = 8192;
    private static final String NO_SEPARATOR = "";

    private final Map<String, Pending> pending = new HashMap<>();

    /**
     * @param text the text
     * @return its parts in order, one empty part for an empty text
     */
    public static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        do {
            int end = Math.min(text.length(), start + CHARS_PER_PART);
            // A surrogate pair stays whole within one part, since writeUtf encodes each part alone.
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
                end--;
            }
            parts.add(text.substring(start, end));
            start = end;
        } while (start < text.length());
        return parts;
    }

    /**
     * Takes one part; the last part to arrive answers the whole text and forgets the rest.
     *
     * @param key   the request the part belongs to, unique per sender
     * @param part  the part's index, from 0
     * @param parts how many parts the text holds
     * @param text  the part's text
     * @return the joined text once every part has arrived, else empty
     */
    public synchronized Optional<String> accept(String key, int part, int parts, String text) {
        if (parts <= 1) {
            return Optional.of(text);
        }
        Pending held = pending.computeIfAbsent(key, ignored -> new Pending(new String[parts]));
        if (part < 0 || part >= held.parts().length) {
            pending.remove(key);
            return Optional.empty();
        }
        held.parts()[part] = text;
        for (String arrived : held.parts()) {
            if (arrived == null) {
                return Optional.empty();
            }
        }
        pending.remove(key);
        return Optional.of(String.join(NO_SEPARATOR, held.parts()));
    }

    /**
     * Forgets every part a sender's key prefix began, as a sender leaving does.
     *
     * @param keyPrefix the prefix every key of that sender begins with
     */
    public synchronized void forget(String keyPrefix) {
        pending.keySet().removeIf(key -> key.startsWith(keyPrefix));
    }

    private record Pending(String[] parts) {
    }
}
