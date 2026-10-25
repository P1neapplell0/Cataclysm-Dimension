package com.p1nero.cataclysm_dimension.mixin;

import com.p1nero.cataclysm_dimension.StructureResetManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructurePiece.class)
public abstract class StructurePieceMixin {
    @Shadow protected abstract BlockPos.MutableBlockPos getWorldPos(int localX, int localY, int localZ);

    @Inject(method = "createChest(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"))
    private void rewind$chest(ServerLevelAccessor level, BoundingBox box, RandomSource random, BlockPos pos,
                              ResourceKey<LootTable> loot, BlockState state, CallbackInfoReturnable<Boolean> ci) {
        rewind$clear(level, box, pos);
    }

    @Inject(method = "createDispenser", at = @At("HEAD"))
    private void rewind$dispenser(WorldGenLevel level, BoundingBox box, RandomSource random, int localX, int localY, int localZ,
                                  Direction facing, ResourceKey<LootTable> loot, CallbackInfoReturnable<Boolean> ci) {
        rewind$clear(level, box, getWorldPos(localX, localY, localZ));
    }

    private static void rewind$clear(ServerLevelAccessor level, BoundingBox box, BlockPos pos) {
        if (StructureResetManager.isPlacing() && box.isInside(pos) && level.getBlockEntity(pos) != null) {
            Clearable.tryClear(level.getBlockEntity(pos));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }
}
