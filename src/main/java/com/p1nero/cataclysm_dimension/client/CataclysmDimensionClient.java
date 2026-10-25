package com.p1nero.cataclysm_dimension.client;

import com.p1nero.cataclysm_dimension.entity.CDEntities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * 客户端专用注册(专用服务端不加载本类)。从主类的 dist.isClient() 分支调用,显式挂 mod 总线。
 */
public final class CataclysmDimensionClient {

    private CataclysmDimensionClient() {
    }

    public static void init(IEventBus modBus) {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new DimensionConfigScreen(parent)));
        modBus.addListener(CataclysmDimensionClient::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CDEntities.RETURN_RIFT.get(), ReturnRiftRenderer::new);
    }
}
