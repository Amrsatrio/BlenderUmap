# BlenderUmap
A Java tool to export Fortnite .umaps and a Python script to import it. More games will be supported as time goes on.

## Usage
* Before running the tool you need to have 64-bit Java installed. [Get it here. (choose 64-bit Offline)](https://www.java.com/en/download/manual.jsp)\
  **32-bit Java won't work with this tool!**
* Extract the zip file
* If you want to export from Fortnite, execute `Fill Fortnite AES Keys.bat`
* Edit [config.json](#config.json) to suit your needs
* Execute `Run Exporter.bat`
* Make sure you have the [Blender PSK import/export plugin](https://github.com/Befzz/blender3d_import_psk_psa) **at least version 2.7.13** installed. If you use prior versions of the plugin the scaling of the props will appear broken.
* In Blender, import the python file as a script (via scripting tab)
* (Optional) If you want to see the output of the script, show the system console (Window > Toggle System Console)
* Click Run Script (Alt+P), Blender will freeze if all goes well
* Profit!

## config.json
* **`PaksDirectory`: Path to the Paks folder.**
* `UEVersion`: Unreal Engine version. Supports up to UE4.25.
* **`EncryptionKeys`: List of AES keys to use for loading the paks**
  * `Guid`: Identify a pak by its encryption key GUID. Use `00000000000000000000000000000000` (32 0's) to refer to the main paks.
  * `FileName`: Alternatively, you can use this to identify a pak by its file name.
  * `Key`: The pak's encryption key, in either hex (starting with "0x") or base64.
* `bReadMaterials`: Export materials. Materials are experimental! Not all imported materials will be perfect. **Min. 24GB of RAM recommended!**
* `bRunUModel`: Run UModel within the exporting process to export meshes, materials, and textures.
* `UModelAdditionalArgs`: Additional command line args when starting UModel.
* `bDumpAssets`: Save assets as JSON format, useful for debugging.
* **`ExportPackage`: The .umap you want to export.** Accepts these path formats:
  * /Game/Maps/MapName.umap
  * GameName/Content/Maps/MapName.umap
