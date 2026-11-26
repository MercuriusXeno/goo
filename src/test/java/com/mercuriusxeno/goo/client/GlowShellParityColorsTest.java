package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.ability.ChargeMotes;
import com.mercuriusxeno.goo.client.ability.GlowCore;
import com.mercuriusxeno.goo.client.ability.GloveGlow;
import com.mercuriusxeno.goo.client.ability.HeldSweepVisual;
import com.mercuriusxeno.goo.client.ability.ReapSlashBlades;
import com.mercuriusxeno.goo.client.ber.WispRenderer;
import com.mercuriusxeno.goo.client.entity.MeteorRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each visual drawing through GLOW_SHELL_TYPE emits, at full strength, the colors and opacities it emitted at
 * fc4bde6d11, since the glow shell's retune toward a see-through layer lives in the shader its OIT phases alone run;
 * with improved transparency off, or on any path the OIT phase never reaches, each looks as before.
 * decision additive-glows-draw-as-translucent-layers-in-the-oit-phase
 */
class GlowShellParityColorsTest {

    private static final int LEAF = 0x4CAF50;

    private static Set<Integer> colorsOf(Consumer<RecordingVertexConsumer> emit) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        emit.accept(consumer);
        Set<Integer> colors = new LinkedHashSet<>();
        consumer.vertices().forEach(vertex -> colors.add(vertex.color()));
        return colors;
    }

    @Test
    void eachGlowShellVisualKeepsItsColorsOffTheOitPhase() {
        PoseStack.Pose pose = new PoseStack().last();
        assertAll(
                () -> assertEquals(Set.of(ARGB.color(200, 0xFFFFF0), ARGB.color(120, 0xFFF6C8),
                                ARGB.color(60, 0xFFE628), ARGB.color(28, 0xFFD700), ARGB.color(12, 0xFFD700)),
                        colorsOf(consumer -> GlowCore.emit(pose, consumer, 1f, 1f)),
                        "GlowCore's layers, the Laser's core and the reflector's"),
                () -> assertEquals(List.of(ARGB.color(255, 0xE0FFF0), ARGB.color(114, LEAF)),
                        List.of(ReapSlashBlades.frontColor(1f), ReapSlashBlades.backColor(1f, LEAF)),
                        "Reap's slash blades"),
                () -> assertEquals(Set.of(ARGB.color(55, 0xFFE628), ARGB.color(210, 0xFFF6C8)),
                        colorsOf(consumer -> WispRenderer.drawMote(pose, consumer, 1f)),
                        "the wisp glow, its echoes drawing the same mote"),
                () -> assertEquals(ARGB.color(70, 0xFF6A1E), MeteorRenderer.haloColor(), "the meteor's halo"),
                () -> assertEquals(ARGB.color(20, 0x123456), HeldSweepVisual.frontColor(0x123456),
                        "the held sweep's fronts"),
                () -> assertEquals(Set.of(ARGB.color(45, 0xFFD84A), ARGB.color(120, 0xFFF0A8)),
                        colorsOf(consumer -> GloveGlow.emitShells(pose, consumer, 0.1f, 1f, 0f)),
                        "the glove's shells"),
                () -> assertEquals(Set.of(ARGB.color(70, 0x39FF14), ARGB.color(210, 0xC8FFB4)),
                        colorsOf(consumer -> ChargeMotes.emitWisp(pose, consumer, 0.1f, 1f)),
                        "the glove's blob wisps"));
    }
}
