package com.p1nero.cataclysm_dimension;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class StructureResetManager {
    private static final int CHUNKS_PER_TICK = 2;
    private static final int MAX_CHUNKS = 4096;
    private static final TicketType<ChunkPos> RESET_TICKET = TicketType.create(
            "cataclysm_dimension_reset", Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<ResourceLocation, Job> JOBS = new LinkedHashMap<>();
    private static Job placing;

    private StructureResetManager() {
    }

    public static boolean isBusy(ResourceLocation dimension) {
        return JOBS.containsKey(dimension);
    }

    public static boolean begin(ServerLevel level, BlockPos origin, Consumer<Boolean> completion) {
        if (isBusy(level.dimension().location())) {
            return false;
        }
        try {
            Set<StructureStart> starts = new LinkedHashSet<>();
            int centerX = origin.getX() >> 4;
            int centerZ = origin.getZ() >> 4;
            for (int offsetX = -4; offsetX <= 4; offsetX++) {
                for (int offsetZ = -4; offsetZ <= 4; offsetZ++) {
                    level.getChunk(centerX + offsetX, centerZ + offsetZ);
                    starts.addAll(level.structureManager().startsForStructure(
                            new ChunkPos(centerX + offsetX, centerZ + offsetZ), structure -> true));
                }
            }
            List<StructureStart> copies = new ArrayList<>();
            var context = StructurePieceSerializationContext.fromLevel(level);
            for (StructureStart start : starts) {
                if (!start.isValid()) {
                    continue;
                }
                CompoundTag tag = start.createTag(context, start.getChunkPos());
                for (Tag child : tag.getList("Children", Tag.TAG_COMPOUND)) {
                    CompoundTag piece = (CompoundTag) child;
                    if (!piece.getString("id").startsWith("minecraft:")) {
                        continue;
                    }
                    for (String key : List.of("hasPlacedChest0", "hasPlacedChest1", "hasPlacedChest2", "hasPlacedChest3",
                            "placedMainChest", "placedHiddenChest", "placedTrap1", "placedTrap2", "Mob", "hps", "Witch", "Cat")) {
                        if (piece.contains(key)) {
                            piece.putBoolean(key, false);
                        }
                    }
                    if (piece.getString("id").equals("minecraft:shcc")) {
                        piece.putBoolean("Chest", false);
                    }
                }
                StructureStart copy = StructureStart.loadStaticStart(context, tag, level.getSeed());
                if (copy == null || !copy.isValid()) {
                    throw new IllegalStateException("Cannot deserialize structure layout");
                }
                copies.add(copy);
            }
            if (copies.isEmpty()) {
                return false;
            }
            Job job = new Job(level, copies, completion);
            if (job.positions.size() > MAX_CHUNKS) {
                throw new IllegalStateException("Structure reset exceeds chunk limit");
            }
            JOBS.put(level.dimension().location(), job);
            LogUtils.getLogger().info("Structure reset started in {}: {} chunks", level.dimension().location(), job.positions.size());
            return true;
        } catch (Exception exception) {
            LogUtils.getLogger().error("Cannot prepare structure reset in {}", level.dimension().location(), exception);
            return false;
        }
    }

    public static void tick() {
        if (JOBS.isEmpty()) {
            return;
        }
        Job job = JOBS.values().iterator().next();
        try {
            if (++job.elapsed > 2400) {
                throw new IllegalStateException("Structure reset timed out");
            }
            if (job.elapsed % 20 == 0) {
                for (ServerPlayer player : job.level.players()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "tip.cataclysm_dimension.resetting", job.index, job.positions.size()), true);
                }
            }
            for (int count = 0; count < CHUNKS_PER_TICK && job.requested < job.positions.size(); count++) {
                ChunkPos pos = job.positions.get(job.requested++);
                job.level.getChunkSource().addRegionTicket(RESET_TICKET, pos, 2, pos);
                job.level.getChunk(pos.x, pos.z);
            }
            if (job.requested < job.positions.size()
                    || job.positions.stream().anyMatch(pos -> !job.level.areEntitiesLoaded(pos.toLong()))) {
                return;
            }
            for (int count = 0; count < CHUNKS_PER_TICK && job.index < job.positions.size(); count++) {
                placeChunk(job, job.positions.get(job.index++));
            }
            if (job.index == job.positions.size()) {
                finish(job, true);
            }
        } catch (Exception exception) {
            LogUtils.getLogger().error("Structure reset failed in {}", job.level.dimension().location(), exception);
            finish(job, false);
        } finally {
            placing = null;
        }
    }

    private static void placeChunk(Job job, ChunkPos pos) {
        ServerLevel level = job.level;
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        placing = job;
        try {
            for (StructureStart start : job.starts) {
                if (!start.getBoundingBox().intersects(pos.getMinBlockX(), pos.getMinBlockZ(), pos.getMaxBlockX(), pos.getMaxBlockZ())) {
                    continue;
                }
                start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
                        RandomSource.create(level.getSeed() ^ pos.toLong()),
                        new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                                pos.getMaxBlockX(), level.getMaxBuildHeight() - 1, pos.getMaxBlockZ()), pos);
            }
            chunk.postProcessGeneration();
        } finally {
            job.entities.forEach(StructureEntityRestorer::commitReplacements);
            placing = null;
        }
        chunk.setUnsaved(true);
        var packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
        for (ServerPlayer viewer : level.getChunkSource().chunkMap.getPlayers(pos, false)) {
            viewer.connection.send(packet);
        }
        for (ServerPlayer player : level.players()) {
            if (player.chunkPosition().equals(pos) && !level.noCollision(player)) {
                rescue(player);
            }
        }
    }

    private static void rescue(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int height = player.blockPosition().getY(); height < level.getMaxBuildHeight() - 2; height++) {
            BlockPos pos = new BlockPos(player.getBlockX(), height, player.getBlockZ());
            if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                    && level.getBlockState(pos).getFluidState().isEmpty()
                    && level.noCollision(player, player.getBoundingBox().move(0, height - player.getY(), 0))) {
                player.teleportTo(player.getX(), height, player.getZ());
                player.fallDistance = 0;
                return;
            }
        }
    }

    private static void finish(Job job, boolean success) {
        for (int index = 0; index < job.requested; index++) {
            ChunkPos pos = job.positions.get(index);
            job.level.getChunkSource().removeRegionTicket(RESET_TICKET, pos, 2, pos);
        }
        JOBS.remove(job.level.dimension().location());
        LogUtils.getLogger().info("Structure reset finished in {}: success={}", job.level.dimension().location(), success);
        job.completion.accept(success);
    }

    public static void clear() {
        for (Job job : List.copyOf(JOBS.values())) {
            finish(job, false);
        }
        placing = null;
    }

    public static boolean isPlacing() {
        return placing != null && placing.level.getServer().isSameThread();
    }

    public static BlockPos originalAnchor(TemplateStructurePiece piece) {
        return isPlacing() ? placing.anchors.get(piece) : null;
    }

    public static boolean queuePostprocessing(ChunkAccess chunk, BlockPos pos) {
        if (!isPlacing() || !(chunk instanceof LevelChunk loaded) || loaded.getLevel() != placing.level) {
            return false;
        }
        if (!chunk.isOutsideBuildHeight(pos)) {
            var entries = ChunkAccess.getOrCreateOffsetList(chunk.getPostProcessing(), chunk.getSectionIndex(pos.getY()));
            short packed = ProtoChunk.packOffsetCoordinates(pos);
            if (!entries.contains(packed)) {
                entries.add(packed);
            }
        }
        return true;
    }

    public static void entityJoin(EntityJoinLevelEvent event) {
        if (!isPlacing() || event.getLevel() != placing.level || event.loadedFromDisk()) {
            return;
        }
        for (StructureEntityRestorer restorer : placing.entities) {
            if (restorer.contains(event.getEntity())) {
                restorer.onSpawn(event);
                break;
            }
        }
    }

    private static final class Job {
        private final ServerLevel level;
        private final List<StructureStart> starts;
        private final List<ChunkPos> positions;
        private final List<StructureEntityRestorer> entities = new ArrayList<>();
        private final Map<TemplateStructurePiece, BlockPos> anchors = new IdentityHashMap<>();
        private final Consumer<Boolean> completion;
        private int requested;
        private int index;
        private int elapsed;

        private Job(ServerLevel level, List<StructureStart> starts, Consumer<Boolean> completion) {
            this.level = level;
            this.starts = starts;
            this.completion = completion;
            Set<ChunkPos> chunks = new LinkedHashSet<>();
            for (StructureStart start : starts) {
                BoundingBox box = start.getBoundingBox();
                long count = (long) ((box.maxX() >> 4) - (box.minX() >> 4) + 1)
                        * ((box.maxZ() >> 4) - (box.minZ() >> 4) + 1);
                if (count > MAX_CHUNKS) {
                    throw new IllegalStateException("Structure reset exceeds chunk limit");
                }
                ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4),
                        new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4)).forEach(chunks::add);
                for (var piece : start.getPieces()) {
                    if (piece instanceof TemplateStructurePiece template) {
                        anchors.put(template, template.templatePosition().immutable());
                    }
                }
                entities.add(new StructureEntityRestorer(level, start));
            }
            positions = new ArrayList<>(chunks);
        }
    }
}
