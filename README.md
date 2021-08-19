# BlenderUmap
A Java tool to export Fortnite .umaps and a Python script to import it.

## Disclaimer
This tool was made to fulfill my curiosity about exporting a Fortnite building into Blender back in May 2020, and I did not plan to support this for long term. Although I rarely release some updates to fix some breaking stuff with Fortnite (and/or incorporating enhancements on the underlying parser), **no support will be given when it's about small issues, failing to use the tool properly, or adding support for more games.** Take your time to read the stuff below, and thank you for your understanding.

## Usage
* Before running the tool you need to have 64-bit Java installed. [Get it here. (choose 64-bit Offline)](https://www.java.com/en/download/manual.jsp)\
  **32-bit Java won't work with this tool!**
* Extract the zip file
* If you want to export from Fortnite, execute `Fill Fortnite AES Keys.bat`
* Edit [config.json](#configjson) to suit your needs
* Execute `Run Exporter.bat`
* Make sure you have the [Blender PSK import/export plugin](https://github.com/Befzz/blender3d_import_psk_psa) **at least version 2.7.13** installed. If you use prior versions of the plugin the scaling of the props will appear broken.
* In Blender, import the python file as a script (via scripting tab)
* (Optional) If you want to see the output of the script, show the system console (Window > Toggle System Console)
* Click Run Script (Alt+P), Blender will freeze if all goes well
* Profit!

## config.json
* **`PaksDirectory`: Path to the Paks folder.** Backslashes must be doubled like this: `\\`.
* `UEVersion`: Unreal Engine version. Supports up to UE4.26.
* **`EncryptionKeys`: List of AES keys to use for loading the paks**
  * `Guid`: Identify a pak by its encryption key GUID. Use `00000000000000000000000000000000` (32 0's) to refer to the main paks.
  * `FileName`: Alternatively, you can use this to identify a pak by its file name.
  * `Key`: The pak's encryption key, in either hex (starting with "0x") or base64.
  
  Example for main paks:
  ```json
  {
    "Guid": "00000000000000000000000000000000",
    "Key": "0x36983D73A17CAF253F9D1A322A79D6DC53D8E81B661B7564343F41D4835275D5"
  }
  ```
* `bDumpAssets`: Save assets as JSON format, useful for debugging.
* `ObjectCacheSize`: Configure the object loader cache size to tune the performance, or set to 0 to disable. Defaults to 100.
* `bReadMaterials`: Export materials. Materials are experimental! Not all imported materials will be perfect. **Min. 16GB of RAM recommended!**
* `bExportToDDSWhenPossible`: Prefer PNG to DDS? Set this to `false` and textures will always be exported as PNG. **Warning: Export times will significantly increase when this is set to `false`!** Defaults to enabled.
* `bExportBuildingFoundations`: You can turn off exporting sub-buildings in large POIs if you want to quickly port the base POI structures, by setting this to `false`. Defaults to enabled.
* **`ExportPackage`: The .umap you want to export.** Accepts these path formats:
  * `/Game/Maps/MapName` (package path)
  * `/Game/Maps/MapName.MapName` (object path)
  * `GameName/Content/Maps/MapName.umap` (file path)