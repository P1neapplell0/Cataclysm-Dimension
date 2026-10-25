package com.p1nero.cataclysm_dimension;

import com.mojang.logging.LogUtils;
import com.p1nero.cataclysm_dimension.worldgen.CataclysmDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class DimensionPreparation {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("cataclysm_dimension_prepare", Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<ResourceLocation, Job> JOBS = new LinkedHashMap<>();
    private static final Map<UUID, Request> REQUESTS = new LinkedHashMap<>();
    private static int ticks;

    private DimensionPreparation() {
    }

    public static void start(MinecraftServer server) {
        clear();
        if (CataclysmDimensionModConfig.PREGENERATE_DIMENSIONS) {
            for (var dimension : CataclysmDimensions.LEVELS) {
                ServerLevel level = server.getLevel(dimension);
                if (level != null) {
                    JOBS.put(dimension.location(), new Job(level, true));
                }
            }
        }
    }

    public static void request(ServerPlayer player, ServerLevel target, BooleanSupplier valid, Runnable teleport) {
        if (REQUESTS.containsKey(player.getUUID())) {
            return;
        }
        Job job = JOBS.computeIfAbsent(target.dimension().location(), ignored -> new Job(target, false));
        Request request = new Request(player, valid, teleport, job);
        REQUESTS.put(player.getUUID(), request);
        player.displayClientMessage(Component.translatable("tip.cataclysm_dimension.loading", dimensionName(target), 0, job.positions.size()), true);
    }

    private static Component dimensionName(ServerLevel level) {
        return Component.translatable("dimension." + level.dimension().location().getNamespace() + "." + level.dimension().location().getPath());
    }

    public static void tick() {
        for (Request request : List.copyOf(REQUESTS.values())) {
            if (request.player.isRemoved() || !request.player.isAlive() || !request.valid.getAsBoolean()) {
                REQUESTS.remove(request.player.getUUID());
                request.player.displayClientMessage(Component.translatable("tip.cataclysm_dimension.loading_cancelled"), true);
            }
        }
        for (Job job : List.copyOf(JOBS.values())) {
            if (!job.background && REQUESTS.values().stream().noneMatch(request -> request.job == job)) {
                finish(job, false);
            }
        }
        if (++ticks % 20 == 0) {
            for (Request request : REQUESTS.values()) {
                request.player.displayClientMessage(Component.translatable("tip.cataclysm_dimension.loading",
                        dimensionName(request.job.level), request.job.ready, request.job.positions.size()), true);
            }
        }
        Job job = REQUESTS.values().stream().map(request -> request.job).findFirst()
                .orElseGet(() -> JOBS.values().stream().findFirst().orElse(null));
        if (job == null) {
            return;
        }
        try {
            for (int budget = 0; budget < 2 && job.requested < job.positions.size(); budget++) {
                ChunkPos position = job.positions.get(job.requested++);
                job.level.getChunkSource().addRegionTicket(TICKET, position, 2, position);
            }
            int ready = 0;
            for (int index = 0; index < job.requested; index++) {
                ChunkPos position = job.positions.get(index);
                if (job.level.getChunkSource().getChunkNow(position.x, position.z) != null
                        && job.level.areEntitiesLoaded(position.toLong())) {
                    ready++;
                }
            }
            job.ready = ready;
            job.elapsed++;
            if (ready == job.positions.size()) {
                finish(job, true);
            } else if (job.elapsed >= 2400) {
                finish(job, false);
            }
        } catch (Exception exception) {
            LogUtils.getLogger().error("Cannot prepare dimension {}", job.level.dimension().location(), exception);
            finish(job, false);
        }
    }

    private static void finish(Job job, boolean success) {
        JOBS.remove(job.level.dimension().location());
        try {
            for (Request request : List.copyOf(REQUESTS.values())) {
                if (request.job != job) {
                    continue;
                }
                REQUESTS.remove(request.player.getUUID());
                if (success && request.valid.getAsBoolean()) {
                    try {
                        request.player.displayClientMessage(Component.translatable("tip.cataclysm_dimension.loading_ready"), true);
                        request.teleport.run();
                    } catch (Exception exception) {
                        LogUtils.getLogger().error("Cannot teleport player {} to {}", request.player.getUUID(), job.level.dimension().location(), exception);
                        request.player.displayClientMessage(Component.translatable("tip.cataclysm_dimension.teleport_failed"), false);
                    }
                } else {
                    request.player.displayClientMessage(Component.translatable("tip.cataclysm_dimension.loading_failed"), false);
                }
            }
        } finally {
            for (int index = 0; index < job.requested; index++) {
                ChunkPos position = job.positions.get(index);
                job.level.getChunkSource().removeRegionTicket(TICKET, position, 2, position);
            }
        }
        LogUtils.getLogger().info("Dimension preparation finished in {}: success={}", job.level.dimension().location(), success);
    }

    public static void clear() {
        REQUESTS.clear();
        for (Job job : List.copyOf(JOBS.values())) {
            finish(job, false);
        }
    }

    private record Request(ServerPlayer player, BooleanSupplier valid, Runnable teleport, Job job) {
    }

    private static final class Job {
        private final ServerLevel level;
        private final boolean background;
        private final List<ChunkPos> positions = new ArrayList<>();
        private int requested;
        private int ready;
        private int elapsed;

        private Job(ServerLevel level, boolean background) {
            this.level = level;
            this.background = background;
            ChunkPos.rangeClosed(new ChunkPos(-2, -2), new ChunkPos(2, 2)).forEach(positions::add);
            positions.sort(Comparator.comparingInt(position -> Math.abs(position.x) + Math.abs(position.z)));
        }
    }
}
