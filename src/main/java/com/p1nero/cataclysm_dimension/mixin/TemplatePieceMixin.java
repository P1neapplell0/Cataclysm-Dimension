package com.p1nero.cataclysm_dimension.mixin;

import com.p1nero.cataclysm_dimension.StructureResetManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TemplateStructurePiece.class)
public abstract class TemplatePieceMixin {
    @Shadow protected BlockPos templatePosition;
    @Inject(method = "postProcess", at = @At("HEAD"))
    private void rewind$restoreAnchor(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot, CallbackInfo ci) {
        BlockPos original = StructureResetManager.originalAnchor((TemplateStructurePiece) (Object) this);
        if (original != null) templatePosition = original;
    }
}
