package net.mehvahdjukaar.moonlight.api.client.gui.misc;

import net.mehvahdjukaar.moonlight.api.client.gui.widget.SyntaxEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A per-line syntax colorer. An implementation only has to classify each character of a line into an RGB color;
 * highlightLine (multi-line, for SyntaxEditBox) and formatter (single-line, for
 * EditBox.addFormatter) are derived from that. Built-in implementations are singletons, and being a
 * functional interface an ad-hoc line -> int[] lambda works too.
 */
@FunctionalInterface
public interface SyntaxHighlighter {

    int FALLBACK_COLOR = ConfigGuiColors.SYNTAX_DEFAULT;

    /** @return one RGB color per character of the line. */
    int[] colors(String line);

    default FormattedCharSequence highlightLine(String line) {
        if (line.isEmpty()) return FormattedCharSequence.EMPTY;
        int[] colors = colors(line);
        List<FormattedCharSequence> parts = new ArrayList<>();
        int runStart = 0;
        for (int i = 1; i <= line.length(); i++) {
            int prev = colorAt(colors, i - 1);
            if (i == line.length() || colorAt(colors, i) != prev) {
                parts.add(FormattedCharSequence.forward(line.substring(runStart, i), Style.EMPTY.withColor(TextColor.fromRgb(prev))));
                runStart = i;
            }
        }
        return FormattedCharSequence.fromList(parts);
    }

    /**
     * A formatter for EditBox.addFormatter bound to box: colors the box's whole value and hands back
     * the requested chunk. The scan is cached per source string so it isn't redone for every rendered chunk.
     */
    default EditBox.TextFormatter formatter(EditBox box) {
        return new EditBox.TextFormatter() {
            private String cachedSource;
            private int[] cachedColors;

            @Override
            public FormattedCharSequence format(String chunk, int displayPos) {
                String source = box.getValue();
                if (!source.equals(cachedSource)) {
                    cachedSource = source;
                    cachedColors = colors(source);
                }
                List<FormattedCharSequence> parts = new ArrayList<>(chunk.length());
                for (int i = 0; i < chunk.length(); i++) {
                    int color = colorAt(cachedColors, displayPos + i);
                    parts.add(FormattedCharSequence.forward(String.valueOf(chunk.charAt(i)),
                            Style.EMPTY.withColor(TextColor.fromRgb(color))));
                }
                return FormattedCharSequence.fromList(parts);
            }
        };
    }

    private static int colorAt(int[] colors, int index) {
        return index >= 0 && index < colors.length ? colors[index] : FALLBACK_COLOR;
    }
}
