package dev.customcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    /** MiniMessage-String zu Component. */
    public static Component mm(String s) {
        return MM.deserialize(s == null ? "" : s);
    }

    /** Wie mm(), aber ohne die Standard-Kursivschrift von Item-Namen und Lore. */
    public static Component item(String s) {
        return mm(s).decoration(TextDecoration.ITALIC, false);
    }
}
