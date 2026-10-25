package com.p1nero.cataclysm_dimension;

import com.github.L_Ender.cataclysm.init.ModEntities;
import com.github.L_Ender.cataclysm.init.ModItems;
import com.mojang.logging.LogUtils;
import com.p1nero.cataclysm_dimension.client.CataclysmDimensionClient;
import com.p1nero.cataclysm_dimension.entity.CDEntities;
import com.p1nero.cataclysm_dimension.entity.ReturnRiftEntity;
import com.p1nero.cataclysm_dimension.worldgen.CataclysmDimensions;
import com.p1nero.cataclysm_dimension.worldgen.placements.CDPlacementTypes;
import com.p1nero.cataclysm_dimension.worldgen.portal.CDTeleporter;
import com.p1nero.cataclysm_dimension.worldgen.portal.CDNetherTeleporter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraftforge.fml.ModList;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraft.world.level.portal.PortalInfo;
import java.util.function.Function;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod(CataclysmDimensionMod.MOD_ID)
public class CataclysmDimensionMod {
    public static final String MOD_ID = "cataclysm_dimension";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CataclysmDimensionMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.addListener(this::onItemUse);
        MinecraftForge.EVENT_BUS.addListener(this::onToolTip);
        MinecraftForge.EVENT_BUS.addListener(this::onServerLevelTick);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, StructureResetManager::entityJoin);
        // LOWEST:让取消死亡(复活图腾类)的监听器先行,免得 boss 没死也开裂隙
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onBossDeath);
        MinecraftForge.EVENT_BUS.addListener(this::onEntityJoinLevel);
        CataclysmDimensionModConfig.loadConfig();
        bus.addListener(EventPriority.HIGHEST, this::onDatapackLoad);
        bus.addListener(this::onCommonSetup);
        CDPlacementTypes.STRUCTURE_PLACEMENT_TYPES.register(bus);
        CDEntities.ENTITY_TYPES.register(bus);
        if (FMLEnvironment.dist.isClient()) {
            CataclysmDimensionClient.init(bus);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // 主线程建好两张对照表,免得懒初始化在单人下被两个线程同时首触
        event.enqueueWork(() -> {
            initTeleportConfigs();
            initDimensionBosses();
            initMap();
        });
    }

    public record DimensionTeleportInfo(ResourceKey<Level> dimensionKey, ITeleporter teleporter) {}

    public static Map<Item, DimensionTeleportInfo> TELEPORT_MAP;

    private void initMap() {
        TELEPORT_MAP = Map.of(
                ModItems.ABYSS_EYE.get(), new DimensionTeleportInfo(
                        CataclysmDimensions.CATACLYSM_ABYSSAL_DEPTHS_LEVEL_KEY,
                        new CDTeleporter(new BlockPos(0, 200, 0))
                ),
                ModItems.MECH_EYE.get(), new DimensionTeleportInfo(
                        CataclysmDimensions.CATACLYSM_FORGE_OF_AEONS_LEVEL_KEY,
                        new CDTeleporter(new BlockPos(0, 150, 0))
                ),
                ModItems.FLAME_EYE.get(), new DimensionTeleportInfo(
                        CataclysmDimensions.CATACLYSM_INFERNOS_MAW_LEVEL_KEY,
                        new CDNetherTeleporter(new BlockPos(0, 64, 0))
                ),
                ModItems.VOID_EYE.get(), new DimensionTeleportInfo(
                        CataclysmDimensions.CATACLYSM_BASTION_LOST_LEVEL_KEY,
                        new CDTeleporter(new BlockPos(0, 150, 0))
                ),
                ModItems.MONSTROUS_EYE.get(), new DimensionTeleportInfo(
                        CataclysmDimensions.CATACLYSM_SOULS_ANVIL_LEVEL_KEY,
                        new CDNetherTeleporter(new BlockPos(0, 64, 0))
                ),
                ModItems.DESERT_EYE.get(), new DimensionTeleportInfo(
                        CataclysmDimensions.CATACLYSM_PHARAOHS_BANE_LEVEL_KEY,
                        new CDTeleporter(new BlockPos(0, 200, 0), 400)
                ),
                ModItems.CURSED_EYE.get(), new DimensionTeleportInfo(
                        CataclysmDimensions.CATACLYSM_ETERNAL_FROSTHOLD_LEVEL_KEY,
                        new CDTeleporter(new BlockPos(0, 200, 0), 400)
                ),
                ModItems.STORM_EYE.get(), new DimensionTeleportInfo(
                        CataclysmDimensions.CATACLYSM_SANCTUM_FALLEN_LEVEL_KEY,
                        new CDTeleporter(new BlockPos(0, 200, 0))
                )
        );
    }


    private static final int COOLDOWN_TICKS = 600;
    private static final int DEFAULT_SLOW_FALLING_DURATION = 200;
    private static final int LONG_SLOW_FALLING_DURATION = 400;

    // 维度传送配置映射表
    private static final Map<Item, TeleportConfig> TELEPORT_CONFIGS = new HashMap<>();

    private static void initTeleportConfigs() {
        // 初始化配置映射（实际使用时需要替换为对应的物品和维度键）
        TELEPORT_CONFIGS.put(ModItems.ABYSS_EYE.get(),
                new TeleportConfig(CataclysmDimensions.CATACLYSM_ABYSSAL_DEPTHS_LEVEL_KEY, 150, DEFAULT_SLOW_FALLING_DURATION));
        TELEPORT_CONFIGS.put(ModItems.MECH_EYE.get(),
                new TeleportConfig(CataclysmDimensions.CATACLYSM_FORGE_OF_AEONS_LEVEL_KEY, 150, DEFAULT_SLOW_FALLING_DURATION));
        TELEPORT_CONFIGS.put(ModItems.FLAME_EYE.get(),
                new TeleportConfig(CataclysmDimensions.CATACLYSM_INFERNOS_MAW_LEVEL_KEY, 64, DEFAULT_SLOW_FALLING_DURATION));
        TELEPORT_CONFIGS.put(ModItems.VOID_EYE.get(),
                new TeleportConfig(CataclysmDimensions.CATACLYSM_BASTION_LOST_LEVEL_KEY, 150, DEFAULT_SLOW_FALLING_DURATION));
        TELEPORT_CONFIGS.put(ModItems.MONSTROUS_EYE.get(),
                new TeleportConfig(CataclysmDimensions.CATACLYSM_SOULS_ANVIL_LEVEL_KEY, 64, DEFAULT_SLOW_FALLING_DURATION));
        TELEPORT_CONFIGS.put(ModItems.DESERT_EYE.get(),
                new TeleportConfig(CataclysmDimensions.CATACLYSM_PHARAOHS_BANE_LEVEL_KEY, 200, LONG_SLOW_FALLING_DURATION));
        TELEPORT_CONFIGS.put(ModItems.CURSED_EYE.get(),
                new TeleportConfig(CataclysmDimensions.CATACLYSM_ETERNAL_FROSTHOLD_LEVEL_KEY, 200, LONG_SLOW_FALLING_DURATION));
        TELEPORT_CONFIGS.put(ModItems.STORM_EYE.get(),
                new TeleportConfig(CataclysmDimensions.CATACLYSM_SANCTUM_FALLEN_LEVEL_KEY, 200, DEFAULT_SLOW_FALLING_DURATION)); // 0表示无效果
    }

    private void onItemUse(LivingEntityUseItemEvent event) {
        if (!CataclysmDimensionModConfig.ENABLE_TELEPORT_EYE) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !entity.isShiftKeyDown()) {
            return;
        }

        ItemStack itemStack = event.getItem();
        Item eyeItem = itemStack.getItem(); // 先记住眼睛物品:后面若消耗(shrink 到 0)itemStack.getItem() 会变成 AIR
        MinecraftServer server = entity.level().getServer();
        if (server == null) {
            return;
        }

        if (entity instanceof Player player && player.getCooldowns().isOnCooldown(eyeItem)) {
            return;
        }

        if (TELEPORT_CONFIGS.isEmpty()) {
            initTeleportConfigs();
        }

        TeleportConfig config = TELEPORT_CONFIGS.get(itemStack.getItem());
        if (config == null) {
            return;
        }

        // 身处灾变维度内时禁止使用眼睛(可配置,默认禁止)
        if (!CataclysmDimensionModConfig.ALLOW_USE_EYE_IN_DIMENSION
                && CataclysmDimensions.LEVELS.contains(entity.level().dimension())) {
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("tip.cataclysm_dimension.eye_disabled_in_dimension").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        if (TELEPORT_MAP == null) {
            initMap();
        }
        DimensionTeleportInfo teleportInfo = TELEPORT_MAP.get(eyeItem);
        if (teleportInfo == null) {
            return;
        }
        ServerLevel targetLevel = server.getLevel(teleportInfo.dimensionKey());
        if (targetLevel == null) {
            return;
        }

        if (entity instanceof ServerPlayer player) {
            Level origin = entity.level();
            DimensionPreparation.request(player, targetLevel,
                    () -> CataclysmDimensionModConfig.ENABLE_TELEPORT_EYE && player.level() == origin
                            && (CataclysmDimensionModConfig.ALLOW_USE_EYE_IN_DIMENSION || !CataclysmDimensions.LEVELS.contains(origin.dimension()))
                            && !player.isRemoved() && player.isAlive() && itemStack.is(eyeItem)
                            && (player.getMainHandItem() == itemStack || player.getOffhandItem() == itemStack)
                            && !player.getCooldowns().isOnCooldown(eyeItem),
                    () -> completeEyeTeleport(entity, itemStack, eyeItem, config, targetLevel, teleportInfo));
            return;
        }
        completeEyeTeleport(entity, itemStack, eyeItem, config, targetLevel, teleportInfo);
    }

    private void completeEyeTeleport(LivingEntity entity, ItemStack itemStack, Item eyeItem, TeleportConfig config,
                                     ServerLevel targetLevel, DimensionTeleportInfo teleportInfo) {
        // 可配置:传送时消耗对应的眼睛物品(创造模式不消耗)
        if (CataclysmDimensionModConfig.CONSUME_TELEPORT_EYE
                && !(entity instanceof Player creativePlayer && creativePlayer.isCreative())) {
            itemStack.shrink(1);
        }

        if (CataclysmDimensionModConfig.SLOW_FALL_WHEN_ENTER_DIMENSIONS && config.effectDuration() > 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, config.effectDuration(), 1, false, true));
        }

        // 记住出发点,供归返裂隙送回(已在灾变维度里再用眼时保留最初的出发点)
        if (entity instanceof ServerPlayer serverPlayer
                && !CataclysmDimensions.LEVELS.contains(serverPlayer.level().dimension())) {
            saveReturnPoint(serverPlayer);
        }

        Vec3 targetPosition = new Vec3(0, config.targetY(), 0);
        if (entity.level() instanceof ServerLevel originLevel) {
            playDepartureEffects(originLevel, entity.position());
        }
        net.minecraft.world.entity.Entity transported = entity.changeDimension(targetLevel, teleportInfo.teleporter());
        if (transported == null) {
            return;
        }
        targetPosition = transported.position();
        targetLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, targetPosition.x, targetPosition.y + 1.0, targetPosition.z, 60, 0.6, 1.2, 0.6, 0.05);

        if (entity instanceof Player player) {
            player.getCooldowns().addCooldown(eyeItem, COOLDOWN_TICKS);
        }
    }

    private record TeleportConfig(ResourceKey<Level> dimensionKey, int targetY, int effectDuration) {
    }

    /** 维度 -> 对应的眼睛(归返裂隙渲染用;未知维度退回末影之眼)。 */
    public static Item getEyeItemFor(ResourceKey<Level> dimension) {
        if (TELEPORT_CONFIGS.isEmpty()) {
            initTeleportConfigs();
        }
        for (Map.Entry<Item, TeleportConfig> entry : TELEPORT_CONFIGS.entrySet()) {
            if (entry.getValue().dimensionKey().equals(dimension)) {
                return entry.getKey();
            }
        }
        return Items.ENDER_EYE;
    }

    // ===== 归返裂隙:大 boss 被击杀后在死亡点开启,右键送玩家回进入前的出发点 =====
    private static final String RETURN_POINT_TAG = MOD_ID + ":return_point";
    // 维度 -> 该维度的主 boss(精确匹配,小怪不触发)。熔岩竞技场是 Ignis,不是守门的 Ignited Revenant。
    private static final Map<ResourceKey<Level>, EntityType<?>> DIMENSION_BOSSES = new HashMap<>();
    // 已重盖、待清旧裂隙的维度:异步加载晚到的旧裂隙靠它拦(见 onEntityJoinLevel)
    private static final Set<ResourceLocation> PURGE_STALE_RIFTS = new HashSet<>();

    private static void initDimensionBosses() {
        DIMENSION_BOSSES.put(CataclysmDimensions.CATACLYSM_FORGE_OF_AEONS_LEVEL_KEY, ModEntities.THE_HARBINGER.get());
        DIMENSION_BOSSES.put(CataclysmDimensions.CATACLYSM_ABYSSAL_DEPTHS_LEVEL_KEY, ModEntities.THE_LEVIATHAN.get());
        DIMENSION_BOSSES.put(CataclysmDimensions.CATACLYSM_PHARAOHS_BANE_LEVEL_KEY, ModEntities.ANCIENT_REMNANT.get());
        DIMENSION_BOSSES.put(CataclysmDimensions.CATACLYSM_ETERNAL_FROSTHOLD_LEVEL_KEY, ModEntities.MALEDICTUS.get());
        DIMENSION_BOSSES.put(CataclysmDimensions.CATACLYSM_SANCTUM_FALLEN_LEVEL_KEY, ModEntities.SCYLLA.get());
        DIMENSION_BOSSES.put(CataclysmDimensions.CATACLYSM_SOULS_ANVIL_LEVEL_KEY, ModEntities.NETHERITE_MONSTROSITY.get());
        DIMENSION_BOSSES.put(CataclysmDimensions.CATACLYSM_INFERNOS_MAW_LEVEL_KEY, ModEntities.IGNIS.get());
        DIMENSION_BOSSES.put(CataclysmDimensions.CATACLYSM_BASTION_LOST_LEVEL_KEY, ModEntities.ENDER_GUARDIAN.get());
    }

    /**
     * 主 boss 被击杀:打赢才重置模式下此刻标脏,再按开关在死亡点开归返裂隙。
     */
    private void onBossDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        if (!CataclysmDimensions.LEVELS.contains(dimension)) {
            return;
        }
        if (DIMENSION_BOSSES.isEmpty()) {
            initDimensionBosses();
        }
        if (event.getEntity().getType() != DIMENSION_BOSSES.get(dimension)) {
            return;
        }
        if (CataclysmDimensionModConfig.RESET_STRUCTURE_REQUIRES_BOSS_KILL && DIRTY_DIMS.add(dimension.location())) {
            saveDirtyDims(level.getServer());
        }
        if (!CataclysmDimensionModConfig.ENABLE_RETURN_RIFT) {
            return;
        }
        // 已有裂隙(上一局残留 / 同局再杀)就不再开新的
        if (!level.getEntities(CDEntities.RETURN_RIFT.get(), rift -> true).isEmpty()) {
            return;
        }
        spawnReturnRift(level, event.getEntity().position());
    }

    private void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()
                && event.getEntity() instanceof ReturnRiftEntity
                && event.getLevel() instanceof ServerLevel level
                && (level.players().isEmpty() || PURGE_STALE_RIFTS.contains(level.dimension().location()))) {
            event.setCanceled(true);
        }
    }

    private static void spawnReturnRift(ServerLevel level, Vec3 deathPos) {
        PURGE_STALE_RIFTS.remove(level.dimension().location());
        Vec3 pos = deathPos.add(0, 1.0, 0);
        if (pos.y < level.getMinBuildHeight() + 2) {
            // boss 坠入虚空死亡时,裂隙改开在出生点地表,否则玩家够不着
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            pos = new Vec3(0.5, y + 1.0, 0.5);
        }
        ReturnRiftEntity rift = new ReturnRiftEntity(CDEntities.RETURN_RIFT.get(), level);
        rift.setPos(pos);
        level.addFreshEntity(rift);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.NEUTRAL, 0.8F, 1.2F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 0.5, pos.z, 80, 0.8, 0.8, 0.8, 0.1);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 30, 0.5, 0.5, 0.5, 0.05);
        Component tip = Component.translatable("tip.cataclysm_dimension.rift_opened").withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(tip, true);
        }
        LOGGER.info("[Cataclysm Dimension]: boss defeated in {}; return rift opened at {}.", level.dimension().location(), pos);
    }

    private static void saveReturnPoint(ServerPlayer player) {
        CompoundTag point = new CompoundTag();
        point.putString("dim", player.level().dimension().location().toString());
        point.putDouble("x", player.getX());
        point.putDouble("y", player.getY());
        point.putDouble("z", player.getZ());
        point.putFloat("yRot", player.getYRot());
        point.putFloat("xRot", player.getXRot());
        player.getPersistentData().put(RETURN_POINT_TAG, point);
    }

    /** 归返裂隙右键:送回进入维度前记录的出发点;记录缺失 / 维度不存在时退回主世界出生点。 */
    public static void teleportBack(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        player.stopRiding(); // 骑乘中跨维传送前先下坐骑
        ServerLevel targetLevel = null;
        Vec3 targetPos = null;
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        CompoundTag data = player.getPersistentData();
        if (data.contains(RETURN_POINT_TAG)) {
            CompoundTag point = data.getCompound(RETURN_POINT_TAG);
            ResourceLocation dim = ResourceLocation.tryParse(point.getString("dim"));
            ServerLevel level = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (level != null) {
                targetLevel = level;
                targetPos = new Vec3(point.getDouble("x"), point.getDouble("y"), point.getDouble("z"));
                yRot = point.getFloat("yRot");
                xRot = point.getFloat("xRot");
            }
        }
        if (targetLevel == null) {
            targetLevel = server.overworld();
            BlockPos spawn = targetLevel.getSharedSpawnPos();
            targetPos = Vec3.atBottomCenterOf(spawn.atY(targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ())));
        }
        playDepartureEffects(player.serverLevel(), player.position());
        player.changeDimension(targetLevel, returnTeleporter(targetPos, yRot, xRot));
        targetLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, targetPos.x, targetPos.y + 1.0, targetPos.z, 60, 0.6, 1.2, 0.6, 0.05);
    }

    /** 出发点的离场音效 + 粒子(眼睛传送与裂隙归返共用)。 */
    private static void playDepartureEffects(ServerLevel level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0, pos.z, 40, 0.5, 1.0, 0.5, 0.3);
    }

    // ===== 结构地形重置(局内、绕开 C2ME):玩家再进用过的 boss 维度时,把该 boss 结构重盖成完好 =====
    // "脏"维度:本会话被玩家占用过(可能打坏了竞技场)的灾变维度。
    private static final Set<ResourceLocation> DIRTY_DIMS = new HashSet<>();
    // 待执行的结构重盖:维度 -> 触发玩家的落点(用于定位 boss 结构),以及剩余延迟 tick(等区块加载好再重盖)。
    private static final Map<ResourceLocation, BlockPos> PENDING_RESTORE_POS = new HashMap<>();
    private static final Map<ResourceLocation, Integer> PENDING_RESTORE_DELAY = new HashMap<>();
    private static final int RESTORE_DELAY_TICKS = 40; // 进入后等约 2s,确保竞技场区块已随玩家加载

    /**
     * 每 tick 推进结构地形重盖的待处理队列(见下方结构地形重置系统)。
     */
    private void onServerLevelTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        DimensionPreparation.tick();
        processPendingStructureRestores(event.getServer());
        StructureResetManager.tick();
        processEmptyDimensions(event.getServer());
    }

    /**
     * 玩家进入灾变维度:若该维度此前被占用过(可能被打坏)且当前无其他玩家在场,排入一次结构地形重盖
     * (让这个刚进来的玩家/后续玩家看到完好竞技场);随后把该维度标记为"脏"(本次占用可能又打坏)。
     */
    private void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!CataclysmDimensionModConfig.RESET_STRUCTURE_ON_REENTRY) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceKey<Level> toKey = event.getTo();
        if (!CataclysmDimensions.LEVELS.contains(toKey)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level == null || !level.dimension().equals(toKey)) {
            return;
        }
        ResourceLocation loc = toKey.location();
        boolean hasOtherPlayers = level.players().stream().anyMatch(p -> p != player);
        if (DIRTY_DIMS.contains(loc) && !hasOtherPlayers) {
            // 这个玩家是"用过的竞技场"的第一个新访客 -> 延迟重盖(等区块随其加载好)。
            PENDING_RESTORE_POS.put(loc, player.blockPosition());
            PENDING_RESTORE_DELAY.put(loc, RESTORE_DELAY_TICKS);
            LOGGER.info("[Cataclysm Dimension]: {} re-entered (used before); scheduling structure restore at {}.", loc, player.blockPosition());
        }
        // "打赢才重置"开启时,标脏改由 onBossDeath 在主 boss 被击杀时做;否则沿用"进过就标脏"
        if (!CataclysmDimensionModConfig.RESET_STRUCTURE_REQUIRES_BOSS_KILL && DIRTY_DIMS.add(loc)) {
            saveDirtyDims(player.getServer()); // 跨重启持久化:记下"用过的维度"
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
        // 静态状态跨存档存活,换档前清掉,免得旧档的重盖任务在新档误触发
        StructureResetManager.clear();
        RESOURCE_KEY_BOOLEAN_MAP.clear();
        RESOURCE_LOCATION_INTEGER_MAP.clear();
        PENDING_RESTORE_POS.clear();
        PENDING_RESTORE_DELAY.clear();
        PURGE_STALE_RIFTS.clear();
        loadDirtyDims(event.getServer());
        DimensionPreparation.start(event.getServer());
    }

    public static final Map<ResourceLocation, Boolean> RESOURCE_KEY_BOOLEAN_MAP = new HashMap<>();
    public static final Map<ResourceLocation, Integer> RESOURCE_LOCATION_INTEGER_MAP = new HashMap<>();
    public static final int DELAY = 200;

    private void onServerStopped(ServerStoppedEvent event) {
        DimensionPreparation.clear();
        StructureResetManager.clear();
        PENDING_RESTORE_POS.clear();
        PENDING_RESTORE_DELAY.clear();
        RESOURCE_KEY_BOOLEAN_MAP.clear();
        RESOURCE_LOCATION_INTEGER_MAP.clear();
        PURGE_STALE_RIFTS.clear();
    }

    private void processEmptyDimensions(MinecraftServer server) {
        if (!CataclysmDimensionModConfig.RESET_DIMENSION_IF_NO_PLAYER) {
            for (ResourceLocation location : RESOURCE_KEY_BOOLEAN_MAP.keySet()) {
                if (!StructureResetManager.isBusy(location) && !PENDING_RESTORE_DELAY.containsKey(location)) {
                    RESOURCE_KEY_BOOLEAN_MAP.put(location, false);
                    RESOURCE_LOCATION_INTEGER_MAP.put(location, 0);
                }
            }
            return;
        }
        for (ResourceKey<Level> dimension : CataclysmDimensions.LEVELS) {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                continue;
            }
            ResourceLocation location = dimension.location();
            if (!level.players().isEmpty()) {
                if (!StructureResetManager.isBusy(location) && !PENDING_RESTORE_DELAY.containsKey(location)) {
                    RESOURCE_KEY_BOOLEAN_MAP.put(location, false);
                    RESOURCE_LOCATION_INTEGER_MAP.put(location, 0);
                }
                continue;
            }
            if (StructureResetManager.isBusy(location)) {
                continue;
            }
            if (!RESOURCE_KEY_BOOLEAN_MAP.getOrDefault(location, false)) {
                RESOURCE_KEY_BOOLEAN_MAP.put(location, true);
                RESOURCE_LOCATION_INTEGER_MAP.put(location, DELAY);
            }
            int delay = RESOURCE_LOCATION_INTEGER_MAP.getOrDefault(location, 0);
            if (delay > 0) {
                RESOURCE_LOCATION_INTEGER_MAP.put(location, delay - 1);
                if (delay == 1) {
                    restoreStructure(level, new BlockPos(0, level.getSeaLevel(), 0));
                }
            }
        }
    }

    private static Path dirtyDimsFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("cataclysm_dimension_dirty_dims.txt");
    }

    /** 载入上次会话记录的"被用过的 boss 维度",使跨重启后首个访客仍触发结构复原。 */
    private static void loadDirtyDims(MinecraftServer server) {
        DIRTY_DIMS.clear();
        Path file = dirtyDimsFile(server);
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                String s = line.trim();
                if (s.isEmpty()) {
                    continue;
                }
                ResourceLocation rl = ResourceLocation.tryParse(s);
                if (rl != null) {
                    DIRTY_DIMS.add(rl);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[Cataclysm Dimension]: failed to load dirty-dims file.", e);
        }
    }

    private static void saveDirtyDims(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Path file = dirtyDimsFile(server);
        try {
            List<String> lines = new ArrayList<>();
            for (ResourceLocation rl : DIRTY_DIMS) {
                lines.add(rl.toString());
            }
            Files.write(file, lines);
        } catch (IOException e) {
            LOGGER.error("[Cataclysm Dimension]: failed to save dirty-dims file.", e);
        }
    }

    private void processPendingStructureRestores(MinecraftServer server) {
        if (PENDING_RESTORE_DELAY.isEmpty()) {
            return;
        }
        List<ResourceLocation> ready = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> e : PENDING_RESTORE_DELAY.entrySet()) {
            int left = e.getValue() - 1;
            if (left <= 0) {
                ready.add(e.getKey());
            } else {
                e.setValue(left);
            }
        }
        for (ResourceLocation loc : ready) {
            PENDING_RESTORE_DELAY.remove(loc);
            BlockPos around = PENDING_RESTORE_POS.remove(loc);
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, loc));
            if (level == null || around == null) {
                continue;
            }
            // 触发重盖的正是站在竞技场里的这个玩家,不能因附近有人而跳过;多人不打断由排入时的 !hasOtherPlayers 保证。
            try {
                restoreStructure(level, around);
            } catch (Exception ex) {
                LOGGER.error("[Cataclysm Dimension]: structure restore failed for {}.", loc, ex);
            }
        }
    }

    private void restoreStructure(ServerLevel level, BlockPos aroundPos) {
        ResourceLocation dimension = level.dimension().location();
        if (StructureResetManager.isBusy(dimension)) {
            return;
        }
        boolean started = StructureResetManager.begin(level, aroundPos, success -> {
            RESOURCE_KEY_BOOLEAN_MAP.put(dimension, success);
            RESOURCE_LOCATION_INTEGER_MAP.put(dimension, 0);
            if (success && CataclysmDimensionModConfig.RESET_STRUCTURE_REQUIRES_BOSS_KILL && DIRTY_DIMS.remove(dimension)) {
                saveDirtyDims(level.getServer());
            }
        });
        if (started) {
            RESOURCE_KEY_BOOLEAN_MAP.put(dimension, true);
            RESOURCE_LOCATION_INTEGER_MAP.put(dimension, 0);
            PURGE_STALE_RIFTS.add(dimension);
            for (ReturnRiftEntity rift : level.getEntities(CDEntities.RETURN_RIFT.get(), entity -> true)) {
                rift.discard();
            }
        } else {
            RESOURCE_KEY_BOOLEAN_MAP.put(dimension, false);
            RESOURCE_LOCATION_INTEGER_MAP.put(dimension, 0);
        }
    }

    private static ITeleporter returnTeleporter(Vec3 position, float yaw, float pitch) {
        return new ITeleporter() {
            @Override
            public net.minecraft.world.entity.Entity placeEntity(net.minecraft.world.entity.Entity entity, ServerLevel currentLevel,
                    ServerLevel destinationLevel, float rotation, Function<Boolean, net.minecraft.world.entity.Entity> repositionEntity) {
                return repositionEntity.apply(false);
            }

            @Override
            public PortalInfo getPortalInfo(net.minecraft.world.entity.Entity entity, ServerLevel destinationLevel,
                    Function<ServerLevel, PortalInfo> defaultPortalInfo) {
                return new PortalInfo(position, Vec3.ZERO, yaw, pitch);
            }
        };
    }

    private void onToolTip(ItemTooltipEvent event) {
        if (!CataclysmDimensionModConfig.ENABLE_TELEPORT_EYE) {
            return;
        }
        if (TELEPORT_CONFIGS.isEmpty()) {
            initTeleportConfigs();
        }
        if (TELEPORT_CONFIGS.containsKey(event.getItemStack().getItem())) {
            event.getToolTip().add(Component.translatable("tip.cataclysm_dimension.enter").withStyle(ChatFormatting.GRAY));
        }
    }

    private void onDatapackLoad(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            addPacket(event, "base_dimension");
            addPacket(event, CataclysmDimensionModConfig.KEEP_STRUCTURES_IN_ORIGINAL_DIMENSIONS ? "keep_original" : "not_keep_original");
            if (CataclysmDimensionModConfig.RANDOM_SPREAD_IN_DIMENSION) {
                addPacket(event, CataclysmDimensionModConfig.KEEP_STRUCTURES_IN_ORIGINAL_DIMENSIONS ? "random_spread_dim" : "random_spread");
            }
            if (CataclysmDimensionModConfig.DISABLE_RESPAWN) {
                addPacket(event, "disable_respawn");
            }
        }
    }

    private void addPacket(AddPackFindersEvent event, String name) {
        var resourcePath = ModList.get().getModFileById(MOD_ID).getFile().findResource("packs/" + name);
        var pack = Pack.readMetaAndCreate(name, Component.literal(name), true,
                (path) -> new PathPackResources(path, resourcePath, false), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.WORLD);
        event.addRepositorySource((packConsumer) -> packConsumer.accept(pack));
    }

}
