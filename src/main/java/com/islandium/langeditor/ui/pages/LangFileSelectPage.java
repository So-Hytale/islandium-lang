package com.islandium.langeditor.ui.pages;

import com.islandium.langeditor.LangEditorPlugin;
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
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Page for selecting a mod folder to edit its server.lang file.
 */
public class LangFileSelectPage extends InteractiveCustomUIPage<LangFileSelectPage.PageData> {

    private final LangEditorPlugin plugin;
    private final String modsPath;
    private List<ModInfo> availableMods = new ArrayList<>();

    public LangFileSelectPage(@Nonnull PlayerRef playerRef, LangEditorPlugin plugin, String modsPath) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.modsPath = modsPath;
        scanMods();
    }

    private void scanMods() {
        availableMods.clear();

        try {
            Path modsDir = Paths.get(modsPath);
            if (!Files.exists(modsDir) || !Files.isDirectory(modsDir)) {
                return;
            }

            try (Stream<Path> stream = Files.list(modsDir)) {
                stream.filter(Files::isDirectory)
                      .forEach(modDir -> {
                          String modName = modDir.getFileName().toString();

                          // Check for server.lang in common locations
                          String[] possiblePaths = {
                              "Server/Languages/en-US/server.lang",
                              "Server/Languages/fr-FR/server.lang",
                              "Common/Languages/en-US/server.lang",
                              "Languages/en-US/server.lang"
                          };

                          List<String> foundLangs = new ArrayList<>();
                          for (String relativePath : possiblePaths) {
                              Path langPath = modDir.resolve(relativePath);
                              if (Files.exists(langPath)) {
                                  foundLangs.add(relativePath);
                              }
                          }

                          // Also scan for any .lang files
                          try (Stream<Path> langStream = Files.walk(modDir, 4)) {
                              long langCount = langStream.filter(p -> p.toString().endsWith(".lang")).count();
                              if (langCount > 0 || !foundLangs.isEmpty()) {
                                  availableMods.add(new ModInfo(modName, modDir.toString(), foundLangs, (int) langCount));
                              }
                          } catch (IOException ignored) {}
                      });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/LangEditor/LangFileSelectPage.ui");

        // Setup events
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshBtn", EventData.of("Action", "refresh"), false);

        // Build mod list
        buildModList(cmd, event);
    }

    private void buildModList(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#ModsList");

        cmd.set("#ModCountLabel.Text", availableMods.size() + " mod" + (availableMods.size() > 1 ? "s" : "") + " avec fichiers .lang");

        if (availableMods.isEmpty()) {
            cmd.set("#ModsList.Visible", false);
            cmd.set("#NoModsMessage.Visible", true);
            return;
        }

        cmd.set("#ModsList.Visible", true);
        cmd.set("#NoModsMessage.Visible", false);

        int index = 0;
        for (ModInfo mod : availableMods) {
            String rowId = "ModRow" + index;
            String bgColor = index % 2 == 0 ? "#121a26" : "#151d28";

            String rowUi = String.format(
                    "Button #%s { Anchor: (Height: 65, Bottom: 3); Background: (Color: %s); Padding: (Horizontal: 15, Vertical: 8); LayoutMode: Top; }",
                    rowId, bgColor
            );

            cmd.appendInline("#ModsList", rowUi);

            // Mod name
            cmd.appendInline("#" + rowId, String.format(
                    "Label #Name { Anchor: (Height: 24); Style: (FontSize: 15, TextColor: #ffd700, RenderBold: true, VerticalAlignment: Center); }"
            ));
            cmd.set("#" + rowId + " #Name.Text", mod.name);

            // Info line
            String infoText = mod.totalLangFiles + " fichier" + (mod.totalLangFiles > 1 ? "s" : "") + " .lang";
            if (!mod.availableLangs.isEmpty()) {
                infoText += " | server.lang disponible";
            }

            cmd.appendInline("#" + rowId, String.format(
                    "Label #Info { Anchor: (Height: 18); Style: (FontSize: 11, TextColor: #808080, VerticalAlignment: Center); }"
            ));
            cmd.set("#" + rowId + " #Info.Text", infoText);

            // Language options (if multiple)
            if (mod.availableLangs.size() > 1) {
                cmd.appendInline("#" + rowId, String.format(
                        "Label #Langs { Anchor: (Height: 16); Style: (FontSize: 10, TextColor: #4ade80, VerticalAlignment: Center); }"
                ));
                cmd.set("#" + rowId + " #Langs.Text", "Langues: " + String.join(", ",
                    mod.availableLangs.stream()
                        .map(p -> p.contains("en-US") ? "EN" : p.contains("fr-FR") ? "FR" : "?")
                        .toList()
                ));
            }

            // Bind click event
            event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId,
                    EventData.of("SelectMod", mod.name), false);

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

        // Handle refresh
        if ("refresh".equals(data.action)) {
            scanMods();
            buildModList(cmd, event);
            sendUpdate(cmd, event, false);
            return;
        }

        // Handle mod selection
        if (data.selectMod != null) {
            ModInfo selectedMod = availableMods.stream()
                    .filter(m -> m.name.equals(data.selectMod))
                    .findFirst()
                    .orElse(null);

            if (selectedMod == null) {
                player.sendMessage(ColorUtil.parse("&cMod non trouve!"));
                sendUpdate(cmd, event, false);
                return;
            }

            // Try to load the first available server.lang
            String langPath = null;
            if (!selectedMod.availableLangs.isEmpty()) {
                // Prefer en-US, then fr-FR
                for (String lang : selectedMod.availableLangs) {
                    if (lang.contains("en-US")) {
                        langPath = selectedMod.path + "/" + lang;
                        break;
                    }
                }
                if (langPath == null) {
                    langPath = selectedMod.path + "/" + selectedMod.availableLangs.get(0);
                }
            }

            if (langPath == null) {
                // No server.lang found, open language selection page
                player.getPageManager().openCustomPage(ref, store,
                        new LangBrowserPage(playerRef, plugin, selectedMod.path)
                );
                return;
            }

            // Load the file
            if (LangFileManager.get().loadFile(langPath)) {
                int entryCount = LangFileManager.get().getEntryCount();
                player.sendMessage(ColorUtil.parse("&aFichier charge: " + selectedMod.name + " (" + entryCount + " entrees)"));

                player.getPageManager().openCustomPage(ref, store,
                        new LangEditorMainPage(playerRef, plugin)
                );
            } else {
                player.sendMessage(ColorUtil.parse("&cImpossible de charger: " + langPath));
            }
            return;
        }

        sendUpdate(cmd, event, false);
    }

    private static class ModInfo {
        final String name;
        final String path;
        final List<String> availableLangs;
        final int totalLangFiles;

        ModInfo(String name, String path, List<String> availableLangs, int totalLangFiles) {
            this.name = name;
            this.path = path;
            this.availableLangs = availableLangs;
            this.totalLangFiles = totalLangFiles;
        }
    }

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("SelectMod", Codec.STRING), (d, v) -> d.selectMod = v, d -> d.selectMod)
                .build();

        public String action;
        public String selectMod;
    }
}
