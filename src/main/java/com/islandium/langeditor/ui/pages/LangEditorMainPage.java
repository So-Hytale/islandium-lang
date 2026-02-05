package com.islandium.langeditor.ui.pages;

import com.islandium.langeditor.LangEditorPlugin;
import com.islandium.langeditor.model.LangEntry;
import com.islandium.langeditor.service.LangFileManager;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.islandium.langeditor.util.ColorUtil;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Main page for the language file editor with search functionality.
 */
public class LangEditorMainPage extends InteractiveCustomUIPage<LangEditorMainPage.PageData> {

    private final LangEditorPlugin plugin;
    private String searchQuery = "";
    private int currentPage = 0;
    private static final int ENTRIES_PER_PAGE = 30; // 15 par colonne x 2 colonnes

    public LangEditorMainPage(@Nonnull PlayerRef playerRef, LangEditorPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/LangEditor/LangEditorMainPage.ui");

        // Setup events
        event.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchField", EventData.of("@Search", "#SearchField.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BackBtn", EventData.of("Action", "back"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#AddEntryBtn", EventData.of("Action", "add_entry"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SaveBtn", EventData.of("Action", "save"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ReloadBtn", EventData.of("Action", "reload"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageBtn", EventData.of("Action", "prev_page"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageBtn", EventData.of("Action", "next_page"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"), false);

        // Build initial content
        buildHeader(cmd);
        buildResultsList(cmd, event);
    }

    private void buildHeader(UICommandBuilder cmd) {
        LangFileManager manager = LangFileManager.get();
        String fileName = manager.getCurrentFileName();
        if (fileName != null) {
            cmd.set("#FileNameLabel.Text", fileName);
        }

        cmd.set("#EntryCountLabel.Text", manager.getEntryCount() + " entrees");

        // Show unsaved indicator
        if (manager.hasUnsavedChanges()) {
            cmd.set("#UnsavedIndicator.Visible", true);
        } else {
            cmd.set("#UnsavedIndicator.Visible", false);
        }
    }

    private void buildResultsList(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#LeftColumn");
        cmd.clear("#RightColumn");

        List<LangEntry> results = LangFileManager.get().search(searchQuery);
        int totalPages = (int) Math.ceil((double) results.size() / ENTRIES_PER_PAGE);

        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1;
        }

        int startIndex = currentPage * ENTRIES_PER_PAGE;
        int endIndex = Math.min(startIndex + ENTRIES_PER_PAGE, results.size());

        // Update pagination info
        cmd.set("#PageInfo.Text", "Page " + (currentPage + 1) + "/" + Math.max(1, totalPages));
        cmd.set("#ResultCount.Text", results.size() + " resultat" + (results.size() > 1 ? "s" : ""));

        // Update pagination buttons
        cmd.set("#PrevPageBtn.Disabled", currentPage == 0);
        cmd.set("#NextPageBtn.Disabled", currentPage >= totalPages - 1);

        if (results.isEmpty()) {
            cmd.set("#EntriesContainer.Visible", false);
            cmd.set("#NoResultsMessage.Visible", true);
            return;
        }

        cmd.set("#EntriesContainer.Visible", true);
        cmd.set("#NoResultsMessage.Visible", false);

        int itemsOnPage = endIndex - startIndex;
        int halfPoint = (itemsOnPage + 1) / 2; // Premiere colonne peut avoir 1 de plus

        for (int i = startIndex; i < endIndex; i++) {
            LangEntry entry = results.get(i);
            int localIndex = i - startIndex;
            String rowId = "EntryRow" + i;
            String bgColor = localIndex % 2 == 0 ? "#121a26" : "#151d28";

            // Determiner la colonne
            String columnId = localIndex < halfPoint ? "#LeftColumn" : "#RightColumn";

            // Modified indicator
            String modifiedIndicator = entry.isModified() ? "*" : "";
            String modifiedColor = entry.isModified() ? "#ffd700" : "#4ade80";

            // Truncate key if too long
            String keyDisplay = entry.getKey();
            if (keyDisplay.length() > 35) {
                keyDisplay = keyDisplay.substring(0, 32) + "...";
            }

            String rowUi = String.format(
                    "Button #%s { Anchor: (Height: 32, Bottom: 2); Background: (Color: %s); Padding: (Horizontal: 8, Vertical: 4); " +
                    "Label { Text: \"%s%s\"; Style: (FontSize: 12, TextColor: %s, HorizontalAlignment: Center, VerticalAlignment: Center); } }",
                    rowId, bgColor, modifiedIndicator, escapeForUi(keyDisplay), modifiedColor
            );

            cmd.appendInline(columnId, rowUi);

            // Bind click event to edit
            event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId,
                    EventData.of("EditEntry", entry.getKey()), false);
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

        // Handle close
        if ("close".equals(data.action)) {
            close();
            return;
        }

        // Handle search input
        if (data.search != null) {
            searchQuery = data.search;
            currentPage = 0;
            buildResultsList(cmd, event);
            sendUpdate(cmd, event, false);
            return;
        }

        // Handle actions
        if (data.action != null) {
            switch (data.action) {
                case "back" -> {
                    // Go back to mod selection
                    player.getPageManager().openCustomPage(ref, store,
                            new LangFileSelectPage(playerRef, plugin, "./mods")
                    );
                    return;
                }
                case "add_entry" -> {
                    player.getPageManager().openCustomPage(ref, store,
                            new LangEntryEditorPage(playerRef, plugin, null)
                    );
                    return;
                }
                case "save" -> {
                    if (LangFileManager.get().saveFile()) {
                        player.sendMessage(ColorUtil.parse("&aFichier sauvegarde avec succes!"));
                    } else {
                        player.sendMessage(ColorUtil.parse("&cErreur lors de la sauvegarde!"));
                    }
                    buildHeader(cmd);
                    buildResultsList(cmd, event);
                }
                case "reload" -> {
                    String path = LangFileManager.get().getCurrentFilePath();
                    if (path != null && LangFileManager.get().loadFile(path)) {
                        player.sendMessage(ColorUtil.parse("&aFichier recharge!"));
                    }
                    buildHeader(cmd);
                    buildResultsList(cmd, event);
                }
                case "prev_page" -> {
                    if (currentPage > 0) {
                        currentPage--;
                        buildResultsList(cmd, event);
                    }
                }
                case "next_page" -> {
                    currentPage++;
                    buildResultsList(cmd, event);
                }
            }
            sendUpdate(cmd, event, false);
            return;
        }

        // Handle entry selection for editing
        if (data.editEntry != null) {
            LangFileManager.get().getEntry(data.editEntry).ifPresent(entry -> {
                player.getPageManager().openCustomPage(ref, store,
                        new LangEntryEditorPage(playerRef, plugin, entry)
                );
            });
            return;
        }

        sendUpdate(cmd, event, false);
    }

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("@Search", Codec.STRING), (d, v) -> d.search = v, d -> d.search)
                .addField(new KeyedCodec<>("EditEntry", Codec.STRING), (d, v) -> d.editEntry = v, d -> d.editEntry)
                .build();

        public String action;
        public String search;
        public String editEntry;
    }
}
