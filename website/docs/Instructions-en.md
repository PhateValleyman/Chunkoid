<br />

## Chunkoid User Manual

---

### 1. Basic World Conversion Workflow

1. **Select World**: Tap the "Select World" button on the home screen. Supported formats include `.zip` archives, `.mcworld` Bedrock world packages, or uncompressed world folders.
2. **Automatic Package Scan**: The built-in World Scanning System (WSS) automatically identifies nested world structures (`level.dat`, `db`, `region`), removing the need for manual extraction.
3. **Configure Conversion Options**:
   - Choose the target edition (Java Edition JE or Bedrock Edition BE).
   - Select the target Minecraft version (e.g., 1.20.x, 1.21.x).
   - Configure **Dimension Pruning** (Overworld / Nether / The End) if you wish to omit specific dimensions to save storage and conversion time.
4. **Start Conversion**: Review your settings and tap "Start Conversion". If cross-edition entities are detected, an **Entity Warning Alert** will display compatibility notices. Confirm to begin real-time conversion.
5. **Manage & Export**: After completion, open the "Output Manager" to export, share, or import the converted world directly into Minecraft.

---

### 2. Core Settings & Performance Optimizations

- **Low RAM Anti-Crash Mode**: Optimizes memory allocation and garbage collection to prevent Out-Of-Memory (OOM) crashes on devices with 4GB~6GB RAM.
- **Background Wake Lock**: Keeps the CPU active during conversion so background or screen-off conversions won't be killed by aggressive Android power managers.
- **JVM Maximum Memory**: Adjust the maximum memory allocated to the Java runtime according to available physical RAM (recommended: ~60% of available RAM).
- **Preserve Original NBT**: Preserves unaffected original NBT tags when source and target formats are structurally compatible.
- **Dynamic Theming**: Adapts app accent colors automatically using Material You / system wallpaper themes.
- **Log Export**: Enable verbose logging to export and report error logs to the community when troubleshooting issues.

---

### 3. Extended Toolbox Features

- **Resource Pack Converter**: Cross-converts textures, audio, and manifests between Java and Bedrock resource packs.
- **NetEase World Decryptor**: Import encrypted NetEase worlds to decrypt them into standard world formats before conversion.
- **Terminal Console**: Direct CLI access to the underlying Chunker-cli core for advanced users who prefer manual parameter commands.
