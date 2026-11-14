package com.mercuriusxeno.goo.ability.zoo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the armor and armor toughness a rallied mob wears from its rallier.
 * rally-minions-get-half-your-armor-and-a-floor
 */
class RallyArmorTest {

    private static final float HALF = 0.5f;
    private static final float FLOOR = 4f;
    private static final double RALLIER_ARMOR = 20;
    private static final double RALLIER_TOUGHNESS = 8;
    private static final double SHARED_ARMOR = 10;
    private static final double SHARED_TOUGHNESS = 4;
    private static final double TOLERANCE = 1e-6;

    @Test
    void anArmoredRallierLendsHalfItsArmorAndToughness() {
        RallyArmor armor = new RallyArmor(HALF, FLOOR);

        assertEquals(SHARED_ARMOR, armor.armorFrom(RALLIER_ARMOR), TOLERANCE);
        assertEquals(SHARED_TOUGHNESS, armor.toughnessFrom(RALLIER_TOUGHNESS), TOLERANCE);
    }

    @Test
    void anUnarmoredRallierLeavesTheShippedFloor() {
        JsonObject step = JsonParser.parseString(AbilityJson.read("zoo_rally")).getAsJsonObject()
                .getAsJsonArray("behaviors").get(0).getAsJsonObject()
                .getAsJsonArray("steps").get(0).getAsJsonObject();
        float floor = step.get("armor_floor").getAsFloat();
        RallyArmor armor = new RallyArmor(step.get("armor_share").getAsFloat(), floor);

        assertEquals(floor, armor.armorFrom(0), TOLERANCE);
    }
}
