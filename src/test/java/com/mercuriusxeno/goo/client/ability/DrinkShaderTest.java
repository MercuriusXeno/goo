package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drink field shader marches the same field DrinkField states and reads
 * the block DrinkUpload packs: its constants equal the Java's and its uniform
 * arrays are as long as the upload's caps, and its skin is two solid colors
 * with no texture (decisions unmake-waves-dissolve-by-crucible-cost and
 * unmake-skin-is-a-solid-sampled-color). Read through the classpath.
 */
class DrinkShaderTest {

    private static final String FRAGMENT = "/assets/goo/shaders/core/drink_field.fsh";
    private static final String VERTEX = "/assets/goo/shaders/core/drink_field.vsh";
    private static final double DELTA = 1e-9;
    private static final int IVEC4 = 4;
    /** How tight the glisten's highlight is. */
    private static final double GLISTEN_POWER = 48;
    /** How bright the highlight adds. */
    private static final double GLISTEN_STRENGTH = 0.35;
    /** How close to grazing the rim brightens. */
    private static final double RIM_POWER = 3;
    /** How bright the rim adds. */
    private static final double RIM_STRENGTH = 0.25;

    private static String source(String path) throws IOException {
        try (InputStream in = DrinkShaderTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static double constant(String source, String name) {
        Matcher matcher = Pattern.compile("const (?:float|int) " + name + " = ([-0-9.e]+);").matcher(source);
        assertTrue(matcher.find(), name);
        return Double.parseDouble(matcher.group(1));
    }

    private static int arrayLength(String source, String name) {
        Matcher matcher = Pattern.compile("i?vec4 " + name + "\\[(\\d+)\\];").matcher(source);
        assertTrue(matcher.find(), name);
        return Integer.parseInt(matcher.group(1));
    }

    @Test
    void theShadersFieldConstantsAreTheJavasAndItReadsTheUploadsBlock() throws IOException {
        String fragment = source(FRAGMENT);

        assertEquals(DrinkField.DEPTH, constant(fragment, "DEPTH"), DELTA);
        assertEquals(DrinkField.REACH, constant(fragment, "REACH"), DELTA);
        assertEquals(DrinkField.FULL_RADIUS, constant(fragment, "FULL_RADIUS"), DELTA);
        assertEquals(DrinkUpload.RUN_START, constant(fragment, "RUN_START"), DELTA);
        assertEquals(DrinkUpload.BOX_BASE, constant(fragment, "BOX_BASE"), DELTA);
        assertEquals(DrinkUpload.REGION, constant(fragment, "REGION_SPAN"), DELTA);
        assertEquals(DrinkUpload.STREAM_VEC4S, constant(fragment, "STREAM_VEC4S"), DELTA);
        assertEquals(DrinkUpload.COAT_SLOT, constant(fragment, "COAT_SLOT"), DELTA);
        assertEquals(DrinkUpload.BLOCK_COLOR_SLOT, constant(fragment, "BLOCK_COLOR_SLOT"), DELTA);
        assertEquals(DrinkUpload.GOO_COLOR_SLOT, constant(fragment, "GOO_COLOR_SLOT"), DELTA);
        assertEquals(DrinkUpload.RUN_LOW_SLOT, constant(fragment, "RUN_LOW_SLOT"), DELTA);
        assertEquals(DrinkUpload.RUN_HIGH_SLOT, constant(fragment, "RUN_HIGH_SLOT"), DELTA);
        assertEquals(DrinkUpload.MOST_STREAMS * DrinkUpload.STREAM_VEC4S, arrayLength(fragment, "Streams"));
        assertEquals(DrinkUpload.MOST_ENTRIES / IVEC4, arrayLength(fragment, "Table"));
        assertEquals(2 * DrinkUpload.MOST_BOXES, arrayLength(fragment, "Boxes"));
        assertEquals(2 * DrinkUpload.MOST_RINGS, arrayLength(fragment, "Rings"));
        assertTrue(fragment.contains("gl_FragDepth ="), "the hit writes its depth");
    }

    /** decision diagnose-then-fix-unmake-seam-and-ribbing */
    /**
     * A ray out of steps short of its region's exit reads the field at the
     * exit and refines the hit between, so the region always draws its own
     * surface where the goo runs on past it, and the region beyond never
     * shows a flat face at its entry that cuts the goo on the block grid.
     */
    @Test
    void aRayOutOfStepsShortOfItsRegionsExitStillFindsTheSurfaceBeforeIt() throws IOException {
        String fragment = source(FRAGMENT).replace("\r\n", "\n");

        assertTrue(fragment.contains("    if (!hit && t < tExit) {\n        before = t;\n        t = tExit;\n"
                + "        hit = fieldOnly(rd * t, streams) >= iso;\n    }\n    if (!hit) {"));
    }

    /**
     * A segment whose reach falls short of the point reads REACH without
     * evaluating its curve or its cyclone, which its caller would clamp to
     * REACH anyway, so a crowded region's march pays only for the segments
     * near the point.
     */
    @Test
    void aSegmentPastItsReachSkipsItsCurve() throws IOException {
        String fragment = source(FRAGMENT).replace("\r\n", "\n");
        int bound = fragment.indexOf("    read.y = bound - REACH * min(1.0, widest / FULL_RADIUS);");
        int skip = fragment.indexOf("    if (read.y > 0.0) {\n        read.x = REACH;", bound);
        int curve = fragment.indexOf("vec3 foot = hermite3(", bound);

        assertTrue(bound > 0 && skip > bound && skip < curve, "the skip sits between the bound and the curve");
    }

    @Test
    void theShaderReadsTheSmoothTubeAndJoinsStreamsByTheirFilletAsTheJavaDoes() throws IOException {
        String fragment = source(FRAGMENT);

        assertEquals(DrinkField.BOX_BLEND, constant(fragment, "BOX_BLEND"), DELTA);
        assertEquals(DrinkField.MEET_BLEND, constant(fragment, "MEET_BLEND"), DELTA);
        assertTrue(fragment.contains("int before = sameStream(ring, ring - 1) ? ring - 1 : ring;")
                        && fragment.contains("int after = sameStream(ring, ring + 1) ? ring + 1 : ring;"),
                "a ring's tangent runs from the ring before it to the ring after it, as DrinkField.tangentAt");
        assertTrue(fragment.contains("float t = before <= 0.0 ? 0.0 : after >= 0.0 ? 1.0 : before / (before - after);"),
                "a point's foot is its share of the way between the planes at the segment's rings");
        assertTrue(fragment.contains("vec3 foot = hermite3(a.xyz, b.xyz, leaving * chord, arriving * chord, t);"),
                "on the curve leaving each ring along its tangent");
        assertTrue(fragment.contains("MEET_BLEND * (1.0 - dot(firstFacing, secondFacing)) / 2.0"),
                "the two nearest streams are filleted as wide as their surfaces face apart, never summed");
        assertFalse(fragment.contains("value += runField"), "no stream's field is added onto another's");
    }

    /** decision unmake-stream-is-a-shrinking-waterspout */
    @Test
    void theShaderCarvesTheCyclonesGroovesByEachRingsPhaseAsTheJavaDoes() throws IOException {
        String fragment = source(FRAGMENT);

        assertEquals(DrinkStream.LOBES, constant(fragment, "LOBES"), DELTA);
        assertEquals(DrinkStream.SPIN_DEPTH, constant(fragment, "SPIN_DEPTH"), DELTA);
        assertEquals(DrinkUpload.SIDE_SLOT, constant(fragment, "SIDE_SLOT"), DELTA);
        assertTrue(fragment.contains("return 1.0 - SPIN_DEPTH * (1.0 - cos(float(LOBES) * angle - phase)) / 2.0;"),
                "the grooves fall in between lobes standing at the radius, as DrinkStream.cycloneAt");
        assertTrue(fragment.contains("float phase = mix(Rings[2 * segment + 1].w, Rings[2 * segment + 3].w, t);"),
                "the phase each ring carries, which turns with time, eased along the segment");
        assertTrue(fragment.contains("vec3 side = streamSlot(stream, SIDE_SLOT).xyz;"),
                "the angle about the axis is read from the stream's side");
    }

    @Test
    void theMarchSamplesTheRegionsExitAndTakesAnEntryInsideTheFieldAsTheHit() throws IOException {
        String fragment = source(FRAGMENT);

        assertTrue(fragment.contains("t = min(t + step, tExit);"),
                "the last step lands on the exit plane rather than past it, so no crossing before it is skipped");
        assertTrue(fragment.contains("if (f >= iso && tEnter <= 0.0) {"),
                "only a camera inside the goo discards an entry inside the field");
        assertTrue(fragment.contains("bool hit = f >= iso;"), "an entry inside the field from another region is the hit");
    }

    @Test
    void aStepReadsOnlyTheRunsWithinReach() throws IOException {
        String fragment = source(FRAGMENT);

        assertTrue(fragment.contains("gap = min(gap, outside);"),
                "a run whose box the point is outside is skipped, its box the gap to jump");
        assertTrue(fragment.contains("vec3 n = normalAt(p, streams);"),
                "the normal reads the whole field, so skin two streams share is one smooth shape");
    }

    /** decision unmake-skin-is-a-solid-sampled-color */
    @Test
    void theSkinIsTheBlockColorMixedToTheGooColorByTheRouteWithNoTexture() throws IOException {
        String fragment = source(FRAGMENT);

        assertTrue(fragment.contains("return mix(Rings[2 * segment + 1].y, Rings[2 * segment + 3].y, t);"),
                "the hit reads its share of the route from its segment's rings");
        assertTrue(fragment.contains("float route = body >= BOX_BASE ? 0.0 : routeOnSegment(body, p);"),
                "the standing block is at route 0, wearing the block's color whole");
        assertTrue(fragment.contains("vec4 color = vec4(mix(streamSlot(stream, BLOCK_COLOR_SLOT).rgb, "
                        + "streamSlot(stream, GOO_COLOR_SLOT).rgb, route), 1.0);"),
                "the block's color mixes to the goo's color by the route share, 0 at the block and 1 at the hand");
        assertFalse(fragment.contains("texture(Sampler0"), "the skin samples no texture");
        assertFalse(fragment.contains("Sampler0"), "no texture is even declared");
        assertFalse(fragment.contains("mingle_noise"), "the mingle noise left with the textures");
    }

    @Test
    void theSkinAtAHitBlendsTheTwoStreamsMostPresentByTheirPresence() throws IOException {
        String fragment = source(FRAGMENT);

        assertTrue(fragment.contains("float f = runOnly(p, s);"), "each stream's presence at the hit is its own field");
        assertTrue(fragment.contains("vec4 color = skinOf(first, p);"), "the most present stream's skin");
        assertTrue(fragment.contains(
                "color = mix(color, skinOf(second, p), secondField / (firstField + secondField));"),
                "blended with the second's by their presence, so no seam where a pick would flip");
        assertTrue(fragment.contains("int body = nearestBodyOf(p, stream);"),
                "each stream's skin is read on its own nearest body");
    }

    /** decision unmake-metaball-glistens-and-shades */
    @Test
    void theHitGlistensFromTheNormalAndTheViewBeforeTheLightMix() throws IOException {
        String fragment = source(FRAGMENT);

        assertEquals(GLISTEN_POWER, constant(fragment, "GLISTEN_POWER"), DELTA);
        assertEquals(GLISTEN_STRENGTH, constant(fragment, "GLISTEN_STRENGTH"), DELTA);
        assertEquals(RIM_POWER, constant(fragment, "RIM_POWER"), DELTA);
        assertEquals(RIM_STRENGTH, constant(fragment, "RIM_STRENGTH"), DELTA);
        assertTrue(fragment.contains("vec4 glisten(vec4 color, vec3 n, vec3 view) {"), "the glisten function");
        assertTrue(fragment.contains("pow(max(dot(n, normalize(Light0_Direction + view)), 0.0), GLISTEN_POWER)")
                && fragment.contains("pow(max(dot(n, normalize(Light1_Direction + view)), 0.0), GLISTEN_POWER)"),
                "a specular highlight from each cardinal light's half vector");
        assertTrue(fragment.contains("float rim = pow(1.0 - max(dot(n, view), 0.0), RIM_POWER);"),
                "a rim brightening at grazing angles");
        assertTrue(fragment.contains("color.rgb += vec3(shine * GLISTEN_STRENGTH + rim * RIM_STRENGTH);"),
                "both added white-leaning, each by its strength");
        int glistened = fragment.indexOf("vec4 color = glisten(skinAtHit(p, streams), viewNormal, toEye);");
        int mixed = fragment.indexOf("color = minecraft_mix_light(");
        assertTrue(glistened >= 0 && mixed > glistened, "main glistens the hit before the light mix");
        assertTrue(fragment.contains("vec3 toEye = normalize(mat3(ModelViewMat) * -p);"),
                "the view direction is toward the eye, in the lights' space");
    }

    @Test
    void theVertexShaderHandsTheFragmentTheRayToMarch() throws IOException {
        String vertex = source(VERTEX);

        assertTrue(vertex.contains("out vec3 rayPoint;"));
        assertTrue(vertex.contains("rayPoint = Position;"));
    }
}
