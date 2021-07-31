import typing
import bpy
from bpy.props import StringProperty, IntProperty, CollectionProperty, BoolProperty
import json
import os
from urllib.request import urlopen, Request

from bpy.types import Context
from .config import Config

try:
    from .umap import import_umap, cleanup
except ImportError:
    from ..umap import import_umap, cleanup

def main(context):
    sc = bpy.context.scene
    reuse_maps = sc.reuse_maps
    reuse_meshes = sc.reuse_mesh
    use_cube_as_fallback = sc.use_cube_as_fallback
    data_dir = sc.exportPath
    addon_dir = os.path.dirname(os.path.splitext(__file__)[0])

    Config().dump(sc.exportPath)

    exporter_result = os.system(f'START /WAIT /D "{data_dir}" cmd /K java -jar "{os.path.join(addon_dir,"BlenderUmap.jar")}"')
    if exporter_result != 0:
        raise Exception("Exporter returned non zero exit code")

    uvm = bpy.data.node_groups.get("UV Shader Mix")
    tex_shader = bpy.data.node_groups.get("Texture Shader")

    if not uvm or not tex_shader:
        with bpy.data.libraries.load(os.path.join(addon_dir, "deps.blend")) as (data_from, data_to):
            data_to.node_groups = data_from.node_groups

        uvm = bpy.data.node_groups.get("UV Shader Mix")
        tex_shader = bpy.data.node_groups.get("Texture Shader")

    # make sure we're on main scene to deal with the fallback objects
    main_scene = bpy.data.scenes.get("Scene") or bpy.data.scenes.new("Scene")
    bpy.context.window.scene = main_scene

    # prepare collection for imports
    import_collection = bpy.data.collections.get("Imported")

    if import_collection:
        bpy.ops.object.select_all(action='DESELECT')

        for obj in import_collection.objects:
            obj.select_set(True)

        bpy.ops.object.delete()
    else:
        import_collection = bpy.data.collections.new("Imported")
        main_scene.collection.children.link(import_collection)

    cleanup()

    # setup fallback cube mesh
    bpy.ops.mesh.primitive_cube_add(size=2)
    fallback_cube = bpy.context.active_object
    fallback_cube_mesh = fallback_cube.data
    fallback_cube_mesh.name = "__fallback"
    bpy.data.objects.remove(fallback_cube)

    # 2. empty mesh
    empty_mesh = bpy.data.meshes.get("__empty", bpy.data.meshes.new("__empty"))

    # do it!
    with open(os.path.join(data_dir, "processed.json")) as file:
        import_umap(json.loads(file.read()), import_collection, data_dir, reuse_maps, reuse_meshes, use_cube_as_fallback, tex_shader)

    # go back to main scene
    bpy.context.window.scene = main_scene
    cleanup()

class UE4Version:  # idk why
    """Supported UE4 Versions"""

    Versions = (
        ("GAME_UE4_0", "GAME_UE4_0", ""),
        ("GAME_UE4_1", "GAME_UE4_1", ""),
        ("GAME_UE4_2", "GAME_UE4_2", ""),
        ("GAME_UE4_3", "GAME_UE4_3", ""),
        ("GAME_UE4_4", "GAME_UE4_4", ""),
        ("GAME_UE4_5", "GAME_UE4_5", ""),
        ("GAME_UE4_6", "GAME_UE4_6", ""),
        ("GAME_UE4_7", "GAME_UE4_7", ""),
        ("GAME_UE4_8", "GAME_UE4_8", ""),
        ("GAME_UE4_9", "GAME_UE4_9", ""),
        ("GAME_UE4_10", "GAME_UE4_10", ""),
        ("GAME_UE4_11", "GAME_UE4_11", ""),
        ("GAME_UE4_12", "GAME_UE4_12", ""),
        ("GAME_UE4_13", "GAME_UE4_13", ""),
        ("GAME_UE4_14", "GAME_UE4_14", ""),
        ("GAME_UE4_15", "GAME_UE4_15", ""),
        ("GAME_UE4_16", "GAME_UE4_16", ""),
        ("GAME_UE4_17", "GAME_UE4_17", ""),
        ("GAME_UE4_18", "GAME_UE4_18", ""),
        ("GAME_UE4_19", "GAME_UE4_19", ""),
        ("GAME_UE4_20", "GAME_UE4_20", ""),
        ("GAME_UE4_21", "GAME_UE4_21", ""),
        ("GAME_UE4_22", "GAME_UE4_22", ""),
        ("GAME_UE4_23", "GAME_UE4_23", ""),
        ("GAME_UE4_24", "GAME_UE4_24", ""),
        ("GAME_UE4_25", "GAME_UE4_25", ""),
        ("GAME_UE4_26", "GAME_UE4_26", ""),
        ("GAME_VALORANT", "GAME_VALORANT", ""),
        ("GAME_UE4_LATEST", "GAME_UE4_LATEST", ""),
    )

class VIEW3D_MT_AdditionalOptions(bpy.types.Menu):
        bl_label = "Additional Options"

        def draw(self, context):
            layout = self.layout
            col = layout.column()

            col.operator("umap.fillfortnitekeys", icon="FILE_FONT",text="Fill Fortnite AES Keys", depress=False)

# UI
class VIEW3D_PT_Import(bpy.types.Panel):
    """Creates a Panel in Properties(N)"""

    bl_label = "BlenderUmap"
    bl_idname = "VIEW3D_PT_Umap"
    bl_space_type = "VIEW_3D"
    bl_region_type = "UI"
    bl_category = "Umap"
    bl_context = "objectmode"

    bpy.types.Scene.ue4_versions = bpy.props.EnumProperty(
        name="UE4 Version", items=UE4Version.Versions
    )

    def draw(self, context):
        layout = self.layout

        col = layout.column(align=True)

        if os.path.isfile(os.path.join(bpy.context.scene.exportPath, "config.json")):
            col.operator("umap.load_configs", icon="FILE_REFRESH", text="Reload Last Used Config")

        col.label(text="Exporter Settings:")
        col.prop(context.scene, "Game_Path")
        col.prop(context.scene, "aeskey")
        col.prop(context.scene, "exportPath")
        col.label(text="Dynamic Keys:")

        row = col.row(align=True)

        col2 = row.column(align=True)
        col2.template_list(
            "VIEW3D_UL_DPKLIST",
            "VIEW3D_UL_dpklist",
            context.scene,
            "dpklist",
            context.scene,
            "list_index",
            rows=3,
        )
        row.separator()

        col3 = row.column(align=True)
        col3.operator("dpklist.new_item", icon='ADD', text="")
        col3.operator("dpklist.delete_item", icon='REMOVE', text="")
        col3.separator()
        col3.menu("VIEW3D_MT_AdditionalOptions", icon='DOWNARROW_HLT', text="")
        col.separator()

        if context.scene.list_index >= 0 and context.scene.dpklist:
            item = context.scene.dpklist[context.scene.list_index]
            col.prop(item, "pakname")
            col.prop(item, "daeskey")
            col.prop(item, "guid")
            col.separator()

        col.prop(context.scene, "package")
        col.prop(context.scene, "ue4_versions") # TODO custom ue4 versions

        col.prop(context.scene, "readmats")
        col.prop(context.scene, "bExportToDDSWhenPossible")
        col.prop(context.scene,"bExportBuildingFoundations")
        col.prop(context.scene, "bdumpassets")
        col.prop(context.scene, "ObjectCacheSize")
        col.prop(context.scene, "bUseUModel")
        if context.scene.bUseUModel == True:
            col.prop(context.scene, "additionalargs")
        col.separator()

        col.label(text="Importer Settings:")
        col.prop(context.scene, "reuse_maps", text="Reuse Maps")
        col.prop(context.scene, "reuse_mesh", text="Reuse Meshes")
        col.prop(context.scene, "use_cube_as_fallback")

        col.operator("umap.import", text="Import", icon="IMPORT")


class VIEW_PT_UmapOperator(bpy.types.Operator):
    """Import Umap"""

    bl_idname = "umap.import"
    bl_label = "Umap Exporter"

    def execute(self, context):
        if bpy.context.scene.ue4_versions in ["GAME_UE4_26","GAME_UE4_27","GAME_UE4_LATEST"]:
            self.check_mappings()

        if context.scene.bUseUModel:
            if not os.path.exists(os.path.join(bpy.context.scene.exportPath,"umodel.exe")):
                self.report({'ERROR'}, 'umodel.exe not found in Export Directory(Export Path)')
                return {"CANCELLED"}

        main(context)
        return {"FINISHED"}

    def check_mappings(self):
        path = bpy.context.scene.exportPath
        mappings_path = os.path.join(path, "mappings")
        if not os.path.exists(mappings_path):
            os.makedirs(mappings_path)
            self.dl_mappings(mappings_path)
            return False

        try:
            self.dl_mappings(mappings_path)
        except: pass
        return True

    def dl_mappings(self,path):
        ENDPOINT = "https://benbot.app/api/v1/mappings"
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.150 Safari/537.36",
            "accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        }

        req = Request(url=ENDPOINT, headers=headers)
        r = urlopen(req)
        data = json.loads(r.read().decode(r.info().get_param("charset") or "utf-8"))

        if not os.path.exists(os.path.join(path,data[0]["fileName"])):
            with open(os.path.join(path,data[0]["fileName"]),"wb") as f:
                downfile = urlopen(Request(url=data[0]["url"], headers=headers))
                print("Downloading",data[0]["fileName"])
                f.write(downfile.read(downfile.length))
        return True


class ListItem(bpy.types.PropertyGroup):
    pakname: StringProperty(
        name="Pak Name", description="Name of the Pak file.", default=""
    )
    daeskey: StringProperty(
        name="AES Key", description="AES key for the Pak file.", default=""
    )
    guid: StringProperty(
        name="Encryption Guid", description= "Encryption Guid for the Pak file.", default="", maxlen=32)


class VIEW3D_UL_DPKLIST(bpy.types.UIList):
    """Dynamic Pak AES key List"""

    def draw_item(
        self, context, layout, data, item, icon, active_data, active_propname, index
    ):
        if self.layout_type in {"DEFAULT", "COMPACT"}:
            if item.pakname == "":
                layout.label(text=f"{item.guid}:{item.daeskey}")
            else:
                layout.label(text=f"{item.pakname}:{item.daeskey}")

        elif self.layout_type in {"GRID"}:
            layout.alignment = "CENTER"
            if item.pakname == "":
                layout.label(text=f"{item.guid}")
            else:
                layout.label(text=item.pakname)

class Fortnite(bpy.types.Operator):
    bl_idname = "umap.fillfortnitekeys"
    bl_label = "Fill Fortnite Keys"
    bl_description = "Automatically fill AES Key/s for Latest Fortnite version"
    bl_options = {"UNDO"}

    @classmethod
    def poll(cls, context):
        return True

    def execute(self, context):
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.3"
        }

        req = Request(url="https://fortnite-api.com/v2/aes", headers=headers)
        r = urlopen(req)

        if r.status != 200:
            self.report({"ERROR"}, "API returned {r.status} status code")
            return {"CANCELLED"}

        raw_data = r.read().decode(r.info().get_param("charset") or "utf-8")
        try:
            data = json.loads(raw_data)["data"]
        except Exception as e:
            self.report({'ERROR'}, "Error Loading JSON \n{e}")
            return {"CANCELLED"}

        main_key = data["mainKey"]
        bpy.context.scene.aeskey = main_key if main_key.startswith("0x") else f"0x{main_key}"

        dpklist = context.scene.dpklist
        context.scene.list_index = len(dpklist)
        index = context.scene.list_index

        for _ in bpy.context.scene.dpklist:
            dpklist.remove(index)
            index = index - 1

        context.scene.list_index = 0
        for x in data["dynamicKeys"]:
            PakPath, Guid, AESKey = x.values()
            Pakname = os.path.basename(PakPath)
            context.scene.dpklist.add()
            item = context.scene.dpklist[index]
            item.pakname = Pakname
            item.guid = Guid
            item.daeskey = AESKey if AESKey.startswith("0x") else "0x" + AESKey
            index = index + 1

        return {"FINISHED"}


class DPKLIST_OT_NewItem(bpy.types.Operator):
    """Add a new item to the list."""

    bl_idname = "dpklist.new_item"
    bl_label = "Add a new item"

    def execute(self, context):
        context.scene.dpklist.add()
        context.scene.list_index + 1
        return {"FINISHED"}


class LIST_OT_DeleteItem(bpy.types.Operator):
    """Delete the selected item from the list."""

    bl_idname = "dpklist.delete_item"
    bl_label = "Deletes an item"

    @classmethod
    def poll(cls, context):
        return len(context.scene.dpklist) > 0

    def execute(self, context):
        dpklist = context.scene.dpklist
        index = context.scene.list_index
        dpklist.remove(index)
        context.scene.list_index = min(max(0, index - 1), len(dpklist) - 1)
        return {"FINISHED"}


class LOAD_Configs(bpy.types.Operator):
    bl_label = "Load Config from File"
    bl_idname = "umap.load_configs"
    bl_description = "Load Configs from File"

    def execute(self, context: 'Context') -> typing.Union[typing.Set[str], typing.Set[int]]:
        try:
            Config().load()
        except Exception as e:
            self.report({"ERROR"}, str(e))
            return {"CANCELLED"}
        return {"FINISHED"}


def register():
    # bpy.utils.register_class(VIEW_PT_UmapOperator)
    # bpy.utils.register_class(VIEW_PT_Import)

    bpy.types.Scene.dpklist = CollectionProperty(type=ListItem)
    bpy.types.Scene.list_index = IntProperty(name="", default=0)

    bpy.types.Scene.Game_Path = StringProperty(
        name="Game Path",
        description="Path to the Paks folder",
        subtype="DIR_PATH",
    )

    bpy.types.Scene.aeskey = StringProperty(
        name="Main AES Key",
        description="AES key",
        subtype="NONE",
    )

    bpy.types.Scene.package = StringProperty(
        name="Package",
        description="Umap to export",
        subtype="NONE",
    )

    bpy.types.Scene.readmats = BoolProperty(
        name="Read Materials",
        description="Import Materials",
        default=True,
        subtype="NONE",
    )

    bpy.types.Scene.bExportToDDSWhenPossible = BoolProperty(
        name="Export DDS When Possible",
        description="Export textures to .dds format",
        default=False,
        subtype="NONE",
    )

    bpy.types.Scene.bExportBuildingFoundations = BoolProperty(
        name="Export Building Foundations",
        description="You can turn off exporting sub-buildings in large POIs\
if you want to quickly port the base POI structures, by setting this to false",
        default=True,
        subtype="NONE",
    )

    bpy.types.Scene.bdumpassets = BoolProperty(
        name="Dump Assets",
        description="Save assets as JSON format",
        default=False,
        subtype="NONE",
    )

    bpy.types.Scene.ObjectCacheSize = IntProperty(
        name="Object Cache Size",
        description="Configure the object loader cache size to tune the performance, or set to 0 to disable",
        default=100,
        min=0,
    )

    bpy.types.Scene.bUseUModel = BoolProperty(
        name="Use UModel",
        description="Use UModel for the exporting process to export meshes, materials, and textures",
        default=False,
        subtype="NONE",
    )

    bpy.types.Scene.reuse_maps = BoolProperty(
        name="Reuse Maps",
        description="Reuse already imported map rather then importing them again",
        default=True,
        subtype="NONE",
    )

    bpy.types.Scene.reuse_mesh = BoolProperty(
        name="Reuse Meshes",
        description="Reuse already imported meshes rather then importing them again",
        default=True,
        subtype="NONE",
    )

    bpy.types.Scene.use_cube_as_fallback = BoolProperty(
        name="Use Cube as Fallback Mesh",
        description="Use cube if mesh is not found",
        default=True,
        subtype="NONE",
    )

    bpy.types.Scene.additionalargs = StringProperty(
        name="UModel Additional Args",
        description="Additional Args for UModel",
        subtype="NONE",
    )

    bpy.types.Scene.exportPath = StringProperty(
            name="Export Path",
            description="Path to Export Folder",
            subtype="DIR_PATH",
        )

def unregister():
    # bpy.utils.unregister_class(VIEW_PT_UmapOperator)
    # bpy.utils.unregister_class(VIEW_PT_Import)

    sc = bpy.types.Scene
    del sc.Game_Path
    del sc.aeskey
    del sc.package
    del sc.readmats
    del sc.bExportToDDSWhenPossible
    del sc.bExportBuildingFoundations
    del sc.bdumpassets
    del sc.ObjectCacheSize
    del sc.bUseUModel
    del sc.reuse_maps
    del sc.reuse_mesh
    del sc.use_cube_as_fallback
    del sc.additionalargs
    del sc.exportPath

if __name__ == "__main__":
    register()
