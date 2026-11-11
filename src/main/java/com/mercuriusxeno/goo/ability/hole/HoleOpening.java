package com.mercuriusxeno.goo.ability.hole;

import com.mercuriusxeno.goo.ability.pocket.PocketEntry;
import com.mercuriusxeno.goo.ability.pocket.PocketShelves;
import com.mercuriusxeno.goo.item.GooDeposit;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.item.PlayerUtils;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Opens a player's portable hole on a struck face: on a top face it stands to
 * swallow what is thrown in, and on a bottom face it pours the owner's whole
 * shelf out beneath it. A face of a wall takes no hole.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
public final class HoleOpening {

    /** How far below a ceiling's face the poured items appear, an item's height clear of it. */
    private static final double POUR_DROP = 0.75;
    /** How far off the struck face a refused hole's sound plays. */
    private static final double FACE_LIFT = 0.55;
    private static final float SOUND_VOLUME = 1.0F;
    private static final float REFUSED_PITCH = 0.6F;

    private HoleOpening() {
    }

    /**
     * Whether a face takes a hole: a block's top face or its bottom face.
     *
     * @param face the struck face
     * @return true for a floor's or a ceiling's face
     */
    public static boolean takes(Direction face) {
        return face.getAxis() == Direction.Axis.Y;
    }

    /**
     * Opens the owner's hole on a face, paying back the old Pocket's reserve
     * the first time, and pours the shelf out when the face is a ceiling's.
     *
     * @param level    the level
     * @param owner    the player whose hole it is
     * @param block    the struck block
     * @param face     the struck face, up or down
     * @param lifetime the ticks the hole stands
     * @param refresh  the ticks left each swallow raises the hole's time left to when less remains
     */
    public static void open(ServerLevel level, ServerPlayer owner, BlockPos block, Direction face, int lifetime,
                            int refresh) {
        PocketShelves shelves = PocketShelves.get(level);
        payBack(owner, shelves.settleReserve(owner.getUUID()));
        PortableHole hole = new PortableHole(owner.getUUID(), level.dimension(), block, face,
                HoleFootprint.of(level, block, face), level.getGameTime(), level.getGameTime() + lifetime, refresh);
        if (!hole.swallows()) {
            pour(level, hole, shelves);
        }
        // stood after the pour, so the clients are told the emptied shelf
        PortableHoles.stand(level.getServer(), hole);
        Vec3 middle = hole.mouth().getCenter();
        level.playSound(null, middle.x(), middle.y(), middle.z(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS,
                SOUND_VOLUME, 1.0F);
    }

    /**
     * Shows a hole cast at a face that takes none: a dull drop of sound.
     *
     * @param level the level
     * @param block the struck block
     * @param face  the struck face
     */
    public static void refuse(ServerLevel level, BlockPos block, Direction face) {
        Vec3 middle = Vec3.atCenterOf(block).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(FACE_LIFT));
        level.playSound(null, middle.x(), middle.y(), middle.z(), SoundEvents.CONDUIT_DEACTIVATE, SoundSource.BLOCKS,
                SOUND_VOLUME, REFUSED_PITCH);
    }

    private static void pour(ServerLevel level, PortableHole hole, PocketShelves shelves) {
        // decision hole-mouth-opens-three-by-three-and-fits-its-room: the pour drops from the whole mouth's middle
        Vec3 middle = hole.blocks().getBottomCenter();
        Vec3 below = middle.subtract(0, POUR_DROP, 0);
        for (PocketEntry entry : shelves.takeAll(hole.owner())) {
            int perStack = Math.max(1, entry.kind().getMaxStackSize());
            for (long left = entry.count(); left > 0; left -= perStack) {
                ItemStack poured = entry.kind().copyWithCount((int) Math.min(left, perStack));
                ItemEntity item = new ItemEntity(level, below.x(), below.y(), below.z(), poured);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
        }
    }

    private static void payBack(ServerPlayer owner, long owed) {
        if (owed <= 0) {
            return;
        }
        int amount = (int) Math.min(owed, Integer.MAX_VALUE);
        int homeless = GooDeposit.intoInventory(owner, ItemStack.EMPTY).deposit(GooTypes.VOID, amount);
        if (homeless > 0) {
            PlayerUtils.addOrDrop(owner, GooStacks.createForOutput(GooTypes.VOID, homeless));
        }
    }
}
