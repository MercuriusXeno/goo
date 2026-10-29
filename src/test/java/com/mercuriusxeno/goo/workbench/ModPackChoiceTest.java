package com.mercuriusxeno.goo.workbench;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests which pack's base_values.json layer the dev client's live apply replaces: the one carrying the mod's own
 * file, never a world's folder datapack (decision dev-client-workbench-targets-the-source-base-values).
 */
class ModPackChoiceTest {

    private static final byte[] MOD_VALUES = "{\"minecraft:dirt\":1}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER_VALUES = "{\"minecraft:dirt\":2}".getBytes(StandardCharsets.UTF_8);

    @Test
    void replacedPackIsTheOneCarryingTheModsFile() {
        List<ModPackChoice.PackLayer> stack = List.of(new ModPackChoice.PackLayer("addon", OTHER_VALUES),
                new ModPackChoice.PackLayer("mod/goo", MOD_VALUES));
        assertEquals(Optional.of("mod/goo"), ModPackChoice.packCarrying(stack, MOD_VALUES));
    }

    @Test
    void worldFolderPackIsKeptEvenCarryingTheSameBytes() {
        List<ModPackChoice.PackLayer> stack = List.of(new ModPackChoice.PackLayer("file/goo_values", MOD_VALUES),
                new ModPackChoice.PackLayer("goo", MOD_VALUES));
        assertEquals(Optional.of("goo"), ModPackChoice.packCarrying(stack, MOD_VALUES));
    }

    @Test
    void noPackCarryingTheModsFileAnswersNone() {
        List<ModPackChoice.PackLayer> stack = List.of(new ModPackChoice.PackLayer("file/goo_values", MOD_VALUES),
                new ModPackChoice.PackLayer("addon", OTHER_VALUES));
        assertEquals(Optional.empty(), ModPackChoice.packCarrying(stack, MOD_VALUES));
    }
}
