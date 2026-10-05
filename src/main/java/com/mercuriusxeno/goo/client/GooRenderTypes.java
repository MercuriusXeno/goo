package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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

    /** Depth state that always passes (see-through rendering). */
    private static final DepthStencilState DEPTH_ALWAYS = new DepthStencilState(
            com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS, false);

    /** Lines pipeline with depth test disabled for see-through ghost outlines. */
    public static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/lines_no_depth"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DEPTH_ALWAYS)
            .build();

    /** RenderType for see-through wireframe lines (ghost outline). */
    public static final RenderType LINES_NO_DEPTH = RenderType.create(
            "goo_lines_no_depth",
            RenderSetup.builder(LINES_NO_DEPTH_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()
    );

    /** Quads pipeline with depth test disabled for see-through ghost fill. */
    public static final RenderPipeline QUADS_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/quads_no_depth"))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DEPTH_ALWAYS)
            .build();

    /** RenderType for see-through translucent fill quads (ghost outline). */
    public static final RenderType QUADS_NO_DEPTH = RenderType.create(
            "goo_quads_no_depth",
            RenderSetup.builder(QUADS_NO_DEPTH_PIPELINE)
                    .sortOnUpload()
                    .createRenderSetup()
    );

    /** Quads pipeline with additive blend (SRC_ALPHA, ONE) and no depth test.
     * Used for the ghost fill brightening pass. */
    public static final RenderPipeline QUADS_ADDITIVE_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/quads_additive_no_depth"))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(DEPTH_ALWAYS)
            .build();

    /** RenderType for additive-blend see-through quads (ghost fill glow pass). */
    public static final RenderType QUADS_ADDITIVE_NO_DEPTH = RenderType.create(
            "goo_quads_additive_no_depth",
            RenderSetup.builder(QUADS_ADDITIVE_NO_DEPTH_PIPELINE)
                    .sortOnUpload()
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
     * Unstable goo's burnout explosion pipeline (decision
     * elemental-explosion-per-type): the fireball sphere and its shockwave
     * ring, additive with depth write off and both faces drawn, through
     * {@code unstable_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline UNSTABLE_EXPLOSION = burnoutPipeline("unstable_explosion", BlendFunction.LIGHTNING);

    /** RenderType that draws unstable goo's burnout explosion. */
    public static final RenderType UNSTABLE_EXPLOSION_TYPE = burnoutType(UNSTABLE_EXPLOSION);

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
     * Crystal goo's burnout explosion pipeline: the prism burst, alpha
     * blended, through {@code crystal_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline CRYSTAL_EXPLOSION = burnoutPipeline("crystal_explosion",
            BlendFunction.TRANSLUCENT);

    /** RenderType that draws crystal goo's burnout explosion. */
    public static final RenderType CRYSTAL_EXPLOSION_TYPE = burnoutType(CRYSTAL_EXPLOSION);

    /**
     * Glow goo's burnout explosion pipeline: the aurora bloom, additive,
     * through {@code glow_explosion.vsh / .fsh}.
     */
    public static final RenderPipeline GLOW_EXPLOSION = burnoutPipeline("glow_explosion", BlendFunction.LIGHTNING);

    /** RenderType that draws glow goo's burnout explosion. */
    public static final RenderType GLOW_EXPLOSION_TYPE = burnoutType(GLOW_EXPLOSION);

    /**
     * Goo's swirling ring particle pipeline (decision goo-swirl-ring-particle):
     * the disc in front of a layer about to break, alpha blended, through
     * {@code goo_ring.vsh / .fsh}. Its quads carry position, UV0 and color, so
     * the vertex color holds the theme tint beside the progress.
     */
    public static final RenderPipeline GOO_RING = burnoutPipeline("goo_ring", BlendFunction.TRANSLUCENT,
            DefaultVertexFormat.POSITION_TEX_COLOR);

    /** RenderType that draws goo's ring particle. */
    public static final RenderType GOO_RING_TYPE = burnoutType(GOO_RING);

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
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, PIPELINE_PATH + name))
                .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + name))
                .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, CORE_SHADER_PATH + name))
                .withVertexFormat(format, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
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
        event.registerPipeline(NETHER_DISK);
        event.registerPipeline(NETHER_CUBE_EDGE);
        event.registerPipeline(LINES_NO_DEPTH_PIPELINE);
        event.registerPipeline(QUADS_NO_DEPTH_PIPELINE);
        event.registerPipeline(QUADS_ADDITIVE_NO_DEPTH_PIPELINE);
        event.registerPipeline(VORONOI_FISSURE);
        event.registerPipeline(CRYSTAL_SHARD);
        event.registerPipeline(GOO_FLUID);
        event.registerPipeline(GOO_FLUID_SURFACE);
        event.registerPipeline(CRUCIBLE_DISSOLVE);
        event.registerPipeline(GOO_MOB_COAT);
        event.registerPipeline(GOO_AILMENT_OVERLAY);
    }

    /**
     * Registers the burnout explosion pipelines (decision
     * elemental-explosion-per-type).
     *
     * @param event the event instance
     */
    private static void registerBurnoutPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(UNSTABLE_EXPLOSION);
        event.registerPipeline(ROCK_EXPLOSION);
        event.registerPipeline(BLAZE_EXPLOSION);
        event.registerPipeline(FROST_EXPLOSION);
        event.registerPipeline(NETHER_EXPLOSION);
        event.registerPipeline(METAL_EXPLOSION);
        event.registerPipeline(CRYSTAL_EXPLOSION);
        event.registerPipeline(GLOW_EXPLOSION);
        event.registerPipeline(GOO_RING);
    }
}
