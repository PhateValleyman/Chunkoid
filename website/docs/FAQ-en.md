<br />

## Conversion Error Issues

### Q: After opening the converted folder, there are very few files, and Minecraft cannot read it (missing level.dat/region/db and other key world files)

<img src="q.jpg" width="256">

### A: Please try converting again. Pay attention to the log content during conversion (or go to Settings > Enable log export). Carefully observe the log output and troubleshoot according to the following:

<br />

### Q: Importing the save shows

<img src="q2.jpg" width="256">

### A: Make sure your zip file contains the correct save, or extract it directly and import it as a folder.

<br />

### Q: Conversion shows "Termux environment not initialized"

<img src="q1.jpg" width="256">

### A: Download the latest version, reinstall the app, uninstall the old version first, do not choose to overwrite install.

<br />

### Q: The following issue appears during conversion

<img src="q3.jpg" width="256">

### A: This can be ignored, it does not affect the conversion result. This is a core conversion mechanism issue that cannot be avoided. Entity loss will occur in the output save.

<br />

### Q: Conversion shows "Original NBT is not available for this conversion due to differing formats. Please disable it to continue"

<img src="q4.jpg" width="256">

### A: Go to Settings page and disable the "Preserve Original NBT" option.

<br />

### Q: The progress bar gets stuck for a long time during conversion, and finally shows "out of memory" error

### A: Go to Settings page and increase the conversion memory allocation. Adjust according to your device's performance.

<br />

### Q: Why does conversion stop or the app exit when switching to background or turning off the screen?

### A: Some Android manufacturers (MIUI/HyperOS, ColorOS, OriginOS, EMUI, etc.) have aggressive power saving rules. Please enable **Background Wake Lock** in Chunkoid Settings, set app battery optimization to **"Unrestricted"** in system settings, and lock the app in the recent task switcher.

<br />

### Q: Phone has limited RAM (4GB~6GB) and crashes during large world conversion?

### A: Please enable **Low RAM Anti-Crash Mode** in Settings. This mode deeply tunes garbage collection and memory usage to significantly prevent OOM crashes.

<br />

### Q: An "Entity Loss Warning" popped up before conversion. Will my world be corrupted?

### A: It will not corrupt your world. Minecraft Java Edition and Bedrock Edition have inherent differences in entity NBT data (such as custom villager trades and mob properties) that cannot always be 1:1 mapped. Terrain, buildings, redstone, and chest items are completely unaffected. You can safely confirm and proceed.

<br />

### Q: Logs are spamming messages during conversion

<img src="q5.jpg" width="256">

### A: As long as you see "Conversion complete! Took 0h 0m 16s 389ms" or similar messages at the end, you can ignore it.

<br />

### Q: Conversion error shows "Conversion failed: Chunker exited with code: 1"

<img src="q6.jpg" width="256">

### A: Check if this is an encrypted NetEase save. If not, it means the save is corrupted and Chunker reader cannot read your save (rare case, usually due to too many mods in the save). If you still have doubts or the conversion is important to you, please visit [QQ Group Community](../en/about-en.html) for help.

<br />

### If none of the above issues match yours, please join [QQ Group Community](../en/about-en.html) to contact the developer for help. I will check the QQ group irregularly and reply to your questions.

<br />

<br />

## Usage Suggestions

### Q: Want to change the selected world after choosing the wrong one?

<img src="s1.jpg" width="256">

### A: Simple, just long press the world name box and click "Delete Current Task".

<br />

### Q: Worried about encrypted NetEase saves that cannot be converted?

<img src="s2.jpg" width="256">

### A: Save Decryptor Kit is now available, one-click decryption, remove obstacles.

<br />

### Q: Save has strongly associated resource packs?

<img src="s3.jpg" width="256">

### A: Resource Pack Converter Kit is now available, JE-BE dual-end texture conversion, one-stop solution for the best experience.

<br />

### Q: I'm a Termux expert, I used to use Termux commands with Chunker-cli to convert saves for more low-level features

<img src="s4.jpg" width="256">

### A: Terminal Console provides a simple terminal session environment, no complex environment configuration needed, directly restore various Termux-level operations.

<br />

### Q: The save is too large, conversion takes too long and phone storage is low?

### A: In the conversion options, configure the **Dimension Pruning** feature to uncheck the Nether and The End, converting only the Overworld. This reduces conversion data and storage consumption by more than 50%.

<br />

### Q: Want a more modern visual look and custom colors?

### A: Chunkoid v2.0.0 introduces a global dynamic theming engine (Material You), automatically adapting color tones to your system wallpaper and theme, or custom selections in Settings.

<br />
