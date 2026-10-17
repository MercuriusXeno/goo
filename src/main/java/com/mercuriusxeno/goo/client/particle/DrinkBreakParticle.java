package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.ability.DrinkRide;
import com.mercuriusxeno.goo.client.ability.DrinkStream;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import java.util.function.Supplier;

/**
 * One break particle of a block Unmake drinks: a random crop of the block's
 * own particle sprite, shaded and tinted as vanilla shades its break
 * particles and lit where it stands, flying straight from the cloud the block
 * burst into to the glove, placed afresh every frame so it follows the hand
 * as it moves, and gone at the glove.
 * decision unmake-drink-is-a-stream-of-break-particles
 */
public final class DrinkBreakParticle extends SingleQuadParticle {

    /** The shade vanilla's break particles wear over the sprite. */
    private static final float SHADE = 0.6f;
    private static final float HALF = 0.5f;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL = 0xFF;
    private static final float CHANNEL_MAX = 255f;
    /** Ticks a particle outlives its drinker's glove going unseen, so a frame missing it does not end the flight. */
    private static final int ORPHAN_TICKS = 5;
    /** How many times the longest route's travel a particle may live past its turn before it is let go. */
    private static final int TRAVELS = 2;

    private final Supplier<@Nullable Vec3> glove;
    private final DrinkRide.Mote mote;
    private final SpriteCrop crop;
    private final Layer layer;
    private int orphaned;

    /**
     * @param level  the client level
     * @param state  the block it broke from
     * @param pos    where the block stands
     * @param sprite the block's particle sprite
     * @param glove  the drinker's glove as last placed, or null while the drink is not
     * @param mote   its part in the block's drain
     */
    public DrinkBreakParticle(ClientLevel level, BlockState state, BlockPos pos, TextureAtlasSprite sprite,
                              Supplier<@Nullable Vec3> glove, DrinkRide.Mote mote) {
        super(level, mote.cloud().x, mote.cloud().y, mote.cloud().z, sprite);
        this.glove = glove;
        this.mote = mote;
        this.crop = SpriteCrop.within(sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), random);
        this.layer = Layer.bySprite(sprite);
        this.gravity = 0f;
        this.hasPhysics = false;
        this.quadSize *= HALF;
        this.lifetime = (int) Math.ceil(mote.release() - level.getGameTime()) + TRAVELS * DrinkStream.LONGEST_TRAVEL_TICKS;
        shade(level, state, pos);
    }

    private void shade(ClientLevel level, BlockState state, BlockPos pos) {
        float red = SHADE;
        float green = SHADE;
        float blue = SHADE;
        BlockTintSource tint = Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
        if (tint != null) {
            int color = tint.colorAsTerrainParticle(state, level, pos);
            red *= ((color >> RED_SHIFT) & CHANNEL) / CHANNEL_MAX;
            green *= ((color >> GREEN_SHIFT) & CHANNEL) / CHANNEL_MAX;
            blue *= (color & CHANNEL) / CHANNEL_MAX;
        }
        setColor(red, green, blue);
    }

    /** Moves the particle to where it flies at the end of this tick, and ends it at the glove. */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.age++;
        if (this.age > this.lifetime) {
            remove();
            return;
        }
        Vec3 hand = seenGlove();
        if (hand == null) {
            return;
        }
        Vec3 at = DrinkRide.pointOf(mote, hand, level.getGameTime() + 1);
        if (at == null) {
            remove();
            return;
        }
        setPos(at.x, at.y, at.z);
    }

    /**
     * @return the drinker's glove this tick; or null while the drink goes unplaced, the particle let go once it
     *         has gone unplaced too long
     */
    private @Nullable Vec3 seenGlove() {
        Vec3 hand = glove.get();
        if (hand == null) {
            orphaned++;
            if (orphaned > ORPHAN_TICKS) {
                remove();
            }
            return null;
        }
        orphaned = 0;
        return hand;
    }

    /** Draws the particle where it flies this very frame, so it keeps to the line between ticks. */
    @Override
    protected void extractRotatedQuad(QuadParticleRenderState state, Camera camera, Quaternionf rotation,
                                      float partialTick) {
        Vec3 hand = glove.get();
        Vec3 at = hand == null ? null : DrinkRide.pointOf(mote, hand, level.getGameTime() + partialTick);
        if (at == null) {
            super.extractRotatedQuad(state, camera, rotation, partialTick);
            return;
        }
        Vec3 eye = camera.position();
        extractRotatedQuad(state, rotation, (float) (at.x - eye.x), (float) (at.y - eye.y), (float) (at.z - eye.z),
                partialTick);
    }

    @Override
    protected Layer getLayer() {
        return layer;
    }

    @Override
    protected float getU0() {
        return crop.u0();
    }

    @Override
    protected float getU1() {
        return crop.u1();
    }

    @Override
    protected float getV0() {
        return crop.v0();
    }

    @Override
    protected float getV1() {
        return crop.v1();
    }
}
