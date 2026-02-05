package com.islandium.langeditor.ui.pages;

import com.islandium.langeditor.LangEditorPlugin;
import com.islandium.langeditor.service.LangFileManager;
import com.islandium.langeditor.util.ColorUtil;
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
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Page for browsing .lang files within a mod folder.
 */
public class LangBrowserPage extends InteractiveCustomUIPage<LangBrowserPage.PageData> {

    private final LangEditorPlugin plugin;
    private final String basePath;
    private List<LangFileInfo> langFiles = new ArrayList<>();

    public LangBrowserPage(@Nonnull PlayerRef playerRef, LangEditorPlugin plugin, String basePath) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.basePath = basePath;
        scanLangFiles();
    }

    private void scanLangFiles() {
        langFiles.clear();

        try {
            Path baseDir = Paths.get(basePath);
            if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
                return;
            }

            try (Stream<Path> stream = Files.walk(baseDir, 5)) {
                stream.filter(p -> p.toString().endsWith(".lang"))
                      .forEach(langPath -> {
                          String relativePath = baseDir.relativize(langPath).toString();
                          String fileName = langPath.getFileName().toString();

                          // Count entries in the file
                          int entryCount = 0;
                          try {
                              entryCount = (int) Files.lines(langPath)
                                      .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                                      .count();
                          } catch (IOException ignored) {}

                          langFiles.add(new LangFileInfo(fileName, relativePath, langPath.toString(), entryCount));
                      });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/LangEditor/LangBrowserPage.ui");

        // Setup events
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BackBtn", EventData.of("Action", "back"), false);

        // Set mod name in header
        String modName = Paths.get(basePath).getFileName().toString();
        cmd.set("#ModNameLabel.Text", modName);

        // Build file list
        buildFileList(cmd, event);
    }

    private void buildFileList(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#FilesList");

        cmd.set("#FileCountLabel.Text", langFiles.size() + " fichier" + (langFiles.size() > 1 ? "s" : "") + " .lang");

        if (langFiles.isEmpty()) {
            cmd.set("#FilesList.Visible", false);
            cmd.set("#NoFilesMessage.Visible", true);
            return;
        }

        cmd.set("#FilesList.Visible", true);
        cmd.set("#NoFilesMessage.Visible", false);

        int index = 0;
        for (LangFileInfo file : langFiles) {
            String rowId = "FileRow" + index;
            String bgColor = index % 2 == 0 ? "#121a26" : "#151d28";

            // Highlight server.lang files
            String nameColor = file.fileName.equals("server.lang") ? "#4ade80" : "#ffffff";

            String rowUi = String.format(
                    "Button #%s { Anchor: (Height: 55, Bottom: 2); Background: (Color: %s); Padding: (Horizontal: 15, Vertical: 8); LayoutMode: Top; }",
                    rowId, bgColor
            );

            cmd.appendInline("#FilesList", rowUi);

            // File name
            cmd.appendInline("#" + rowId, String.format(
                    "Label #Name { Anchor: (Height: 22); Style: (FontSize: 14, TextColor: %s, RenderBold: true, VerticalAlignment: Center); }",
                    nameColor
            ));
            cmd.set("#" + rowId + " #Name.Text", file.fileName);

            // Relative path and entry count
            cmd.appendInline("#" + rowId, String.format(
                    "Label #Path { Anchor: (Height: 18); Style: (FontSize: 11, TextColor: #808080, VerticalAlignment: Center); }"
            ));
            cmd.set("#" + rowId + " #Path.Text", file.relativePath + " (" + file.entryCount + " entrees)");

            // Bind click event
            event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId,
                    EventData.of("SelectFile", String.valueOf(index)), false);

            index++;
        }
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

        // Handle back
        if ("back".equals(data.action)) {
            // Go back to mod selection (assuming /mods is parent)
            String modsPath = Paths.get(basePath).getParent().toString();
            player.getPageManager().openCustomPage(ref, store,
                    new LangFileSelectPage(playerRef, plugin, modsPath)
            );
            return;
        }

        // Handle file selection
        if (data.selectFile != null) {
            try {
                int fileIndex = Integer.parseInt(data.selectFile);
                if (fileIndex >= 0 && fileIndex < langFiles.size()) {
                    LangFileInfo selectedFile = langFiles.get(fileIndex);

                    if (LangFileManager.get().loadFile(selectedFile.fullPath)) {
                        int entryCount = LangFileManager.get().getEntryCount();
                        player.sendMessage(ColorUtil.parse("&aFichier charge: " + selectedFile.fileName + " (" + entryCount + " entrees)"));

                        player.getPageManager().openCustomPage(ref, store,
                                new LangEditorMainPage(playerRef, plugin)
                        );
                    } else {
                        player.sendMessage(ColorUtil.parse("&cImpossible de charger: " + selectedFile.fullPath));
                    }
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        sendUpdate(cmd, event, false);
    }

    private static class LangFileInfo {
        final String fileName;
        final String relativePath;
        final String fullPath;
        final int entryCount;

        LangFileInfo(String fileName, String relativePath, String fullPath, int entryCount) {
            this.fileName = fileName;
            this.relativePath = relativePath;
            this.fullPath = fullPath;
            this.entryCount = entryCount;
        }
    }

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("SelectFile", Codec.STRING), (d, v) -> d.selectFile = v, d -> d.selectFile)
                .build();

        public String action;
        public String selectFile;
    }
}
