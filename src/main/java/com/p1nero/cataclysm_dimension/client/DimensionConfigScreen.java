package com.p1nero.cataclysm_dimension.client;

import com.p1nero.cataclysm_dimension.CataclysmDimensionModConfig;
import com.p1nero.cataclysm_dimension.CataclysmDimensionModConfig.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

public final class DimensionConfigScreen extends Screen {
    private final Screen parent;
    private final Map<Setting, Boolean> values = CataclysmDimensionModConfig.values();
    private int page;
    private boolean saveFailed;

    public DimensionConfigScreen(Screen parent) {
        super(Component.translatable("config.cataclysm_dimension.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int rows = Math.max(1, (height - 155) / 25);
        int pages = (Setting.values().length + rows - 1) / rows;
        page = Math.min(page, pages - 1);
        int buttonWidth = Math.min(440, width - 30);
        int left = (width - buttonWidth) / 2;
        boolean remote = minecraft.getCurrentServer() != null && !minecraft.hasSingleplayerServer();
        for (int index = page * rows; index < Math.min(Setting.values().length, (page + 1) * rows); index++) {
            Setting setting = Setting.values()[index];
            Button button = Button.builder(label(setting), clicked -> {
                values.put(setting, !values.get(setting));
                clicked.setMessage(label(setting));
            }).bounds(left, 60 + (index - page * rows) * 25, buttonWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable(setting.translationKey() + ".tooltip")
                            .append("\n\n").append(Component.translatable("config.cataclysm_dimension.effect." + setting.effect))))
                    .build();
            button.active = !remote;
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(width / 2 - 110, height - 64, 35, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.translatable("config.cataclysm_dimension.page", page + 1, pages), button -> {})
                .bounds(width / 2 - 70, height - 64, 140, 20).build()).active = false;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(width / 2 + 75, height - 64, 35, 20).build()).active = page < pages - 1;
        addRenderableWidget(Button.builder(Component.translatable("config.cataclysm_dimension.defaults"), button -> {
            values.putAll(CataclysmDimensionModConfig.defaultValues());
            changePage(0);
        }).bounds(width / 2 - 155, height - 35, 100, 20).build()).active = !remote;
        addRenderableWidget(Button.builder(Component.translatable("config.cataclysm_dimension.save"), button -> {
            if (CataclysmDimensionModConfig.save(values)) {
                onClose();
            } else {
                saveFailed = true;
            }
        }).bounds(width / 2 - 50, height - 35, 100, 20).build()).active = !remote;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 55, height - 35, 100, 20).build());
    }

    private Component label(Setting setting) {
        return Component.translatable(setting.translationKey()).append(": ")
                .append(Component.translatable(values.get(setting) ? "options.on" : "options.off"));
    }

    private void changePage(int difference) {
        page += difference;
        clearWidgets();
        init();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        graphics.drawWordWrap(font, Component.translatable("config.cataclysm_dimension.hint"), 15, 32, width - 30, 0xAAAAAA);
        graphics.drawWordWrap(font, Component.translatable(saveFailed ? "config.cataclysm_dimension.save_failed"
                : "config.cataclysm_dimension.server_note"), 15, height - 88, width - 30, saveFailed ? 0xFF5555 : 0xAAAAAA);
    }
}
