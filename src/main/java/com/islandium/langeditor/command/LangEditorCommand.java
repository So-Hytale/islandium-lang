package com.islandium.langeditor.command;

import com.islandium.langeditor.LangEditorPlugin;
import com.islandium.langeditor.service.LangFileManager;
import com.islandium.langeditor.ui.pages.LangEditorMainPage;
import com.islandium.langeditor.ui.pages.LangFileSelectPage;
import com.islandium.langeditor.util.ColorUtil;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Command to open the language file editor.
 *
 * Usage:
 *   /langedit                    - Opens the mod selection GUI
 *   /langedit <file_path>        - Opens a specific .lang file
 *   /langedit list <directory>   - List .lang files in a directory
 */
public class LangEditorCommand extends AbstractCommand {

    private final LangEditorPlugin plugin;
    private static final String DEFAULT_MODS_PATH = "./mods";

    private final OptionalArg<String> actionArg;
    private final OptionalArg<String> argValue;

    public LangEditorCommand(@NotNull LangEditorPlugin plugin) {
        super("langedit", "Ouvre l'editeur de fichiers de langue");
        this.plugin = plugin;

        actionArg = withOptionalArg("action", "Action (list) ou chemin du fichier", ArgTypes.STRING);
        argValue = withOptionalArg("value", "Valeur supplementaire", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String action = ctx.get(actionArg);

        // No action = open mod selection
        if (action == null || action.isEmpty()) {
            return executeSelect(ctx);
        }

        // Handle "list" subcommand
        if (action.equalsIgnoreCase("list")) {
            String directory = ctx.get(argValue);
            return executeList(ctx, directory != null ? directory : DEFAULT_MODS_PATH);
        }

        // Otherwise treat action as file path
        return executeOpen(ctx, action);
    }

    /**
     * Opens the mod selection page.
     */
    private CompletableFuture<Void> executeSelect(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(ColorUtil.parse("&cCette commande doit etre executee par un joueur."));
            return CompletableFuture.completedFuture(null);
        }

        Player player = ctx.senderAs(Player.class);

        var ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(ColorUtil.parse("&cErreur: impossible d'ouvrir l'interface."));
            return CompletableFuture.completedFuture(null);
        }

        var store = ref.getStore();
        var world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store.getExternalData()).getWorld();

        return CompletableFuture.runAsync(() -> {
            var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                ctx.sendMessage(ColorUtil.parse("&cErreur: PlayerRef non trouve."));
                return;
            }

            LangFileSelectPage page = new LangFileSelectPage(playerRef, plugin, DEFAULT_MODS_PATH);
            player.getPageManager().openCustomPage(ref, store, page);
        }, world);
    }

    /**
     * Opens a specific .lang file.
     */
    private CompletableFuture<Void> executeOpen(CommandContext ctx, String path) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(ColorUtil.parse("&cCette commande doit etre executee par un joueur."));
            return CompletableFuture.completedFuture(null);
        }

        Player player = ctx.senderAs(Player.class);

        // Validate file extension
        if (!path.endsWith(".lang")) {
            ctx.sendMessage(ColorUtil.parse("&cLe fichier doit avoir l'extension .lang"));
            return CompletableFuture.completedFuture(null);
        }

        // Try to load the file
        if (!LangFileManager.get().loadFile(path)) {
            ctx.sendMessage(ColorUtil.parse("&cImpossible de charger le fichier: " + path));
            ctx.sendMessage(ColorUtil.parse("&7Verifiez que le chemin est correct."));
            return CompletableFuture.completedFuture(null);
        }

        int entryCount = LangFileManager.get().getEntryCount();
        ctx.sendMessage(ColorUtil.parse("&aFichier charge: " + LangFileManager.get().getCurrentFileName() + " (" + entryCount + " entrees)"));

        var ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(ColorUtil.parse("&cErreur: impossible d'ouvrir l'interface."));
            return CompletableFuture.completedFuture(null);
        }

        var store = ref.getStore();
        var world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store.getExternalData()).getWorld();

        return CompletableFuture.runAsync(() -> {
            var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                ctx.sendMessage(ColorUtil.parse("&cErreur: PlayerRef non trouve."));
                return;
            }

            LangEditorMainPage page = new LangEditorMainPage(playerRef, plugin);
            player.getPageManager().openCustomPage(ref, store, page);
        }, world);
    }

    /**
     * Lists .lang files in a directory.
     */
    private CompletableFuture<Void> executeList(CommandContext ctx, String directory) {
        List<Path> langFiles = LangFileManager.get().findLangFiles(directory);

        if (langFiles.isEmpty()) {
            ctx.sendMessage(ColorUtil.parse("&eAucun fichier .lang trouve dans: " + directory));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sendMessage(ColorUtil.parse("&aFichiers .lang trouves (" + langFiles.size() + "):"));
        for (Path file : langFiles) {
            ctx.sendMessage(ColorUtil.parse("&7 - " + file.toString()));
        }

        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<List<String>> tabComplete(CommandContext ctx, String partial) {
        if (!ctx.provided(actionArg)) {
            return CompletableFuture.completedFuture(
                    List.of("list")
                            .stream()
                            .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                            .toList()
            );
        }
        return CompletableFuture.completedFuture(List.of());
    }
}
