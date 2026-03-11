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
    private static KeyBinding cycleScriptKey;
    private static KeyBinding toggleAutoScrollKey;
    private static KeyBinding playPauseAutoScrollKey;
    private static KeyBinding skipLineAutoScrollKey;
    private static KeyBinding prevLineAutoScrollKey;

    public static class PrompterLine {
        public OrderedText text;
        public TeleprompterConfig.TextAlignment alignment;
        public String rawText;
        public boolean isWait;

        public PrompterLine(OrderedText text, TeleprompterConfig.TextAlignment alignment, String rawText, boolean isWait) {
            this.text = text;
            this.alignment = alignment;
            this.rawText = rawText;
            this.isWait = isWait;
        }
    }

    private static List<String> loadedScript = new ArrayList<>();
    private static List<List<PrompterLine>> pages = new ArrayList<>();
    private static int currentPageIndex = 0;

    private static long lastModifiedTime = 0;
    private static int autoSyncTimer = 0;
    private static long lastRenderTime = 0;

    public static boolean isAutoScrolling = false;
    public static boolean isAutoScrollPaused = false;
    public static int scrollLineIndex = 0;
    public static float scrollLineProgress = 0f;
    public static float slideAnimationOffset = 0f;

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

        toggleVisibilityKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, teleprompterCategory));
        nextLineKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, teleprompterCategory));
        prevLineKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.prev", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, teleprompterCategory));
        reloadScriptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, teleprompterCategory));
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, teleprompterCategory));
        cycleScriptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.cycle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, teleprompterCategory));
        toggleAutoScrollKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.autoscroll", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, teleprompterCategory));
        playPauseAutoScrollKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.playpause", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, teleprompterCategory));
        skipLineAutoScrollKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.skipline", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, teleprompterCategory));
        prevLineAutoScrollKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teleprompter.prevline", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, teleprompterCategory));

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
                if (currentPageIndex < pages.size() - 1) {
                    currentPageIndex++;
                    scrollLineIndex = 0;
                    scrollLineProgress = 0f;
                    slideAnimationOffset = 500f;
                }
            }
            while (prevLineKey.wasPressed()) {
                if (currentPageIndex > 0) {
                    currentPageIndex--;
                    scrollLineIndex = 0;
                    scrollLineProgress = 0f;
                    slideAnimationOffset = -500f;
                }
            }
            while (reloadScriptKey.wasPressed()) loadScriptFromFile();

            while (openSettingsKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(ModMenuIntegration.buildScreen(null));
            }

            while (toggleAutoScrollKey.wasPressed()) {
                isAutoScrolling = !isAutoScrolling;
                isAutoScrollPaused = false;

                // FIX: If we turn it ON, and it's currently stuck at the very end of the page, reset to top!
                if (isAutoScrolling && !pages.isEmpty() && currentPageIndex < pages.size()) {
                    if (scrollLineIndex >= pages.get(currentPageIndex).size()) {
                        scrollLineIndex = 0;
                        scrollLineProgress = 0f;
                    }
                }

                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§a[Teleprompter] Auto-Scroll " + (isAutoScrolling ? "Enabled" : "Disabled")), true);
                }
            }

            while (playPauseAutoScrollKey.wasPressed()) {
                if (isAutoScrolling) isAutoScrollPaused = !isAutoScrollPaused;
            }

            while (skipLineAutoScrollKey.wasPressed()) {
                if (isAutoScrolling && !pages.isEmpty()) {
                    scrollLineProgress = 0f;
                    do {
                        scrollLineIndex++;
                        if (scrollLineIndex >= pages.get(currentPageIndex).size()) {
                            if (currentPageIndex < pages.size() - 1) {
                                currentPageIndex++;
                                scrollLineIndex = 0;
                                slideAnimationOffset = 500f;
                            } else {
                                scrollLineIndex = pages.get(currentPageIndex).size() - 1;
                                break;
                            }
                        }
                    } while (pages.get(currentPageIndex).get(scrollLineIndex).rawText.trim().isEmpty());
                }
            }

            while (prevLineAutoScrollKey.wasPressed()) {
                if (isAutoScrolling && !pages.isEmpty()) {
                    scrollLineProgress = 0f;
                    do {
                        scrollLineIndex--;
                        if (scrollLineIndex < 0) {
                            if (currentPageIndex > 0) {
                                currentPageIndex--;
                                scrollLineIndex = pages.get(currentPageIndex).size() - 1;
                                slideAnimationOffset = -500f;
                            } else {
                                scrollLineIndex = 0;
                                break;
                            }
                        }
                    } while (pages.get(currentPageIndex).get(scrollLineIndex).rawText.trim().isEmpty());
                }
            }

            while (cycleScriptKey.wasPressed()) {
                try {
                    List<String> scripts = new ArrayList<>();
                    Files.list(SCRIPTS_DIR).filter(p -> p.toString().endsWith(".txt")).forEach(p -> scripts.add(p.getFileName().toString().replace(".txt", "")));
                    if (!scripts.isEmpty()) {
                        int idx = scripts.indexOf(config.activeScript);
                        idx = (idx + 1) % scripts.size();
                        config.activeScript = scripts.get(idx);
                        AutoConfig.getConfigHolder(TeleprompterConfig.class).save();
                        loadScriptFromFile();
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("§a[Teleprompter] Switched to '" + config.activeScript + "'"), true);
                        }
                    }
                } catch (Exception e) {}
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
            }
            String defaultName = (config.roleName == null || config.roleName.isEmpty()) ? "Player" : config.roleName;

            if (!Files.exists(SCRIPTS_DIR.resolve("default_script.txt"))) {
                String tutorialText = "Thank you for downloading **Teleprompter**!\n\n" +
                        defaultName + ": \"Click the button in the mod settings to open the scripts folder and edit this default script, or add your own!\"\n\n" +
                        "Director: \"You can press **B** to toggle the visibility, or **Right Shift** to open the settings menu.\"\n\n" +
                        "Press **[** or **]** to manually navigate between pages.\n\n" +
                        "---\n\n" +
                        "Welcome to the **second page**!\n\n" +
                        "Director: \"Press **P** to enable *Auto-Scroll*.\"\n" +
                        "You can bind the *Play/Pause* hotkey in your controls to pause the scroll, or skip lines using the *Next/Prev Line* hotkeys!\n\n" +
                        "## WAIT 5 sec\n\n" +
                        "That was a **Wait Command**! The teleprompter paused and counted down for exactly 5 seconds. Notice how it auto-formatted '5 sec' into a clean tracker!\n\n" +
                        "You can format text using **Markdown**:\n" +
                        "Surround text with two asterisks for **bold**.\n" +
                        "Use one asterisk for *italics*.\n" +
                        "Use two underscores for __underlined__ text.\n\n" +
                        "*(Tip: Type three dashes '---' on a new line to force a page break!)*";

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

        currentPageIndex = 0;
        scrollLineIndex = 0;
        scrollLineProgress = 0f;
        slideAnimationOffset = 0f;
        needsRebuild = true;
    }

    public static float getWaitMs(String activeStringRaw) {
        String waitStr = activeStringRaw.substring(8).trim();
        String numStr = waitStr.replaceAll("[^0-9.]", "");
        String letterStr = waitStr.replaceAll("[0-9. ]", "").toUpperCase();
        try {
            float amount = Float.parseFloat(numStr);
            if (letterStr.startsWith("M") && !letterStr.startsWith("MS")) return amount * 60000f;
            if (letterStr.startsWith("MS")) return amount;
            return amount * 1000f;
        } catch (Exception e) { return 0f; }
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
        if (!config.useMarkdown) return Text.literal(text).setStyle(Style.EMPTY.withColor(baseColor));

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

    public static MutableText parseLine(String text, boolean isDialogue) {
        if (text.toUpperCase().startsWith("## WAIT ")) {
            return applyMarkdown(text, 0x888888);
        }

        if (isDialogue) {
            int colonIdx = text.indexOf(':');
            if (colonIdx > 0) {
                String prefixRaw = text.substring(0, colonIdx);
                String cleanPrefix = prefixRaw.replaceAll("[\\*\\_\\-]", " ").trim();

                String detectedName = "";
                int nameStartIdx = 0;

                if (!config.roleName.isEmpty() && cleanPrefix.toLowerCase().endsWith(config.roleName.toLowerCase())) {
                    detectedName = config.roleName;
                    nameStartIdx = prefixRaw.toLowerCase().lastIndexOf(config.roleName.toLowerCase());
                } else {
                    detectedName = cleanPrefix;
                }

                if (!detectedName.isEmpty()) {
                    boolean isMe = !config.roleName.isEmpty() && detectedName.equalsIgnoreCase(config.roleName.trim());
                    int color = isMe ? config.myLineColor : config.dialogueColor;

                    if (nameStartIdx > 0) {
                        MutableText line = applyMarkdown(text.substring(0, nameStartIdx), config.textColor);
                        line.append(applyMarkdown(text.substring(nameStartIdx, colonIdx + 1), brightenColor(color)));
                        line.append(applyMarkdown(text.substring(colonIdx + 1), color));
                        return line;
                    } else {
                        MutableText line = applyMarkdown(text.substring(0, colonIdx + 1), brightenColor(color));
                        line.append(applyMarkdown(text.substring(colonIdx + 1), color));
                        return line;
                    }
                }
            }
        }
        return applyMarkdown(text, config.textColor);
    }

    public static void rebuildPages() {
        pages.clear();
        if (loadedScript.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.textRenderer == null) return;

        List<PrompterLine> currentPage = new ArrayList<>();
        int currentLinesCount = 0;

        for (String paragraph : loadedScript) {
            String rawStr = paragraph.trim();
            if (rawStr.equals("---")) {
                if (!currentPage.isEmpty()) {
                    pages.add(new ArrayList<>(currentPage));
                    currentPage.clear();
                    currentLinesCount = 0;
                }
                continue;
            }

            if (rawStr.isEmpty()) {
                if (currentPage.isEmpty()) continue;
                if (currentLinesCount + 1 > config.maxLinesPerPage) {
                    pages.add(new ArrayList<>(currentPage));
                    currentPage.clear();
                    currentLinesCount = 0;
                    continue;
                }
                currentPage.add(new PrompterLine(OrderedText.EMPTY, config.textAlignment, "", false));
                currentLinesCount++;
                continue;
            }

            boolean isWait = rawStr.toUpperCase().startsWith("## WAIT ");
            boolean isDialogue = rawStr.contains(":") && rawStr.contains("\"");

            String visualStr = rawStr;

            if (isWait) {
                float ms = getWaitMs(rawStr);
                int secs = (int) Math.ceil(ms / 1000f);
                visualStr = "## WAIT " + secs + " SECONDS";
            } else if (isDialogue) {
                visualStr = rawStr.replace("\"", "");
            }

            TeleprompterConfig.TextAlignment align = config.textAlignment;
            if (align == TeleprompterConfig.TextAlignment.SMART) {
                if (isDialogue) {
                    align = TeleprompterConfig.TextAlignment.LEFT;
                } else {
                    align = TeleprompterConfig.TextAlignment.CENTER;
                }
            }

            int effectiveWidth = (int) (config.boxWidth / config.textScale);
            if (effectiveWidth < 10) effectiveWidth = 10;

            List<OrderedText> wrappedLines = client.textRenderer.wrapLines(parseLine(visualStr, isDialogue), effectiveWidth);

            if (currentLinesCount + wrappedLines.size() > config.maxLinesPerPage && !currentPage.isEmpty()) {
                if (wrappedLines.size() <= config.maxLinesPerPage) {
                    pages.add(new ArrayList<>(currentPage));
                    currentPage.clear();
                    currentLinesCount = 0;
                }
            }

            for (OrderedText line : wrappedLines) {
                if (currentLinesCount >= config.maxLinesPerPage) {
                    pages.add(new ArrayList<>(currentPage));
                    currentPage.clear();
                    currentLinesCount = 0;
                }
                currentPage.add(new PrompterLine(line, align, rawStr, isWait));
                currentLinesCount++;
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

        long currentTime = System.currentTimeMillis();
        long deltaMs = lastRenderTime == 0 ? 0 : currentTime - lastRenderTime;
        if (deltaMs > 1000) deltaMs = 1000;
        lastRenderTime = currentTime;

        if (client.isPaused() || (client.currentScreen != null && client.currentScreen != activeConfigScreen)) {
            deltaMs = 0;
        }

        List<PrompterLine> currentLines = pages.get(currentPageIndex);

        float currentWaitSecondsLeft = 0;

        if (isAutoScrolling && !isAutoScrollPaused) {

            while (scrollLineIndex < currentLines.size() && currentLines.get(scrollLineIndex).rawText.trim().isEmpty()) {
                scrollLineIndex++;
                scrollLineProgress = 0f;
            }

            if (scrollLineIndex >= currentLines.size()) {
                if (currentPageIndex < pages.size() - 1) {
                    currentPageIndex++;
                    scrollLineIndex = 0;
                    scrollLineProgress = 0f;
                    slideAnimationOffset = 500f;
                } else {
                    isAutoScrolling = false;
                }
            } else {
                String activeStringRaw = currentLines.get(scrollLineIndex).rawText;
                float msRequired = 0;

                if (activeStringRaw.trim().isEmpty()) {
                    msRequired = 50;
                } else if (currentLines.get(scrollLineIndex).isWait) {
                    msRequired = getWaitMs(activeStringRaw);
                }

                if (msRequired == 0) {
                    int pxWidth = client.textRenderer.getWidth(currentLines.get(scrollLineIndex).text);
                    float estimatedWords = Math.max(1, pxWidth / 30.0f);
                    float minsRequired = estimatedWords / config.autoScrollWpm;
                    msRequired = minsRequired * 60 * 1000;
                } else if (currentLines.get(scrollLineIndex).isWait) {
                    currentWaitSecondsLeft = (msRequired - (msRequired * scrollLineProgress)) / 1000f;
                }

                if (msRequired > 0) {
                    scrollLineProgress += deltaMs / msRequired;
                }

                if (scrollLineProgress >= 1.0f) {
                    scrollLineProgress = 0f;
                    scrollLineIndex++;
                }
            }
        }

        if (slideAnimationOffset != 0) {
            slideAnimationOffset += (0 - slideAnimationOffset) * (deltaMs * 0.015f);
            if (Math.abs(slideAnimationOffset) < 1.0f) slideAnimationOffset = 0;
        }

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        context.getMatrices().pushMatrix();

        int maxLineWidthTextPixels = 0;
        for (PrompterLine line : currentLines) {
            int width = client.textRenderer.getWidth(line.text);
            if (width > maxLineWidthTextPixels) maxLineWidthTextPixels = width;
        }

        boolean hasPageIndicator = config.showPageIndicator && pages.size() > 1;
        String pageStr = "[" + (currentPageIndex + 1) + " / " + pages.size() + "]";
        int strWidth = hasPageIndicator ? client.textRenderer.getWidth(pageStr) : 0;

        if (maxLineWidthTextPixels < strWidth) maxLineWidthTextPixels = strWidth;

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

        float pad = config.boxPadding * config.textScale;
        if (x - pad < 0) x = pad;
        if (y - pad < 0) y = pad;
        if (x + pixelBoxWidth + pad > screenW) x = screenW - pixelBoxWidth - pad;
        if (y + pixelBoxHeight + pad > screenH) y = screenH - pixelBoxHeight - pad;

        int argbBg = (config.backgroundOpacity << 24) | 0x000000;
        context.fill((int)(x - pad), (int)(y - pad), (int)(x + pixelBoxWidth + pad), (int)(y + pixelBoxHeight + pad), argbBg);

        context.enableScissor((int)(x - pad), (int)(y - pad), (int)(x + pixelBoxWidth + pad), (int)(y + pixelBoxHeight + pad));

        context.getMatrices().translate(x, y);
        context.getMatrices().scale(config.textScale, config.textScale);

        int effectiveShadowOpacity = (int) ((config.shadowOpacity / 255.0f) * config.textOpacity);

        for (int i = 0; i < currentLines.size(); i++) {

            OrderedText lineTextToDraw = currentLines.get(i).text;
            boolean isWaitCommand = currentLines.get(i).isWait;

            if (isAutoScrolling && i == scrollLineIndex && currentWaitSecondsLeft > 0 && isWaitCommand) {
                int timeLeft = (int)Math.ceil(currentWaitSecondsLeft);
                String newStr = "## WAIT " + timeLeft + " SECONDS";
                int effectiveWidth = (int) (config.boxWidth / config.textScale);
                if (effectiveWidth < 10) effectiveWidth = 10;

                List<OrderedText> dynamicWrapped = client.textRenderer.wrapLines(parseLine(newStr, false), effectiveWidth);
                if (!dynamicWrapped.isEmpty()) {
                    lineTextToDraw = dynamicWrapped.get(0);
                }
            }

            float lineX = 0;
            int lineWidth = client.textRenderer.getWidth(lineTextToDraw);

            if (currentLines.get(i).alignment == TeleprompterConfig.TextAlignment.CENTER) {
                lineX = (maxLineWidthTextPixels - lineWidth) / 2.0f;
            } else if (currentLines.get(i).alignment == TeleprompterConfig.TextAlignment.RIGHT) {
                lineX = maxLineWidthTextPixels - lineWidth;
            }

            int lineY = (int) (i * (client.textRenderer.fontHeight + 2) + slideAnimationOffset);

            float dimMultiplier = 1.0f;
            if (isAutoScrolling && config.autoScrollHighlightOpacity > 0) {
                if (i == scrollLineIndex) {
                    int[] extractedColor = new int[] {-1};
                    lineTextToDraw.accept((index, style, codePoint) -> {
                        if (extractedColor[0] == -1 && style.getColor() != null) extractedColor[0] = style.getColor().getRgb();
                        return extractedColor[0] == -1;
                    });
                    int inheritedColor = extractedColor[0] != -1 ? extractedColor[0] : config.textColor;

                    int hColor = (config.autoScrollHighlightOpacity << 24) | (inheritedColor & 0xFFFFFF);

                    if (isWaitCommand) {
                        int progWidth = (int) (maxLineWidthTextPixels * scrollLineProgress);
                        context.fill(0, lineY, progWidth, lineY + client.textRenderer.fontHeight, hColor);
                    } else {
                        int progWidth = (int) (lineWidth * scrollLineProgress);
                        context.fill((int)lineX, lineY, (int)lineX + progWidth, lineY + client.textRenderer.fontHeight, hColor);
                    }

                } else {
                    dimMultiplier = 0.4f;
                }
            }

            int dynamicTextOpacity = (int)(config.textOpacity * dimMultiplier);
            int dynamicShadowOpacity = (int)(effectiveShadowOpacity * dimMultiplier);
            int argbText = (dynamicTextOpacity << 24) | 0xFFFFFF;
            int argbShadow = (dynamicShadowOpacity << 24) | 0x000000;

            if (dynamicShadowOpacity > 0) {
                context.drawText(client.textRenderer, stripColorForShadow(lineTextToDraw), (int)lineX + 1, lineY + 1, argbShadow, false);
            }
            if (dynamicTextOpacity > 0) {
                context.drawText(client.textRenderer, lineTextToDraw, (int)lineX, lineY, argbText, false);
            }
        }

        if (hasPageIndicator) {
            float indX = maxLineWidthTextPixels - strWidth;
            int indY = (int) (currentLines.size() * (client.textRenderer.fontHeight + 2) + 2 + slideAnimationOffset);

            int argbText = (config.textOpacity << 24) | 0xFFFFFF;
            int argbShadow = (effectiveShadowOpacity << 24) | 0x000000;

            if (effectiveShadowOpacity > 0) context.drawText(client.textRenderer, pageStr, (int)indX + 1, indY + 1, argbShadow, false);
            if (config.textOpacity > 0) context.drawText(client.textRenderer, pageStr, (int)indX, indY, argbText, false);
        }

        context.disableScissor();
        context.getMatrices().popMatrix();
    }
}