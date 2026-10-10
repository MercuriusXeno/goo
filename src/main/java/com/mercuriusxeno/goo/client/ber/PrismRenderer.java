package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.ability.oculus.OculusNodes;
import com.mercuriusxeno.goo.ability.program.AgitationState;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ability.AgitatorWisps;
import com.mercuriusxeno.goo.client.ability.ThumpRings;
import com.mercuriusxeno.goo.client.ability.TransformationRenderer;
import com.mercuriusxeno.goo.client.ability.Transformations;
import com.mercuriusxeno.goo.client.ber.style.AgitatorPrismStyle;
import com.mercuriusxeno.goo.client.ber.style.OculusLids;
import com.mercuriusxeno.goo.client.ber.style.OculusStyle;
import com.mercuriusxeno.goo.client.ber.style.PrismComboStyle;
import com.mercuriusxeno.goo.client.ber.style.PrismComboStyles;
import com.mercuriusxeno.goo.client.ber.style.PulsePrismStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Draws the prism's milky quartz crystal, one pointed column standing out of
 * the face it grew from. While the landing blob transforms into it, the blob's
 * cube morphs into the column, its goo look fading into the quartz. A prism
 * holding a combo draws by the style its combo registered in
 * {@link PrismComboStyles}, scaled about the landing face's center as it grows,
 * and keeps drawing a combo's beam from as far as the render distance reaches.
 * decision prism-blob-becomes-a-milky-quartz-crystal
 * decision prism-is-one-pointed-quartz-column
 * decision prism-hosts-the-combos
 * decision bulb-one-model-max-light-beacon-combo
 */
public class PrismRenderer implements BlockEntityRenderer<PrismBlockEntity, PrismRenderState> {

    private static final float HALF = 0.5f;
    /** Ticks in vanilla's beacon beam scroll cycle. */
    private static final int BEAM_CYCLE_TICKS = 40;
    /** The camera distance past which a beam widens, in blocks, vanilla's beacon threshold. */
    static final float BEAM_WIDEN_DISTANCE = 96f;

    /**
     * Creates the prism renderer.
     *
     * @param context the renderer context
     */
    public PrismRenderer(BlockEntityRendererProvider.Context context) {
        // The crystal is procedural; the context carries nothing it draws from.
    }

    @Override
    public PrismRenderState createRenderState() {
        return new PrismRenderState();
    }

    @Override
    public void extractRenderState(PrismBlockEntity prism, PrismRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(prism, state, breakProgress);
        state.look = PrismCrystal.look();
        state.facing = prism.getBlockState().getValue(PrismBlock.FACING);
        state.scale = TransformationRenderer.blockModelScale(prism.getBlockPos());
        Transformations.Transformation blob = TransformationRenderer.transformationInto(prism.getBlockPos());
        state.blobLook = blob == null ? null : CrystalClusterSubmitter.lookOf(blob.gooType(),
                ClientGooTypes.color(blob.gooType()));
        state.combo = prism.getCombo();
        extractGlow(prism, state, partialTick, cameraPos);
        extractPulse(prism, state);
        extractAgitation(prism, state, partialTick);
        extractOculus(prism, state, partialTick, cameraPos);
    }

    /**
     * Reads glow's beams: the beacon beam's scroll and width, and a reflector's links.
     *
     * @param prism       the prism
     * @param state       the render state
     * @param partialTick the partial tick
     * @param cameraPos   the camera's position
     */
    private static void extractGlow(PrismBlockEntity prism, PrismRenderState state, float partialTick, Vec3 cameraPos) {
        // bulb-one-model-max-light-beacon-combo: a beam scrolls and widens as vanilla's beacon beam does
        long gameTime = prism.getLevel() == null ? 0L : prism.getLevel().getGameTime();
        state.animationTime = Math.floorMod(gameTime, BEAM_CYCLE_TICKS) + partialTick;
        state.beamRadiusScale = beamRadiusScale((float) cameraPos.subtract(state.blockPos.getCenter()).horizontalDistance());
        state.links = prism.getLinks().stream().map(link -> Vec3.atLowerCornerOf(link.subtract(prism.getBlockPos())))
                .toList();
        state.linkLight = prism.getLinkLight();
    }

    /**
     * Reads pulse's redstone: the power given, the signal heard and the time since the last beat.
     *
     * @param prism the prism
     * @param state the render state
     */
    private static void extractPulse(PrismBlockEntity prism, PrismRenderState state) {
        // metronome-prism-pulses-at-the-learned-rate, relay-prism-carries-the-signal-through-air
        state.power = prism.getBlockState().getValue(PrismBlock.POWER);
        state.signalHeard = prism.beat().heard();
        state.sinceBeat = PrismBeats.secondsSinceBeat(prism.getBlockPos(), state.power > 0);
        if (PulsePrismStyle.METRONOME_COMBO.equals(state.combo)) {
            // metronome-prism-pulses-at-the-learned-rate: each beat sends a red ring out from the prism's base
            ThumpRings.see(prism.getBlockPos(), state.facing, state.power > 0);
        }
    }

    /**
     * Reads an agitator's beat off its synced countdown.
     * agitator-prism-quickens-until-a-spawn
     *
     * @param prism       the prism
     * @param state       its render state
     * @param partialTick the frame's partial tick
     */
    private static void extractAgitation(PrismBlockEntity prism, PrismRenderState state, float partialTick) {
        AgitationState agitation = prism.programState().agitation();
        state.beat = agitation.interval() > 0
                ? AgitatorPrismStyle.beat(agitation.interval() - agitation.countdown() + partialTick)
                : 0f;
        if (AgitatorPrismStyle.COMBO.equals(state.combo)) {
            AgitatorWisps.report(prism.getBlockPos(), agitation.countdown());
        }
    }

    /**
     * How much a beam widens at a camera distance: not at all within 96
     * blocks, then in step with the distance, as vanilla's beacon beam widens.
     *
     * @param horizontalDistance the camera's horizontal distance from the prism, in blocks
     * @return the factor the beam's radii take
     */
    static float beamRadiusScale(float horizontalDistance) {
        return Math.max(1f, horizontalDistance / BEAM_WIDEN_DISTANCE);
    }

    /**
     * The box a prism draws within: its cell, stretched along the face it
     * grew from by its combo's reach, so a beam draws while its prism is off screen.
     *
     * @param pos    the prism's position
     * @param facing the face the prism grew from
     * @param reach  how far its combo draws out of the cell, in blocks
     * @return the render bounding box
     */
    static AABB drawnBounds(BlockPos pos, Direction facing, int reach) {
        return new AABB(pos).expandTowards(facing.getStepX() * reach, facing.getStepY() * reach,
                facing.getStepZ() * reach);
    }

    @Override
    public AABB getRenderBoundingBox(PrismBlockEntity prism) {
        PrismComboStyle style = PrismComboStyles.forCombo(prism.getCombo());
        int reach = style == null ? 0 : style.beamReach();
        AABB bounds = drawnBounds(prism.getBlockPos(), prism.getBlockState().getValue(PrismBlock.FACING), reach);
        // reflector-rails-carry-the-brightest-light: the box reaches every linked reflector, so its beams draw
        for (BlockPos link : prism.getLinks()) {
            bounds = bounds.minmax(new AABB(link));
        }
        return bounds;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * SectionPos.SECTION_SIZE;
    }

    /**
     * Reads what an oculus draws by: its combo's start, the time, the turn
     * toward the camera and how shut its lids stand for this viewer.
     * oculus-prism-becomes-a-hovering-eye
     *
     * @param prism       the prism
     * @param state       its render state, its facing and combo already read
     * @param partialTick the frame's partial tick
     * @param cameraPos   the camera
     */
    private static void extractOculus(PrismBlockEntity prism, PrismRenderState state, float partialTick,
                                      Vec3 cameraPos) {
        state.comboSince = prism.comboSince();
        state.gameTime = prism.getLevel() == null ? 0f : prism.getLevel().getGameTime() + partialTick;
        state.yawToCamera = yawToward(prism.getBlockPos().getCenter(), cameraPos);
        state.lidClosure = OculusNodes.OCULUS.equals(state.combo) ? lidClosureFor(prism, state, cameraPos) : 1f;
    }

    /**
     * How shut an oculus's lids stand for this viewer: open while the
     * camera's look rests on the eye.
     * decision oculus-prism-becomes-a-hovering-eye
     *
     * @param prism     the oculus prism
     * @param state     its render state, its facing and time already read
     * @param cameraPos the camera
     * @return 0 open to 1 shut
     */
    private static float lidClosureFor(PrismBlockEntity prism, PrismRenderState state, Vec3 cameraPos) {
        Vec3 eye = Vec3.atLowerCornerOf(prism.getBlockPos()).add(OculusStyle.eyeInCell(state.facing));
        Vec3 forward = new Vec3(Minecraft.getInstance().gameRenderer.getMainCamera().forwardVector());
        return OculusLids.closure(prism.getBlockPos(), OculusLids.lookedAt(cameraPos, forward, eye), state.gameTime);
    }

    /**
     * The yaw that turns a model at a point toward the camera, about the
     * vertical: 0 faces south, as a model's front does.
     *
     * @param from      the model's centre
     * @param cameraPos the camera
     * @return the yaw in degrees
     */
    static float yawToward(Vec3 from, Vec3 cameraPos) {
        return (float) Math.toDegrees(Math.atan2(cameraPos.x - from.x, cameraPos.z - from.z));
    }

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState cameraState) {
        CrystalClusterSubmitter.Look look = state.look;
        if (state.scale <= 0f || look == null) {
            return;
        }
        poseStack.pushPose();
        // prism-hosts-the-combos: a combined prism draws by its combo's style, a plain one as the crystal
        PrismComboStyle style = PrismComboStyles.forCombo(state.combo);
        if (style == null) {
            PrismCrystal.standOnLandingFace(poseStack, state.facing);
            submitPlain(state, look, poseStack, nodeCollector);
        } else {
            scaleAboutBase(poseStack, state.facing, state.scale);
            style.submit(state, poseStack, nodeCollector);
        }
        poseStack.popPose();
    }

    /**
     * Draws the plain column, or the landing blob part way into it, its goo look
     * fading out as the quartz fades in over the one morphing mesh.
     * decision prism-is-one-pointed-quartz-column
     *
     * @param state         the render state
     * @param look          the column's look
     * @param poseStack     the pose, turned to stand on the landing face
     * @param nodeCollector the submit collector
     */
    private static void submitPlain(PrismRenderState state, CrystalClusterSubmitter.Look look, PoseStack poseStack,
                                    SubmitNodeCollector nodeCollector) {
        CrystalClusterSubmitter.Look blobLook = state.blobLook;
        if (blobLook == null || state.scale >= 1f) {
            CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS, look, state.lightCoords);
            return;
        }
        List<Vec3[]> faces = PrismCrystal.morphFaces(state.scale);
        CrystalClusterSubmitter.submitFaces(poseStack, nodeCollector, faces,
                PrismCrystal.fade(blobLook, 1f - state.scale), state.lightCoords);
        CrystalClusterSubmitter.submitFaces(poseStack, nodeCollector, faces,
                PrismCrystal.fade(look, state.scale), state.lightCoords);
    }

    /**
     * Scales the pose about the center of the face the prism grew from, so the
     * crystal grows out of the face rather than out of the middle of the cell.
     *
     * @param poseStack the pose stack at the cell's corner
     * @param facing    the face the prism grew from
     * @param scale     the prism's size
     */
    private static void scaleAboutBase(PoseStack poseStack, Direction facing, float scale) {
        float baseX = HALF - HALF * facing.getStepX();
        float baseY = HALF - HALF * facing.getStepY();
        float baseZ = HALF - HALF * facing.getStepZ();
        poseStack.translate(baseX, baseY, baseZ);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-baseX, -baseY, -baseZ);
    }
}
