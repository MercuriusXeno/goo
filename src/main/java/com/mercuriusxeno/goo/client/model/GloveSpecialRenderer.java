package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ability.GloveGlow;
import com.mercuriusxeno.goo.client.ability.KnivesCharge;
import com.mercuriusxeno.goo.client.ability.LiftedPalmBlob;
import com.mercuriusxeno.goo.client.ability.PalmBlobHop;
import com.mercuriusxeno.goo.client.ability.PhaseLook;
import com.mercuriusxeno.goo.client.ability.PhasedHands;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mercuriusxeno.goo.client.outsider.RoomBodyCopyRenderer;
import com.mercuriusxeno.goo.client.throwing.BlobShape;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Special item renderer for glove items. Renders the glove body model
 * (arm sheath) and draws the charge blob in the palm area when a goo
 * type is selected. Shared by glove, glove_netherite, and glove_exorite tiers.
 */
public class GloveSpecialRenderer implements SpecialModelRenderer<GloveSpecialRenderer.GloveData> {

    // --- Goo center capture for arc rendering ---
    /**
     * Smoothing factor per frame (lower = more smoothing).
     */
    private static final float ARC_SMOOTH_FACTOR = 0.15f;
    /**
     * Pixels per block for coordinate conversion.
     */
    private static final float BLOCK_PIXELS = 16f;
    /**
     * Fully opaque white for untinted rendering.
     */
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    /**
     * Center X of the goo in model pixels.
     */
    private static final float GOO_CX_PX = 8f;
    /**
     * Where the palm blob's bottom rests in model pixels: a quarter pixel
     * above the finger plate's top face, so the shell never shares that face
     * and swells up the palm rather than down through the fingers.
     */
    static final float PALM_FLOOR_PX = 1.25f;
    /**
     * Where the palm blob's back face sits in model pixels: inside the
     * hand, hidden by it, so the swell grows out of the palm.
     */
    static final float PALM_BACK_Z_PX = 8.5f;
    /**
     * Half-width of the shell at rest in model pixels, a flight blob's own.
     */
    private static final float SHELL_HW_PX = GooFlightRenderer.SHELL_HW * 16f;
    /**
     * The palm blob's size at rest against itself, the size it lifts off at.
     */
    private static final float REST_SCALE = 1f;
    /**
     * Minimum extent X in model pixels.
     */
    private static final float EXTENT_MIN_X_PX = 4.5f;
    /**
     * Minimum extent Y in model pixels.
     */
    private static final float EXTENT_MIN_Y_PX = -1f;
    /**
     * Minimum extent Z in model pixels.
     */
    private static final float EXTENT_MIN_Z_PX = 4f;
    /**
     * Maximum extent X in model pixels.
     */
    private static final float EXTENT_MAX_X_PX = 11.5f;
    /**
     * Maximum extent Y in model pixels.
     */
    private static final float EXTENT_MAX_Y_PX = 11f;
    /**
     * Maximum extent Z in model pixels.
     */
    private static final float EXTENT_MAX_Z_PX = 11.5f;
    /**
     * Camera-relative position of the held goo's center, captured during
     * item rendering. The arc renderer adds camera.position() to get world space.
     */
    private static volatile Vec3 gooCenterCamRel;
    /**
     * Smoothed goo center for arc origin - filters out swing jitter.
     */
    private static volatile Vec3 smoothedGooCenter;

    /**
     * Returns the last captured camera-relative goo center. No age
     * check - once captured, the value is always valid. The position
     * updates every frame the glove's held goo renders, so staleness
     * is at most one frame (~16ms at 60fps). Returns null only if the
     * item renderer has never fired (first frame after world load,
     * before the glove has ever been held - in practice unreachable
     * because the arc handler only runs when you're holding the glove).
     *
     * @return camera-relative goo center, or null if never captured
     */
    /**
     * Creates a glove special renderer.
     */
    public GloveSpecialRenderer() {
    }

    /**
     * Returns the raw captured goo center (includes swing).
     *
     * @return camera-relative goo center, or null if never captured
     */
    public static @Nullable Vec3 getLastGooCenterCamRel() {
        return gooCenterCamRel;
    }

    /**
     * Returns the smoothed goo center for arc origin (swing filtered out).
     *
     * @return smoothed camera-relative goo center, or null if never captured
     */
    public static @Nullable Vec3 getSmoothedGooCenterCamRel() {
        return smoothedGooCenter;
    }

    /**
     * Submits the baked glove body model (arm sheath geometry) for the given
     * tier, opaque with its wrist opening cut out, so the palm hides the
     * part of the core held inside the hand whatever order the shell's
     * faces sort in (decision diagnose-and-correct-the-palm-core-cull).
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight   the packed light value
     * @param gloveItem     the glove item instance
     */
    private static void submitGloveBody(PoseStack poseStack,
                                        SubmitNodeCollector nodeCollector, int packedLight, Item gloveItem) {
        QuadCollection model = GloveBodyModels.getModel(gloveItem);
        // phased-things-draw-as-a-ghostly-image: a phased player's own held glove draws under the phase fade
        boolean ghost = PhasedHands.ghosting();
        nodeCollector.submitCustomGeometry(poseStack, ghost ? GooSubmitter.renderType() : bodyType(),
                (pose, c) -> {
                    QuadInstance qi = new QuadInstance();
                    qi.setColor(ghost ? PhaseLook.bodyTint(COLOR_WHITE) : COLOR_WHITE);
                    qi.setLightCoords(packedLight);
                    qi.setOverlayCoords(OverlayTexture.NO_OVERLAY);
                    for (BakedQuad quad : model.getAll()) {
                        c.putBakedQuad(pose, quad, qi);
                    }
                });
    }

    /**
     * The render type the glove body submits on: cutout on the block atlas,
     * opaque, so the hand pass draws it before any translucent face
     * (decision diagnose-and-correct-the-palm-core-cull).
     *
     * @return entityCutout on the block atlas
     */
    static RenderType bodyType() {
        return GooSubmitter.cutoutOnBlockAtlas();
    }

    /**
     * The render type the palm blob's core submits on: solid on the block atlas.
     *
     * @return entitySolid on the block atlas
     */
    static RenderType coreType() {
        return GooSubmitter.solidOnBlockAtlas();
    }

    /**
     * Draws the held goo in the palm as the charge blob, a pulsing opaque
     * core inside a translucent tinted shell, both fullbright and pressed
     * flat, resting on the fingers with its back in the hand. A charge swells
     * both layers from that floor and back, so the blob grows up and out of
     * the palm. First person, another player's hand and the inventory slot
     * all draw the palm blob here.
     * decision palm-blob-draws-as-the-charge-blob
     * decision charge-swells-the-palm-blob
     * palm-blob-rests-pressed-and-stands-into-the-cube
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param type          the goo type
     * @param blobScale     the blob's size against its rest size
     */
    private static void submitHeldGoo(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                      ResourceKey<GooTypeDefinition> type, float blobScale) {
        BlobShape shape = palmShape(blobScale);
        Vector3f center = palmCenter(shape);
        captureGooCenter(poseStack, center.x(), center.y(), center.z());
        submitBlob(poseStack, nodeCollector, type, center, shape);
    }

    /**
     * The palm blob's shape at a size: pressed flat on the palm, a charge
     * swelling its width, depth and height alike.
     * palm-blob-rests-pressed-and-stands-into-the-cube
     * decision charge-swells-the-palm-blob
     *
     * @param blobScale the blob's size against its rest size
     * @return the shape against the rest cube
     */
    private static BlobShape palmShape(float blobScale) {
        return PalmBlobHop.pressed().scaled(blobScale);
    }

    /**
     * Where the palm blob's center sits in the model for its shape: its
     * bottom on the fingers and its back in the hand, so it grows up and out
     * of the palm.
     * palm-blob-rests-pressed-and-stands-into-the-cube
     *
     * @param shape the palm blob's shape
     * @return the center in block coords
     */
    private static Vector3f palmCenter(BlobShape shape) {
        return new Vector3f(GOO_CX_PX / BLOCK_PIXELS, (PALM_FLOOR_PX + SHELL_HW_PX * shape.height()) / BLOCK_PIXELS,
                (PALM_BACK_Z_PX - SHELL_HW_PX * shape.depth()) / BLOCK_PIXELS);
    }

    /**
     * Submits the charge-style blob, its core in its shell, about a point of
     * the glove's model, turned with the glove.
     * decision palm-blob-draws-as-the-charge-blob
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param type          the goo type
     * @param center        the blob's center in the model, in block coords
     * @param shape         the blob's box against the palm blob's rest cube
     */
    private static void submitBlob(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                   ResourceKey<GooTypeDefinition> type, Vector3fc center, BlobShape shape) {
        float gameTime = gameTime();
        poseStack.pushPose();
        poseStack.translate(center.x(), center.y(), center.z());
        nodeCollector.submitCustomGeometry(poseStack, coreType(),
                (pose, c) -> GooFlightRenderer.emitCore(pose, c, type, gameTime, shape));
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(),
                (pose, c) -> GooFlightRenderer.emitShell(pose, c, type, shape));
        poseStack.popPose();
    }

    /**
     * Submits the local first-person player's lifted blob in the hand's pass,
     * so it draws over the glove and turns with it: from the palm blob's own
     * center, size and pressed shape it hops, its bottom on the palm as it
     * stands up, then eases toward the spray source as the hand's pass shows
     * it, growing to the lifted size and settling into the cube.
     * palm-blob-lifts-as-the-spray-source
     * palm-blob-rests-pressed-and-stands-into-the-cube
     * every-lift-begins-with-a-hop
     *
     * @param poseStack     the pose stack, at the glove's model
     * @param nodeCollector the render node collector
     * @param type          the goo type
     * @param lift          the local player's lift
     */
    private static void submitLiftedGoo(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                        ResourceKey<GooTypeDefinition> type, LiftedPalmBlob.HandLift lift) {
        Matrix4f toHandPass = new Matrix4f(poseStack.last().pose());
        Vector3f standing = palmCenter(palmShape(REST_SCALE)).add(0f, lift.stand() * SHELL_HW_PX / BLOCK_PIXELS, 0f);
        Vector3f palm = toHandPass.transformPosition(standing);
        Vector3f lifted = palm.lerp(lift.sourceInHand().toVector3f(), lift.share());
        submitBlob(poseStack, nodeCollector, type, toHandPass.invert().transformPosition(lifted), lift.shape());
    }

    /**
     * The level's game time with the frame's partial tick, zero with no
     * level so the inventory slot still draws the blob.
     *
     * @return the game time in ticks
     */
    private static float gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0f
                : mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    /**
     * Submits the light breathing around the held goo while a glow channel
     * holds, in the hand's own pass so the glove never hides it.
     * operator ruling 2026-10-10: a light glow around the hand marks a held glow channel
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param strength      how strongly it glows, zero for no glow
     */
    private static void submitChannelGlow(PoseStack poseStack, SubmitNodeCollector nodeCollector, float strength) {
        if (strength <= 0f) {
            return;
        }
        float time = gameTime();
        Vector3f center = palmCenter(palmShape(REST_SCALE));
        poseStack.pushPose();
        poseStack.translate(center.x(), center.y(), center.z());
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.GLOW_SHELL_TYPE,
                (pose, c) -> GloveGlow.emitShells(pose, c, SHELL_HW_PX / BLOCK_PIXELS, strength, time));
        poseStack.popPose();
    }

    /**
     * Transforms the goo center through the current PoseStack to get
     * camera-relative coordinates and stores them for the arc renderer.
     *
     * @param poseStack the pose stack for rendering
     * @param cx        the center X in block coords
     * @param cy        the center Y in block coords
     * @param cz        the center Z in block coords
     */
    static void captureGooCenter(PoseStack poseStack,
                                          float cx, float cy, float cz) {
        // portals-link-by-looking-through-portal-vision: a glove drawn on the player's body in a view through a
        // portal stands where that view's camera sees it, not before the player's eyes, so it leaves the aim line's
        // hand where the first-person glove put it.
        // decision room-tiles-to-render-distance-held-to-the-picture: so does a glove on the player's copy in an
        // Outsider room's copies, which stands a room's width or more away
        if (PortalView.drawing() || RoomBodyCopyRenderer.drawingCopies()) {
            return;
        }
        Vector4f pos = new Vector4f(cx, cy, cz, 1.0f);
        poseStack.last().pose().transform(pos);
        Vec3 raw = new Vec3(pos.x(), pos.y(), pos.z());
        gooCenterCamRel = raw;
        Vec3 prev = smoothedGooCenter;
        smoothedGooCenter = prev == null ? raw : prev.lerp(raw, ARC_SMOOTH_FACTOR);
    }

    /**
     * Extracts the selected goo type from the glove item stack.
     *
     * @param stack the glove item stack
     * @return render data, or null if the item is not a glove
     */
    @Override
    public @Nullable GloveData extractArgument(ItemStack stack) {
        if (!(stack.getItem() instanceof GooGloveItem)) {
            return null;
        }
        ResourceKey<GooTypeDefinition> type = GooGloveItem.getSelectedType(stack);
        // Suppress the held goo visual when the player has no goo of that type
        if (type != null && !GloveUseTracker.isSelectedTypeAvailable()) {
            type = null;
        }
        return new GloveData(stack.getItem(), type, channelGlowOf(type, stack),
                KnivesCharge.palmScaleAt(KnivesCharge.localPalmShare(stack)),
                // palm-blob-lifts-as-the-spray-source
                type != null && LiftedPalmBlob.liftedFrom(stack), LiftedPalmBlob.heldInView(stack));
    }

    /**
     * How strongly the light around a glove's goo glows: by the held glow
     * channel's fade for glow goo, none for any other.
     *
     * @param type  the goo shown in the glove, or null for none
     * @param stack the glove
     * @return the strength, zero to one
     */
    private static float channelGlowOf(@Nullable ResourceKey<GooTypeDefinition> type, ItemStack stack) {
        return type == GooTypes.GLOW ? GloveGlow.strengthFor(stack) : 0f;
    }

    /**
     * Renders the glove body and optional held goo overlay.
     *
     * @param data          extracted glove data (may be null)
     * @param poseStack     the current pose stack
     * @param nodeCollector the render node collector
     * @param packedLight   packed light value
     * @param packedOverlay packed overlay value
     * @param hasFoil       whether the item has enchantment foil
     * @param outlineColor  outline color for selected items
     */
    @Override
    public void submit(@Nullable GloveData data,
                       PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();

        Item gloveItem = data != null ? data.item() : GooItems.GOO_GLOVE.get();
        submitGloveBody(poseStack, nodeCollector, packedLight, gloveItem);

        if (data != null && data.selectedType() != null) {
            submitPalm(data, poseStack, nodeCollector);
        }

        poseStack.popPose();
    }

    /**
     * Submits the palm's goo and its glow, or, while the palm blob stands
     * lifted at the spray source, only the palm's place for the aim line.
     * palm-blob-lifts-as-the-spray-source
     * decision charge-swells-the-palm-blob
     *
     * @param data          extracted glove data with a goo type selected
     * @param poseStack     the current pose stack
     * @param nodeCollector the render node collector
     */
    private static void submitPalm(GloveData data, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        if (data.palmLifted()) {
            Vector3f palm = palmCenter(palmShape(REST_SCALE));
            captureGooCenter(poseStack, palm.x(), palm.y(), palm.z());
            if (data.heldInView() && !PortalView.drawing() && !RoomBodyCopyRenderer.drawingCopies()) {
                LiftedPalmBlob.liftHeldInView().ifPresent(lift -> submitLiftedGoo(poseStack, nodeCollector,
                        data.selectedType(), lift));
            }
            return;
        }
        submitHeldGoo(poseStack, nodeCollector, data.selectedType(), data.blobScale());
        submitChannelGlow(poseStack, nodeCollector, data.channelGlow());
    }

    /**
     * Reports the geometric extents of the glove for GUI rendering.
     * Covers the full glove volume including cuff and finger plate.
     *
     * @param output consumer for extent corner vertices
     */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(EXTENT_MIN_X_PX / BLOCK_PIXELS, EXTENT_MIN_Y_PX / BLOCK_PIXELS, EXTENT_MIN_Z_PX / BLOCK_PIXELS));
        output.accept(new Vector3f(EXTENT_MAX_X_PX / BLOCK_PIXELS, EXTENT_MAX_Y_PX / BLOCK_PIXELS, EXTENT_MAX_Z_PX / BLOCK_PIXELS));
    }

    /**
     * Extracted render data from the glove item stack.
     *
     * @param item         the glove item instance (determines tier/body model)
     * @param selectedType the selected goo type, or null if none selected
     * @param channelGlow  how strongly the light around the held goo glows while a glow channel holds, zero to one
     * @param blobScale    the palm blob's size against its rest size, swelling with the local charge
     * @param palmLifted   whether the palm blob stands lifted at the holder's spray source, leaving the palm none
     * @param heldInView   whether the local player holds this glove before the eyes in first person, so the
     *                     hand's pass draws its lifted blob
     */
    public record GloveData(Item item, @Nullable ResourceKey<GooTypeDefinition> selectedType, float channelGlow,
                            float blobScale, boolean palmLifted, boolean heldInView) {
    }


    /**
     * Unbaked factory for the glove special renderer. Registered as
     * "goo:glove_goo" in the special model renderer registry.
     */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<GloveData> {

        /**
         * Codec for data-driven item model deserialization.
         */
        public static final MapCodec<GloveSpecialRenderer.Unbaked> MAP_CODEC =
                MapCodec.unit(new GloveSpecialRenderer.Unbaked());

        @Override
        public MapCodec<GloveSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<GloveData> bake(SpecialModelRenderer.BakingContext context) {
            return new GloveSpecialRenderer();
        }
    }
}
