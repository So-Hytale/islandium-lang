package com.islandium.langeditor;

import com.islandium.langeditor.command.LangEditorCommand;
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

        log(Level.INFO, "LangEditor plugin initialized!");
        log(Level.INFO, "Use /langedit to open the editor.");
    }

    public void log(Level level, String message) {
        LOGGER.log(level, "[LangEditor] " + message);
    }
}
