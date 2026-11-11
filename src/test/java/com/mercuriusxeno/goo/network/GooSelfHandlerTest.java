package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A self ability whose program plays its own sound plays it in place of the throw sound, read from the shipped JSON.
 */
class GooSelfHandlerTest {

    private final AbilityDefinition pocket = AbilityJson.decode("void_pocket");

    // decision pocket-is-a-void-reserving-container-with-scroll-and-search
    @Test
    void pocketsProgramPlaysTheEnderChestOpening() {
        assertEquals(List.of(Identifier.withDefaultNamespace("block.ender_chest.open")),
                pocket.behaviors().stream().filter(SoundStep.class::isInstance).map(SoundStep.class::cast)
                        .map(SoundStep::sound).toList());
    }

    @Test
    void pocketPlaysItsOwnSoundInPlaceOfTheThrow() {
        assertTrue(GooSelfHandler.playsItsOwnSound(pocket));
    }
}
