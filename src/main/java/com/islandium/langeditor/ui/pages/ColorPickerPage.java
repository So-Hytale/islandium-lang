package com.islandium.langeditor.ui.pages;

import com.islandium.langeditor.LangEditorPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Color Picker popup page
 */
public class ColorPickerPage extends InteractiveCustomUIPage<ColorPickerPage.PageData> {

    private final LangEditorPlugin plugin;
    private final Consumer<String> onColorSelected;
    private final LangEntryEditorPage parentEditor;
    private String selectedColor = "#ffffff";

    public ColorPickerPage(@Nonnull PlayerRef playerRef, LangEditorPlugin plugin, Consumer<String> onColorSelected, LangEntryEditorPage parentEditor) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.onColorSelected = onColorSelected;
        this.parentEditor = parentEditor;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/LangEditor/ColorPickerPopup.ui");

        // Close/Cancel buttons
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn", EventData.of("Action", "cancel"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CancelBtn", EventData.of("Action", "cancel"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SelectBtn", EventData.of("Action", "select"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyCustomBtn", EventData.of("Action", "apply_custom"), false);

        // HEX input
        event.addEventBinding(CustomUIEventBindingType.ValueChanged, "#HexInput", EventData.of("HexValue", "#HexInput.Value"), false);

        // Build color grid
        buildColorGrid(cmd, event);
        updateSelectedColor(cmd);
    }

    private void buildColorGrid(UICommandBuilder cmd, UIEventBuilder event) {
        // Les boutons sont definis dans l'UI - on ajoute juste les event bindings
        // Palette: 6 lignes x 8 colonnes = 48 couleurs (C0 a C47)
        String[] allColors = {
            // Row 1 - Rouges
            "#fecaca", "#fca5a5", "#f87171", "#ef4444", "#dc2626", "#b91c1c", "#991b1b", "#7f1d1d",
            // Row 2 - Oranges/Jaunes
            "#fed7aa", "#fdba74", "#fb923c", "#f97316", "#fde047", "#facc15", "#eab308", "#ca8a04",
            // Row 3 - Verts
            "#bbf7d0", "#86efac", "#4ade80", "#22c55e", "#16a34a", "#15803d", "#166534", "#14532d",
            // Row 4 - Cyans/Bleus
            "#a5f3fc", "#67e8f9", "#22d3ee", "#06b6d4", "#93c5fd", "#60a5fa", "#3b82f6", "#2563eb",
            // Row 5 - Violets/Roses
            "#e9d5ff", "#d8b4fe", "#c084fc", "#a855f7", "#f5d0fe", "#f0abfc", "#e879f9", "#d946ef",
            // Row 6 - Gris/Neutres
            "#ffffff", "#e5e5e5", "#a3a3a3", "#737373", "#525252", "#404040", "#262626", "#000000"
        };

        for (int i = 0; i < allColors.length; i++) {
            event.addEventBinding(CustomUIEventBindingType.Activating, "#C" + i,
                    EventData.of("PickColor", allColors[i]), false);
        }
    }

    private void updateSelectedColor(UICommandBuilder cmd) {
        cmd.set("#SelectedPreview.Background.Color", selectedColor);
        cmd.set("#SelectedHex.Text", selectedColor);
        cmd.set("#ColorPreview.Background.Color", selectedColor);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        super.handleDataEvent(ref, store, data);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder event = new UIEventBuilder();

        // Color picked from grid
        if (data.pickColor != null) {
            selectedColor = data.pickColor;
            updateSelectedColor(cmd);
            sendUpdate(cmd, event, false);
            return;
        }

        // HEX input changed
        if (data.hexValue != null) {
            String hex = data.hexValue.trim();
            if (!hex.startsWith("#")) {
                hex = "#" + hex;
            }
            // Validate HEX format
            if (hex.matches("^#[0-9A-Fa-f]{6}$")) {
                selectedColor = hex.toLowerCase();
                cmd.set("#ColorPreview.Background.Color", selectedColor);
            }
            sendUpdate(cmd, event, false);
            return;
        }

        // Actions
        if (data.action != null) {
            switch (data.action) {
                case "cancel" -> {
                    // Retourner a l'editeur sans appliquer
                    if (parentEditor != null) {
                        player.getPageManager().openCustomPage(ref, store, parentEditor);
                    } else {
                        close();
                    }
                    return;
                }
                case "select", "apply_custom" -> {
                    // Callback avec la couleur selectionnee
                    if (onColorSelected != null) {
                        onColorSelected.accept(selectedColor);
                    }
                    // Retourner a l'editeur
                    if (parentEditor != null) {
                        player.getPageManager().openCustomPage(ref, store, parentEditor);
                    } else {
                        close();
                    }
                    return;
                }
            }
        }

        sendUpdate(cmd, event, false);
    }

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("PickColor", Codec.STRING), (d, v) -> d.pickColor = v, d -> d.pickColor)
                .addField(new KeyedCodec<>("HexValue", Codec.STRING), (d, v) -> d.hexValue = v, d -> d.hexValue)
                .build();

        public String action;
        public String pickColor;
        public String hexValue;
    }
}
