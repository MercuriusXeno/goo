package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.HeldSweepPace;
import com.mercuriusxeno.goo.ability.program.HeldSweepWorld;
import com.mercuriusxeno.goo.client.BufferedPass;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.network.HeldSweepPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * What one frame of the held sweeps submits, for fixed pulses over the
 * fixed synthetic world at a fixed game time, equals the capture: the same
 * wall quads at the same colors and the same front spheres. The capture
 * was taken from commit 96d5c55, before the held sweeps' frame cost was
 * cut, and taken again once each lit wall drew its own model quads, a
 * whole cube's face on each open side here, where it drew box faces
 * standing just off the block (decision
 * scry-shows-surfaces-as-the-sculker-echo-does). Five pulses leave one
 * spot twenty ticks apart, astral and crystal by turns, so walls lit by two
 * colors at once tie and fade. The quads are compared as a set, since both
 * the walls and the fronts blend additively and their order changes no pixel.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
class HeldSweepVisualLookTest {

    private static final int ASTRAL_TINT = 0x8866FF;
    private static final int CRYSTAL_TINT = 0x66DDEE;
    private static final ToIntFunction<ResourceKey<GooTypeDefinition>> TINTS =
            key -> key == GooTypes.ASTRAL ? ASTRAL_TINT : CRYSTAL_TINT;
    /** The pace the captures were taken at: 96 blocks at 48 a second. */
    private static final HeldSweepPace PACE = new HeldSweepPace(96, 48);
    private static final long START = 1_000;
    private static final int PULSES = 5;
    private static final int PULSE_EVERY = 20;
    private static final int FRAMES_A_TICK = 3;
    private static final double EYE = 1.62;
    private static final int VERTICES_A_QUAD = 4;

    @Test
    void aFrameWhileFivePulsesOverlapSubmitsTheCapturedQuads() {
        Capture frame = captureAt(100);

        assertEquals(new Capture(14_908,
                "52f699e7c6ea39f0327af00dd9f4c378daf387da17e71d76d7c511213da6194d"), frame, "the frame's quads and their sorted SHA-256");
    }

    @Test
    void aFrameOnceTheFrontsHaveLandedAndTheWallsFadeSubmitsTheCapturedQuads() {
        Capture frame = captureAt(170);

        assertEquals(new Capture(13_756,
                "ef096868bd62b6ad623bc177f6d1e0f59f123c94153ed76ade820fec1c3876cb"), frame, "the frame's quads and their sorted SHA-256");
    }

    /**
     * Runs the five pulses to a tick, three frames a tick, and records the
     * frame drawn on the tick after it, before the server's next send.
     */
    private static Capture captureAt(int lastTick) {
        LiveSweeps sweeps = new LiveSweeps();
        HeldSweepVisual.Upkeep upkeep = new HeldSweepVisual.Upkeep(sweeps, CubeWalls.of(HeldSweepWorld::isAir));
        for (int tick = 1; tick <= lastTick; tick++) {
            hearTick(sweeps, tick);
            for (int frame = 0; frame < FRAMES_A_TICK; frame++) {
                HeldSweepVisual.upkeepFronts(upkeep, START + tick + (double) frame / FRAMES_A_TICK, List.of(), null);
            }
        }
        double now = START + lastTick + 1;
        HeldSweepVisual.upkeepFronts(upkeep, now, List.of(), null);
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        BufferedPass pass = new BufferedPass(recorder, () -> { });
        Vec3 camera = HeldSweepWorld.CASTER.add(0, EYE, 0);
        PoseStack.Pose pose = new PoseStack().last();
        EchoQuads quads = new EchoQuads();
        SculkerEchoRenderer.EchoWorld world = new HeldSweepHold.CubeEchoWorld(List.of(new CubeFaces()));
        HeldSweepVisual.drawWalls(new PoseStack(), pass, camera, HeldSweepVisual.wallsToDraw(sweeps.walls(), now),
                pos -> quads.at(world, pos));
        HeldSweepVisual.drawFronts(pose, pass, camera,
                sweeps.pulses().values().stream().filter(pulse -> pulse.travelling(now)).toList(), now);
        return Capture.of(recorder.vertices());
    }

    /** The server's tick: each pulse whose front still travels sends its radius. */
    private static void hearTick(LiveSweeps sweeps, int tick) {
        for (int pulse = 0; pulse < PULSES; pulse++) {
            int startedOn = 1 + pulse * PULSE_EVERY;
            int age = tick - startedOn + 1;
            if (age >= 1 && age <= PACE.ticksToRange()) {
                ResourceKey<GooTypeDefinition> goo = pulse % 2 == 0 ? GooTypes.ASTRAL : GooTypes.CRYSTAL;
                HeldSweepPayload payload = new HeldSweepPayload(pulse + 1, HeldSweepWorld.CASTER,
                        (float) PACE.radiusAt(age), (float) PACE.range(), (float) PACE.blocksATick(), goo, false);
                sweeps.hear(payload, START + tick, TINTS);
            }
        }
    }

    /** A whole cube's model part: one quad on each face, at the cube's own bounds, none unculled. */
    private static final class CubeFaces implements BlockStateModelPart {

        private static final float[][][] CORNERS = {
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}},
        };
        private static final BakedQuad.MaterialInfo MATERIAL = new BakedQuad.MaterialInfo(
                mock(TextureAtlasSprite.class), ChunkSectionLayer.SOLID, null, null, null, -1, null, 0, true);

        private final Map<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);

        CubeFaces() {
            for (Direction side : Direction.values()) {
                float[][] at = CORNERS[side.ordinal()];
                faces.put(side, List.of(new BakedQuad(corner(at[0]), corner(at[1]), corner(at[2]), corner(at[3]),
                        0L, 0L, 0L, 0L, side, MATERIAL)));
            }
        }

        private static Vector3f corner(float[] at) {
            return new Vector3f(at[0], at[1], at[2]);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? List.of() : faces.get(direction);
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public Material.Baked particleMaterial() {
            throw new UnsupportedOperationException("a captured cube draws no particle");
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }

    /**
     * A frame's quads, each written as its four corners and colors, sorted.
     *
     * @param quads  how many quads
     * @param digest the sorted quads' SHA-256
     */
    private record Capture(int quads, String digest) {

        static Capture of(List<RecordingVertexConsumer.Vertex> vertices) {
            List<String> quads = new ArrayList<>();
            for (int first = 0; first + VERTICES_A_QUAD <= vertices.size(); first += VERTICES_A_QUAD) {
                StringBuilder quad = new StringBuilder();
                for (RecordingVertexConsumer.Vertex vertex : vertices.subList(first, first + VERTICES_A_QUAD)) {
                    quad.append(String.format(Locale.ROOT, "%s,%s,%s,%08x;", vertex.x(), vertex.y(), vertex.z(),
                            vertex.color()));
                }
                quads.add(quad.toString());
            }
            quads.sort(null);
            return new Capture(quads.size(), sha256(String.join("\n", quads)));
        }

        private static String sha256(String text) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(text.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException missing) {
                throw new IllegalStateException(missing);
            }
        }
    }
}
