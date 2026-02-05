package com.islandium.langeditor.ui.pages;

import com.islandium.langeditor.LangEditorPlugin;
import com.islandium.langeditor.model.LangEntry;
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

/**
 * Delete confirmation popup page
 */
public class DeleteConfirmPage extends InteractiveCustomUIPage<DeleteConfirmPage.PageData> {

    private final LangEditorPlugin plugin;
    private final LangEntry entryToDelete;
    private final LangEntryEditorPage parentEditor;
    private final Runnable onConfirm;

    public DeleteConfirmPage(@Nonnull PlayerRef playerRef, LangEditorPlugin plugin,
                             LangEntry entryToDelete, LangEntryEditorPage parentEditor, Runnable onConfirm) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.entryToDelete = entryToDelete;
        this.parentEditor = parentEditor;
        this.onConfirm = onConfirm;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/LangEditor/DeleteConfirmPopup.ui");

        // Event bindings
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn", EventData.of("Action", "cancel"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CancelBtn", EventData.of("Action", "cancel"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmBtn", EventData.of("Action", "confirm"), false);

        // Show the key to be deleted
        if (entryToDelete != null) {
            String keyDisplay = entryToDelete.getKey();
            if (keyDisplay.length() > 40) {
                keyDisplay = keyDisplay.substring(0, 37) + "...";
            }
            cmd.set("#KeyLabel.Text", "\"" + escapeForUi(keyDisplay) + "\"");
        }
    }

    private String escapeForUi(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        super.handleDataEvent(ref, store, data);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder event = new UIEventBuilder();

        if (data.action != null) {
            switch (data.action) {
                case "cancel" -> {
                    // Return to editor without deleting
                    if (parentEditor != null) {
                        player.getPageManager().openCustomPage(ref, store, parentEditor);
                    } else {
                        close();
                    }
                    return;
                }
                case "confirm" -> {
                    // Execute the delete callback
                    if (onConfirm != null) {
                        onConfirm.run();
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
                .build();

        public String action;
    }
}
