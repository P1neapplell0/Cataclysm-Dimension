package com.p1nero.cataclysm_dimension.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.p1nero.cataclysm_dimension.entity.ReturnRiftEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 归返裂隙渲染:所在维度对应的眼睛悬浮自旋 + 呼吸浮动,全亮度(粒子由实体客户端 tick 负责)。
 */
public class ReturnRiftRenderer extends EntityRenderer<ReturnRiftEntity> {

    public ReturnRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ReturnRiftEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTicks;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.55F + Mth.sin(age * 0.08F) * 0.1F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 1.5F));
        poseStack.scale(1.6F, 1.6F, 1.6F);
        Minecraft.getInstance().getItemRenderer().renderStatic(entity.getDisplayItem(), ItemDisplayContext.GROUND,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ReturnRiftEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
