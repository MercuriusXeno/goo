package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarCrossingShift;
import com.mercuriusxeno.goo.lunar.LunarCrossingViews;
import com.mercuriusxeno.goo.network.LunarCrossingMovedPayload;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.server.level.ChunkTrackingView;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.ArrayList;
import net.minecraft.core.QuartPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * Gametests for lunar's seam on the live server: the terrain lunar generates, its biomes and its surface rule's noise
 * read the same just past +7,500,000 as just past -7,500,000 on x and z, through the random state the server seeds
 * lunar with, while the overworld's noise repeats nowhere; a player by the seam is sent the other side's own chunk
 * for a chunk past it, and a block placed past the seam lands on the other side.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@RegistersGameTests
public final class LunarSeamTests {

    private static final String LUNAR_TERRAIN_REPEATS_ACROSS_THE_SEAM = "lunar_terrain_repeats_across_the_seam";
    private static final String LUNAR_VIEW_PAST_THE_SEAM_IS_THE_OTHER_SIDE = "lunar_view_past_the_seam_is_the_other_side";
    private static final String LUNAR_BLOCK_PLACED_ACROSS_THE_SEAM_LANDS_ON_THE_OTHER_SIDE =
            "lunar_block_placed_across_the_seam_lands_on_the_other_side";
    private static final int CHUNK_SHIFT = 4;
    private static final int SEAM_CHUNK = 7_500_000 >> CHUNK_SHIFT;
    /** A chunk a step past the east seam, and the chunk a step in from the west seam it wraps to. */
    private static final ChunkPos PAST_THE_EAST_SEAM = new ChunkPos(SEAM_CHUNK + 1, 2);
    private static final ChunkPos IN_FROM_THE_WEST_SEAM = new ChunkPos(-SEAM_CHUNK + 1, 2);
    /** Where a block is placed in the chunk in from the west seam, high in the open sky. */
    private static final BlockPos PLACED_IN_FROM_THE_WEST_SEAM = new BlockPos(-7_500_000 + 16 + 5, 200, 32 + 9);
    private static final double HALF_BLOCK = 0.5;
    /** The most chunk batches a client may owe acknowledgements for, and the most chunks a tick it may ask for. */
    private static final int UNACKNOWLEDGED_BATCHES = 10;
    private static final float CHUNKS_PER_TICK = 64.0F;
    /** A chunk is sent only once it ticks, so the test holds it ticking and sends once it is. */
    private static final int TICKING_RADIUS = 2;
    private static final String SENT_NO_CHUNK = "The server should send a player standing by the seam the chunk %s,"
            + " sends none";
    private static final String NOT_READY = "The chunk %s past the seam should stand ready to send";
    private static final String SENT_OWN_CHUNK = "The chunk sent for %s should hold the blocks of %s, the block placed"
            + " at %s included, holds others";
    private static final String PLACED_ON_THE_OTHER_SIDE = "A block placed at %s from %s should stand at %s on the"
            + " other side, reads %s there";
    private static final String NOT_PLACED_PAST_THE_SEAM = "A block placed at %s should land on the other side, not"
            + " past the seam, reads %s past it";
    private static final String LUNAR_CROSSING_KEEPS_THE_VIEW_THE_CLIENT_HOLDS =
            "lunar_crossing_keeps_the_view_the_client_holds";
    private static final String LUNAR_CROSSING_RESENDS_THE_VIEW_A_CLIENT_DID_NOT_MOVE =
            "lunar_crossing_resends_the_view_a_client_did_not_move";
    private static final String LUNAR_CROSSING_WAITS_ON_NO_GENERATION = "lunar_crossing_waits_on_no_generation";
    /** Each crossing test stands on its own row of chunks, clear of every other test's tickets. */
    private static final int KEEPS_THE_VIEW_CHUNK_Z = 100;
    private static final int RESENDS_THE_VIEW_CHUNK_Z = 200;
    private static final int WAITS_ON_NO_GENERATION_CHUNK_Z = 300;
    /**
     * Ticks a mock client waits to receive its whole view, and ticks it waits after crossing for what follows, both
     * well inside a portal ticket's lapse, so the chunks it holds keep ticking throughout.
     */
    private static final int RECEIVE_TICKS = 200;
    private static final int SETTLE_TICKS = 40;
    /** A ticket ticks a chunk one short of its radius, and a view holds chunks three out, so five ticks them all. */
    private static final int CROSSING_TICKET_RADIUS = 5;
    private static final double CROSSING_HEIGHT = 200.0;
    private static final double CHUNK_MIDDLE = 8.5;
    private static final String NOTHING_RECEIVED = "The mock client should receive its view before it crosses";
    private static final String FORGOT_MOVED = "The crossing should tell the client to forget no chunk it moved,"
            + " forgets %s: %s";
    private static final String RESENT_MOVED = "The crossing should resend no chunk the client moved, resends %s: %s";
    private static final String LANDING_UNSENT = "A client that moved nothing should be sent its whole landing view,"
            + " %s go unsent: %s";
    private static final String LANDING_UNLOADED = "A player by the seam should hold its whole landing view loaded,"
            + " %s stand unloaded: %s";
    private static final int SEAM = 7_500_000;
    private static final int[] PAST_THE_SEAM = {0, 1, 7, 40, 333, 641, 1_919};
    private static final int[] ACROSS = {0, 2_345, -SEAM + 3};
    private static final int[] HEIGHTS = {-110, -40, 0, 30, 60, 120};

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NOISE_GENERATOR = "goo:lunar should generate from noise settings, generates from %s";
    private static final String TERRAIN = "Lunar's terrain should read %s at %s, %s, %s as at %s, %s, %s, reads %s";
    private static final String BIOME = "Lunar's biome should read %s at quart %s, %s as at quart %s, %s, reads %s";
    private static final String SURFACE_NOISE = "Lunar's surface noise should read %s at %s, %s as at %s, %s, reads %s";
    private static final String OVERWORLD_REPEATS = "The overworld's surface noise should read apart just past either"
            + " lunar edge, reads the same at every step past them";

    private LunarSeamTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_TERRAIN_REPEATS_ACROSS_THE_SEAM,
                LunarSeamTests::lunarTerrainRepeatsAcrossTheSeam);
        GooTestFunctions.registerTest(registrar, LUNAR_VIEW_PAST_THE_SEAM_IS_THE_OTHER_SIDE,
                LunarSeamTests::lunarViewPastTheSeamIsTheOtherSide);
        GooTestFunctions.registerTest(registrar, LUNAR_BLOCK_PLACED_ACROSS_THE_SEAM_LANDS_ON_THE_OTHER_SIDE,
                LunarSeamTests::lunarBlockPlacedAcrossTheSeamLandsOnTheOtherSide);
        GooTestFunctions.registerTest(registrar, LUNAR_CROSSING_KEEPS_THE_VIEW_THE_CLIENT_HOLDS,
                LunarSeamTests::lunarCrossingKeepsTheViewTheClientHolds);
        GooTestFunctions.registerTest(registrar, LUNAR_CROSSING_RESENDS_THE_VIEW_A_CLIENT_DID_NOT_MOVE,
                LunarSeamTests::lunarCrossingResendsTheViewAClientDidNotMove);
        GooTestFunctions.registerTest(registrar, LUNAR_CROSSING_WAITS_ON_NO_GENERATION,
                LunarSeamTests::lunarCrossingWaitsOnNoGeneration);
    }

    /**
     * A mock client by the east seam receives its whole view, is carried across, confirms it moved what it held by
     * the crossing's shift, and accepts the teleport; the server then tells it to forget no chunk it moved and sends
     * none of the landing view's chunks it moved there.
     *
     * @param helper the gametest helper
     */
    public static void lunarCrossingKeepsTheViewTheClientHolds(GameTestHelper helper) {
        CrossingClient client = CrossingClient.standingBy(helper, KEEPS_THE_VIEW_CHUNK_Z);
        helper.runAfterDelay(RECEIVE_TICKS, () -> client.cross(true));
        helper.runAfterDelay(RECEIVE_TICKS + SETTLE_TICKS, () -> {
            client.leave();
            helper.assertTrue(client.received > 0, NOTHING_RECEIVED);
            List<ChunkPos> forgotten = client.forgottenAfter.stream()
                    .filter(pos -> client.landing.contains(client.shift.apply(pos))).toList();
            helper.assertTrue(forgotten.isEmpty(), String.format(FORGOT_MOVED, forgotten.size(), forgotten));
            List<ChunkPos> resent = client.sentAfter.stream()
                    .filter(pos -> client.held.contains(client.shift.undo(pos))).toList();
            helper.assertTrue(resent.isEmpty(), String.format(RESENT_MOVED, resent.size(), resent));
            helper.succeed();
        });
    }

    /**
     * A mock client by the east seam receives its whole view and is carried across without confirming a move; the
     * server sends it every chunk of the landing view, as a crossing did before the client could move its chunks.
     *
     * @param helper the gametest helper
     */
    public static void lunarCrossingResendsTheViewAClientDidNotMove(GameTestHelper helper) {
        CrossingClient client = CrossingClient.standingBy(helper, RESENDS_THE_VIEW_CHUNK_Z);
        helper.runAfterDelay(RECEIVE_TICKS, () -> client.cross(false));
        helper.runAfterDelay(RECEIVE_TICKS + SETTLE_TICKS, () -> {
            client.leave();
            helper.assertTrue(client.received > 0, NOTHING_RECEIVED);
            List<ChunkPos> unsent = new ArrayList<>();
            client.landing.forEach(pos -> {
                if (!client.sentAfter.contains(pos)) {
                    unsent.add(pos);
                }
            });
            helper.assertTrue(unsent.isEmpty(), String.format(LANDING_UNSENT, unsent.size(), unsent));
            helper.succeed();
        });
    }

    /**
     * A mock player standing by the east seam for one hold cycle holds every chunk of the view it would land in
     * loaded at full status, past the far edge included, before any crossing carries it there.
     *
     * @param helper the gametest helper
     */
    public static void lunarCrossingWaitsOnNoGeneration(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        ServerPlayer player = standingIn(helper, lunar, standingSpot(WAITS_ON_NO_GENERATION_CHUNK_Z));
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        ChunkTrackingView landing = landingViewOf(player, WAITS_ON_NO_GENERATION_CHUNK_Z);
        helper.succeedWhen(() -> {
            List<ChunkPos> unloaded = new ArrayList<>();
            landing.forEach(pos -> {
                if (lunar.getChunkSource().getChunkNow(pos.x(), pos.z()) == null) {
                    unloaded.add(pos);
                }
            });
            helper.assertTrue(unloaded.isEmpty(), String.format(LANDING_UNLOADED, unloaded.size(), unloaded));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    private static Vec3 standingSpot(int chunkZ) {
        return new Vec3(SEAM - HALF_BLOCK, CROSSING_HEIGHT, (chunkZ << CHUNK_SHIFT) + CHUNK_MIDDLE);
    }

    private static ChunkTrackingView landingViewOf(ServerPlayer player, int chunkZ) {
        int viewDistance = player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view
                ? view.viewDistance() : 2;
        return ChunkTrackingView.of(new ChunkPos(-SEAM_CHUNK, chunkZ), viewDistance);
    }

    /**
     * A mock client standing just inside the east seam: it takes every chunk the server sends it each tick, as a
     * client acknowledging each batch does, and keeps what the server sent and forgot after its crossing.
     */
    private static final class CrossingClient {

        private final GameTestHelper helper;
        private final ServerLevel lunar;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final List<ChunkPos> sentAfter = new ArrayList<>();
        private final List<ChunkPos> forgottenAfter = new ArrayList<>();
        private int received;
        private boolean crossed;
        private ChunkTrackingView held = ChunkTrackingView.EMPTY;
        private ChunkTrackingView landing = ChunkTrackingView.EMPTY;
        private LunarCrossingShift shift = new LunarCrossingShift(0, 0);

        private CrossingClient(GameTestHelper helper, ServerLevel lunar, ServerPlayer player) {
            this.helper = helper;
            this.lunar = lunar;
            this.player = player;
            this.channel = (EmbeddedChannel) player.connection.getConnection().channel();
        }

        /** Stands a client by the seam with both sides of its crossing loaded and ticking, so every chunk sends. */
        static CrossingClient standingBy(GameTestHelper helper, int chunkZ) {
            ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
            helper.assertTrue(lunar != null, LUNAR_REQUIRED);
            for (ChunkPos centre : List.of(new ChunkPos(SEAM_CHUNK - 1, chunkZ), new ChunkPos(-SEAM_CHUNK, chunkZ))) {
                for (int dx = -CROSSING_TICKET_RADIUS; dx <= CROSSING_TICKET_RADIUS; dx++) {
                    for (int dz = -CROSSING_TICKET_RADIUS; dz <= CROSSING_TICKET_RADIUS; dz++) {
                        lunar.getChunk(centre.x() + dx, centre.z() + dz);
                    }
                }
                lunar.getChunkSource().addTicketWithRadius(TicketType.PORTAL, centre, CROSSING_TICKET_RADIUS);
            }
            CrossingClient client = new CrossingClient(helper, lunar, standingIn(helper, lunar,
                    standingSpot(chunkZ)));
            helper.onEachTick(client::takeWhatIsSent);
            return client;
        }

        private void takeWhatIsSent() {
            acknowledgeEveryBatch(player);
            player.connection.chunkSender.sendNextChunks(player);
            player.connection.getConnection().flushChannel();
            for (Object sent = channel.readOutbound(); sent != null; sent = channel.readOutbound()) {
                Iterable<?> packets = sent instanceof BundlePacket<?> bundle ? bundle.subPackets() : List.of(sent);
                for (Object packet : packets) {
                    take(packet);
                }
            }
        }

        private void take(Object packet) {
            if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
                received++;
                if (crossed) {
                    sentAfter.add(new ChunkPos(chunk.x(), chunk.z()));
                }
            } else if (packet instanceof ClientboundForgetLevelChunkPacket forget && crossed) {
                forgottenAfter.add(forget.pos());
            }
        }

        /** Carries the client across the seam, confirms its move where asked, and accepts the teleport. */
        void cross(boolean confirmsTheMove) {
            takeWhatIsSent();
            held = player.getChunkTrackingView();
            List<ChunkPos> heldChunks = new ArrayList<>();
            held.forEach(heldChunks::add);
            Vec3 stood = player.position();
            player.setPos(stood.x + 1.0, stood.y, stood.z);
            crossed = true;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            shift = LunarCrossingShift.between(stood, player.position());
            if (confirmsTheMove) {
                LunarCrossingViews.confirmed(player, LunarCrossingMovedPayload.of(shift,
                        heldChunks.stream().map(shift::apply).toList()));
            }
            lunar.getChunkSource().move(player);
            landing = player.getChunkTrackingView();
            takeWhatIsSent();
        }

        void leave() {
            takeWhatIsSent();
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    /**
     * A player standing by the east seam is sent, for a chunk a step past it, the chunk a step in from the west seam,
     * a block placed there included, labelled where they see it.
     *
     * @param helper the gametest helper
     */
    public static void lunarViewPastTheSeamIsTheOtherSide(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        LevelChunk otherSide = lunar.getChunk(IN_FROM_THE_WEST_SEAM.x(), IN_FROM_THE_WEST_SEAM.z());
        lunar.setBlock(PLACED_IN_FROM_THE_WEST_SEAM, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        for (int dx = -TICKING_RADIUS; dx <= TICKING_RADIUS; dx++) {
            for (int dz = -TICKING_RADIUS; dz <= TICKING_RADIUS; dz++) {
                lunar.getChunk(PAST_THE_EAST_SEAM.x() + dx, PAST_THE_EAST_SEAM.z() + dz);
            }
        }
        lunar.getChunkSource().addTicketWithRadius(TicketType.PORTAL, PAST_THE_EAST_SEAM, TICKING_RADIUS);
        ServerPlayer player = standingIn(helper, lunar, new Vec3(SEAM - 10.5, 180.0, 40.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(lunar.getChunkSource().chunkMap.getChunkToSend(PAST_THE_EAST_SEAM.pack()) != null,
                    String.format(NOT_READY, PAST_THE_EAST_SEAM));
            LevelChunk seen = lunar.getChunk(PAST_THE_EAST_SEAM.x(), PAST_THE_EAST_SEAM.z());
            EmbeddedChannel channel = (EmbeddedChannel) player.connection.getConnection().channel();
            player.connection.getConnection().flushChannel();
            drain(channel);
            acknowledgeEveryBatch(player);
            player.connection.chunkSender.markChunkPendingToSend(seen);
            player.connection.chunkSender.sendNextChunks(player);
            player.connection.getConnection().flushChannel();
            ClientboundLevelChunkWithLightPacket sent = sentChunk(channel, PAST_THE_EAST_SEAM);
            helper.assertTrue(sent != null, String.format(SENT_NO_CHUNK, PAST_THE_EAST_SEAM));
            boolean holdsTheOtherSide = Arrays.equals(blocksOf(sent.chunkData()),
                    blocksOf(new ClientboundLevelChunkPacketData(otherSide)));
            helper.assertTrue(holdsTheOtherSide, String.format(SENT_OWN_CHUNK, PAST_THE_EAST_SEAM,
                    IN_FROM_THE_WEST_SEAM, PLACED_IN_FROM_THE_WEST_SEAM));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A block a player standing at x 7,499,999 places at x 7,500,002 stands at x -7,499,998, on the real chunk at the
     * other edge, and not past the seam.
     *
     * @param helper the gametest helper
     */
    public static void lunarBlockPlacedAcrossTheSeamLandsOnTheOtherSide(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        int otherSideX = -SEAM + 2;
        int ground = lunar.getChunk(otherSideX >> CHUNK_SHIFT, 0).getHeight(Heightmap.Types.MOTION_BLOCKING,
                otherSideX, 0);
        lunar.getChunk((SEAM + 2) >> CHUNK_SHIFT, 0);
        BlockPos target = new BlockPos(SEAM + 2, ground + 1, 0);
        BlockPos landing = new BlockPos(otherSideX, ground + 1, 0);
        Vec3 stand = new Vec3(SEAM - 0.5, ground + 1.0, 0.5);
        ServerPlayer player = standingIn(helper, lunar, stand);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_BLOCK));
        BlockHitResult onTheGround = new BlockHitResult(Vec3.atCenterOf(target.below()).add(0.0, HALF_BLOCK, 0.0),
                Direction.UP, target.below(), false);
        player.gameMode.useItemOn(player, lunar, player.getMainHandItem(), InteractionHand.MAIN_HAND, onTheGround);
        BlockState landed = lunar.getChunk(landing.getX() >> CHUNK_SHIFT, 0).getBlockState(landing);
        BlockState pastTheSeam = lunar.getChunk(target.getX() >> CHUNK_SHIFT, 0).getBlockState(target);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(landed.is(Blocks.DIAMOND_BLOCK),
                String.format(PLACED_ON_THE_OTHER_SIDE, target, stand, landing, landed));
        helper.assertFalse(pastTheSeam.is(Blocks.DIAMOND_BLOCK),
                String.format(NOT_PLACED_PAST_THE_SEAM, target, pastTheSeam));
        helper.succeed();
    }

    private static ServerPlayer standingIn(GameTestHelper helper, ServerLevel lunar, Vec3 stand) {
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        player.teleportTo(lunar, stand.x, stand.y, stand.z, Set.of(), 0.0F, 0.0F, false);
        return player;
    }

    /** A mock player's client acknowledges no chunk batch, so the test acknowledges every one a client could owe. */
    private static void acknowledgeEveryBatch(ServerPlayer player) {
        for (int batch = 0; batch < UNACKNOWLEDGED_BATCHES; batch++) {
            player.connection.chunkSender.onChunkBatchReceivedByClient(CHUNKS_PER_TICK);
        }
    }

    private static void drain(EmbeddedChannel channel) {
        Object sent = channel.readOutbound();
        while (sent != null) {
            sent = channel.readOutbound();
        }
    }

    /** The server holds its writes back while it ticks, so the test flushes them before it reads what was sent. */
    private static @Nullable ClientboundLevelChunkWithLightPacket sentChunk(EmbeddedChannel channel, ChunkPos pos) {
        for (Object sent = channel.readOutbound(); sent != null; sent = channel.readOutbound()) {
            Iterable<?> packets = sent instanceof BundlePacket<?> bundle ? bundle.subPackets() : List.of(sent);
            for (Object packet : packets) {
                if (packet instanceof ClientboundLevelChunkWithLightPacket chunk && chunk.x() == pos.x()
                        && chunk.z() == pos.z()) {
                    return chunk;
                }
            }
        }
        return null;
    }

    private static byte[] blocksOf(ClientboundLevelChunkPacketData data) {
        FriendlyByteBuf buffer = data.getReadBuffer();
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    /**
     * Lunar's final density, its biome and its surface noise read the same at each step past the east edge as at
     * that step past the west edge, and likewise past the south edge and the north; the overworld's surface noise
     * reads apart there.
     *
     * @param helper the gametest helper
     */
    public static void lunarTerrainRepeatsAcrossTheSeam(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        ChunkGenerator generator = lunar.getChunkSource().getGenerator();
        helper.assertTrue(generator instanceof NoiseBasedChunkGenerator,
                String.format(NOISE_GENERATOR, generator.getClass().getSimpleName()));
        NoiseRouter router = ((NoiseBasedChunkGenerator) generator).generatorSettings().value().noiseRouter();
        RandomState randomState = lunar.getChunkSource().randomState();
        BiomeResolver biomes = generator.getBiomeSource().createUncachedResolver(randomState);
        Noise surface = randomState.getOrCreateNoise(Noises.SURFACE);
        for (int past : PAST_THE_SEAM) {
            for (int across : ACROSS) {
                for (int y : HEIGHTS) {
                    assertTerrainRepeats(helper, randomState, router, -SEAM + past, y, across, SEAM + past, y, across);
                    assertTerrainRepeats(helper, randomState, router, across, y, -SEAM + past, across, y, SEAM + past);
                }
                assertBiomeRepeats(helper, biomes, QuartPos.fromBlock(-SEAM + past),
                        QuartPos.fromBlock(across), QuartPos.fromBlock(SEAM + past), QuartPos.fromBlock(across));
                assertSurfaceNoiseRepeats(helper, surface, -SEAM + past, across, SEAM + past, across);
                assertSurfaceNoiseRepeats(helper, surface, across, -SEAM + past, across, SEAM + past);
            }
        }
        helper.assertTrue(overworldSurfaceReadsApartPastTheSeam(helper.getLevel().getServer().getLevel(Level.OVERWORLD)),
                OVERWORLD_REPEATS);
        helper.succeed();
    }

    private static void assertTerrainRepeats(GameTestHelper helper, RandomState randomState, NoiseRouter router,
                                             int westX, int westY, int westZ, int eastX, int eastY, int eastZ) {
        float west = randomState.sampleBlockValueUncached(router.finalDensity(), westX, westY, westZ);
        float east = randomState.sampleBlockValueUncached(router.finalDensity(), eastX, eastY, eastZ);
        helper.assertTrue(west == east,
                String.format(TERRAIN, west, eastX, eastY, eastZ, westX, westY, westZ, east));
    }

    private static void assertBiomeRepeats(GameTestHelper helper, BiomeResolver biomes, int westQuartX, int quartZ,
                                           int eastQuartX, int eastQuartZ) {
        int quartY = QuartPos.fromBlock(0);
        Holder<Biome> west = biomes.getNoiseBiome(westQuartX, quartY, quartZ);
        Holder<Biome> east = biomes.getNoiseBiome(eastQuartX, quartY, eastQuartZ);
        helper.assertTrue(west.equals(east), String.format(BIOME, west.getRegisteredName(), eastQuartX, eastQuartZ,
                westQuartX, quartZ, east.getRegisteredName()));
    }

    private static void assertSurfaceNoiseRepeats(GameTestHelper helper, Noise surface, int westX, int westZ,
                                                  int eastX, int eastZ) {
        float west = surface.get(westX, 0.0, westZ);
        float east = surface.get(eastX, 0.0, eastZ);
        helper.assertTrue(west == east, String.format(SURFACE_NOISE, west, eastX, eastZ, westX, westZ, east));
    }

    private static boolean overworldSurfaceReadsApartPastTheSeam(ServerLevel overworld) {
        Noise surface = overworld.getChunkSource().randomState().getOrCreateNoise(Noises.SURFACE);
        for (int past : PAST_THE_SEAM) {
            if (surface.get(-SEAM + past, 0.0, 0.0) != surface.get(SEAM + past, 0.0, 0.0)) {
                return true;
            }
        }
        return false;
    }
}
