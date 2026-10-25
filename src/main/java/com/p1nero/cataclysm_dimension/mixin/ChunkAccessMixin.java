package com.p1nero.cataclysm_dimension.mixin;

import com.p1nero.cataclysm_dimension.StructureResetManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin {
    @Inject(method = "markPosForPostprocessing", at = @At("HEAD"), cancellable = true)
    private void rewind$markInheritedMethod(BlockPos pos, CallbackInfo ci) {
        if (StructureResetManager.queuePostprocessing((ChunkAccess) (Object) this, pos)) ci.cancel();
    }
}
