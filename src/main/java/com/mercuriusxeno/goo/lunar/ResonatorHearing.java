package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooPoiTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import java.util.List;

/**
 * How resonators hear, with no block entity to listen through: every vibration that goes off on the server is matched
 * against the resonators within a sculk sensor's hearing range of it, found through their point-of-interest type, and
 * each resting one the sound reaches unblocked takes it up to echo. Wool and the other blocks that stop a vibration
 * reaching a sensor stop it reaching a resonator.
 * decision resonator-echoes-a-vibration-as-a-sound-relay
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class ResonatorHearing {

    /** How far the line from a sound to a listener is nudged off the sound's block centre, as vanilla nudges it. */
    private static final float NUDGE = 1.0E-5F;
    private static final double BLOCK_CENTRE = 0.5;

    private ResonatorHearing() {
    }

    /**
     * Hands a vibration to every resting resonator in hearing range that it reaches unblocked.
     *
     * @param event the vanilla game event
     */
    @SubscribeEvent
    public static void onVanillaGameEvent(VanillaGameEvent event) {
        Holder<GameEvent> vibration = event.getVanillaEvent();
        if (!(event.getLevel() instanceof ServerLevel level) || !vibration.is(GameEventTags.VIBRATIONS)) {
            return;
        }
        Vec3 source = event.getEventPosition();
        List<BlockPos> resonators = level.getPoiManager().getInRange(type -> type.value() == GooPoiTypes.RESONATOR.get(),
                BlockPos.containing(source), ResonatorBlock.HEARING_RANGE, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos).toList();
        for (BlockPos pos : resonators) {
            BlockState state = level.getBlockState(pos);
            Vec3 listener = Vec3.atCenterOf(pos);
            if (state.getBlock() instanceof ResonatorBlock resonator && ResonatorBlock.hears(state)
                    && !isOccluded(level, source, listener)) {
                resonator.carry(level, pos, state, vibration, source.distanceTo(listener));
            }
        }
    }

    /**
     * Whether every line from a sound to a listener, the sound's centre nudged each way, runs through a block that
     * stops vibrations, as vanilla tests a sculk sensor's hearing.
     *
     * @param level  the level
     * @param origin where the sound went off
     * @param dest   the listener
     * @return whether the sound is blocked
     */
    static boolean isOccluded(Level level, Vec3 origin, Vec3 dest) {
        Vec3 from = centreOfBlockAt(origin);
        Vec3 to = centreOfBlockAt(dest);
        for (Direction direction : Direction.values()) {
            Vec3 nudged = from.relative(direction, NUDGE);
            if (level.isBlockInLine(new ClipBlockStateContext(nudged, to,
                    state -> state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS))).getType() != HitResult.Type.BLOCK) {
                return false;
            }
        }
        return true;
    }

    private static Vec3 centreOfBlockAt(Vec3 at) {
        return new Vec3(Mth.floor(at.x) + BLOCK_CENTRE, Mth.floor(at.y) + BLOCK_CENTRE, Mth.floor(at.z) + BLOCK_CENTRE);
    }
}
