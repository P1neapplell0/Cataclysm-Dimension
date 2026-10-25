package com.p1nero.cataclysm_dimension.entity;

import com.p1nero.cataclysm_dimension.CataclysmDimensionMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 归返裂隙:大 boss 被击杀后在死亡点上方开启。右键把玩家送回进入维度前的出发点。
 * 不可破坏、无碰撞;渲染为所在维度对应的眼睛悬浮旋转(见 client.ReturnRiftRenderer)。
 */
public class ReturnRiftEntity extends Entity {

    // 渲染用的眼睛物品按所在维度惰性求值(维度对实体不变,算一次即可)
    private ItemStack displayItem = ItemStack.EMPTY;

    public ReturnRiftEntity(EntityType<? extends ReturnRiftEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && level().players().isEmpty()) {
            discard();
            return;
        }
        if (level().isClientSide) {
            // 环绕的反向传送门粒子,维持存在感
            for (int i = 0; i < 2; i++) {
                level().addParticle(ParticleTypes.REVERSE_PORTAL,
                        getX() + (random.nextDouble() - 0.5) * 1.2,
                        getY() + 0.6 + (random.nextDouble() - 0.5) * 1.2,
                        getZ() + (random.nextDouble() - 0.5) * 1.2,
                        (random.nextDouble() - 0.5) * 0.02,
                        random.nextDouble() * 0.03,
                        (random.nextDouble() - 0.5) * 0.02);
            }
            if (random.nextInt(10) == 0) {
                level().addParticle(ParticleTypes.END_ROD,
                        getX() + (random.nextDouble() - 0.5) * 0.8,
                        getY() + 0.6 + (random.nextDouble() - 0.5) * 0.8,
                        getZ() + (random.nextDouble() - 0.5) * 0.8,
                        0.0, 0.02, 0.0);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            CataclysmDimensionMod.teleportBack(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    public ItemStack getDisplayItem() {
        if (displayItem.isEmpty()) {
            displayItem = new ItemStack(CataclysmDimensionMod.getEyeItemFor(level().dimension()));
        }
        return displayItem;
    }
}
