package com.mercuriusxeno.goo.block.alientech;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.NonNull;

/**
 * The oculus, the stable teleporter Blink snaps to: a small base that connects to a solid face, an ender eye
 * hovering just off it on the open side, which its block entity's renderer draws, and now and then a pitched-up
 * enderman sound. It is a block the player places; no blink makes one.
 * decision oculus-prism-becomes-a-hovering-eye
 * decision blink-at-a-prism-spends-into-an-oculus
 * decision all-utilities-are-redesigned-as-alien-tech-blocks
 */
public class OculusBlock extends FaceMountedBlock {

    /** How far off the face the eye's middle hovers, in model pixels. */
    public static final double EYE_LIFT = 10.5;
    /** The obsidian cradle's width and its height off the face to its prongs' tips, in pixels. */
    private static final float BASE_WIDTH = 8f;
    private static final float BASE_HEIGHT = 5f;
    /** The eye's edge, in pixels, and half of it, from the eye's middle to a face. */
    private static final float EYE_WIDTH = 7f;
    private static final float EYE_HALF_WIDTH = 3.5f;
    private static final float CELL = 16f;
    /** One display tick in this many sounds the eye. */
    private static final int SOUND_CHANCE = 100;
    private static final float SOUND_VOLUME = 0.3f;
    /** The enderman's voice raised by half again, and up to a little more. */
    private static final float SOUND_PITCH = 1.5f;
    private static final float SOUND_PITCH_SPREAD = 0.3f;
    private static final double HALF = 0.5;

    /**
     * Creates the block, pointing up off a floor.
     *
     * @param properties the block properties
     */
    public OculusBlock(Properties properties) {
        super(properties, Shapes.or(Block.boxZ(BASE_WIDTH, CELL - BASE_HEIGHT, CELL),
                Block.boxZ(EYE_WIDTH, CELL - (float) EYE_LIFT - EYE_HALF_WIDTH,
                        CELL - (float) EYE_LIFT + EYE_HALF_WIDTH)));
    }

    /**
     * Now and then sounds the eye: an enderman's ambient voice pitched up, heard on this client alone.
     */
    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                            @NonNull RandomSource random) {
        if (random.nextInt(SOUND_CHANCE) == 0) {
            level.playLocalSound(pos.getX() + HALF, pos.getY() + HALF, pos.getZ() + HALF, SoundEvents.ENDERMAN_AMBIENT,
                    SoundSource.BLOCKS, SOUND_VOLUME, SOUND_PITCH + random.nextFloat() * SOUND_PITCH_SPREAD, false);
        }
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new OculusBlockEntity(pos, state);
    }
}
