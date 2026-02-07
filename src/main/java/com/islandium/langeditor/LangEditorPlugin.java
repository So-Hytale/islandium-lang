package com.islandium.langeditor;

import com.islandium.core.ui.IslandiumUIRegistry;
import com.islandium.langeditor.command.LangEditorCommand;
import com.islandium.langeditor.ui.pages.LangFileSelectPage;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Language file editor plugin for Hytale.
 * Provides a GUI to search, view, edit, and add entries in .lang files.
 */
public class LangEditorPlugin extends JavaPlugin {

    private static volatile LangEditorPlugin instance;
    private static final Logger LOGGER = Logger.getLogger("LangEditor");

    public LangEditorPlugin(JavaPluginInit init) {
        super(init);
    }

    public static LangEditorPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        instance = this;

        // Register command
        getCommandRegistry().registerCommand(new LangEditorCommand(this));

        // Bouton menu desactive pour le moment
        // IslandiumUIRegistry.getInstance().register(new IslandiumUIRegistry.Entry(
        //         "lang",
        //         "LANGUES",
        //         "Editeur de traductions",
        //         "#69f0ae",
        //         playerRef -> new LangFileSelectPage(playerRef, this, "./mods"),
        //         false
        // ));

        log(Level.INFO, "LangEditor plugin initialized!");
        log(Level.INFO, "Use /langedit to open the editor.");
    }

    public void log(Level level, String message) {
        LOGGER.log(level, "[LangEditor] " + message);
    }
}
