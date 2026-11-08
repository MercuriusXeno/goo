package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mercuriusxeno.goo.entity.HunkShatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a struck Terrakinesis hunk coming apart as Unmake peels a block: each
 * block, on its turn (HunkShatter), breaks into chunks of its own faces that
 * crumble off its outside one by one in random order, each bursting into the
 * block's break particles as it goes, with the block's own break sound, so the
 * hunk breaks up piece by piece where it struck instead of vanishing at once.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 * decision unmake-peels-the-block-into-chunks-that-stream-to-the-hand
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HunkCrumbles {

    /** The drinker a crumble's peel names: no glove is ever placed for it, so each chunk ends at its turn. */
    private static final int NO_GLOVE = -1;
    /** Break particles each chunk bursts into as it crumbles off. */
    private static final int PARTICLES_PER_CHUNK = 4;
    /** How fast a chunk's particles scatter, in blocks a tick. */
    private static final double SCATTER_SPEED = 0.12;
    private static final double HALF = 0.5;
    private static final float HALF_F = 0.5f;
    /** How loud each chunk's crumble plays, beside the block's own volume. */
    private static final float CRUMBLE_VOLUME = 0.7f;
    /** The lowest a chunk's crumble is pitched, before its random spread. */
    private static final float CRUMBLE_PITCH_LOW = 0.75f;
    /** Vanilla plays a block's break sound a little low. */
    private static final float BREAK_PITCH = 0.8f;

    private static final List<Crumble> CRUMBLES = new ArrayList<>();

    private HunkCrumbles() {
    }

    /**
     * One block of a struck hunk crumbling.
     *
     * @param state   the block
     * @param cell    the cell it crumbles in
     * @param breakup how it breaks into chunks, each with its turn
     * @param start   the game time it begins to crumble
     * @param burst   by chunk index, whether that chunk has burst into particles
     * @param sounded whether its break sound has played, in its first slot
     */
    private record Crumble(BlockState state, BlockPos cell, DrinkRide.Breakup breakup, long start, boolean[] burst,
                           boolean[] sounded) {
    }

    /**
     * Begins a struck hunk's crumble where the client last drew it.
     *
     * @param hunk the hunk, just struck
     */
    public static void begin(BlockHunk hunk) {
        if (!(hunk.level() instanceof ClientLevel level)) {
            return;
        }
        Vec3 at = hunk.getInterpolation().hasActiveInterpolation() ? hunk.getInterpolation().position()
                : hunk.position();
        List<BlockHunk.HunkBlock> blocks = hunk.blocks();
        Direction face = hunk.shatterFace();
        int[] turns = HunkShatter.turnsOf(blocks, face);
        for (int index = 0; index < blocks.size(); index++) {
            BlockHunk.HunkBlock block = blocks.get(index);
            long start = HunkShatter.crumbleStart(hunk.shatterStart(), turns[index]);
            long end = HunkShatter.crumbleEnd(hunk.shatterStart(), turns[index]);
            CRUMBLES.add(peel(level, BlockHunk.cornerOf(at, block), block.state(), start, end));
        }
    }

    private static Crumble peel(ClientLevel level, Vec3 corner, BlockState state, long start, long end) {
        BlockPos cell = BlockPos.containing(corner.add(HALF, HALF, HALF));
        AABB outline = new AABB(corner, corner.add(1, 1, 1)).move(Vec3.atLowerCornerOf(cell).reverse());
        DrinkRide.Breakup breakup = DrinkRide.chunksOf(
                new DrinkTree.Block(cell, Vec3.atCenterOf(cell), 1, start, start, end), outline, level.getRandom());
        DrinkPeels.CLIENT.begin(new DrinkPeels.Peel(NO_GLOVE, cell, breakup, PeelFaces.of(level, cell, state),
                end + 1.0));
        return new Crumble(state, cell, breakup, start, new boolean[breakup.chunks().size()], new boolean[1]);
    }

    /**
     * Plays each crumbling block's break sound as its turn comes and bursts
     * each chunk into particles as it crumbles off.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            CRUMBLES.clear();
            return;
        }
        long now = level.getGameTime();
        CRUMBLES.removeIf(crumble -> crumbled(level, crumble, now));
    }

    /**
     * Advances one block's crumble to now.
     *
     * @param level   the client level
     * @param crumble the crumbling block
     * @param now     the game time
     * @return true once every chunk has burst
     */
    private static boolean crumbled(ClientLevel level, Crumble crumble, long now) {
        if (now < crumble.start()) {
            return false;
        }
        if (!crumble.sounded()[0]) {
            crumble.sounded()[0] = true;
            breakSound(level, crumble.state(), crumble.cell());
        }
        List<DrinkRide.Chunk> chunks = crumble.breakup().chunks();
        boolean done = true;
        for (int index = 0; index < chunks.size(); index++) {
            if (crumble.burst()[index]) {
                continue;
            }
            if (DrinkRide.stands(chunks.get(index), now)) {
                done = false;
                continue;
            }
            crumble.burst()[index] = true;
            burst(level, crumble.state(), chunks.get(index).place());
            crumbleSound(level, crumble.state(), crumble.cell());
        }
        return done;
    }

    private static void burst(ClientLevel level, BlockState state, Vec3 at) {
        RandomSource random = level.getRandom();
        BlockParticleOption chip = new BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int index = 0; index < PARTICLES_PER_CHUNK; index++) {
            level.addParticle(chip, at.x, at.y, at.z, scatter(random), scatter(random) + SCATTER_SPEED,
                    scatter(random));
        }
    }

    private static void breakSound(ClientLevel level, BlockState state, BlockPos cell) {
        SoundType sound = state.getSoundType(level, cell, null);
        level.playLocalSound(cell, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) * HALF_F,
                sound.getPitch() * BREAK_PITCH, false);
    }

    /**
     * The crumble of one chunk breaking off: its block's own hit sound, quiet
     * and a little low, so the hunk grinds apart as its chunks go.
     *
     * @param level the client level
     * @param state the block
     * @param cell  where it crumbles
     */
    private static void crumbleSound(ClientLevel level, BlockState state, BlockPos cell) {
        SoundType sound = state.getSoundType(level, cell, null);
        level.playLocalSound(cell, sound.getHitSound(), SoundSource.BLOCKS, sound.getVolume() * CRUMBLE_VOLUME,
                sound.getPitch() * BREAK_PITCH * (CRUMBLE_PITCH_LOW + level.getRandom().nextFloat() * HALF_F), false);
    }

    private static double scatter(RandomSource random) {
        return (random.nextDouble() - HALF) * SCATTER_SPEED;
    }
}
