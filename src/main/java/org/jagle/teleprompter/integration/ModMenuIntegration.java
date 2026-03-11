package org.jagle.teleprompter.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jagle.teleprompter.Teleprompter;
import org.jagle.teleprompter.config.TeleprompterConfig;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuIntegration::buildScreen;
    }

    public static Screen buildScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("text.autoconfig.teleprompter.title"))
                .setTransparentBackground(true)
                .setDoesConfirmSave(false);

        ConfigCategory mainCat = builder.getOrCreateCategory(Text.literal("Main"));
        ConfigCategory visualCat = builder.getOrCreateCategory(Text.literal("Visuals"));
        ConfigCategory formatCat = builder.getOrCreateCategory(Text.literal("Formatting"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        List<String> scripts = new ArrayList<>();
        try {
            if (Files.exists(Teleprompter.SCRIPTS_DIR)) {
                Files.list(Teleprompter.SCRIPTS_DIR)
                        .filter(p -> p.toString().endsWith(".txt"))
                        .forEach(p -> scripts.add(p.getFileName().toString().replace(".txt", "")));
            }
        } catch (Exception e) {}
        if (scripts.isEmpty()) scripts.add("default_script");

        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int maxX = screenW > 0 ? screenW : 1920;
        int maxY = screenH > 0 ? screenH : 1080;

        String defaultUsername = "Player";
        if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getSession() != null) {
            defaultUsername = MinecraftClient.getInstance().getSession().getUsername();
        }

        // --- MAIN TAB ---
        BooleanListEntry isVis = entryBuilder.startBooleanToggle(Text.literal("Is Visible"), Teleprompter.config.isVisible).setDefaultValue(false).build();
        var scriptDropdown = entryBuilder.startStringDropdownMenu(Text.literal("Active Script"), Teleprompter.config.activeScript).setSelections(scripts).setDefaultValue("default_script").build();
        StringListEntry roleName = entryBuilder.startStrField(Text.literal("Your Role Name"), Teleprompter.config.roleName).setDefaultValue(defaultUsername).setTooltip(Text.literal("Used to auto-highlight your lines.")).build();
        IntegerSliderEntry wpm = entryBuilder.startIntSlider(Text.literal("Auto-Scroll Speed (WPM)"), Teleprompter.config.autoScrollWpm, 50, 400).setDefaultValue(150).build();
        BooleanListEntry reloadBtn = entryBuilder.startBooleanToggle(Text.literal("▶ ACTION: Force Sync Scripts Folder"), false).setYesNoTextSupplier(bool -> Text.literal("Sync")).build();
        BooleanListEntry openFolderBtn = entryBuilder.startBooleanToggle(Text.literal("▶ ACTION: Open Scripts Folder"), false).setYesNoTextSupplier(bool -> Text.literal("Open Folder")).build();

        mainCat.addEntry(isVis);
        mainCat.addEntry(scriptDropdown);
        mainCat.addEntry(roleName);
        mainCat.addEntry(wpm);
        mainCat.addEntry(reloadBtn);
        mainCat.addEntry(openFolderBtn);

        // --- VISUALS TAB (Colors & Opacities) ---
        ColorEntry textColor = entryBuilder.startColorField(Text.literal("Default Text Color"), Teleprompter.config.textColor).setDefaultValue(0xFFFFFF).build();
        ColorEntry dialogueColor = entryBuilder.startColorField(Text.literal("Dialogue Color"), Teleprompter.config.dialogueColor).setDefaultValue(0xAAAAAA).build();
        ColorEntry myLineColor = entryBuilder.startColorField(Text.literal("My Lines Color"), Teleprompter.config.myLineColor).setDefaultValue(0x55FF55).build();
        IntegerSliderEntry txtOp = entryBuilder.startIntSlider(Text.literal("Text Opacity"), Teleprompter.config.textOpacity, 0, 255).setDefaultValue(255).build();
        IntegerSliderEntry shadowOp = entryBuilder.startIntSlider(Text.literal("Shadow Opacity"), Teleprompter.config.shadowOpacity, 0, 255).setDefaultValue(150).build();
        IntegerSliderEntry bgOp = entryBuilder.startIntSlider(Text.literal("Background Opacity"), Teleprompter.config.backgroundOpacity, 0, 255).setDefaultValue(100).build();
        IntegerSliderEntry hlOp = entryBuilder.startIntSlider(Text.literal("Scroll Highlight Opacity"), Teleprompter.config.autoScrollHighlightOpacity, 0, 255).setDefaultValue(80).build();
        BooleanListEntry showPage = entryBuilder.startBooleanToggle(Text.literal("Show Page Indicator"), Teleprompter.config.showPageIndicator).setDefaultValue(true).build();

        visualCat.addEntry(textColor);
        visualCat.addEntry(dialogueColor);
        visualCat.addEntry(myLineColor);
        visualCat.addEntry(txtOp);
        visualCat.addEntry(shadowOp);
        visualCat.addEntry(bgOp);
        visualCat.addEntry(hlOp);
        visualCat.addEntry(showPage);

        // --- FORMATTING TAB (Layout & Structure) ---
        EnumListEntry<TeleprompterConfig.TextAlignment> align = entryBuilder.startEnumSelector(Text.literal("Text Alignment"), TeleprompterConfig.TextAlignment.class, Teleprompter.config.textAlignment).setDefaultValue(TeleprompterConfig.TextAlignment.SMART).build();
        BooleanListEntry useMd = entryBuilder.startBooleanToggle(Text.literal("Use Markdown Formatting"), Teleprompter.config.useMarkdown).setDefaultValue(true).build();
        EnumListEntry<TeleprompterConfig.Anchor> anchor = entryBuilder.startEnumSelector(Text.literal("Screen Anchor"), TeleprompterConfig.Anchor.class, Teleprompter.config.anchor).setDefaultValue(TeleprompterConfig.Anchor.TOP_LEFT).build();
        IntegerSliderEntry xOff = entryBuilder.startIntSlider(Text.literal("X Offset"), Teleprompter.config.xOffset, -maxX, maxX).setDefaultValue(0).build();
        IntegerSliderEntry yOff = entryBuilder.startIntSlider(Text.literal("Y Offset"), Teleprompter.config.yOffset, -maxY, maxY).setDefaultValue(0).build();
        IntegerSliderEntry boxWidth = entryBuilder.startIntSlider(Text.literal("Box Width (Pixels)"), Teleprompter.config.boxWidth, 50, maxX).setDefaultValue(250).build();
        IntegerSliderEntry padding = entryBuilder.startIntSlider(Text.literal("Box Padding"), Teleprompter.config.boxPadding, 0, 50).setDefaultValue(10).build();
        FloatListEntry textScale = entryBuilder.startFloatField(Text.literal("Text Size Scale"), Teleprompter.config.textScale).setDefaultValue(1.0f).build();
        IntegerSliderEntry maxLines = entryBuilder.startIntSlider(Text.literal("Max Lines Per Page"), Teleprompter.config.maxLinesPerPage, 1, 50).setDefaultValue(12).build();

        formatCat.addEntry(align);
        formatCat.addEntry(useMd);
        formatCat.addEntry(anchor);
        formatCat.addEntry(xOff);
        formatCat.addEntry(yOff);
        formatCat.addEntry(boxWidth);
        formatCat.addEntry(padding);
        formatCat.addEntry(textScale);
        formatCat.addEntry(maxLines);

        Screen screen = builder.build();
        Teleprompter.activeConfigScreen = screen;

        boolean[] folderWasOpen = { false };
        boolean[] reloadWasOpen = { false };

        Teleprompter.livePreviewUpdater = () -> {
            float oldScale = Teleprompter.config.textScale;
            int oldWidth = Teleprompter.config.boxWidth;
            int oldMax = Teleprompter.config.maxLinesPerPage;
            boolean oldMd = Teleprompter.config.useMarkdown;
            String oldRole = Teleprompter.config.roleName;
            TeleprompterConfig.TextAlignment oldAlign = Teleprompter.config.textAlignment;

            Teleprompter.config.isVisible = isVis.getValue();
            Teleprompter.config.autoScrollWpm = wpm.getValue();
            Teleprompter.config.useMarkdown = useMd.getValue();
            Teleprompter.config.showPageIndicator = showPage.getValue();
            Teleprompter.config.textAlignment = align.getValue();
            Teleprompter.config.anchor = anchor.getValue();
            Teleprompter.config.xOffset = xOff.getValue();
            Teleprompter.config.yOffset = yOff.getValue();
            Teleprompter.config.boxPadding = padding.getValue();
            Teleprompter.config.boxWidth = boxWidth.getValue();
            Teleprompter.config.textScale = textScale.getValue();
            Teleprompter.config.maxLinesPerPage = maxLines.getValue();
            Teleprompter.config.roleName = roleName.getValue();
            Teleprompter.config.textColor = textColor.getValue();
            Teleprompter.config.dialogueColor = dialogueColor.getValue();
            Teleprompter.config.myLineColor = myLineColor.getValue();
            Teleprompter.config.textOpacity = txtOp.getValue();
            Teleprompter.config.shadowOpacity = shadowOp.getValue();
            Teleprompter.config.backgroundOpacity = bgOp.getValue();
            Teleprompter.config.autoScrollHighlightOpacity = hlOp.getValue();

            boolean currentFolderVal = openFolderBtn.getValue();
            if (currentFolderVal != folderWasOpen[0]) {
                Util.getOperatingSystem().open(Teleprompter.SCRIPTS_DIR.toFile());
                folderWasOpen[0] = currentFolderVal;
            }

            boolean currentReloadVal = reloadBtn.getValue();
            if (currentReloadVal != reloadWasOpen[0]) {
                AutoConfig.getConfigHolder(TeleprompterConfig.class).save();
                Teleprompter.loadScriptFromFile();
                MinecraftClient.getInstance().setScreen(buildScreen(parent));
                return;
            }

            if (!Teleprompter.config.activeScript.equals(scriptDropdown.getValue())) {
                Teleprompter.config.activeScript = scriptDropdown.getValue();
                Teleprompter.loadScriptFromFile();
            }

            if (oldScale != Teleprompter.config.textScale || oldWidth != Teleprompter.config.boxWidth ||
                    oldMax != Teleprompter.config.maxLinesPerPage || oldMd != Teleprompter.config.useMarkdown ||
                    !oldRole.equals(Teleprompter.config.roleName) || oldAlign != Teleprompter.config.textAlignment) {
                Teleprompter.needsRebuild = true;
            }
        };

        return screen;
    }
}