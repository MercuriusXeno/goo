package com.mercuriusxeno.goo.ability.yarn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the armor and armor toughness a led mob wears from its leader.
 * rally-minions-get-half-your-armor-and-a-floor
 */
class LeadArmorTest {

    private static final float HALF = 0.5f;
    private static final float FLOOR = 4f;
    private static final double LEADER_ARMOR = 20;
    private static final double LEADER_TOUGHNESS = 8;
    private static final double SHARED_ARMOR = 10;
    private static final double SHARED_TOUGHNESS = 4;
    private static final double TOLERANCE = 1e-6;

    @Test
    void anArmoredLeaderLendsHalfItsArmorAndToughness() {
        LeadArmor armor = new LeadArmor(HALF, FLOOR);

        assertEquals(SHARED_ARMOR, armor.armorFrom(LEADER_ARMOR), TOLERANCE);
        assertEquals(SHARED_TOUGHNESS, armor.toughnessFrom(LEADER_TOUGHNESS), TOLERANCE);
    }

    @Test
    void anUnarmoredLeaderLeavesTheShippedFloor() {
        JsonObject step = JsonParser.parseString(AbilityJson.read("yarn_lead")).getAsJsonObject()
                .getAsJsonArray("behaviors").get(0).getAsJsonObject()
                .getAsJsonArray("steps").get(0).getAsJsonObject();
        float floor = step.get("armor_floor").getAsFloat();
        LeadArmor armor = new LeadArmor(step.get("armor_share").getAsFloat(), floor);

        assertEquals(floor, armor.armorFrom(0), TOLERANCE);
    }
}
