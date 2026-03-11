package org.jagle.teleprompter.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "teleprompter")
public class TeleprompterConfig implements ConfigData {

    public boolean isVisible = false;
    public String activeScript = "default_script";

    // Formatting & Colors
    public String roleName = "";
    public int textColor = 0xFFFFFF;
    public int dialogueColor = 0xAAAAAA;
    public int myLineColor = 0x55FF55;

    public int textOpacity = 255;
    public int shadowOpacity = 150;
    public int backgroundOpacity = 100;

    // Positioning
    public Anchor anchor = Anchor.TOP_LEFT;
    public int xOffset = 0;
    public int yOffset = 0;
    public float textScale = 2.0f;
    public int boxWidth = 800;
    public int maxLinesPerPage = 12; // Increased to 12 as a much better default!

    // Toggles
    public boolean useMarkdown = true;
    public boolean showPageIndicator = true;
    public TextAlignment textAlignment = TextAlignment.LEFT;

    public enum Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    public enum TextAlignment {
        LEFT, CENTER, RIGHT
    }
}