package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.util.Map;

/**
 * The ability files the running server's packs hold, and the hand that applies them with the drafts over them to the
 * server and every client, the ability counterpart of {@link LiveApplier}
 * (decision ability-drafts-promote-per-file-like-base-values).
 */
public interface AbilityFiles {
    /** No server: no ability file, and an apply that changes nothing. */
    AbilityFiles NONE = new AbilityFiles() {
        @Override
        public Map<Identifier, JsonObject> live() {
            return Map.of();
        }

        @Override
        public void apply(Map<Identifier, JsonObject> drafts) {
            // No server holds abilities to replace.
        }
    };

    /**
     * @return every ability file the packs hold, by id, as written, the top pack's where several hold one
     * @throws IOException where a file cannot be read
     */
    Map<Identifier, JsonObject> live() throws IOException;

    /**
     * Rebuilds the server's abilities from the live files with the drafts standing over them, and syncs them to
     * every client, with no /reload.
     *
     * @param drafts every ability draft standing, by id
     * @throws IOException where a live file cannot be read
     */
    void apply(Map<Identifier, JsonObject> drafts) throws IOException;
}
