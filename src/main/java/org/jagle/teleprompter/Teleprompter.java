package org.jagle.teleprompter;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jagle.teleprompter.config.TeleprompterConfig;
import org.jagle.teleprompter.integration.ModMenuIntegration;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Environment(EnvType.CLIENT)
public class Teleprompter implements ClientModInitializer {

    public static TeleprompterConfig config;
    public static final Path SCRIPTS_DIR = FabricLoader.getInstance().getConfigDir().resolve("scripts");

    public static Screen activeConfigScreen = null;
    public static Runnable livePreviewUpdater = null;
    public static boolean wasScreenOpen = false;
    public static boolean needsRebuild = false;

    private static KeyBinding toggleVisibilityKey;
    private static KeyBinding nextLineKey;
    private static KeyBinding prevLineKey;
    private static KeyBinding reloadScriptKey;
    private static KeyBinding openSettingsKey;

    private static List<String> loadedScript = new ArrayList<>();
    private static List<List<OrderedText>> pages = new ArrayList<>();
    private static int currentPageIndex = 0;

    private static long lastModifiedTime = 0;
    private static int autoSyncTimer = 0;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(TeleprompterConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(TeleprompterConfig.class).getConfig();

        if (config.roleName == null || config.roleName.isEmpty()) {
            if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getSession() != null) {
                config.roleName = MinecraftClient.getInstance().getSession().getUsername();
                AutoConfig.getConfigHolder(TeleprompterConfig.class).save();
            }
        }

        loadScriptFromFile();

        KeyBinding.Category teleprompterCategory = KeyBinding.Category.create(Identifier.of("teleprompter", "main"));

        // Changed from GLFW_KEY_V to GLFW_KEY_B
        toggleVisibilityKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, teleprompterCategory));
        nextLineKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, teleprompterCategory));
        prevLineKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.prev", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, teleprompterCategory));
        reloadScriptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, teleprompterCategory));
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, teleprompterCategory));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            autoSyncTimer++;
            if (autoSyncTimer >= 20) {
                autoSyncTimer = 0;
                try {
                    Path activePath = SCRIPTS_DIR.resolve(config.activeScript + ".txt");
                    if (Files.exists(activePath)) {
                        long currentModified = Files.getLastModifiedTime(activePath).toMillis();
                        if (currentModified != lastModifiedTime && lastModifiedTime != 0) {
                            loadScriptFromFile();
                            if (client.player != null) {
                                client.player.sendMessage(Text.literal("§a[Teleprompter] Auto-synced '" + config.activeScript + "'!"), true);
                            }
                        }
                    }
                } catch (Exception e) {}
            }

            while (toggleVisibilityKey.wasPressed()) {
                config.isVisible = !config.isVisible;
                AutoConfig.getConfigHolder(TeleprompterConfig.class).save();
            }
            while (nextLineKey.wasPressed()) {
                if (currentPageIndex < pages.size() - 1) currentPageIndex++;
            }
            while (prevLineKey.wasPressed()) {
                if (currentPageIndex > 0) currentPageIndex--;
            }
            while (reloadScriptKey.wasPressed()) {
                loadScriptFromFile();
            }
            while (openSettingsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(ModMenuIntegration.buildScreen(null));
                }
            }

            if (client.currentScreen != null && client.currentScreen == activeConfigScreen) {
                if (livePreviewUpdater != null) {
                    livePreviewUpdater.run();
                    wasScreenOpen = true;
                }
            } else if (wasScreenOpen) {
                wasScreenOpen = false;
                AutoConfig.getConfigHolder(TeleprompterConfig.class).save();
                needsRebuild = true;
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;

            if (needsRebuild && client.textRenderer != null) {
                rebuildPages();
                needsRebuild = false;
            }

            if (config.isVisible && !client.options.hudHidden) {
                renderTeleprompter(drawContext, client);
            }
        });
    }

    public static void loadScriptFromFile() {
        try {
            if (!Files.exists(SCRIPTS_DIR)) {
                Files.createDirectories(SCRIPTS_DIR);
                String defaultName = (config.roleName == null || config.roleName.isEmpty()) ? "Player" : config.roleName;

                String tutorialText = "Thank you for downloading Teleprompter!\n\n" +
                        defaultName + ": Open the scripts folder to edit this default script, or put your own txt files in yourself!\n\n" +
                        "Player2: You can press Right Shift at any time to open the settings menu.\n\n" +
                        "Director: You can use Markdown to make text **bold**, *italic*, or __underlined__!\n\n" +
                        "Press [ or ] to navigate between pages!\n" +
                        "(Tip: Type three dashes '---' on a new line to force a page break!)\n" +
                        "---\n" +
                        "This is the 2nd page!";

                Files.writeString(SCRIPTS_DIR.resolve("default_script.txt"), tutorialText);
            }

            Path activePath = SCRIPTS_DIR.resolve(config.activeScript + ".txt");
            if (!Files.exists(activePath)) {
                loadedScript = Arrays.asList("Script file '" + config.activeScript + ".txt' not found.");
            } else {
                lastModifiedTime = Files.getLastModifiedTime(activePath).toMillis();
                loadedScript = Files.readAllLines(activePath);
                if (loadedScript.isEmpty()) loadedScript.add("Script is empty.");
            }
        } catch (Exception e) {
            loadedScript = Arrays.asList("Error loading script file!");
            e.printStackTrace();
        }
        needsRebuild = true;
    }

    private static int brightenColor(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, (int)(r * 1.5 + 75));
        g = Math.min(255, (int)(g * 1.5 + 75));
        b = Math.min(255, (int)(b * 1.5 + 75));
        return (r << 16) | (g << 8) | b;
    }

    public static MutableText applyMarkdown(String text, int baseColor) {
        if (!config.useMarkdown) {
            return Text.literal(text).setStyle(Style.EMPTY.withColor(baseColor));
        }

        MutableText result = Text.empty();
        boolean bold = false, italic = false, underline = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            if (i < text.length() - 1 && text.charAt(i) == '*' && text.charAt(i+1) == '*') {
                if (current.length() > 0) {
                    result.append(Text.literal(current.toString()).setStyle(Style.EMPTY.withColor(baseColor).withBold(bold).withItalic(italic).withUnderline(underline)));
                    current.setLength(0);
                }
                bold = !bold; i++;
            } else if (i < text.length() - 1 && text.charAt(i) == '_' && text.charAt(i+1) == '_') {
                if (current.length() > 0) {
                    result.append(Text.literal(current.toString()).setStyle(Style.EMPTY.withColor(baseColor).withBold(bold).withItalic(italic).withUnderline(underline)));
                    current.setLength(0);
                }
                underline = !underline; i++;
            } else if (text.charAt(i) == '*') {
                if (current.length() > 0) {
                    result.append(Text.literal(current.toString()).setStyle(Style.EMPTY.withColor(baseColor).withBold(bold).withItalic(italic).withUnderline(underline)));
                    current.setLength(0);
                }
                italic = !italic;
            } else {
                current.append(text.charAt(i));
            }
        }
        if (current.length() > 0) {
            result.append(Text.literal(current.toString()).setStyle(Style.EMPTY.withColor(baseColor).withBold(bold).withItalic(italic).withUnderline(underline)));
        }
        return result;
    }

    public static MutableText parseLine(String paragraph) {
        int colonIdx = paragraph.indexOf(':');
        if (colonIdx > 0) {
            String prefixRaw = paragraph.substring(0, colonIdx);
            String cleanPrefix = prefixRaw.replaceAll("[\\*\\_\\-]", " ").trim();
            String[] words = cleanPrefix.split("\\s+");

            String detectedName = "";
            int nameStartIdx = 0;

            if (words.length <= 4 && cleanPrefix.length() < 40) {
                detectedName = cleanPrefix;
                nameStartIdx = 0;
            } else {
                if (!config.roleName.isEmpty()) {
                    String role = config.roleName.trim();
                    if (cleanPrefix.toLowerCase().endsWith(role.toLowerCase())) {
                        detectedName = role;
                        nameStartIdx = prefixRaw.toLowerCase().lastIndexOf(role.toLowerCase());
                    }
                }
            }

            if (!detectedName.isEmpty()) {
                boolean isMe = !config.roleName.isEmpty() && detectedName.equalsIgnoreCase(config.roleName.trim());
                int color = isMe ? config.myLineColor : config.dialogueColor;

                if (nameStartIdx > 0) {
                    MutableText line = applyMarkdown(paragraph.substring(0, nameStartIdx), config.textColor);
                    line.append(applyMarkdown(paragraph.substring(nameStartIdx, colonIdx + 1), brightenColor(color)));
                    line.append(applyMarkdown(paragraph.substring(colonIdx + 1), color));
                    return line;
                } else {
                    MutableText line = applyMarkdown(paragraph.substring(0, colonIdx + 1), brightenColor(color));
                    line.append(applyMarkdown(paragraph.substring(colonIdx + 1), color));
                    return line;
                }
            }
        }
        return applyMarkdown(paragraph, config.textColor);
    }

    public static void rebuildPages() {
        pages.clear();
        if (loadedScript.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.textRenderer == null) return;

        List<OrderedText> currentPage = new ArrayList<>();
        int currentLinesCount = 0;

        for (String paragraph : loadedScript) {
            if (paragraph.trim().equals("---")) {
                if (!currentPage.isEmpty()) {
                    pages.add(new ArrayList<>(currentPage));
                    currentPage.clear();
                    currentLinesCount = 0;
                }
                continue;
            }

            if (paragraph.trim().isEmpty()) {
                if (currentPage.isEmpty()) {
                    continue;
                }
                if (currentLinesCount + 1 > config.maxLinesPerPage) {
                    pages.add(new ArrayList<>(currentPage));
                    currentPage.clear();
                    currentLinesCount = 0;
                    continue;
                }
                currentPage.add(OrderedText.EMPTY);
                currentLinesCount++;
                continue;
            }

            int effectiveWidth = (int) (config.boxWidth / config.textScale);
            if (effectiveWidth < 10) effectiveWidth = 10;

            List<OrderedText> wrappedLines = client.textRenderer.wrapLines(parseLine(paragraph), effectiveWidth);

            if (wrappedLines.size() > config.maxLinesPerPage) {
                for (OrderedText line : wrappedLines) {
                    if (currentLinesCount >= config.maxLinesPerPage) {
                        pages.add(new ArrayList<>(currentPage));
                        currentPage.clear();
                        currentLinesCount = 0;
                    }
                    currentPage.add(line);
                    currentLinesCount++;
                }
            } else {
                if (currentLinesCount + wrappedLines.size() > config.maxLinesPerPage && !currentPage.isEmpty()) {
                    pages.add(new ArrayList<>(currentPage));
                    currentPage.clear();
                    currentLinesCount = 0;
                }
                currentPage.addAll(wrappedLines);
                currentLinesCount += wrappedLines.size();
            }
        }
        if (!currentPage.isEmpty()) pages.add(currentPage);
        if (currentPageIndex >= pages.size()) currentPageIndex = Math.max(0, pages.size() - 1);
    }

    private static OrderedText stripColorForShadow(OrderedText original) {
        return (visitor) -> original.accept((index, style, codePoint) ->
                visitor.accept(index, style.withColor(0x000000), codePoint)
        );
    }

    private void renderTeleprompter(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {
        if (pages.isEmpty() || currentPageIndex >= pages.size()) return;

        double guiScale = client.getWindow().getScaleFactor();
        int screenW = client.getWindow().getFramebufferWidth();
        int screenH = client.getWindow().getFramebufferHeight();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale((float)(1.0 / guiScale), (float)(1.0 / guiScale));

        List<OrderedText> currentLines = pages.get(currentPageIndex);

        int maxLineWidthTextPixels = 0;
        for (OrderedText line : currentLines) {
            int width = client.textRenderer.getWidth(line);
            if (width > maxLineWidthTextPixels) maxLineWidthTextPixels = width;
        }

        boolean hasPageIndicator = config.showPageIndicator && pages.size() > 1;
        String pageStr = "[" + (currentPageIndex + 1) + " / " + pages.size() + "]";
        int strWidth = hasPageIndicator ? client.textRenderer.getWidth(pageStr) : 0;

        if (maxLineWidthTextPixels < strWidth) {
            maxLineWidthTextPixels = strWidth;
        }

        float indicatorExtraHeight = hasPageIndicator ? (client.textRenderer.fontHeight + 4) * config.textScale : 0;

        float pixelBoxWidth = maxLineWidthTextPixels * config.textScale;
        float pixelBoxHeight = currentLines.size() * client.textRenderer.fontHeight * config.textScale + (currentLines.size() - 1) * 2 * config.textScale + 4 * config.textScale + indicatorExtraHeight;

        float x = config.xOffset;
        float y = config.yOffset;

        switch (config.anchor) {
            case TOP_LEFT: break;
            case TOP_CENTER: x += (screenW - pixelBoxWidth) / 2.0f; break;
            case TOP_RIGHT: x += screenW - pixelBoxWidth - (config.xOffset * 2); break;
            case CENTER_LEFT: y += (screenH - pixelBoxHeight) / 2.0f; break;
            case CENTER: x += (screenW - pixelBoxWidth) / 2.0f; y += (screenH - pixelBoxHeight) / 2.0f; break;
            case CENTER_RIGHT: x += screenW - pixelBoxWidth - (config.xOffset * 2); y += (screenH - pixelBoxHeight) / 2.0f; break;
            case BOTTOM_LEFT: y += screenH - pixelBoxHeight - (config.yOffset * 2); break;
            case BOTTOM_CENTER: x += (screenW - pixelBoxWidth) / 2.0f; y += screenH - pixelBoxHeight - (config.yOffset * 2); break;
            case BOTTOM_RIGHT: x += screenW - pixelBoxWidth - (config.xOffset * 2); y += screenH - pixelBoxHeight - (config.yOffset * 2); break;
        }

        float pad = 4 * config.textScale;
        if (x - pad < 0) x = pad;
        if (y - pad < 0) y = pad;
        if (x + pixelBoxWidth + pad > screenW) x = screenW - pixelBoxWidth - pad;
        if (y + pixelBoxHeight + pad > screenH) y = screenH - pixelBoxHeight - pad;

        int argbBg = (config.backgroundOpacity << 24) | 0x000000;
        context.fill((int)(x - 4 * config.textScale), (int)(y - 4 * config.textScale),
                (int)(x + pixelBoxWidth + 4 * config.textScale), (int)(y + pixelBoxHeight), argbBg);

        context.getMatrices().translate(x, y);
        context.getMatrices().scale(config.textScale, config.textScale);

        int effectiveShadowOpacity = (int) ((config.shadowOpacity / 255.0f) * config.textOpacity);

        int argbText = (config.textOpacity << 24) | 0xFFFFFF;
        int argbShadow = (effectiveShadowOpacity << 24) | 0x000000;

        for (int i = 0; i < currentLines.size(); i++) {
            float lineX = 0;
            int lineWidth = client.textRenderer.getWidth(currentLines.get(i));

            if (config.textAlignment == TeleprompterConfig.TextAlignment.CENTER) {
                lineX = (maxLineWidthTextPixels - lineWidth) / 2.0f;
            } else if (config.textAlignment == TeleprompterConfig.TextAlignment.RIGHT) {
                lineX = maxLineWidthTextPixels - lineWidth;
            }

            int lineY = i * (client.textRenderer.fontHeight + 2);

            if (effectiveShadowOpacity > 0) {
                context.drawText(client.textRenderer, stripColorForShadow(currentLines.get(i)), (int)lineX + 1, lineY + 1, argbShadow, false);
            }
            if (config.textOpacity > 0) {
                context.drawText(client.textRenderer, currentLines.get(i), (int)lineX, lineY, argbText, false);
            }
        }

        if (hasPageIndicator) {
            float indX = maxLineWidthTextPixels - strWidth;
            int indY = currentLines.size() * (client.textRenderer.fontHeight + 2) + 2;

            if (effectiveShadowOpacity > 0) {
                context.drawText(client.textRenderer, pageStr, (int)indX + 1, indY + 1, argbShadow, false);
            }
            if (config.textOpacity > 0) {
                context.drawText(client.textRenderer, pageStr, (int)indX, indY, argbText, false);
            }
        }

        context.getMatrices().popMatrix();
    }
}