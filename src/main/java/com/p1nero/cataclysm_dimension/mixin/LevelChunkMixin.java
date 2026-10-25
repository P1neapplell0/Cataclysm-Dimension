package com.p1nero.cataclysm_dimension.mixin;

import com.p1nero.cataclysm_dimension.StructureResetManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(method = "markPosForPostprocessing", at = @At("HEAD"), cancellable = true, require = 0)
    private void rewind$markExistingOverride(BlockPos pos, CallbackInfo callback) {
        if (StructureResetManager.queuePostprocessing((ChunkAccess) (Object) this, pos)) {
            callback.cancel();
        }
    }
}
