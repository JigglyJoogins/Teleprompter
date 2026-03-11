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

        int screenW = MinecraftClient.getInstance().getWindow().getFramebufferWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getFramebufferHeight();
        int maxX = screenW > 0 ? screenW / 2 : 1920;
        int maxY = screenH > 0 ? screenH / 2 : 1080;

        // --- MAIN TAB ---
        BooleanListEntry isVis = entryBuilder.startBooleanToggle(Text.literal("Is Visible"), Teleprompter.config.isVisible).setDefaultValue(false).build();
        var scriptDropdown = entryBuilder.startStringDropdownMenu(Text.literal("Active Script"), Teleprompter.config.activeScript).setSelections(scripts).setDefaultValue("default_script").build();
        BooleanListEntry reloadBtn = entryBuilder.startBooleanToggle(Text.literal("▶ ACTION: Force Sync Script"), false).setYesNoTextSupplier(bool -> Text.literal("Sync")).build();
        BooleanListEntry openFolderBtn = entryBuilder.startBooleanToggle(Text.literal("▶ ACTION: Open Scripts Folder"), false).setYesNoTextSupplier(bool -> Text.literal("Open Folder")).build();

        mainCat.addEntry(isVis);
        mainCat.addEntry(scriptDropdown);
        mainCat.addEntry(reloadBtn);
        mainCat.addEntry(openFolderBtn);

        // --- VISUALS TAB ---
        EnumListEntry<TeleprompterConfig.Anchor> anchor = entryBuilder.startEnumSelector(Text.literal("Screen Anchor"), TeleprompterConfig.Anchor.class, Teleprompter.config.anchor).setDefaultValue(TeleprompterConfig.Anchor.TOP_LEFT).build();
        IntegerSliderEntry xOff = entryBuilder.startIntSlider(Text.literal("X Offset"), Teleprompter.config.xOffset, -maxX, maxX).setDefaultValue(0).build();
        IntegerSliderEntry yOff = entryBuilder.startIntSlider(Text.literal("Y Offset"), Teleprompter.config.yOffset, -maxY, maxY).setDefaultValue(0).build();
        IntegerSliderEntry boxWidth = entryBuilder.startIntSlider(Text.literal("Box Width (Pixels)"), Teleprompter.config.boxWidth, 200, screenW > 0 ? screenW : 3840).setDefaultValue(800).build();
        FloatListEntry textScale = entryBuilder.startFloatField(Text.literal("Text Size Scale"), Teleprompter.config.textScale).setDefaultValue(2.0f).build();
        IntegerSliderEntry maxLines = entryBuilder.startIntSlider(Text.literal("Max Lines Per Page"), Teleprompter.config.maxLinesPerPage, 1, 50).setDefaultValue(12).build(); // Default updated to 12
        IntegerSliderEntry txtOp = entryBuilder.startIntSlider(Text.literal("Text Opacity"), Teleprompter.config.textOpacity, 0, 255).setDefaultValue(255).build();
        IntegerSliderEntry shadowOp = entryBuilder.startIntSlider(Text.literal("Shadow Opacity"), Teleprompter.config.shadowOpacity, 0, 255).setDefaultValue(150).build();
        IntegerSliderEntry bgOp = entryBuilder.startIntSlider(Text.literal("Background Opacity"), Teleprompter.config.backgroundOpacity, 0, 255).setDefaultValue(100).build();
        BooleanListEntry showPage = entryBuilder.startBooleanToggle(Text.literal("Show Page Indicator"), Teleprompter.config.showPageIndicator).setDefaultValue(true).build();
        EnumListEntry<TeleprompterConfig.TextAlignment> align = entryBuilder.startEnumSelector(Text.literal("Text Alignment"), TeleprompterConfig.TextAlignment.class, Teleprompter.config.textAlignment).setDefaultValue(TeleprompterConfig.TextAlignment.LEFT).build();

        visualCat.addEntry(anchor);
        visualCat.addEntry(boxWidth);
        visualCat.addEntry(textScale);
        visualCat.addEntry(maxLines);
        visualCat.addEntry(xOff);
        visualCat.addEntry(yOff);
        visualCat.addEntry(txtOp);
        visualCat.addEntry(shadowOp);
        visualCat.addEntry(bgOp);
        visualCat.addEntry(showPage);
        visualCat.addEntry(align);

        // --- FORMATTING TAB ---
        BooleanListEntry useMd = entryBuilder.startBooleanToggle(Text.literal("Use Markdown Formatting"), Teleprompter.config.useMarkdown).setDefaultValue(true).build();
        StringListEntry roleName = entryBuilder.startStrField(Text.literal("Your Role Name"), Teleprompter.config.roleName).setDefaultValue("").setTooltip(Text.literal("Used to auto-highlight your lines.")).build();
        ColorEntry textColor = entryBuilder.startColorField(Text.literal("Default Text Color"), Teleprompter.config.textColor).setDefaultValue(0xFFFFFF).build();
        ColorEntry dialogueColor = entryBuilder.startColorField(Text.literal("Dialogue Color"), Teleprompter.config.dialogueColor).setDefaultValue(0xAAAAAA).build();
        ColorEntry myLineColor = entryBuilder.startColorField(Text.literal("My Lines Color"), Teleprompter.config.myLineColor).setDefaultValue(0x55FF55).build();

        formatCat.addEntry(useMd);
        formatCat.addEntry(roleName);
        formatCat.addEntry(textColor);
        formatCat.addEntry(dialogueColor);
        formatCat.addEntry(myLineColor);

        Screen screen = builder.build();
        Teleprompter.activeConfigScreen = screen;

        boolean[] folderWasOpen = { false };
        boolean[] reloadWasOpen = { false };

        Teleprompter.livePreviewUpdater = () -> {
            boolean currentFolderVal = openFolderBtn.getValue();
            if (currentFolderVal != folderWasOpen[0]) {
                Util.getOperatingSystem().open(Teleprompter.SCRIPTS_DIR.toFile());
                folderWasOpen[0] = currentFolderVal;
            }

            boolean currentReloadVal = reloadBtn.getValue();
            if (currentReloadVal != reloadWasOpen[0]) {
                Teleprompter.loadScriptFromFile();
                reloadWasOpen[0] = currentReloadVal;
            }

            if (!Teleprompter.config.activeScript.equals(scriptDropdown.getValue())) {
                Teleprompter.config.activeScript = scriptDropdown.getValue();
                Teleprompter.loadScriptFromFile();
            }

            float oldScale = Teleprompter.config.textScale;
            int oldWidth = Teleprompter.config.boxWidth;
            int oldMax = Teleprompter.config.maxLinesPerPage;
            boolean oldMd = Teleprompter.config.useMarkdown;
            String oldRole = Teleprompter.config.roleName;

            Teleprompter.config.isVisible = isVis.getValue();
            Teleprompter.config.useMarkdown = useMd.getValue();
            Teleprompter.config.showPageIndicator = showPage.getValue();
            Teleprompter.config.textAlignment = align.getValue();
            Teleprompter.config.anchor = anchor.getValue();
            Teleprompter.config.xOffset = xOff.getValue();
            Teleprompter.config.yOffset = yOff.getValue();
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

            if (oldScale != Teleprompter.config.textScale || oldWidth != Teleprompter.config.boxWidth ||
                    oldMax != Teleprompter.config.maxLinesPerPage || oldMd != Teleprompter.config.useMarkdown ||
                    !oldRole.equals(Teleprompter.config.roleName)) {
                Teleprompter.needsRebuild = true;
            }
        };

        return screen;
    }
}