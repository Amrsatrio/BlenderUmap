import bpy
from bpy.props import StringProperty, IntProperty, CollectionProperty, BoolProperty
import json
import os
from math import *
from urllib.request import urlopen, Request
from .config import Config
# from ..umap import import_umap, cleanup


def main(context):
    pass


class UE4Version(bpy.types.Operator):  # idk why
    """Supported UE4 Versions"""

    bl_idname = "umap.ue4versions"
    bl_label = "uhhh versions"
    bl_description = "Supported UE4 Versions"

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

    # ue4_versions: bpy.props.EnumProperty(name="UE4 Version:", items=Versions)


# Button
class VIEW_PT_Import(bpy.types.Panel):
    """Creates a Panel in Properties(N)"""

    bl_label = "BlenderUmap"
    bl_idname = "Umap"
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

        col.label(text="Exporter Settings:")

        col.prop(context.scene, "Game_Path", text="Game Path")

        col.prop(context.scene, "aeskey", text="Main AES Key")

        col.prop(context.scene, "exportPath", text="Export Path")


        col.label(text="Dynamic Keys:")
        col.template_list(
            "DPKLIST",
            "DPK_List",
            context.scene,
            "dpklist",
            context.scene,
            "list_index",
            rows=2,
        )

        row = col.row()
        row.operator("dpklist.new_item", text="+")
        row.operator("dpklist.delete_item", text="-")

        if context.scene.list_index >= 0 and context.scene.dpklist:
            item = context.scene.dpklist[context.scene.list_index]
            col.prop(item, "pakname")
            col.prop(item, "daeskey")

        col.prop(context.scene, "package", text="Package")

        col.prop(context.scene, "ue4_versions")

        col.prop(context.scene, "readmats", text="Read Materials")

        col.prop(
            context.scene, "bExportToDDSWhenPossible", text="Export DDS When Possible"
        )

        col.prop(
            context.scene,
            "bExportBuildingFoundations",
            text="Export Building Foundations",
        )

        col.prop(context.scene, "bdumpassets", text="Dump Assets")

        col.prop(context.scene, "ObjectCacheSize", text="Object Cache Size")

        col.prop(context.scene, "bUseUModel", text="Use UModel")

        if context.scene.bUseUModel == True:
            col.prop(context.scene, "additionalargs", text="UModel Additional Args")

        col.label(text="Importer Settings:")

        col.prop(context.scene, "reuse_maps", text="Reuse Maps")

        col.prop(context.scene, "reuse_mesh", text="Reuse Meshes")

        col.prop(
            context.scene, "use_cube_as_fallback", text="Use Cube as Fallback Mesh"
        )

        col.operator("umap.import", text="Import", icon="IMPORT")

        col.operator(
            "umap.fillfortnitekeys", text="Fill Fortnite AES Keys", icon="FILE_FONT"
        )


class VIEW_PT_UmapOperator(bpy.types.Operator):
    """Import Umap"""

    bl_idname = "umap.import"
    bl_label = "Umap Exporter"

    def execute(self, context):
        Config().dump()
        main(context)
        return {"FINISHED"}


class ListItem(bpy.types.PropertyGroup):
    pakname: StringProperty(
        name="Pak Name", description="Name of the Pak file.", default=""
    )
    daeskey: StringProperty(
        name="AES Key", description="AES key for the Pak file.", default=""
    )


class DPKLIST(bpy.types.UIList):
    """Dynamic Pak AES key List"""

    def draw_item(
        self, context, layout, data, item, icon, active_data, active_propname, index
    ):
        if self.layout_type in {"DEFAULT", "COMPACT"}:
            layout.label(text=f"{item.pakname}:{item.daeskey}")

        elif self.layout_type in {"GRID"}:
            layout.alignment = "CENTER"
            layout.label(text=item.pakname)


class Fortnite(bpy.types.Operator):
    bl_idname = "umap.fillfortnitekeys"
    bl_label = "Fill Fortnite Keys"
    bl_description = "Description that shows in blender tooltips"
    bl_options = {"UNDO"}

    @classmethod
    def poll(cls, context):
        return True

    def execute(self, context):
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.3"
        }

        req = Request(url="https://benbotfn.tk/api/v1/aes", headers=headers)
        r = urlopen(req)
        data = json.loads(r.read().decode(r.info().get_param("charset") or "utf-8"))
        # bpy.types.Scene.aeskey = data["mainKey"]

        dpklist = context.scene.dpklist
        context.scene.list_index = len(dpklist)
        index = context.scene.list_index

        for _ in bpy.context.scene.dpklist:
            dpklist.remove(index)
            index = index - 1

        for PakPath, AESKey in data["dynamicKeys"].items():
            Pakname = os.path.basename(PakPath)
            context.scene.dpklist.add()
            item = context.scene.dpklist[index]
            item.pakname = Pakname
            item.daeskey = AESKey
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
        return context.scene.dpklist

    def execute(self, context):
        dpklist = context.scene.dpklist
        index = context.scene.list_index
        dpklist.remove(index)
        context.scene.list_index = min(max(0, index - 1), len(dpklist) - 1)
        return {"FINISHED"}


def register():
    # bpy.utils.register_class(VIEW_PT_UmapOperator)
    # bpy.utils.register_class(VIEW_PT_Import)

    bpy.types.Scene.dpklist = CollectionProperty(type=ListItem)
    bpy.types.Scene.list_index = IntProperty(name="Index for dpklist", default=0)

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
        default=True,
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
        default=True,
        subtype="NONE",
    )

    bpy.types.Scene.reuse_maps = BoolProperty(
        name="Reuse Maps",
        description="Reuse already imported map rather then importing them again",
        default=False,
        subtype="NONE",
    )

    bpy.types.Scene.reuse_mesh = BoolProperty(
        name="Reuse Meshes",
        description="Reuse already imported meshes rather then importing them again",
        default=False,
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

    # idk why we unregister
    sc = bpy.context.scene
    del sc.dpklist
    del sc.list_index
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
