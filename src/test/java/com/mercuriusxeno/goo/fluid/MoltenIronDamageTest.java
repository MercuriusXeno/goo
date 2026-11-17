package com.mercuriusxeno.goo.fluid;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Molten iron's own damage type: its death message reads "wanted a cast of molten metal", credited or not, and the
 * type sits in every damage type tag lava sits in. Reads the shipped data through the classpath.
 * molten-iron-death-reads-wanted-a-cast-of-molten-metal
 */
class MoltenIronDamageTest {
    private static final String DAMAGE_TYPE = "/data/goo/damage_type/molten_iron.json";
    private static final String LANG = "/assets/goo/lang/en_us.json";
    private static final String DEATH_KEY = "death.attack.goo.molten_iron";
    private static final String DEATH_LINE = "%1$s wanted a cast of molten metal";
    private static final String MOLTEN_IRON = "goo:molten_iron";

    @Test
    void deathMessageReadsWantedACastOfMoltenMetal() throws IOException {
        DamageType type = DamageType.DIRECT_CODEC.parse(JsonOps.INSTANCE, read(DAMAGE_TYPE)).getOrThrow();
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getDisplayName()).thenReturn(Component.literal("Steve"));
        Component message = new DamageSource(Holder.direct(type)).getLocalizedDeathMessage(victim);
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, message.getContents());
        assertEquals(DEATH_KEY, contents.getKey());
        JsonObject lang = read(LANG).getAsJsonObject();
        assertEquals(DEATH_LINE, lang.get(DEATH_KEY).getAsString());
        assertEquals(DEATH_LINE, lang.get(DEATH_KEY + ".player").getAsString(),
                "a kill credited to a player reads the same line, molten iron's kill being environmental");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "data/minecraft/tags/damage_type/is_fire.json",
            "data/minecraft/tags/damage_type/bypasses_shield.json",
            "data/minecraft/tags/damage_type/no_knockback.json",
            "data/minecraft/tags/damage_type/panic_environmental_causes.json",
            "data/neoforge/tags/damage_type/is_environment.json"})
    void moltenIronSitsInEachTagLavaSitsIn(String tag) throws IOException {
        // vanilla's and NeoForge's jars ship the same tag paths, so goo's copy is whichever holds molten iron
        boolean held = false;
        for (URL copy : Collections.list(MoltenIronDamageTest.class.getClassLoader().getResources(tag))) {
            try (InputStream stream = copy.openStream()) {
                held |= JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("values").asList().stream()
                        .anyMatch(value -> MOLTEN_IRON.equals(value.getAsString()));
            }
        }
        assertTrue(held, tag + " should hold " + MOLTEN_IRON);
    }

    private static JsonElement read(String path) throws IOException {
        try (InputStream stream = MoltenIronDamageTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " should ship");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
