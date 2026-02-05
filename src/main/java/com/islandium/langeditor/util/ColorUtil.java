package com.islandium.langeditor.util;

import com.hypixel.hytale.server.core.Message;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaires pour les couleurs et le formatage de texte.
 */
public final class ColorUtil {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("(&#[A-Fa-f0-9]{6}|&[0-9a-fk-or])");

    private ColorUtil() {}

    /**
     * Parse un message avec codes couleur en Message Hytale.
     * Supporte &c, &a, etc. et &#RRGGBB pour les couleurs hex.
     */
    @NotNull
    public static Message parse(@NotNull String message) {
        if (message == null || message.isEmpty()) {
            return Message.raw("");
        }

        message = message.replace('§', '&');

        String[] parts = SPLIT_PATTERN.split(message);
        Matcher matcher = SPLIT_PATTERN.matcher(message);

        List<String> codes = new ArrayList<>();
        while (matcher.find()) {
            codes.add(matcher.group());
        }

        if (codes.isEmpty()) {
            return Message.raw(message);
        }

        Message[] messages = new Message[parts.length];
        String currentColor = "#FFFFFF";

        int codeIndex = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                messages[i] = Message.raw(part).color(currentColor);
            } else {
                messages[i] = Message.raw("");
            }

            if (codeIndex < codes.size()) {
                currentColor = codeToHex(codes.get(codeIndex));
                codeIndex++;
            }
        }

        return Message.join(messages);
    }

    /**
     * Convertit un code couleur (&c, &#RRGGBB) en hex.
     */
    @NotNull
    public static String codeToHex(@NotNull String code) {
        if (code.startsWith("&#")) {
            return "#" + code.substring(2);
        }

        if (code.length() == 2 && code.charAt(0) == '&') {
            char c = Character.toLowerCase(code.charAt(1));
            return switch (c) {
                case '0' -> "#000000";
                case '1' -> "#0000AA";
                case '2' -> "#00AA00";
                case '3' -> "#00AAAA";
                case '4' -> "#AA0000";
                case '5' -> "#AA00AA";
                case '6' -> "#FFAA00";
                case '7' -> "#AAAAAA";
                case '8' -> "#555555";
                case '9' -> "#5555FF";
                case 'a' -> "#55FF55";
                case 'b' -> "#55FFFF";
                case 'c' -> "#FF5555";
                case 'd' -> "#FF55FF";
                case 'e' -> "#FFFF55";
                case 'f' -> "#FFFFFF";
                default -> "#FFFFFF";
            };
        }

        return "#FFFFFF";
    }
}
