# 🎥 Teleprompter Mod

An in-game, highly customizable teleprompter designed for Minecraft content creators, actors, and directors. Read your scripts, stage directions, and dialogue directly on your screen while recording without ever having to Alt-Tab!

## ✨ Features

* **🔄 Live File Syncing:** Edit your scripts in Notepad on a second monitor. As soon as you hit `Ctrl + S`, the teleprompter updates in-game instantly.
* **🖋️ Markdown Support:** Use standard Markdown in your text files to make words **bold**, *italic*, or __underlined__.
* **🎭 Smart Dialogue Engine:** Automatically detects character names (e.g., `Player2: Hello!`) and colors them differently from stage directions. It even highlights your own lines in a custom color so you never miss your cue!
* **📏 Custom Scaling & Positioning:** Decoupled from Minecraft's GUI scale. Pin the prompter to any of the 9 corners/centers of your screen, scale the text up, and adjust the exact pixel offsets.
* **📚 Multi-Script Support:** Drop as many `.txt` files as you want into the `config/scripts/` folder and swap between them in-game via a dropdown menu.

## ⌨️ Default Hotkeys

| Action | Hotkey |
| :--- | :--- |
| **Toggle Visibility** | `B` |
| **Next Page** | `]` (Right Bracket) |
| **Previous Page** | `[` (Left Bracket) |
| **Force Reload Script** | `F8` |
| **Open Settings Menu** | `Right Shift` |

*(All hotkeys can be changed in the standard Minecraft Controls menu).*

## 📝 Writing Scripts

When you launch the game for the first time, the mod will generate a `config/scripts/` folder and a `default_script.txt` file. You can edit this file or add your own!

**Formatting Tips:**
* **Page Breaks:** Type three dashes (`---`) on a new line to force a hard page break.
* **Dialogue:** Type a name followed by a colon (e.g., `Director: Action!`) to trigger the dialogue coloring engine. Set your "Role Name" in the ModMenu settings to highlight your specific lines.
* **Blank Lines:** Empty lines are safely ignored at the top of new pages so your text always aligns perfectly.

## 🛠️ Installation

1. Download the latest `.jar` file from the Releases tab.
2. Place the file inside your Minecraft `.minecraft/mods` folder.
3. **Required Dependencies:**
    * [Fabric API](https://modrinth.com/mod/fabric-api)
    * [Cloth Config API](https://modrinth.com/mod/cloth-config)
    * [ModMenu](https://modrinth.com/mod/modmenu) (To access the settings menu)

---
*Built for Fabric 1.21.11.*