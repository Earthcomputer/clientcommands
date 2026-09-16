package net.earthcomputer.clientcommands;

import org.intellij.lang.annotations.MagicConstant;
import org.jspecify.annotations.Nullable;

import java.awt.Component;
import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;

// A class for lost users who try to double-click clientcommands to "run" it.
public class DoubleClickMain {
    // Bit of a pain to read json without a library, but our language json files are simple so this regex will do
    private static final Pattern JSON_ENTRY = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    static void main() {
        Map<String, String> translations = loadTranslations();
        showFancyMessage(
            translations,
            null,
            String.format(
                Locale.ROOT,
                "<p>%s</p><p>%s</p>",
                translations.get("doubleClickMain.message1"),
                String.format(
                    Locale.ROOT,
                    translations.get("doubleClickMain.message2"),
                    "<a href='" + translations.get("doubleClickMain.installFabricUrl") + "'>"
                        + translations.get("doubleClickMain.installFabric") + "</a>",
                    "<a href='" + translations.get("doubleClickMain.installModsUrl") + "'>"
                        + translations.get("doubleClickMain.installMods") + "</a>"
                )
            ),
            "clientcommands",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    // Enables more features like selecting and copy pasting text, and hyperlinks
    private static void showFancyMessage(
        Map<String, String> translations,
        @Nullable Component parentComponent,
        String message,
        String title,
        @MagicConstant(intValues = {JOptionPane.INFORMATION_MESSAGE, JOptionPane.ERROR_MESSAGE}) int messageType
    ) {
        JEditorPane messagePane = new JEditorPane("text/html", "<html><body>" + message + "</body></html>");
        messagePane.setEditable(false);
        messagePane.setOpaque(false);
        messagePane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        messagePane.setFont(UIManager.getFont("Label.font"));
        messagePane.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ENTERED) {
                messagePane.setToolTipText(event.getURL() == null ? null : event.getURL().toString());
                return;
            }
            if (event.getEventType() == HyperlinkEvent.EventType.EXITED) {
                messagePane.setToolTipText(null);
                return;
            }
            if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
                return;
            }

            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                showFancyMessage(
                    translations,
                    messagePane,
                    String.format(
                        Locale.ROOT,
                        translations.get("doubleClickMain.browseUnsupported"),
                        event.getURL()
                    ),
                    translations.get("doubleClickMain.unableToOpenLink"),
                    JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            try {
                Desktop.getDesktop().browse(event.getURL().toURI());
            } catch (IOException | URISyntaxException e) {
                showFancyMessage(
                    translations,
                    messagePane,
                    String.format(
                        Locale.ROOT,
                        translations.get("doubleClickMain.errorOpeningLink"),
                        event.getURL(),
                        e.getMessage()
                    ),
                    translations.get("doubleClickMain.unableToOpenLink"),
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });

        JOptionPane.showMessageDialog(parentComponent, messagePane, title, messageType);
    }

    private static Map<String, String> loadTranslations() {
        Map<String, String> translations = readTranslations("en_us");
        String locale = Locale.getDefault().toLanguageTag().replace('-', '_').toLowerCase(Locale.ROOT);
        if (!locale.equals("en_us")) {
            translations.putAll(readTranslations(locale));
        }
        return translations;
    }

    private static Map<String, String> readTranslations(String locale) {
        String resourceName = "assets/clientcommands/lang/" + locale + ".json";
        try (InputStream stream = DoubleClickMain.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                return Map.of();
            }

            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = JSON_ENTRY.matcher(json);
            Map<String, String> translations = new HashMap<>();
            while (matcher.find()) {
                translations.put(matcher.group(1).translateEscapes(), matcher.group(2).translateEscapes());
            }
            return translations;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + resourceName, e);
        }
    }
}
