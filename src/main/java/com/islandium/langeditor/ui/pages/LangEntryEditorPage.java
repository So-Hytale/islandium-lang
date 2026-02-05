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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Page for editing or adding a language entry.
 */
public class LangEntryEditorPage extends InteractiveCustomUIPage<LangEntryEditorPage.PageData> {

    private final LangEditorPlugin plugin;
    private final LangEntry existingEntry;
    private final boolean isNewEntry;

    private String currentKey;
    private String currentValue;
    private boolean codeMode = false; // false = apercu, true = code

    // Historique pour annulation (Ctrl+Z manuel)
    private static final int MAX_HISTORY = 20;
    private final java.util.Deque<String> valueHistory = new java.util.ArrayDeque<>();
    private String lastSavedValue = "";

    // Common color presets
    private static final String[] COLOR_PRESETS = {
            "#ffffff", "#4ade80", "#f87171", "#ffd700", "#60a5fa", "#c084fc", "#808080"
    };

    public LangEntryEditorPage(@Nonnull PlayerRef playerRef, LangEditorPlugin plugin, LangEntry entry) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.existingEntry = entry;
        this.isNewEntry = (entry == null);

        if (entry != null) {
            this.currentKey = entry.getKey();
            this.currentValue = entry.getValue();
            this.lastSavedValue = entry.getValue();
        } else {
            this.currentKey = "";
            this.currentValue = "";
            this.lastSavedValue = "";
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/LangEditor/LangEntryEditorPage.ui");

        // Setup events
        event.addEventBinding(CustomUIEventBindingType.ValueChanged, "#KeyField", EventData.of("@Key", "#KeyField.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ValueField", EventData.of("@Value", "#ValueField.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SaveBtn", EventData.of("Action", "save"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CancelBtn", EventData.of("Action", "cancel"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteBtn", EventData.of("Action", "delete"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "cancel"), false);

        // Color preset buttons
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            event.addEventBinding(CustomUIEventBindingType.Activating, "#ColorBtn" + i,
                    EventData.of("InsertColor", COLOR_PRESETS[i]), false);
        }

        // Special format buttons
        event.addEventBinding(CustomUIEventBindingType.Activating, "#InsertNewlineBtn", EventData.of("Action", "insert_newline"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#InsertColorBtn", EventData.of("Action", "open_colorpicker"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CleanTagsBtn", EventData.of("Action", "clean_tags"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#UndoBtn", EventData.of("Action", "undo"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleViewBtn", EventData.of("Action", "toggle_view"), false);

        // Build initial content
        buildForm(cmd);
        buildColorPresets(cmd, event);
        buildPreview(cmd);
        updateViewMode(cmd);
    }

    private void buildForm(UICommandBuilder cmd) {
        // Title
        String title = isNewEntry ? "Nouvelle Entree" : "Modifier Entree";
        cmd.set("#PageTitle.Text", title);

        // Set current values
        cmd.set("#KeyField.Value", currentKey);
        // Convertir \n en vrais retours a la ligne pour l'affichage
        cmd.set("#ValueField.Value", toDisplayFormat(currentValue));

        // Hide delete button for new entries
        cmd.set("#DeleteBtn.Visible", !isNewEntry);
    }

    /**
     * Convertit le format stockage (\n literal) en format affichage (vrais retours a la ligne)
     */
    private String toDisplayFormat(String value) {
        return value.replace("\\n", "\n");
    }

    /**
     * Convertit le format affichage (vrais retours a la ligne) en format stockage (\n literal)
     */
    private String toStorageFormat(String value) {
        return value.replace("\n", "\\n");
    }

    private void buildColorPresets(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#ColorPresets");

        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            String color = COLOR_PRESETS[i];
            String btnId = "ColorBtn" + i;

            // Boutons de couleur compacts avec espacement
            String btnUi = String.format(
                    "Button #%s { Anchor: (Width: 34, Height: 34, Right: 8); Background: (Color: %s); }",
                    btnId, color
            );

            cmd.appendInline("#ColorPresets", btnUi);
        }
    }

    private void buildPreview(UICommandBuilder cmd) {
        cmd.clear("#PreviewContent");

        // Separer par \n pour creer des lignes distinctes
        String[] lines = currentValue.split("\\\\n");

        for (String line : lines) {
            StringBuilder lineUi = new StringBuilder();
            lineUi.append("Group { LayoutMode: Left; Anchor: (Height: 24); ");

            // Parser et afficher les segments avec couleurs
            parseLineWithColors(line, lineUi);

            lineUi.append(" }");
            cmd.appendInline("#PreviewContent", lineUi.toString());
        }

        cmd.set("#ColorInfo.Visible", false);
    }

    /**
     * Parse une ligne et extrait les segments colores
     */
    private void parseLineWithColors(String text, StringBuilder ui) {
        String defaultColor = "#e0e0e0";

        // Pattern pour <color is="#HEX">contenu</color> (contenu peut contenir d'autres balises)
        Pattern colorPattern = Pattern.compile("<color is=\"([^\"]+)\">([\\s\\S]*?)</color>");
        Matcher matcher = colorPattern.matcher(text);

        int lastEnd = 0;
        boolean hasContent = false;

        while (matcher.find()) {
            // Texte avant cette balise color
            if (matcher.start() > lastEnd) {
                String before = text.substring(lastEnd, matcher.start());
                before = stripFormattingTags(before);
                if (!before.isEmpty()) {
                    appendColoredLabel(ui, before, defaultColor);
                    hasContent = true;
                }
            }

            // Texte colore (enlever les balises de formatage internes)
            String color = matcher.group(1);
            String content = stripFormattingTags(matcher.group(2));
            if (!content.isEmpty()) {
                appendColoredLabel(ui, content, color);
                hasContent = true;
            }

            lastEnd = matcher.end();
        }

        // Texte restant apres la derniere balise
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            remaining = stripFormattingTags(remaining);
            if (!remaining.isEmpty()) {
                appendColoredLabel(ui, remaining, defaultColor);
                hasContent = true;
            }
        }

        // Si ligne vide, ajouter un espace
        if (!hasContent) {
            appendColoredLabel(ui, " ", defaultColor);
        }
    }

    private String stripFormattingTags(String text) {
        return text
                .replaceAll("<b>", "")
                .replaceAll("</b>", "")
                .replaceAll("<i>", "")
                .replaceAll("</i>", "")
                .replaceAll("<color[^>]*>", "")
                .replaceAll("</color>", "");
    }

    private void appendColoredLabel(StringBuilder ui, String text, String color) {
        ui.append(String.format(
                "Label { Text: \"%s\"; Style: (FontSize: 14, TextColor: %s); } ",
                escapeForUi(text), color
        ));
    }

    private void updateViewMode(UICommandBuilder cmd) {
        cmd.set("#PreviewView.Visible", !codeMode);
        cmd.set("#ValueField.Visible", codeMode);

        if (codeMode) {
            cmd.set("#ToggleViewBtn.Text", "Mode Apercu");
            cmd.set("#EditorTitle.Text", "CODE SOURCE");
            cmd.set("#EditorTitle.Style.TextColor", "#60a5fa");
        } else {
            cmd.set("#ToggleViewBtn.Text", "Mode Code");
            cmd.set("#EditorTitle.Text", "APERCU");
            cmd.set("#EditorTitle.Style.TextColor", "#ffd700");
        }
    }

    private String escapeForUi(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
    }

    /**
     * Sauvegarde l'etat actuel dans l'historique pour permettre l'annulation
     */
    private void pushToHistory() {
        // Ne pas ajouter si c'est la meme valeur que le dernier etat
        if (!valueHistory.isEmpty() && valueHistory.peekLast().equals(currentValue)) {
            return;
        }
        valueHistory.addLast(currentValue);
        if (valueHistory.size() > MAX_HISTORY) {
            valueHistory.removeFirst();
        }
    }

    /**
     * Restaure l'etat precedent depuis l'historique
     */
    private boolean popFromHistory() {
        if (valueHistory.isEmpty()) {
            return false;
        }
        currentValue = valueHistory.pollLast();
        return true;
    }

    /**
     * Nettoie les balises mal formatees:
     * - Supprime les retours a la ligne juste avant </color>, </b>, </i>
     * - Supprime les retours a la ligne juste apres <color...>, <b>, <i>
     * - Supprime les espaces multiples
     */
    private String cleanTags(String value) {
        String result = value;

        // Supprimer \n juste avant les balises fermantes
        result = result.replaceAll("\\\\n\\s*</color>", "</color>");
        result = result.replaceAll("\\\\n\\s*</b>", "</b>");
        result = result.replaceAll("\\\\n\\s*</i>", "</i>");

        // Supprimer \n juste apres les balises ouvrantes
        result = result.replaceAll("<color([^>]*)>\\s*\\\\n", "<color$1>");
        result = result.replaceAll("<b>\\s*\\\\n", "<b>");
        result = result.replaceAll("<i>\\s*\\\\n", "<i>");

        // Supprimer les espaces multiples avant/apres les balises
        result = result.replaceAll("\\s+</color>", "</color>");
        result = result.replaceAll("\\s+</b>", "</b>");
        result = result.replaceAll("\\s+</i>", "</i>");
        result = result.replaceAll("<color([^>]*)>\\s+", "<color$1>");
        result = result.replaceAll("<b>\\s+", "<b>");
        result = result.replaceAll("<i>\\s+", "<i>");

        // Supprimer les balises color vides
        result = result.replaceAll("<color[^>]*></color>", "");
        result = result.replaceAll("<b></b>", "");
        result = result.replaceAll("<i></i>", "");

        return result;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        super.handleDataEvent(ref, store, data);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder event = new UIEventBuilder();

        // Update current values from input
        if (data.key != null) {
            currentKey = data.key;
        }
        if (data.value != null) {
            // Convertir les vrais retours a la ligne en \n pour le stockage
            currentValue = toStorageFormat(data.value);
            buildPreview(cmd);
            sendUpdate(cmd, event, false);
            return;
        }

        // Handle color insertion
        if (data.insertColor != null) {
            pushToHistory();
            String colorTag = "<color is=\"" + data.insertColor + "\"></color>";
            currentValue = currentValue + colorTag;
            cmd.set("#ValueField.Value", toDisplayFormat(currentValue));
            buildPreview(cmd);
            sendUpdate(cmd, event, false);
            return;
        }

        // Handle actions
        if (data.action != null) {
            switch (data.action) {
                case "save" -> {
                    if (currentKey.isEmpty()) {
                        player.sendMessage(ColorUtil.parse("&cLa cle ne peut pas etre vide!"));
                        sendUpdate(cmd, event, false);
                        return;
                    }

                    if (isNewEntry) {
                        LangEntry newEntry = LangFileManager.get().addEntry(currentKey, currentValue);
                        if (newEntry != null) {
                            player.sendMessage(ColorUtil.parse("&aEntree ajoutee: " + currentKey));
                            // Go back to main page
                            player.getPageManager().openCustomPage(ref, store,
                                    new LangEditorMainPage(playerRef, plugin)
                            );
                        } else {
                            player.sendMessage(ColorUtil.parse("&cCette cle existe deja!"));
                        }
                    } else {
                        String originalKey = existingEntry.getKey();
                        if (LangFileManager.get().updateEntry(originalKey, currentKey, currentValue)) {
                            player.sendMessage(ColorUtil.parse("&aEntree modifiee: " + currentKey));
                            player.getPageManager().openCustomPage(ref, store,
                                    new LangEditorMainPage(playerRef, plugin)
                            );
                        } else {
                            player.sendMessage(ColorUtil.parse("&cErreur lors de la modification!"));
                        }
                    }
                    return;
                }
                case "delete" -> {
                    if (!isNewEntry && existingEntry != null) {
                        // Ouvrir la popup de confirmation
                        LangEntryEditorPage thisPage = this;
                        player.getPageManager().openCustomPage(ref, store,
                                new DeleteConfirmPage(playerRef, plugin, existingEntry, thisPage, () -> {
                                    // Callback quand confirmation - supprimer l'entree
                                    if (LangFileManager.get().deleteEntry(existingEntry.getKey())) {
                                        player.sendMessage(ColorUtil.parse("&eEntree supprimee: " + existingEntry.getKey()));
                                        player.getPageManager().openCustomPage(ref, store,
                                                new LangEditorMainPage(playerRef, plugin)
                                        );
                                    } else {
                                        player.sendMessage(ColorUtil.parse("&cErreur lors de la suppression!"));
                                    }
                                })
                        );
                    }
                    return;
                }
                case "cancel" -> {
                    player.getPageManager().openCustomPage(ref, store,
                            new LangEditorMainPage(playerRef, plugin)
                    );
                    return;
                }
                case "insert_newline" -> {
                    pushToHistory();
                    currentValue = currentValue + "\\n";
                    cmd.set("#ValueField.Value", toDisplayFormat(currentValue));
                    buildPreview(cmd);
                }
                case "open_colorpicker" -> {
                    // Ouvrir le color picker en popup
                    LangEntryEditorPage thisPage = this;
                    player.getPageManager().openCustomPage(ref, store,
                            new ColorPickerPage(playerRef, plugin, (selectedColor) -> {
                                // Callback quand couleur selectionnee - inserere balise color
                                pushToHistory();
                                currentValue = currentValue + "<color is=\"" + selectedColor + "\"></color>";
                            }, thisPage)
                    );
                    return;
                }
                case "toggle_view" -> {
                    codeMode = !codeMode;
                    updateViewMode(cmd);
                    if (codeMode) {
                        // Passage en mode code - afficher avec vrais retours a la ligne
                        cmd.set("#ValueField.Value", toDisplayFormat(currentValue));
                    } else {
                        // Retour en mode apercu, rafraichir l'apercu
                        buildPreview(cmd);
                    }
                }
                case "clean_tags" -> {
                    // Sauvegarder avant modification
                    pushToHistory();
                    // Nettoyer les balises mal formatees
                    currentValue = cleanTags(currentValue);
                    cmd.set("#ValueField.Value", toDisplayFormat(currentValue));
                    buildPreview(cmd);
                    player.sendMessage(ColorUtil.parse("&aBalises nettoyees!"));
                }
                case "undo" -> {
                    if (popFromHistory()) {
                        cmd.set("#ValueField.Value", toDisplayFormat(currentValue));
                        buildPreview(cmd);
                        player.sendMessage(ColorUtil.parse("&eAnnulation effectuee"));
                    } else {
                        // Restaurer la version sauvegardee originale
                        if (!currentValue.equals(lastSavedValue)) {
                            pushToHistory();
                            currentValue = lastSavedValue;
                            cmd.set("#ValueField.Value", toDisplayFormat(currentValue));
                            buildPreview(cmd);
                            player.sendMessage(ColorUtil.parse("&eRestauration de la version sauvegardee"));
                        } else {
                            player.sendMessage(ColorUtil.parse("&7Rien a annuler"));
                        }
                    }
                }
            }
            sendUpdate(cmd, event, false);
            return;
        }

        sendUpdate(cmd, event, false);
    }

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("@Key", Codec.STRING), (d, v) -> d.key = v, d -> d.key)
                .addField(new KeyedCodec<>("@Value", Codec.STRING), (d, v) -> d.value = v, d -> d.value)
                .addField(new KeyedCodec<>("InsertColor", Codec.STRING), (d, v) -> d.insertColor = v, d -> d.insertColor)
                .build();

        public String action;
        public String key;
        public String value;
        public String insertColor;
    }
}
