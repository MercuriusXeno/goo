package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.ability.RippleTarget;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import java.util.List;
import java.util.Optional;

/**
 * Custom render types for goo visuals. The additive glow line type uses
 * alpha-weighted additive blending (SRC_ALPHA, ONE) with depth-write disabled,
 * so overlapping multi-pass line segments accumulate brightness instead of
 * z-fighting against each other.
 */
public final class GooRenderTypes {
    /** Mod namespace for identifier construction. */
    private static final String NAMESPACE = "goo";
    /** Path prefix of a pipeline's location. */
    private static final String PIPELINE_PATH = "pipeline/";
    /** Path prefix of a core shader. */
    private static final String CORE_SHADER_PATH = "core/";
    /** The block transform's depth bias toward the camera, slope scaled and constant, as a decal's. */
    private static final float BLOCK_MINGLE_DEPTH_BIAS_SCALE = -1f;
    private static final float BLOCK_MINGLE_DEPTH_BIAS_CONSTANT = -10f;
    /** Name prefix of a goo render type. */
    private static final String TYPE_NAME_PREFIX = "goo_";
    /** Name suffix of a burnout pipeline's twin that draws through blocks. */
    /** Fragments fainter than this are cut, as vanilla's translucent entity cuts them. */
    private static final float ALPHA_CUTOUT = 0.1f;
    /**
     * The depth bias vanilla's block-breaking cracks draw with, pulling a
     * layer drawn in the same plane as the world's own faces in front of
     * them so the two never fight.
     */
    private static final float DECAL_BIAS_SCALE = -1.0f;
    private static final float DECAL_BIAS_CONSTANT = -10.0f;
    private static final String THROUGH_BLOCKS_SUFFIX = "_through_blocks";
    /** Must match goo_fluid_surface.fsh: the sampler the frame's mingle table binds to. */
    private static final String MINGLE_TABLE_SAMPLER = "MingleTable";

    /**
     * Lines pipeline with LIGHTNING blend (SRC_ALPHA, ONE) and no depth write.
     * Reuses vanilla line shaders; only blend and depth state differ.
     *
     * @return the result
     */
    public static final RenderPipeline LINES_ADDITIVE_GLOW = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/lines_additive_glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .build();

    /** RenderType that draws lines with additive glow blending. */
    public static final RenderType LINES_GLOW = RenderType.create(
            "goo_lines_additive_glow",
            RenderSetup.builder(LINES_ADDITIVE_GLOW)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup()
    );

    /**
     * The additive glow lines passing every depth test, for a mark that must
     * never sink into the face it sits on, such as the aim reticule.
     */
    public static final RenderPipeline LINES_ADDITIVE_GLOW_THROUGH_BLOCKS =
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE,
                            "pipeline/lines_additive_glow" + THROUGH_BLOCKS_SUFFIX))
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .build();

    /** RenderType that draws additive glow lines through blocks. */
    public static final RenderType LINES_GLOW_THROUGH_BLOCKS = RenderType.create(
            "goo_lines_additive_glow" + THROUGH_BLOCKS_SUFFIX,
            RenderSetup.builder(LINES_ADDITIVE_GLOW_THROUGH_BLOCKS)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup()
    );

    /**
     * Sight's fungus x-ray (decision sight-lengthens-shift-and-outlines-fungus):
     * a block's own baked quads through vanilla's translucent entity shader,
     * passing every depth test and writing no depth, so fungus shows through walls.
     */
    public static final RenderPipeline FUNGUS_XRAY = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "fungus_xray"))
            .withShaderDefine("ALPHA_CUTOUT", ALPHA_CUTOUT)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    /**
     * Deep Click's shriek rings drawn through blocks: vanilla's translucent
     * particle pipeline passing every depth test and writing no depth, so a
     * Click rippling through stone shows to the diver inside it
     * (decision submerged-click-ripples-through-the-ground-to-its-range).
     */
    public static final RenderPipeline CLICK_WAVE_THROUGH_BLOCKS =
            RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "click_wave_through_blocks"))
                    .withVertexShader("core/particle")
                    .withFragmentShader("core/particle")
                    .withSampler("Sampler0")
                    .withSampler("Sampler2")
                    .withVertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withCull(false)
                    .build();

    /**
     * Sight's fungus glow (decision sight-lengthens-shift-and-outlines-fungus):
     * the x-ray's quads blended additively, so a swollen copy of each fungus
     * glows like a lamp behind the wall.
     */
    public static final RenderPipeline FUNGUS_GLOW = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "fungus_glow"))
            .withShaderDefine("ALPHA_CUTOUT", ALPHA_CUTOUT)
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    /** Per-atlas memoized render types on the fungus glow pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> FUNGUS_GLOW_FACTORY =
            net.minecraft.util.Util.memoize(atlas -> RenderType.create(
                    "goo_fungus_glow",
                    RenderSetup.builder(FUNGUS_GLOW)
                            .withTexture("Sampler0", atlas)
                            .useLightmap()
                            .useOverlay()
                            .createRenderSetup()
            ));

    /**
     * Sculker's glass field (decision sculker-shows-blocks-translucent-close-up):
     * the x-ray's translucent block quads, depth tested so the diver's own
     * body and every entity in front of the glass stands over it, writing
     * depth so the vision dims each block by its true distance and, drawn
     * nearest first, only the nearest face shows at each point with no layers
     * stacking behind it; both sides of a face draw, so a face is seen from
     * inside the stone as from the open, and every face draws at one shade,
     * floors and ceilings as bright as walls.
     */
    public static final RenderPipeline SCULKER_GLASS = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "sculker_glass"))
            .withShaderDefine("ALPHA_CUTOUT", ALPHA_CUTOUT)
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true,
                    DECAL_BIAS_SCALE, DECAL_BIAS_CONSTANT))
            .withCull(false)
            .build();

    /** Per-atlas memoized render types on Sculker's glass pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> SCULKER_GLASS_FACTORY =
            net.minecraft.util.Util.memoize(atlas -> RenderType.create(
                    "goo_sculker_glass",
                    RenderSetup.builder(SCULKER_GLASS)
                            .withTexture("Sampler0", atlas)
                            .useLightmap()
                            .useOverlay()
                            .sortOnUpload()
                            .createRenderSetup()
            ));

    /** Per-atlas memoized render types on the fungus x-ray pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> FUNGUS_XRAY_FACTORY =
            net.minecraft.util.Util.memoize(atlas -> RenderType.create(
                    "goo_fungus_xray",
                    RenderSetup.builder(FUNGUS_XRAY)
                            .withTexture("Sampler0", atlas)
                            .useLightmap()
                            .useOverlay()
                            .sortOnUpload()
                            .createRenderSetup()
            ));

    /**
     * Shroom's held spore shell (decision held-visual-ghosts-the-landing-in-two-passes):
     * plain colored quads through vanilla's position-color shader, translucent,
     * depth tested with depth write off, both faces drawn; its twin passes every
     * depth test so the shell shows through blocks.
     */
    public static final RenderPipeline SPORE_SHELL = colorShellPipeline("spore_shell",
            DepthStencilState.DEFAULT.depthTest(), BlendFunction.TRANSLUCENT);

    /** The spore shell's twin that ignores depth. */
    public static final RenderPipeline SPORE_SHELL_THROUGH_BLOCKS = colorShellPipeline(
            "spore_shell" + THROUGH_BLOCKS_SUFFIX, CompareOp.ALWAYS_PASS, BlendFunction.TRANSLUCENT);

    /** RenderType for shroom's held spore shell over blocks. */
    public static final RenderType SPORE_SHELL_TYPE = burnoutType(SPORE_SHELL);

    /** RenderType for shroom's held spore shell through blocks. */
    public static final RenderType SPORE_SHELL_THROUGH_BLOCKS_TYPE = burnoutType(SPORE_SHELL_THROUGH_BLOCKS);

    /**
     * Glow's shell of light, Scry's sphere and Radiant's wisps (decisions
     * scry-sphere-reveals-faces-and-glistens-mobs, radiant-wisps-where-light-is-low):
     * plain colored quads added onto the world, depth tested with depth write
     * off and both faces drawn, so a caster inside the sphere sees its shell.
     */
    public static final RenderPipeline GLOW_SHELL = colorShellPipeline("glow_shell",
            DepthStencilState.DEFAULT.depthTest(), BlendFunction.LIGHTNING);

    /** Scry's revealed faces: added onto the world through every depth test, so they show through walls. */
    public static final RenderPipeline SCRY_FACES = colorShellPipeline("scry_faces",
            CompareOp.ALWAYS_PASS, BlendFunction.LIGHTNING);

    /** RenderType for glow's shell of light. */
    public static final RenderType GLOW_SHELL_TYPE = burnoutType(GLOW_SHELL);

    /** RenderType for Scry's revealed faces. */
    public static final RenderType SCRY_FACES_TYPE = burnoutType(SCRY_FACES);

    /**
     * Meteo's scorch over its crater (decision
     * meteo-winds-up-then-drops-in-a-second-trailing-fire): plain colored
     * quads alpha blended over the faces, depth tested with depth write off,
     * so the scorch darkens what it lies on and never shows through walls.
     */
    public static final RenderPipeline METEOR_SCORCH = colorShellPipeline("meteor_scorch",
            DepthStencilState.DEFAULT.depthTest(), BlendFunction.TRANSLUCENT);

    /** RenderType for Meteo's scorch. */
    public static final RenderType METEOR_SCORCH_TYPE = burnoutType(METEOR_SCORCH);

    /**
     * Void's portable hole mouth (decision hole-is-an-in-world-portable-hole-for-items):
     * plain colored rings alpha blended over the face it opens on, depth tested
     * with depth write off, so the mouth lies on its face and never shows through walls.
     */
    public static final RenderPipeline PORTABLE_HOLE_MOUTH = colorShellPipeline("portable_hole_mouth",
            DepthStencilState.DEFAULT.depthTest(), BlendFunction.TRANSLUCENT);

    /** RenderType for Void's portable hole mouth. */
    public static final RenderType PORTABLE_HOLE_MOUTH_TYPE = burnoutType(PORTABLE_HOLE_MOUTH);

    /** The sampler the portable hole carve reads a copy of the world's depth through. */
    public static final String PORTABLE_HOLE_DEPTH_SAMPLER = "HoleDepthSampler";

    /**
     * Void's portable hole carve (decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole):
     * the mouth's oval through {@code portable_hole_carve.vsh / .fsh}, pushing the depth
     * to the far plane wherever the oval is the nearest thing seen, read against a copy
     * of the world's depth, so the pit and the items sunk behind the face draw through
     * the mouth and nowhere else.
     */
    public static final RenderPipeline PORTABLE_HOLE_CARVE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "portable_hole_carve"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "portable_hole_carve"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "portable_hole_carve"))
            .withSampler(PORTABLE_HOLE_DEPTH_SAMPLER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
            .withCull(false)
            .build();

    /**
     * Void's portable hole pit (decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole):
     * the dark walls and floor of the space behind the mouth, plain colored quads depth
     * tested and writing depth, so they show only where the carve opened the depth and
     * hide whatever lies past the pit.
     */
    public static final RenderPipeline PORTABLE_HOLE_PIT = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "portable_hole_pit"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), true))
            .withCull(false)
            .build();

    /** RenderType for Void's portable hole pit. */
    public static final RenderType PORTABLE_HOLE_PIT_TYPE = burnoutType(PORTABLE_HOLE_PIT);

    /**
     * Meteo's rock (decision meteo-winds-up-then-drops-in-a-second-trailing-fire):
     * plain colored quads that write depth, so the rock's near faces hide its
     * far ones and it draws front to back as a solid.
     */
    public static final RenderPipeline METEOR_ROCK = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "meteor_rock"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    /** RenderType for Meteo's rock. */
    public static final RenderType METEOR_ROCK_TYPE = burnoutType(METEOR_ROCK);

    /**
     * Nether black-hole pipeline: POSITION_COLOR billboard quad with a custom
     * vertex + fragment shader pair (nether_blackhole.vsh / .fsh). Reads the
     * implosion progress from the vertex Color.r channel and animates swirl
     * bands with {@code GameTime}. Depth write ON so the sphere solidly
     * occludes whatever the nether effect has chewed out of the world.
     */
    public static final RenderPipeline NETHER_BLACKHOLE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/nether_blackhole"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_blackhole"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_blackhole"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    /** RenderType that submits the nether black-hole billboard quad. */
    public static final RenderType NETHER_BLACKHOLE_TYPE = RenderType.create(
            "goo_nether_blackhole",
            RenderSetup.builder(NETHER_BLACKHOLE)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    /**
     * Nether black-hole corona pipeline: second render pass that emits
     * the same sphere mesh at a slightly larger radius with additive
     * LIGHTNING blend and depth-write OFF, producing an emissive halo
     * that sits in the annular gap just outside the main sphere's
     * silhouette. Uses {@code nether_corona.vsh / .fsh} which discards
     * pixels inside the main silhouette based on the corona mesh's
     * fresnel value.
     */
    public static final RenderPipeline NETHER_CORONA = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/nether_corona"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_corona"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_corona"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    /** RenderType that submits the corona halo pass. */
    public static final RenderType NETHER_CORONA_TYPE = RenderType.create(
            "goo_nether_corona",
            RenderSetup.builder(NETHER_CORONA)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    /**
     * The black hole's held ghost body: its dark core shader, translucent with
     * depth tested and depth write off, so the ghost hides nothing behind it.
     * held-visual-ghosts-the-landing-in-two-passes
     */
    public static final RenderPipeline NETHER_BLACKHOLE_HELD = shaderPairPipeline("nether_blackhole_held",
            "nether_blackhole", BlendFunction.TRANSLUCENT, DefaultVertexFormat.POSITION_COLOR_NORMAL,
            DepthStencilState.DEFAULT.depthTest());

    /** RenderType for the black hole's held ghost body. */
    public static final RenderType NETHER_BLACKHOLE_HELD_TYPE = burnoutType(NETHER_BLACKHOLE_HELD);

    /** The black hole's held ghost body through blocks, depth ignored. */
    public static final RenderPipeline NETHER_BLACKHOLE_THROUGH_BLOCKS = throughBlocksPipeline("nether_blackhole",
            BlendFunction.TRANSLUCENT);

    /** RenderType for the black hole's held ghost body through blocks. */
    public static final RenderType NETHER_BLACKHOLE_THROUGH_BLOCKS_TYPE = burnoutType(NETHER_BLACKHOLE_THROUGH_BLOCKS);

    /** The black hole's corona through blocks, depth ignored, for its held ghost. */
    public static final RenderPipeline NETHER_CORONA_THROUGH_BLOCKS = throughBlocksPipeline("nether_corona",
            BlendFunction.LIGHTNING);

    /** RenderType for the black hole's corona through blocks. */
    public static final RenderType NETHER_CORONA_THROUGH_BLOCKS_TYPE = burnoutType(NETHER_CORONA_THROUGH_BLOCKS);

    /**
     * Unstable goo's burnout explosion pipeline (decision
     * elemental-explosion-per-type): the fireball sphere and its shockwave
     * ring, additive with depth write off and both faces drawn, through
     * {@code unstable_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline UNSTABLE_EXPLOSION = burnoutPipeline("unstable_explosion", BlendFunction.LIGHTNING);

    /** RenderType that draws unstable goo's burnout explosion. */
    public static final RenderType UNSTABLE_EXPLOSION_TYPE = burnoutType(UNSTABLE_EXPLOSION);

    /**
     * Unstable's held ghost through blocks: the unstable explosion shader with
     * no depth test, so the crater the blast would cut shows through blocks.
     * held-visual-ghosts-the-landing-in-two-passes
     */
    public static final RenderPipeline UNSTABLE_EXPLOSION_THROUGH_BLOCKS = throughBlocksPipeline("unstable_explosion",
            BlendFunction.LIGHTNING);

    /** RenderType for unstable's held ghost through blocks. */
    public static final RenderType UNSTABLE_EXPLOSION_THROUGH_BLOCKS_TYPE =
            burnoutType(UNSTABLE_EXPLOSION_THROUGH_BLOCKS);

    /**
     * Rock goo's burnout explosion pipeline: the dust shock disc, alpha
     * blended so the dust hides what is behind it, through
     * {@code rock_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline ROCK_EXPLOSION = burnoutPipeline("rock_explosion", BlendFunction.TRANSLUCENT);

    /** RenderType that draws rock goo's burnout explosion. */
    public static final RenderType ROCK_EXPLOSION_TYPE = burnoutType(ROCK_EXPLOSION);

    /**
     * Rock's held ghost through blocks: the rock dust shader with no depth
     * test, so the part of a rock dome inside blocks shows through them.
     * held-visual-ghosts-the-landing-in-two-passes
     */
    public static final RenderPipeline ROCK_EXPLOSION_THROUGH_BLOCKS = throughBlocksPipeline("rock_explosion",
            BlendFunction.TRANSLUCENT);

    /** RenderType for rock's held ghost through blocks. */
    public static final RenderType ROCK_EXPLOSION_THROUGH_BLOCKS_TYPE = burnoutType(ROCK_EXPLOSION_THROUGH_BLOCKS);

    /**
     * Frost goo's burnout explosion pipeline: the fog ring, alpha blended,
     * through {@code frost_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline FROST_EXPLOSION = burnoutPipeline("frost_explosion", BlendFunction.TRANSLUCENT);

    /** RenderType that draws frost goo's burnout explosion. */
    public static final RenderType FROST_EXPLOSION_TYPE = burnoutType(FROST_EXPLOSION);

    /**
     * Frost's held ghost through blocks: the frost fog shader with no depth
     * test, so the part of the Orb's landing ring inside blocks shows through
     * them (decision orb-carries-a-swirling-nova).
     */
    public static final RenderPipeline FROST_EXPLOSION_THROUGH_BLOCKS = throughBlocksPipeline("frost_explosion",
            BlendFunction.TRANSLUCENT);

    /** RenderType for frost's held ghost through blocks. */
    public static final RenderType FROST_EXPLOSION_THROUGH_BLOCKS_TYPE = burnoutType(FROST_EXPLOSION_THROUGH_BLOCKS);

    /**
     * Nether goo's burnout explosion pipeline: the inward rush, additive,
     * through {@code nether_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline NETHER_EXPLOSION = burnoutPipeline("nether_explosion", BlendFunction.LIGHTNING);

    /** RenderType that draws nether goo's burnout explosion. */
    public static final RenderType NETHER_EXPLOSION_TYPE = burnoutType(NETHER_EXPLOSION);

    /**
     * Metal goo's burnout explosion pipeline: the chrome urchin, drawn
     * solid, through {@code metal_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline METAL_EXPLOSION = burnoutPipeline("metal_explosion", BlendFunction.TRANSLUCENT);

    /** RenderType that draws metal goo's burnout explosion. */
    public static final RenderType METAL_EXPLOSION_TYPE = burnoutType(METAL_EXPLOSION);

    /**
     * Urchin's chrome on a landed trap, its spines and spears: the metal
     * explosion shader writing depth, since they draw opaque, so the
     * translucent goo blob drawn after them stays behind the parts in front
     * of it rather than blending over them.
     * urchin-rests-bristled-and-spears
     */
    public static final RenderPipeline METAL_CHROME = shaderPairPipeline("metal_chrome", "metal_explosion",
            BlendFunction.TRANSLUCENT, DefaultVertexFormat.POSITION_COLOR_NORMAL, DepthStencilState.DEFAULT);

    /** RenderType for Urchin's depth-writing chrome. */
    public static final RenderType METAL_CHROME_TYPE = burnoutType(METAL_CHROME);

    /**
     * Metal's held ghost through blocks: the metal explosion shader with no
     * depth test, so the spikes inside blocks show through them.
     * held-visual-ghosts-the-landing-in-two-passes
     */
    public static final RenderPipeline METAL_EXPLOSION_THROUGH_BLOCKS = throughBlocksPipeline("metal_explosion",
            BlendFunction.TRANSLUCENT);

    /** RenderType for metal's held ghost through blocks. */
    public static final RenderType METAL_EXPLOSION_THROUGH_BLOCKS_TYPE = burnoutType(METAL_EXPLOSION_THROUGH_BLOCKS);

    /**
     * Crystal goo's burnout explosion pipeline: the prism burst, alpha
     * blended, through {@code crystal_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline CRYSTAL_EXPLOSION = burnoutPipeline("crystal_explosion",
            BlendFunction.TRANSLUCENT);

    /** RenderType that draws crystal goo's burnout explosion. */
    public static final RenderType CRYSTAL_EXPLOSION_TYPE = burnoutType(CRYSTAL_EXPLOSION);

    /**
     * Crystal's held ghost through blocks: the crystal explosion shader with no
     * depth test, so the part of the dome inside blocks shows through them.
     * held-visual-ghosts-the-landing-in-two-passes
     */
    public static final RenderPipeline CRYSTAL_EXPLOSION_THROUGH_BLOCKS = throughBlocksPipeline("crystal_explosion",
            BlendFunction.TRANSLUCENT);

    /** RenderType for crystal's held ghost through blocks. */
    public static final RenderType CRYSTAL_EXPLOSION_THROUGH_BLOCKS_TYPE = burnoutType(CRYSTAL_EXPLOSION_THROUGH_BLOCKS);

    /**
     * Glow goo's burnout explosion pipeline: the aurora bloom, additive,
     * through {@code glow_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline GLOW_EXPLOSION = burnoutPipeline("glow_explosion", BlendFunction.LIGHTNING);

    /** RenderType that draws glow goo's burnout explosion. */
    public static final RenderType GLOW_EXPLOSION_TYPE = burnoutType(GLOW_EXPLOSION);

    /**
     * Bulb's prism beacon (decision bulb-one-model-max-light-beacon-combo):
     * vanilla's beacon beam shader blended additively, depth tested with depth
     * write off and both faces drawn, so its layers stack into a bloom.
     */
    public static final RenderPipeline GLOW_BEAM = RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "glow_beam"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    /**
     * A glow prism's column and halo (decision diagnose-and-correct-the-sunbeam-prism-depth):
     * vanilla's translucent entity draw with depth write off, so the beams
     * drawn after it, Sunbeam's ray among them, show in front of the column
     * rather than fail the depth it would write.
     */
    public static final RenderPipeline GLOW_COLUMN = RenderPipelines.ENTITY_TRANSLUCENT.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "glow_column"))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .build();

    /** The glow column render type, on the block atlas. */
    public static final RenderType GLOW_COLUMN_TYPE = RenderType.create("goo_glow_column",
            RenderSetup.builder(GLOW_COLUMN)
                    .withTexture("Sampler0", Identifier.withDefaultNamespace("textures/atlas/blocks.png"))
                    .useLightmap()
                    .useOverlay()
                    .sortOnUpload()
                    .createRenderSetup());

    /**
     * A gate's halo: its color glowing onto the wall around its oval, fading
     * outward to nothing on the sixteen-texel grid, blended additively and
     * depth tested without writing depth, so the wall and the rim stay as
     * they draw beneath it.
     * portal-halo-glows-additively-onto-the-wall
     */
    public static final RenderPipeline GOO_PORTAL_HALO = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_portal_halo"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_idle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_halo"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, VertexFormat.Mode.QUADS)
            // additive: SRC_ALPHA, ONE
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    /** RenderType for a gate's halo. */
    public static final RenderType GOO_PORTAL_HALO_TYPE = RenderType.create("goo_portal_halo",
            RenderSetup.builder(GOO_PORTAL_HALO).setOutputTarget(OutputTarget.MAIN_TARGET).sortOnUpload()
                    .createRenderSetup());

    /**
     * A gate's swirl band: a funnel spiralling out from its rim in front
     * of it, standing off the wall, banded on the sixteen-texel grid, blended
     * additively and depth tested without writing depth, beside the surface
     * glow.
     * tornadic-halo-spirals-out-from-the-rim
     */
    public static final RenderPipeline GOO_PORTAL_SWIRL_BAND = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_portal_swirl_band"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_idle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_swirl_band"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, VertexFormat.Mode.QUADS)
            // additive: SRC_ALPHA, ONE
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    /** RenderType for a gate's swirl band. */
    public static final RenderType GOO_PORTAL_SWIRL_BAND_TYPE = RenderType.create("goo_portal_swirl_band",
            RenderSetup.builder(GOO_PORTAL_SWIRL_BAND).setOutputTarget(OutputTarget.MAIN_TARGET).sortOnUpload()
                    .createRenderSetup());

    /** Per-texture memoized render types on the glow beam pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> GLOW_BEAM_FACTORY =
            net.minecraft.util.Util.memoize(texture -> RenderType.create(
                    "goo_glow_beam",
                    RenderSetup.builder(GLOW_BEAM)
                            .withTexture("Sampler0", texture)
                            .sortOnUpload()
                            .createRenderSetup()
            ));

    /**
     * The glow beam render type over a beam texture.
     *
     * @param texture the beam texture
     * @return the render type
     */
    public static RenderType glowBeam(Identifier texture) {
        return GLOW_BEAM_FACTORY.apply(texture);
    }

    /**
     * Sunbeam's scorch (decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades):
     * a quad flush on each block face the ray burnt, through
     * {@code sunbeam_scorch.vsh / .fsh}, cooling white-hot through orange to a
     * dark burn mark. Alpha blended so the char darkens the face, depth tested
     * with depth write off and biased toward the camera as a decal is, so it
     * wins the face's own depth without standing off it.
     */
    public static final RenderPipeline SUNBEAM_SCORCH = shaderPairPipeline("sunbeam_scorch", "sunbeam_scorch",
            BlendFunction.TRANSLUCENT, DefaultVertexFormat.POSITION_COLOR_NORMAL,
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false,
                    BLOCK_MINGLE_DEPTH_BIAS_SCALE, BLOCK_MINGLE_DEPTH_BIAS_CONSTANT));

    /** RenderType that draws Sunbeam's scorch. */
    public static final RenderType SUNBEAM_SCORCH_TYPE = burnoutType(SUNBEAM_SCORCH);

    /**
     * Sunbeam's scorch glow (decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades):
     * the scorch's shaders in their glow pass, added onto the world after
     * every mark's char, so overlapping marks' glows sum into one streak.
     */
    public static final RenderPipeline SUNBEAM_SCORCH_GLOW = SUNBEAM_SCORCH.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "sunbeam_scorch_glow"))
            .withShaderDefine("GLOW_PASS")
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .build();

    /** RenderType that draws Sunbeam's scorch glow. */
    public static final RenderType SUNBEAM_SCORCH_GLOW_TYPE = burnoutType(SUNBEAM_SCORCH_GLOW);

    /**
     * Nether black-hole accretion-disk pipeline: third render pass that
     * emits a flat annular ring in the world XZ plane around the sphere,
     * inner radius pinned to the main sphere radius and outer radius at
     * {@code DISK_OUTER_SCALE * main}. Additive LIGHTNING blend with
     * depth write off, so the disk layers onto whatever was drawn behind
     * it. The fragment shader decodes a per-vertex radial distance
     * from {@code Color.r} and discards any fragment whose interpolated
     * radial distance is below the main sphere radius (strictly "no
     * geometry inside the sphere").
     */
    public static final RenderPipeline NETHER_DISK = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/nether_disk"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_disk"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_disk"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    /** RenderType that submits the accretion-disk pass. */
    public static final RenderType NETHER_DISK_TYPE = RenderType.create(
            "goo_nether_disk",
            RenderSetup.builder(NETHER_DISK)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    /**
     * Cube black-hole edge-glow pipeline used by the
     * {@code CubeHoleStyle} experiment. Same shape budget as the
     * corona - additive LIGHTNING blend, depth test on, depth write
     * off - but drawn over a cube mesh with a fragment shader that
     * highlights the per-face edges instead of the fresnel silhouette.
     * Each vertex carries its intra-face UV in {@code Color.rg} so the
     * shader can compute edge distance without any ray math.
     */
    public static final RenderPipeline NETHER_CUBE_EDGE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/nether_cube_edge"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_cube_edge"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_cube_edge"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    /** RenderType that submits the cube edge-glow pass. */
    public static final RenderType NETHER_CUBE_EDGE_TYPE = RenderType.create(
            "goo_nether_cube_edge",
            RenderSetup.builder(NETHER_CUBE_EDGE)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    /**
     * Voronoi fissure pipeline: procedural crack pattern on a sphere mesh.
     * Translucent blend, depth test on (occluded by terrain), depth write off
     * (cracks are overlay), cull off (visible from inside). Reserved for
     * future use - not currently wired to any effect.
     */
    public static final RenderPipeline VORONOI_FISSURE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/voronoi_fissure"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/voronoi_fissure"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/voronoi_fissure"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    /** RenderType for the voronoi fissure sphere (reserved, not wired). */
    public static final RenderType VORONOI_FISSURE_TYPE = RenderType.create(
            "goo_voronoi_fissure",
            RenderSetup.builder(VORONOI_FISSURE)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    /**
     * Goo fluid pipeline: translucent fullbright fluid with no directional
     * shading (so faces look uniformly bright regardless of orientation),
     * no lightmap multiplication (world light doesn't dim it), and
     * writeDepth=ON so the cuboid faces depth-test correctly against each
     * other and the surrounding canister body. Texture is sampled from
     * Sampler0; no overlay, no lightmap (skipped via shader defines).
     */
    public static final RenderPipeline GOO_FLUID = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/goo_fluid"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();

    /** RenderType for goo fluid surfaces. Per-texture-key memoized below. */
    private static final java.util.function.Function<Identifier, RenderType> GOO_FLUID_FACTORY =
            net.minecraft.util.Util.memoize(texture -> RenderType.create(
                    "goo_fluid",
                    RenderSetup.builder(GOO_FLUID)
                            .withTexture("Sampler0", texture)
                            .sortOnUpload()
                            .createRenderSetup()
            ));

    /**
     * Returns the goo-fluid render type for the given texture atlas.
     *
     * @param texture the texture atlas identifier (typically blocks atlas)
     * @return memoized RenderType
     */
    public static RenderType gooFluid(Identifier texture) {
        return GOO_FLUID_FACTORY.apply(texture);
    }

    /**
     * Goo fluid surface pipeline (decision undulating-fluid-surface): the
     * goo fluid look, translucent, fullbright and without cardinal
     * lighting, whose vertex shader lifts each vertex by the ripple
     * amplitude its overlay UV carries times a wave of world position and
     * GameTime. Its fragment shader blends every type the surface holds in
     * one pass, each over the ones before at the mingle opacity of its own
     * noise field of world position and GameTime, from the rows of the
     * frame's mingle table the vertex's lightmap coordinates name (decision
     * one-pass-blends-every-type). Cull is off as on entityTranslucent,
     * which the vat and crucible fluid drew on before.
     */
    public static final RenderPipeline GOO_FLUID_SURFACE = RenderPipeline.builder(
                    RenderPipelines.ENTITY_EMISSIVE_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/goo_fluid_surface"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/goo_fluid_surface"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/goo_fluid_surface"))
            .withSampler(MINGLE_TABLE_SAMPLER)
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    /** Per-texture memoized render types on the goo fluid surface pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> GOO_FLUID_SURFACE_FACTORY =
            net.minecraft.util.Util.memoize(texture -> RenderType.create(
                    "goo_fluid_surface",
                    RenderSetup.builder(GOO_FLUID_SURFACE)
                            .withTexture("Sampler0", texture)
                            .withTexture(MINGLE_TABLE_SAMPLER, MingleTableTexture.ID)
                            .sortOnUpload()
                            .createRenderSetup()
            ));

    /**
     * Returns the undulating goo fluid surface render type for a texture atlas.
     *
     * @param texture the texture atlas identifier (typically blocks atlas)
     * @return memoized RenderType
     */
    public static RenderType gooFluidSurface(Identifier texture) {
        return GOO_FLUID_SURFACE_FACTORY.apply(texture);
    }

    /**
     * Crystal shard pipeline: translucent glass splinter quads scattered
     * in a cloud volume. Depth test on, depth write off, cull off.
     */
    public static final RenderPipeline CRYSTAL_SHARD = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/crystal_shard"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/crystal_shard"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/crystal_shard"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    /** RenderType that submits the crystal shard splinters with scene copy sampler. */
    public static final RenderType CRYSTAL_SHARD_TYPE = RenderType.create(
            "goo_crystal_shard",
            RenderSetup.builder(CRYSTAL_SHARD)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    /**
     * Crucible dissolve pipeline (decisions dissolve-shader-on-item, glow-color-from-mingling):
     * the entity look, lit by the lightmap, on shaders that discard where the mingle noise
     * over world position falls below the dissolve fraction the overlay coordinates carry
     * and paint a glow band above it. The item draws once per goo type layer; each later
     * layer paints its glow opaque where its own mingle field picks it, so the render type
     * keeps emission order rather than sorting. Premultiplied blending lets the band
     * draw opaque over the body while a translucent item body keeps its alpha.
     */
    public static final RenderPipeline CRUCIBLE_DISSOLVE = RenderPipeline.builder(
                    RenderPipelines.ENTITY_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/crucible_dissolve"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/crucible_dissolve"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/crucible_dissolve"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withCull(false)
            .build();

    /** Per-texture memoized render types on the crucible dissolve pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> CRUCIBLE_DISSOLVE_FACTORY =
            net.minecraft.util.Util.memoize(texture -> RenderType.create(
                    "goo_crucible_dissolve",
                    RenderSetup.builder(CRUCIBLE_DISSOLVE)
                            .withTexture("Sampler0", texture)
                            .useLightmap()
                            .createRenderSetup()
            ));

    /**
     * Returns the crucible dissolve render type for a texture atlas.
     *
     * @param texture the texture atlas identifier the item's sprites sit on
     * @return memoized RenderType
     */
    public static RenderType crucibleDissolve(Identifier texture) {
        return CRUCIBLE_DISSOLVE_FACTORY.apply(texture);
    }

    /**
     * Block transform pipeline (decision petrify-stone-encasement-and-calcify-map):
     * an old block's quads drawn over the new block it became, on
     * {@code block_mingle.vsh / .fsh}, discarded where the mingle noise over
     * world position falls below the share of the transform run, which the
     * overlay coordinates carry, so the old block mingles into the new.
     */
    public static final RenderPipeline BLOCK_MINGLE = RenderPipeline.builder(
                    RenderPipelines.ENTITY_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "block_mingle"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "block_mingle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "block_mingle"))
            // Drawn on the new block's own faces, the old block wins the depth test by bias rather than by
            // swelling past them, so no face shifts when the transform ends.
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true,
                    BLOCK_MINGLE_DEPTH_BIAS_SCALE, BLOCK_MINGLE_DEPTH_BIAS_CONSTANT))
            .build();

    /**
     * Voidstone's faces: near-black stone holding pale stars placed by world
     * position, drawn by VoidstoneFaces over the block's own plain dark faces,
     * which the depth bias lets them win (decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform).
     */
    public static final RenderPipeline VOIDSTONE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "voidstone"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "voidstone"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "voidstone"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true,
                    BLOCK_MINGLE_DEPTH_BIAS_SCALE, BLOCK_MINGLE_DEPTH_BIAS_CONSTANT))
            .withCull(false)
            .build();

    /** The render type voidstone's faces draw through. */
    public static final RenderType VOIDSTONE_TYPE = RenderType.create(
            "goo_voidstone",
            RenderSetup.builder(VOIDSTONE).createRenderSetup()
    );

    /**
     * The block transform pipeline blended, for a translucent block's quads:
     * a slime coat building in stays as see-through as the coat it becomes,
     * and writes no depth, as translucent blocks write none
     * (decision coat-is-a-green-slime-film-over-the-blocks-own-texture).
     */
    public static final RenderPipeline BLOCK_MINGLE_TRANSLUCENT = RenderPipeline.builder(
                    RenderPipelines.ENTITY_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "block_mingle_translucent"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "block_mingle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "block_mingle"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false,
                    BLOCK_MINGLE_DEPTH_BIAS_SCALE, BLOCK_MINGLE_DEPTH_BIAS_CONSTANT))
            .build();

    /** Per-atlas memoized render types on the blended block transform pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> BLOCK_MINGLE_TRANSLUCENT_FACTORY =
            net.minecraft.util.Util.memoize(atlas -> RenderType.create(
                    "goo_block_mingle_translucent",
                    RenderSetup.builder(BLOCK_MINGLE_TRANSLUCENT)
                            .withTexture("Sampler0", atlas)
                            .useLightmap()
                            .sortOnUpload()
                            .createRenderSetup()
            ));

    /**
     * Returns the blended block transform render type for the atlas a translucent block's sprites sit on.
     *
     * @param atlas the texture atlas identifier
     * @return memoized RenderType
     */
    public static RenderType blockMingleTranslucent(Identifier atlas) {
        return BLOCK_MINGLE_TRANSLUCENT_FACTORY.apply(atlas);
    }

    /** Per-atlas memoized render types on the block transform pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> BLOCK_MINGLE_FACTORY =
            net.minecraft.util.Util.memoize(atlas -> RenderType.create(
                    "goo_block_mingle",
                    RenderSetup.builder(BLOCK_MINGLE)
                            .withTexture("Sampler0", atlas)
                            .useLightmap()
                            .createRenderSetup()
            ));

    /**
     * Returns Sight's fungus glow render type for the atlas the block's sprites sit on.
     *
     * @param atlas the texture atlas identifier
     * @return memoized RenderType
     */
    public static RenderType fungusGlow(Identifier atlas) {
        return FUNGUS_GLOW_FACTORY.apply(atlas);
    }

    /**
     * Returns Sight's fungus x-ray render type for the atlas the block's sprites sit on.
     *
     * @param atlas the texture atlas identifier
     * @return memoized RenderType
     */
    public static RenderType fungusXray(Identifier atlas) {
        return FUNGUS_XRAY_FACTORY.apply(atlas);
    }

    /**
     * Returns Sculker's glass render type for the atlas the block's sprites sit on.
     *
     * @param atlas the texture atlas identifier
     * @return memoized RenderType
     */
    public static RenderType sculkerGlass(Identifier atlas) {
        return SCULKER_GLASS_FACTORY.apply(atlas);
    }

    /**
     * Returns the block transform render type for the atlas the block's sprites sit on.
     *
     * @param atlas the texture atlas identifier
     * @return memoized RenderType
     */
    public static RenderType blockMingle(Identifier atlas) {
        return BLOCK_MINGLE_FACTORY.apply(atlas);
    }

    /**
     * Goo splat pipeline (decision shader-coat-on-every-mob-landing): a struck
     * mob's model drawn again through {@code goo_mob_coat.vsh / .fsh}, lifted
     * off the skin along its normals, painting the goo type's fluid sprite
     * over the half block around the struck point alone, lit as the mob is.
     * Translucent with depth write off, so the splat never hides the mob's
     * own depth.
     */
    public static final RenderPipeline GOO_MOB_COAT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_mob_coat"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_mob_coat"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_mob_coat"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .build();

    /** Per-atlas memoized render types on the goo coat pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> GOO_MOB_COAT_FACTORY =
            net.minecraft.util.Util.memoize(atlas -> RenderType.create(
                    "goo_mob_coat",
                    RenderSetup.builder(GOO_MOB_COAT)
                            .withTexture("Sampler0", atlas)
                            .useLightmap()
                            .sortOnUpload()
                            .createRenderSetup()
            ));

    /**
     * Returns the goo coat render type for the atlas the goo type's fluid
     * sprite sits on.
     *
     * @param atlas the texture atlas identifier
     * @return memoized RenderType
     */
    public static RenderType gooMobCoat(Identifier atlas) {
        return GOO_MOB_COAT_FACTORY.apply(atlas);
    }

    /**
     * Status ailment overlay pipeline (decision ailment-overlay-shader-per-ailment):
     * a mob's or player's model drawn again through {@code goo_ailment_overlay.vsh / .fsh},
     * lifted a hair off the skin, the ailment's pattern laid over the skin coordinates
     * under its color. Translucent with depth write off, so the overlay never hides the
     * model's own depth. The entity layout without the entity snippet's Sampler0,
     * since no pattern samples a texture, only the lightmap.
     */
    public static final RenderPipeline GOO_AILMENT_OVERLAY = RenderPipeline.builder(
                    RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_ailment_overlay"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_ailment_overlay"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_ailment_overlay"))
            .withSampler("Sampler2")
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .build();

    /**
     * Petrify's stone pipeline (decision petrify-stone-encasement-and-calcify-map):
     * a mob's model and the shell it wears drawn again through
     * {@code petrify_stone.vsh / .fsh}, flush at the model's own depth, a stone
     * texture laid over the skin coordinates in noise patches covering the
     * share of the model the vertex alpha carries, whole at a statue.
     */
    public static final RenderPipeline PETRIFY_STONE = RenderPipeline.builder(
                    RenderPipelines.ENTITY_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "petrify_stone"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "petrify_stone"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "petrify_stone"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .build();

    /** The petrify stone render type, sampling vanilla's stone texture. */
    public static final RenderType PETRIFY_STONE_TYPE = RenderType.create(
            "goo_petrify_stone",
            RenderSetup.builder(PETRIFY_STONE)
                    .withTexture("Sampler0", Identifier.withDefaultNamespace("textures/block/stone.png"))
                    .useLightmap()
                    .sortOnUpload()
                    .createRenderSetup());

    /**
     * Vines' tangle pipeline: a rooted mob's model drawn again through
     * {@code vine_tangle.vsh / .fsh}, flush at the model's own depth, the vine
     * texture laid over the skin coordinates in noise patches covering the
     * share of the model the vertex alpha carries.
     * vines-unpack-root-and-thorn
     */
    public static final RenderPipeline VINE_TANGLE = RenderPipeline.builder(
                    RenderPipelines.ENTITY_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "vine_tangle"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "vine_tangle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "vine_tangle"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .build();

    /** The vine tangle render type, sampling vanilla's vine texture. */
    public static final RenderType VINE_TANGLE_TYPE = RenderType.create(
            "goo_vine_tangle",
            RenderSetup.builder(VINE_TANGLE)
                    .withTexture("Sampler0", Identifier.withDefaultNamespace("textures/block/vine.png"))
                    .useLightmap()
                    .sortOnUpload()
                    .createRenderSetup());

    /**
     * Frost's ice over a frozen mob (decision frozen-gauge-per-mob-encases-when-full):
     * Petrify's stone pipeline sampling vanilla's packed ice, so frost spreads
     * over the mob the way stone does, whole at a full gauge.
     */
    public static final RenderType FROST_ICE_TYPE = RenderType.create(
            "goo_frost_ice",
            RenderSetup.builder(PETRIFY_STONE)
                    .withTexture("Sampler0", Identifier.withDefaultNamespace("textures/block/packed_ice.png"))
                    .useLightmap()
                    .sortOnUpload()
                    .createRenderSetup());

    /**
     * Petrify's fog pipeline (decision petrify-stone-encasement-and-calcify-map):
     * puffs of dust poured over the cone, each a quad facing the camera drawn
     * through {@code petrify_fog.vsh / .fsh}, churning as it billows out
     * (decision petrify-fog-is-billowing-roiling-dust).
     */
    public static final RenderPipeline PETRIFY_FOG = burnoutPipeline("petrify_fog", BlendFunction.TRANSLUCENT);

    /** The petrify fog render type. */
    public static final RenderType PETRIFY_FOG_TYPE = burnoutType(PETRIFY_FOG);

    /** The uniform block the fire field pipeline reads one burning Flames jet's shape from. */
    public static final String FIRE_FIELD_BLOCK = "FireField";

    /**
     * Flames' fire field pipeline (decision flames-pixelated-fire-stream):
     * a box around one burning jet whose fragments march the voxel grid
     * through {@code fire_field.vsh / .fsh}, filling each voxel from one
     * field that merges the jet and its bounce off a wall into one body of
     * pixelated flame. Blended translucent, and writing the depth of the
     * nearest voxel it draws, so whatever draws later and farther, such as
     * the clouds, stands behind it.
     */
    public static final RenderPipeline FIRE_FIELD = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "fire_field"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "fire_field"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "fire_field"))
            .withUniform(FIRE_FIELD_BLOCK, UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(true)
            .build();

    /**
     * Growth's breeze pipeline: cross-sections of the cone drawn through
     * {@code growth_breeze.vsh / .fsh}, glowing green wisps drifting outward
     * with pulses running out from the glove.
     * growth-breeze-ticks-plants
     */
    public static final RenderPipeline GROWTH_BREEZE = burnoutPipeline("growth_breeze", BlendFunction.LIGHTNING);

    /** The growth breeze render type. */
    public static final RenderType GROWTH_BREEZE_TYPE = burnoutType(GROWTH_BREEZE);

    /**
     * Leaf's held ghost, Bloom's pollen haze dome, drawn through
     * {@code leaf_ghost.vsh / .fsh}.
     * bloom-places-buds-by-biome-and-surface
     */
    public static final RenderPipeline LEAF_GHOST = burnoutPipeline("leaf_ghost", BlendFunction.LIGHTNING);

    /** The leaf ghost render type. */
    public static final RenderType LEAF_GHOST_TYPE = burnoutType(LEAF_GHOST);

    /** Leaf's held ghost through blocks, so the haze shows where blocks stand between. */
    public static final RenderPipeline LEAF_GHOST_THROUGH_BLOCKS = throughBlocksPipeline("leaf_ghost",
            BlendFunction.LIGHTNING);

    /**
     * Reap's swell, a whole sphere of Growth's breeze swelling out to Reap's
     * radius, drawn through {@code reap_swell.vsh / .fsh}.
     * reap-breeze-harvests-and-replants
     */
    public static final RenderPipeline REAP_SWELL = burnoutPipeline("reap_swell", BlendFunction.LIGHTNING);

    /** The reap swell render type. */
    public static final RenderType REAP_SWELL_TYPE = burnoutType(REAP_SWELL);

    /** The leaf ghost's through-blocks render type. */
    public static final RenderType LEAF_GHOST_THROUGH_BLOCKS_TYPE = burnoutType(LEAF_GHOST_THROUGH_BLOCKS);

    /**
     * Bore's drill pipeline (decision bore-shows-a-big-drill-from-the-glove):
     * a solid cone bit from the glove drawn through {@code bore_drill.vsh / .fsh},
     * spiral grooves spinning about it. Opaque, depth tested and depth written,
     * both faces drawn, so the bit stands solid and the blocks it sinks into
     * hide its buried length.
     */
    public static final RenderPipeline BORE_DRILL = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "bore_drill"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "bore_drill"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "bore_drill"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    /** The bore drill render type. */
    public static final RenderType BORE_DRILL_TYPE = burnoutType(BORE_DRILL);

    /**
     * Ghost trail pipeline (decision ghost-trail-spans-the-blink): an entity's
     * body drawn again through {@code goo_ghost.vsh / .fsh} as a translucent
     * echo in the goo type's color, its skin read for the cutout and the
     * shading alone. Depth write off, so ghosts and the world behind show through.
     */
    public static final RenderPipeline GOO_GHOST = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_ghost"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_ghost"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_ghost"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .build();

    /** Per-skin memoized render types on the ghost pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> GOO_GHOST_FACTORY =
            net.minecraft.util.Util.memoize(skin -> RenderType.create(
                    "goo_ghost",
                    RenderSetup.builder(GOO_GHOST)
                            .withTexture("Sampler0", skin)
                            .useLightmap()
                            .sortOnUpload()
                            .createRenderSetup()
            ));

    /**
     * Returns the ghost render type over an entity's skin.
     *
     * @param skin the entity's texture
     * @return memoized RenderType
     */
    public static RenderType gooGhost(Identifier skin) {
        return GOO_GHOST_FACTORY.apply(skin);
    }

    /**
     * Phase pipeline (decision phased-things-draw-as-a-ghostly-image): a
     * phased entity's body drawn in place of its own through
     * {@code goo_phase.vsh / .fsh}, its skin washed toward grey, translucent
     * under the vertex alpha, with a faint pale outline at its silhouette.
     * Depth write on, as vanilla's translucent entity, so the body hides its
     * own far side.
     */
    public static final RenderPipeline GOO_PHASE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_phase"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_phase"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_phase"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();

    /** Per-skin memoized render types on the phase pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> GOO_PHASE_FACTORY =
            net.minecraft.util.Util.memoize(skin -> RenderType.create(
                    "goo_phase",
                    RenderSetup.builder(GOO_PHASE)
                            .withTexture("Sampler0", skin)
                            .useLightmap()
                            .sortOnUpload()
                            .createRenderSetup()
            ));

    /**
     * Returns the phase render type over an entity's skin.
     *
     * @param skin the entity's texture
     * @return memoized RenderType
     */
    public static RenderType gooPhase(Identifier skin) {
        return GOO_PHASE_FACTORY.apply(skin);
    }

    /**
     * Portal view pipeline (decision portals-link-by-looking-through-portal-vision):
     * a linked portal's face drawn through {@code goo_portal_view.vsh / .fsh},
     * sampling the partner side's render at the fragment's own screen point, so
     * the view through lies flush with the frame. Depth tested and written, as
     * the face stands in the world.
     */
    public static final RenderPipeline GOO_PORTAL_VIEW = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_portal_view"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_view"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_view"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, VertexFormat.Mode.QUADS)
            // portals-and-wormhole-draw-sixteen-texels-a-block: the rim's edge ring lets a quarter of the wall through
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    /**
     * A hole's mouth showing the view through it: the far side see-through
     * over the wall inside a torn, shifting edge
     * (decision hole-closes-the-space-to-an-opening-ahead).
     */
    public static final RenderPipeline GOO_HOLE_VIEW = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_hole_view"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_view"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_hole_view"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    private static final java.util.function.Function<Identifier, RenderType> GOO_HOLE_VIEW_FACTORY =
            net.minecraft.util.Util.memoize(view -> RenderType.create("goo_hole_view",
                    RenderSetup.builder(GOO_HOLE_VIEW)
                            .withTexture("Sampler0", view)
                            .setOutputTarget(OutputTarget.MAIN_TARGET)
                            .createRenderSetup()
            ));

    /**
     * Returns the hole view render type over a hole mouth's view texture.
     *
     * @param view the view texture's id
     * @return memoized RenderType
     */
    public static RenderType gooHoleView(Identifier view) {
        return GOO_HOLE_VIEW_FACTORY.apply(view);
    }

    /**
     * An Outsider room's edge showing the view through it: the room's
     * opposite side, flush over the edge's whole face with no oval and no rim
     * (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
     */
    public static final RenderPipeline GOO_ROOM_EDGE_VIEW = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_room_edge_view"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_view"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_room_edge_view"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    private static final java.util.function.Function<Identifier, RenderType> GOO_ROOM_EDGE_VIEW_FACTORY =
            net.minecraft.util.Util.memoize(view -> RenderType.create("goo_room_edge_view",
                    RenderSetup.builder(GOO_ROOM_EDGE_VIEW)
                            .withTexture("Sampler0", view)
                            .setOutputTarget(OutputTarget.MAIN_TARGET)
                            .createRenderSetup()
            ));

    /**
     * Returns the room edge view render type over a room edge's view texture.
     *
     * @param view the view texture's id
     * @return memoized RenderType
     */
    public static RenderType gooRoomEdgeView(Identifier view) {
        return GOO_ROOM_EDGE_VIEW_FACTORY.apply(view);
    }

    /**
     * Idle portal pipeline (decision portals-link-by-looking-through-portal-vision):
     * a portal with no view to show drawn through {@code goo_portal_idle.vsh / .fsh},
     * an oval of its color swirling in from a glowing rim toward a dark heart,
     * blended over the wall, depth tested and written as the face stands in
     * the world.
     */
    public static final RenderPipeline GOO_PORTAL_IDLE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_portal_idle"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_idle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_idle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    /** RenderType for an idle portal's swirl. */
    public static final RenderType GOO_PORTAL_IDLE_TYPE = RenderType.create("goo_portal_idle",
            RenderSetup.builder(GOO_PORTAL_IDLE).setOutputTarget(OutputTarget.MAIN_TARGET).sortOnUpload()
                    .createRenderSetup());

    /**
     * A portal's outline through blocks while its owner chooses a partner: a
     * thin glowing ring at the oval's edge passing every depth test, so a
     * hidden portal can be found without covering what stands before it
     * (decision portals-link-by-looking-through-portal-vision).
     */
    public static final RenderPipeline GOO_PORTAL_OUTLINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_portal_outline"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_idle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_outline"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    /** RenderType for a portal's outline through blocks. */
    public static final RenderType GOO_PORTAL_OUTLINE_TYPE = RenderType.create("goo_portal_outline",
            RenderSetup.builder(GOO_PORTAL_OUTLINE).setOutputTarget(OutputTarget.MAIN_TARGET).sortOnUpload()
                    .createRenderSetup());

    /**
     * The idle swirl's twin passing every depth test, for portal vision:
     * while a player chooses a partner, every portal of theirs shows its
     * swirl through walls.
     */
    public static final RenderPipeline GOO_PORTAL_VISION = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_portal_vision"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_idle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_portal_idle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    /** RenderType for portal vision's swirls through walls. */
    public static final RenderType GOO_PORTAL_VISION_TYPE = RenderType.create("goo_portal_vision",
            RenderSetup.builder(GOO_PORTAL_VISION).setOutputTarget(OutputTarget.MAIN_TARGET).sortOnUpload()
                    .createRenderSetup());

    /** Per-view memoized render types on the portal view pipeline. */
    private static final java.util.function.Function<Identifier, RenderType> GOO_PORTAL_VIEW_FACTORY =
            net.minecraft.util.Util.memoize(view -> RenderType.create(
                    "goo_portal_view",
                    RenderSetup.builder(GOO_PORTAL_VIEW)
                            .withTexture("Sampler0", view)
                            .setOutputTarget(OutputTarget.MAIN_TARGET)
                            .createRenderSetup()
            ));

    /**
     * Returns the portal view render type over a portal's view texture.
     *
     * @param view the view texture's id
     * @return memoized RenderType
     */
    public static RenderType gooPortalView(Identifier view) {
        return GOO_PORTAL_VIEW_FACTORY.apply(view);
    }
    /** The ripple mask shader pair's name, and the stem of each mask pipeline's. */
    private static final String RIPPLE_MASK = "goo_ripple_mask";

    /** The stem each mask pipeline's name takes its channel index after. */
    private static final String RIPPLE_MASK_CHANNEL = RIPPLE_MASK + "_";

    /**
     * Afterimage ripple mask pipelines (decision afterimage-is-one-shared-effect),
     * one per color channel of the ripple buffer: each fills its channel alone with
     * the vertex alpha, the silhouette's fade, through {@code goo_ripple_mask.vsh / .fsh},
     * no blending, so overlapping cubes of one silhouette merge and the other
     * silhouettes' channels stay as they are. Depth tested against the world's copied
     * depth, depth write off, both faces drawn.
     */
    public static final List<RenderPipeline> GOO_RIPPLE_MASKS = List.of(
            rippleMaskPipeline(0, ColorTargetState.WRITE_RED),
            rippleMaskPipeline(1, ColorTargetState.WRITE_GREEN),
            rippleMaskPipeline(2, ColorTargetState.WRITE_BLUE),
            rippleMaskPipeline(3, ColorTargetState.WRITE_ALPHA));

    /** The render types filling the ripple buffer's channels, in channel order. */
    public static final List<RenderType> GOO_RIPPLE_MASK_TYPES = GOO_RIPPLE_MASKS.stream()
            .map(pipeline -> RenderType.create(pipeline.getLocation().getPath(),
                    RenderSetup.builder(pipeline).setOutputTarget(RippleTarget.OUTPUT).createRenderSetup()))
            .toList();

    /**
     * Afterimage ripple edge pipeline (decision afterimage-is-one-shared-effect): a
     * fullscreen pass over the ripple buffer through {@code goo_ripple_edge.fsh},
     * painting where any channel changes, the silhouettes' perimeters, in the goo
     * type's color that ColorModulator carries, blended over the frame.
     */
    public static final RenderPipeline GOO_RIPPLE_EDGE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_ripple_edge"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_ripple_edge"))
            .withSampler("InSampler")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .build();

    /**
     * Sculker's soft outline pipeline (decision sculker-sight-persists-where-echoes-have-touched):
     * a fullscreen pass over the ripple buffer's red channel through
     * {@code goo_soft_outline.fsh}, painting a blurred glow out from each heard
     * body's edge in the color ColorModulator carries, blended over the frame.
     */
    public static final RenderPipeline GOO_SOFT_OUTLINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_soft_outline"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_soft_outline"))
            .withSampler("InSampler")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .build();

    /** The one render type on the ailment overlay pipeline. */
    public static final RenderType GOO_AILMENT_OVERLAY_TYPE = RenderType.create(
            "goo_ailment_overlay",
            RenderSetup.builder(GOO_AILMENT_OVERLAY)
                    .useLightmap()
                    .sortOnUpload()
                    .createRenderSetup()
    );

    private GooRenderTypes() {}

    /**
     * A ripple mask pipeline filling one channel of the ripple buffer
     * (decision afterimage-is-one-shared-effect).
     *
     * @param channel   the channel's index, which names the pipeline
     * @param writeMask the ColorTargetState write bit of that channel alone
     * @return the pipeline
     */
    private static RenderPipeline rippleMaskPipeline(int channel, int writeMask) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + RIPPLE_MASK_CHANNEL + channel))
                .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + RIPPLE_MASK))
                .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + RIPPLE_MASK))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(Optional.empty(), writeMask))
                .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                .withCull(false)
                .build();
    }

    /**
     * A burnout explosion pipeline (decision elemental-explosion-per-type):
     * the type's own shader pair under {@code core/<name>}, position, color
     * and normal quads, depth tested with depth write off, both faces drawn.
     *
     * @param name  the shader pair's and pipeline's name
     * @param blend how the explosion blends over the world
     * @return the pipeline
     */
    private static RenderPipeline burnoutPipeline(String name, BlendFunction blend) {
        return burnoutPipeline(name, blend, DefaultVertexFormat.POSITION_COLOR_NORMAL);
    }

    /**
     * A burnout-style pipeline on a vertex format of its own: the shader pair
     * under {@code core/<name>}, depth tested with depth write off, both faces drawn.
     *
     * @param name   the shader pair's and pipeline's name
     * @param blend  how the pipeline blends over the world
     * @param format the vertex format its quads carry
     * @return the pipeline
     */
    private static RenderPipeline burnoutPipeline(String name, BlendFunction blend, VertexFormat format) {
        return shaderPairPipeline(name, name, blend, format, DepthStencilState.DEFAULT.depthTest());
    }

    /**
     * A burnout pipeline's twin that ignores depth: the same shader pair and
     * blend with depth test and depth write off, so what it draws shows through blocks.
     * held-visual-ghosts-the-landing-in-two-passes
     *
     * @param name  the burnout's shader pair's name
     * @param blend how the pipeline blends over the world
     * @return the pipeline, located at the burnout's name with a through-blocks suffix
     */
    private static RenderPipeline throughBlocksPipeline(String name, BlendFunction blend) {
        return shaderPairPipeline(name + THROUGH_BLOCKS_SUFFIX, name, blend, DefaultVertexFormat.POSITION_COLOR_NORMAL,
                CompareOp.ALWAYS_PASS);
    }

    /**
     * A plain colored quad pipeline through vanilla's position-color shader,
     * blended with depth write off, both faces drawn.
     *
     * @param location  the pipeline's name
     * @param depthTest the depth comparison its fragments pass
     * @param blend     how its fragments blend onto the world
     * @return the pipeline
     */
    private static RenderPipeline colorShellPipeline(String location, CompareOp depthTest, BlendFunction blend) {
        return RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + location))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(new DepthStencilState(depthTest, false))
                .withCull(false)
                .build();
    }

    /**
     * A quad pipeline over a shader pair under {@code core/<shader>}, depth write
     * off, both faces drawn.
     *
     * @param location  the pipeline's name
     * @param shader    the shader pair's name
     * @param blend     how the pipeline blends over the world
     * @param format    the vertex format its quads carry
     * @param depthTest the depth comparison its fragments pass
     * @return the pipeline
     */
    private static RenderPipeline shaderPairPipeline(String location, String shader, BlendFunction blend,
                                                     VertexFormat format, CompareOp depthTest) {
        return shaderPairPipeline(location, shader, blend, format, new DepthStencilState(depthTest, false));
    }

    /**
     * A quad pipeline over a shader pair under {@code core/<shader>}, both
     * faces drawn, on the depth state given.
     *
     * @param location the pipeline's name
     * @param shader   the shader pair's name
     * @param blend    how the pipeline blends over the world
     * @param format   the vertex format its quads carry
     * @param depth    the depth test and write its fragments take
     * @return the pipeline
     */
    private static RenderPipeline shaderPairPipeline(String location, String shader, BlendFunction blend,
                                                     VertexFormat format, DepthStencilState depth) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + location))
                .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + shader))
                .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + shader))
                .withVertexFormat(format, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(depth)
                .withCull(false)
                .build();
    }

    /**
     * The render type that draws a burnout explosion pipeline to the main target.
     *
     * @param pipeline the explosion's pipeline
     * @return the render type
     */
    private static RenderType burnoutType(RenderPipeline pipeline) {
        String name = pipeline.getLocation().getPath().substring(PIPELINE_PATH.length());
        return RenderType.create(TYPE_NAME_PREFIX + name,
                RenderSetup.builder(pipeline).setOutputTarget(OutputTarget.MAIN_TARGET).createRenderSetup());
    }

    /**
     * Registers the plain pipelines: the additive glow lines, Sight's fungus
     * x-ray, and shroom's spore shell in both passes.
     *
     * @param event the pipeline registration event
     */
    private static void registerLinePipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(LINES_ADDITIVE_GLOW);
        event.registerPipeline(LINES_ADDITIVE_GLOW_THROUGH_BLOCKS);
        event.registerPipeline(FUNGUS_XRAY);
        event.registerPipeline(CLICK_WAVE_THROUGH_BLOCKS);
        event.registerPipeline(SCULKER_GLASS);
        event.registerPipeline(FUNGUS_GLOW);
        event.registerPipeline(SPORE_SHELL);
        event.registerPipeline(SPORE_SHELL_THROUGH_BLOCKS);
    }

    /**
     * Registers custom pipelines with the NeoForge pipeline registry.
     *
     * @param event the event instance
     */
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        registerBurnoutPipelines(event);
        registerMobLayerPipelines(event);
        registerLeafPipelines(event);
        registerLinePipelines(event);
        registerNetherPipelines(event);
        event.registerPipeline(VORONOI_FISSURE);
        event.registerPipeline(CRYSTAL_SHARD);
        event.registerPipeline(GOO_FLUID);
        event.registerPipeline(GOO_FLUID_SURFACE);
        event.registerPipeline(CRUCIBLE_DISSOLVE);
        event.registerPipeline(BLOCK_MINGLE);
        event.registerPipeline(BLOCK_MINGLE_TRANSLUCENT);
        event.registerPipeline(VOIDSTONE);
        event.registerPipeline(PETRIFY_FOG);
        event.registerPipeline(FIRE_FIELD);
        event.registerPipeline(BORE_DRILL);
        registerOverlayPipelines(event);
    }

    /**
     * Registers the overlay pipelines: the goo ripple masks and edge, the
     * ghost, Bulb's glow beam, Sunbeam's scorch, the glow shell, Scry's
     * faces, and Meteo's scorch and rock.
     *
     * @param event the event instance
     */
    private static void registerOverlayPipelines(RegisterRenderPipelinesEvent event) {
        GOO_RIPPLE_MASKS.forEach(event::registerPipeline);
        event.registerPipeline(GOO_RIPPLE_EDGE);
        event.registerPipeline(GOO_SOFT_OUTLINE);
        event.registerPipeline(GOO_GHOST);
        event.registerPipeline(GOO_PHASE);
        event.registerPipeline(GOO_PORTAL_VIEW);
        event.registerPipeline(GOO_PORTAL_IDLE);
        event.registerPipeline(GOO_HOLE_VIEW);
        event.registerPipeline(GOO_ROOM_EDGE_VIEW);
        event.registerPipeline(GOO_PORTAL_OUTLINE);
        event.registerPipeline(GOO_PORTAL_VISION);
        event.registerPipeline(GOO_PORTAL_HALO);
        event.registerPipeline(GOO_PORTAL_SWIRL_BAND);
        event.registerPipeline(GLOW_BEAM);
        event.registerPipeline(GLOW_COLUMN);
        event.registerPipeline(SUNBEAM_SCORCH);
        event.registerPipeline(SUNBEAM_SCORCH_GLOW);
        event.registerPipeline(GLOW_SHELL);
        event.registerPipeline(SCRY_FACES);
        event.registerPipeline(METEOR_SCORCH);
        event.registerPipeline(METEOR_ROCK);
        event.registerPipeline(PORTABLE_HOLE_MOUTH);
        event.registerPipeline(PORTABLE_HOLE_CARVE);
        event.registerPipeline(PORTABLE_HOLE_PIT);
    }

    /**
     * Registers the nether black hole's pipelines: its body, corona, disk and
     * cube edges, and their held and through-blocks twins.
     *
     * @param event the event instance
     */
    private static void registerNetherPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(NETHER_BLACKHOLE);
        event.registerPipeline(NETHER_CORONA);
        event.registerPipeline(NETHER_BLACKHOLE_HELD);
        event.registerPipeline(NETHER_BLACKHOLE_THROUGH_BLOCKS);
        event.registerPipeline(NETHER_CORONA_THROUGH_BLOCKS);
        event.registerPipeline(NETHER_DISK);
        event.registerPipeline(NETHER_CUBE_EDGE);
    }

    /**
     * Registers the pipelines that draw a mob's model again over itself: the
     * goo coat, Petrify's stone, Vines' tangle and the ailment overlays.
     *
     * @param event the event instance
     */
    private static void registerMobLayerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GOO_MOB_COAT);
        event.registerPipeline(PETRIFY_STONE);
        event.registerPipeline(VINE_TANGLE);
        event.registerPipeline(GOO_AILMENT_OVERLAY);
    }

    /**
     * Registers Leaf's pipelines: Growth's breeze, Bloom's haze ghost and Reap's swell.
     *
     * @param event the event instance
     */
    private static void registerLeafPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GROWTH_BREEZE);
        event.registerPipeline(LEAF_GHOST);
        event.registerPipeline(LEAF_GHOST_THROUGH_BLOCKS);
        event.registerPipeline(REAP_SWELL);
    }

    /**
     * Registers the burnout explosion pipelines (decision
     * elemental-explosion-per-type).
     *
     * @param event the event instance
     */
    private static void registerBurnoutPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(UNSTABLE_EXPLOSION);
        event.registerPipeline(UNSTABLE_EXPLOSION_THROUGH_BLOCKS);
        event.registerPipeline(ROCK_EXPLOSION);
        event.registerPipeline(ROCK_EXPLOSION_THROUGH_BLOCKS);
        event.registerPipeline(FROST_EXPLOSION);
        event.registerPipeline(FROST_EXPLOSION_THROUGH_BLOCKS);
        event.registerPipeline(NETHER_EXPLOSION);
        event.registerPipeline(METAL_EXPLOSION);
        event.registerPipeline(METAL_EXPLOSION_THROUGH_BLOCKS);
        event.registerPipeline(METAL_CHROME);
        event.registerPipeline(CRYSTAL_EXPLOSION);
        event.registerPipeline(CRYSTAL_EXPLOSION_THROUGH_BLOCKS);
        event.registerPipeline(GLOW_EXPLOSION);
    }
}
