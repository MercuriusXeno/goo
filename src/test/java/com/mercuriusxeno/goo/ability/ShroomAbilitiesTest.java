package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shroom's shipped roster, read from the shipped ability files through the
 * classpath: Spore left once its power moved into Mycosis' goo.
 * colonize-is-dropped-and-its-power-moves-into-mycosis-goo
 */
class ShroomAbilitiesTest {

    @Test
    void shroomShipsMycosisTransmitAndSight() {
        Set<String> shroom = AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ability -> ability.gooType() == GooTypes.SHROOM)
                .map(ability -> ability.id().getPath())
                .collect(Collectors.toSet());

        assertEquals(Set.of("shroom_mycosis", "shroom_transmit", "shroom_sight"), shroom);
    }
}
