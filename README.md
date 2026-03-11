# 🎥 Teleprompter Mod

An in-game, highly customizable teleprompter designed for Minecraft content creators, actors, and directors. Read your scripts, stage directions, and dialogue directly on your screen while recording without ever having to Alt-Tab!

> *Note: This mod renders directly in-game. If you are capturing your own screen for a video (via OBS, etc.), the teleprompter will show up in your final footage.*

## ✨ Features

* **🔄 Live File Syncing:** Edit your scripts in Notepad on a second monitor. As soon as you hit `Ctrl + S`, the teleprompter updates in-game instantly.
* **▶️ Auto-Scroll Highlighter:** Let the active line tracker smoothly progress down the page based on a customizable WPM (Words Per Minute) setting. The highlight dynamically inherits the text's actual color, and empty lines are instantly skipped to keep your pacing perfect.
* **🎭 Smart Dialogue Formatting:** Automatically detects character names and colors them differently from stage directions. It defaults to highlighting your own username so you never miss a cue!
* **🧠 Smart Alignment:** The mod naturally categorizes your text formatting. Dialogue is cleanly pushed to the left, while stage directions, scenes, and Wait commands are centered automatically on the screen.
* **🖋️ Markdown Support:** Use standard Markdown in your text files to make words **bold**, *italic*, or __underlined__.
* **📏 UI-Responsive Scaling:** Pin the prompter to any corner of your screen. It smoothly scales with your native Minecraft GUI scale, allowing you to fine-tune the box padding, text scale, and exact pixel offsets.
* **📚 Multi-Script Support:** Drop as many `.txt` files as you want into the `config/scripts/` folder and instantly cycle between them in-game via a single hotkey.

## ⌨️ Hotkeys

| Action | Default Key |
| :--- | :--- |
| **Toggle Visibility** | `B` |
| **Toggle Auto-Scroll** | `P` |
| **Next Page** | `]` (Right Bracket) |
| **Previous Page** | `[` (Left Bracket) |
| **Force Reload Script** | `F8` |
| **Open Settings Menu** | `Right Shift` |
| **Play/Pause Auto-Scroll** | *Unbound* |
| **Skip to Next Line** | *Unbound* |
| **Skip to Prev Line** | *Unbound* |
| **Cycle Scripts** | *Unbound* |

*(All hotkeys can be changed in the standard Minecraft Controls menu).*

## 📝 Writing Scripts

When you launch the game for the first time, the mod will generate a `config/scripts/` folder with a built-in tutorial script. You can edit this or add your own!

**Formatting Tips:**
* **Page Breaks:** Type three dashes (`---`) on a new line to force a smooth, hard page flip. (Paragraphs are also dynamically protected, ensuring sentences are never cut in half by a page break!)
* **Wait Commands:** Type `## WAIT 10 SECONDS` or `## WAIT 2 MINUTES` on its own line. The auto-scroll feature will slow, and the text will physically count down to 0 live on your screen before moving to the next line!
* **Dialogue Formatting:** To trigger the dialogue coloring and smart-alignment feature, you **must** use a colon and quotation marks.
   * *Example:* `Director: "Action!"`
   * *Note:* The quotation marks are used purely for the backend engine to detect the dialogue. They will be seamlessly removed and hidden from the final text in-game!
* **Stage Directions:** Any standard text or list (without the dialogue formatting) will automatically be centered on the screen.

## 🛠️ Installation

1. Download the latest `.jar` file from the Releases tab.
2. Place the file inside your Minecraft `.minecraft/mods` folder.
3. **Required Dependencies:**
   * [Fabric API](https://modrinth.com/mod/fabric-api)
   * [Cloth Config API](https://modrinth.com/mod/cloth-config)
   * [ModMenu](https://modrinth.com/mod/modmenu) (To access the settings menu)

---
*Built for Fabric 1.21.11.*