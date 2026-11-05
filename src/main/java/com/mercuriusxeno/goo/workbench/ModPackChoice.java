package com.mercuriusxeno.goo.workbench;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Picks the pack whose base_values.json layer the dev client's live apply replaces: the mod's own pack, found in the
 * resource stack by the file it carries rather than by an assumed id
 * (decision dev-client-workbench-targets-the-source-base-values).
 */
public final class ModPackChoice {
    // A world's folder datapack answers this prefix before its folder's name as its pack id.
    private static final String FILE_PACK_PREFIX = "file/";

    private ModPackChoice() {
    }

    /**
     * One pack's base_values.json in the resource stack.
     *
     * @param packId   the pack's id, as the resource's sourcePackId answers it
     * @param contents the file's bytes in that pack
     */
    public record PackLayer(String packId, byte[] contents) {
    }

    /**
     * @param stack     the base_values.json resource stack, lowest pack first
     * @param modValues the mod's own base_values.json, read from its classpath
     * @return the id of the first pack, other than a world's folder datapack, carrying the mod's file, or none
     */
    public static Optional<String> packCarrying(List<PackLayer> stack, byte[] modValues) {
        return stack.stream()
                .filter(layer -> !layer.packId().startsWith(FILE_PACK_PREFIX))
                .filter(layer -> Arrays.equals(layer.contents(), modValues))
                .map(PackLayer::packId)
                .findFirst();
    }
}
