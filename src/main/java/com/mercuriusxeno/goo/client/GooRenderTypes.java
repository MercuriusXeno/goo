package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.ability.RippleTarget;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
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
    /** Name prefix of a goo render type. */
    private static final String TYPE_NAME_PREFIX = "goo_";
    /** Name suffix of a burnout pipeline's twin that draws through blocks. */
    private static final String THROUGH_BLOCKS_SUFFIX = "_through_blocks";

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
     * Blaze goo's burnout explosion pipeline: the flame bloom, additive so
     * it lights what it covers, through {@code blaze_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline BLAZE_EXPLOSION = burnoutPipeline("blaze_explosion", BlendFunction.LIGHTNING);

    /** RenderType that draws blaze goo's burnout explosion. */
    public static final RenderType BLAZE_EXPLOSION_TYPE = burnoutType(BLAZE_EXPLOSION);

    /**
     * Frost goo's burnout explosion pipeline: the fog ring, alpha blended,
     * through {@code frost_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline FROST_EXPLOSION = burnoutPipeline("frost_explosion", BlendFunction.TRANSLUCENT);

    /** RenderType that draws frost goo's burnout explosion. */
    public static final RenderType FROST_EXPLOSION_TYPE = burnoutType(FROST_EXPLOSION);

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
     * GameTime. Its fragment shader draws layer 0 whole and every later
     * layer at the mingle opacity of its own noise field of world position
     * and GameTime, from the share and layer the vertex's lightmap
     * coordinates carry (decision noise-mingled-type-textures). Cull is off
     * as on entityTranslucent, which the vat and crucible fluid drew on
     * before.
     */
    public static final RenderPipeline GOO_FLUID_SURFACE = RenderPipeline.builder(
                    RenderPipelines.ENTITY_EMISSIVE_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/goo_fluid_surface"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/goo_fluid_surface"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/goo_fluid_surface"))
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
            .build();

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
     * under its color. The glint patterns read the vanilla enchantment glint texture
     * from Sampler0. Translucent with depth write off, so the overlay never hides the
     * model's own depth.
     */
    public static final RenderPipeline GOO_AILMENT_OVERLAY = RenderPipeline.builder(
                    RenderPipelines.ENTITY_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + "goo_ailment_overlay"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_ailment_overlay"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + "goo_ailment_overlay"))
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
     * Petrify's fog pipeline (decision petrify-stone-encasement-and-calcify-map):
     * cross-sections of the cone drawn through {@code petrify_fog.vsh / .fsh},
     * undulating dust-fog waves washing forward through them.
     */
    public static final RenderPipeline PETRIFY_FOG = burnoutPipeline("petrify_fog", BlendFunction.TRANSLUCENT);

    /** The petrify fog render type. */
    public static final RenderType PETRIFY_FOG_TYPE = burnoutType(PETRIFY_FOG);

    /**
     * Unmake's waves pipeline (decision unmake-waves-dissolve-by-crucible-cost):
     * cross-sections of the cone drawn through {@code unmake_waves.vsh / .fsh},
     * goo colored bands sweeping down them from the glove.
     */
    public static final RenderPipeline UNMAKE_WAVES = burnoutPipeline("unmake_waves", BlendFunction.TRANSLUCENT);

    /** The unmake waves render type. */
    public static final RenderType UNMAKE_WAVES_TYPE = burnoutType(UNMAKE_WAVES);

    /**
     * Bore's vortex pipeline (decision bore-vortex-with-a-worldspace-shake):
     * sections down the tunnel drawn through {@code bore_vortex.vsh / .fsh},
     * spiralling dust arms turning about the look.
     */
    public static final RenderPipeline BORE_VORTEX = burnoutPipeline("bore_vortex", BlendFunction.TRANSLUCENT);

    /** The bore vortex render type. */
    public static final RenderType BORE_VORTEX_TYPE = burnoutType(BORE_VORTEX);

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

    /** The one render type on the ailment overlay pipeline, its glint texture bound. */
    public static final RenderType GOO_AILMENT_OVERLAY_TYPE = RenderType.create(
            "goo_ailment_overlay",
            RenderSetup.builder(GOO_AILMENT_OVERLAY)
                    .withTexture("Sampler0", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
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
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + location))
                .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + shader))
                .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + shader))
                .withVertexFormat(format, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(new DepthStencilState(depthTest, false))
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
     * Registers custom pipelines with the NeoForge pipeline registry.
     *
     * @param event the event instance
     */
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        registerBurnoutPipelines(event);
        event.registerPipeline(LINES_ADDITIVE_GLOW);
        event.registerPipeline(NETHER_BLACKHOLE);
        event.registerPipeline(NETHER_CORONA);
        event.registerPipeline(NETHER_BLACKHOLE_HELD);
        event.registerPipeline(NETHER_BLACKHOLE_THROUGH_BLOCKS);
        event.registerPipeline(NETHER_CORONA_THROUGH_BLOCKS);
        event.registerPipeline(NETHER_DISK);
        event.registerPipeline(NETHER_CUBE_EDGE);
        event.registerPipeline(VORONOI_FISSURE);
        event.registerPipeline(CRYSTAL_SHARD);
        event.registerPipeline(GOO_FLUID);
        event.registerPipeline(GOO_FLUID_SURFACE);
        event.registerPipeline(CRUCIBLE_DISSOLVE);
        event.registerPipeline(GOO_MOB_COAT);
        event.registerPipeline(BLOCK_MINGLE);
        event.registerPipeline(PETRIFY_STONE);
        event.registerPipeline(PETRIFY_FOG);
        event.registerPipeline(BORE_VORTEX);
        event.registerPipeline(GOO_AILMENT_OVERLAY);
        GOO_RIPPLE_MASKS.forEach(event::registerPipeline);
        event.registerPipeline(GOO_RIPPLE_EDGE);
        event.registerPipeline(GOO_GHOST);
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
        event.registerPipeline(BLAZE_EXPLOSION);
        event.registerPipeline(FROST_EXPLOSION);
        event.registerPipeline(NETHER_EXPLOSION);
        event.registerPipeline(METAL_EXPLOSION);
        event.registerPipeline(METAL_EXPLOSION_THROUGH_BLOCKS);
        event.registerPipeline(CRYSTAL_EXPLOSION);
        event.registerPipeline(CRYSTAL_EXPLOSION_THROUGH_BLOCKS);
        event.registerPipeline(GLOW_EXPLOSION);
        event.registerPipeline(UNMAKE_WAVES);
    }
}
