"""
BlenderUmap v0.3.1
(C) amrsatrio. All rights reserved.
"""
import json
import os
import time
from math import *

import bpy
from io_import_scene_unreal_psa_psk_280 import pskimport

# Change to the working directory of the Java program with the bat. I'm leaving mine here.
data_dir = r"C:\Users\satri\Documents\AppProjects\BlenderUmap\run"

reuse_maps = True
reuse_meshes = True
use_cube_as_fallback = True


# ---------- END INPUTS, DO NOT MODIFY ANYTHING BELOW UNLESS YOU NEED TO ----------
def import_umap(processed_map_path: str,
                into_collection: bpy.types.Collection) -> bpy.types.Object:
    map_name = processed_map_path[processed_map_path.rindex("/") + 1:processed_map_path.rindex(".")]
    map_collection = bpy.data.collections.get(map_name)

    if reuse_maps and map_collection:
        return place_map(map_collection, into_collection)

    map_collection = bpy.data.collections.new(map_name)
    map_collection_inst = place_map(map_collection, into_collection)
    map_scene = bpy.data.scenes.get(map_collection.name) or bpy.data.scenes.new(map_collection.name)
    map_scene.collection.children.link(map_collection)
    map_layer_collection = map_scene.view_layers[0].layer_collection.children[map_collection.name]

    with open(os.path.join(data_dir, "jsons" + processed_map_path + ".processed.json")) as file:
        comps = json.loads(file.read())

    for comp_i, comp in enumerate(comps):
        # guid = comp[0]
        name = comp[1]
        mesh_path = comp[2]
        mats = comp[3]
        texture_data = comp[4]
        location = comp[5] or [0, 0, 0]
        rotation = comp[6] or [0, 0, 0]
        scale = comp[7] or [1, 1, 1]
        child_comps = comp[8]
        print("\nActor %d of %d: %s" % (comp_i + 1, len(comps), name))

        def apply_ob_props(ob: bpy.types.Object, new_name: str = name) -> bpy.types.Object:
            ob.name = new_name
            ob.location = [location[0] * 0.01, location[1] * -0.01, location[2] * 0.01]
            ob.rotation_mode = 'XYZ'
            ob.rotation_euler = [radians(rotation[2]), radians(-rotation[0]), radians(-rotation[1])]
            ob.scale = scale
            return ob

        def new_object(data: bpy.types.Mesh = None):
            ob = apply_ob_props(bpy.data.objects.new(name, data or bpy.data.meshes["__fallback" if use_cube_as_fallback else "__empty"]), name)
            bpy.context.collection.objects.link(ob)
            bpy.context.view_layer.objects.active = ob

        if child_comps and len(child_comps) > 0:
            for i, child_comp in enumerate(child_comps):
                apply_ob_props(
                    import_umap(child_comp, map_collection),
                    name if i is 0 else ("%s_%d" % (name, i)))

            continue

        bpy.context.window.scene = map_scene
        bpy.context.view_layer.active_layer_collection = map_layer_collection

        if not mesh_path:
            print("WARNING: No mesh, defaulting to fallback mesh")
            new_object()
            continue

        if mesh_path.startswith("/"):
            mesh_path = mesh_path[1:]

        key = os.path.basename(mesh_path)
        td_suffix = ""

        if mats and len(mats) > 0:
            key += f"_{abs(string_hash_code(';'.join(mats.keys()))):08x}"
        if texture_data and len(texture_data) > 0:
            td_suffix = f"_{abs(string_hash_code(';'.join([it[0] if it else '' for it in texture_data]))):08x}"
            key += td_suffix

        existing_mesh = bpy.data.meshes.get(key) if reuse_meshes else None

        if existing_mesh:
            new_object(existing_mesh)
            continue

        full_mesh_path = os.path.join(data_dir, mesh_path)
        if os.path.exists(full_mesh_path + ".psk"):
            full_mesh_path += ".psk"
        elif os.path.exists(full_mesh_path + ".pskx"):
            full_mesh_path += ".pskx"

        if os.path.exists(full_mesh_path) and pskimport(full_mesh_path, bpy.context, bReorientBones=True):
            imported = bpy.context.active_object
            apply_ob_props(imported)
            imported.data.name = key
            bpy.ops.object.shade_smooth()

            for m_idx, (m_path, m_textures) in enumerate(mats.items()):
                if m_textures:
                    import_material(imported, m_idx, m_path, td_suffix, m_textures, texture_data)
        else:
            print("WARNING: Mesh not imported, defaulting to fallback mesh:", full_mesh_path)
            new_object()

    return map_collection_inst


def import_material(ob: bpy.types.Object,
                    m_idx: int,
                    path: str,
                    suffix: str,
                    base_textures: list,
                    tex_data: dict) -> bpy.types.Material:
    # .mat is required to prevent conflicts with empty ones imported by PSK/PSA plugin
    m_name = os.path.basename(path + ".mat" + suffix)
    m = bpy.data.materials.get(m_name)

    if not m:
        for td_idx, td_entry in enumerate(tex_data):
            if not td_entry:
                continue
            index = {1: 3, 2: 2, 3: 2}.get(td_idx, 0)
            td_textures = td_entry[1]

            for i, tex_entry in enumerate(base_textures[index]):
                if i < len(td_textures) and td_textures[i]:
                    base_textures[index][i] = td_textures[i]

        m = bpy.data.materials.new(name=m_name)
        m.use_nodes = True
        tree = m.node_tree

        for node in tree.nodes:
            tree.nodes.remove(node)

        m.blend_method = "OPAQUE"

        def group(sub_tex_idx, location):
            sh = tree.nodes.new("ShaderNodeGroup")
            sh.location = location
            sh.node_tree = tex_shader
            sub_textures = base_textures[sub_tex_idx] if sub_tex_idx < len(base_textures) and base_textures[sub_tex_idx] and len(base_textures[sub_tex_idx]) > 0 else base_textures[0]

            for tex_index, sub_tex in enumerate(sub_textures):
                if sub_tex:
                    img = get_or_load_img(sub_tex) if not sub_tex.endswith("/T_EmissiveColorChart") else None

                    if img:
                        d_tex = tree.nodes.new("ShaderNodeTexImage")
                        d_tex.hide = True
                        d_tex.location = [location[0] - 320, location[1] - tex_index * 40]

                        if tex_index != 0:  # other than diffuse
                            img.colorspace_settings.name = "Non-Color"

                        d_tex.image = img
                        tree.links.new(d_tex.outputs[0], sh.inputs[tex_index])

                        if tex_index is 4:  # change mat blend method if there's an alpha mask texture
                            m.blend_method = 'CLIP'

            return sh

        mat_out = tree.nodes.new("ShaderNodeOutputMaterial")
        mat_out.location = [300, 300]

        if ob.data.uv_layers.get("EXTRAUVS0"):
            uvm_ng = tree.nodes.new("ShaderNodeGroup")
            uvm_ng.location = [100, 300]
            uvm_ng.node_tree = bpy.data.node_groups["UV Shader Mix"]
            uv_map = tree.nodes.new("ShaderNodeUVMap")
            uv_map.location = [-100, 700]
            uv_map.uv_map = "EXTRAUVS0"
            tree.links.new(uv_map.outputs[0], uvm_ng.inputs[0])
            tree.links.new(group(0, [-100, 550]).outputs[0], uvm_ng.inputs[1])
            tree.links.new(group(1, [-100, 300]).outputs[0], uvm_ng.inputs[2])
            tree.links.new(group(2, [-100, 50]).outputs[0], uvm_ng.inputs[3])
            tree.links.new(group(3, [-100, -200]).outputs[0], uvm_ng.inputs[4])
            tree.links.new(uvm_ng.outputs[0], mat_out.inputs[0])
        else:
            tree.links.new(group(0, [100, 300]).outputs[0], mat_out.inputs[0])

        print("Material imported")

    if m_idx < len(ob.data.materials):
        ob.data.materials[m_idx] = m

    return m


def place_map(collection: bpy.types.Collection, into_collection: bpy.types.Collection):
    c_inst = bpy.data.objects.new(collection.name, None)
    c_inst.instance_type = 'COLLECTION'
    c_inst.instance_collection = collection
    into_collection.objects.link(c_inst)
    return c_inst


def get_or_load_img(img_path: str) -> bpy.types.Image:
    name = os.path.basename(img_path)
    existing = bpy.data.images.get(name)

    if existing:
        return existing

    img_path = os.path.join(data_dir, img_path[1:])

    if os.path.exists(img_path + ".tga"):
        img_path += ".tga"
    elif os.path.exists(img_path + ".png"):
        img_path += ".png"
    elif os.path.exists(img_path + ".dds"):
        img_path += ".dds"

    if os.path.exists(img_path):
        loaded = bpy.data.images.load(filepath=img_path)
        loaded.name = name
        loaded.alpha_mode = 'CHANNEL_PACKED'
        return loaded
    else:
        print("WARNING: " + img_path + " not found")
        return None


def cleanup():
    for block in bpy.data.meshes:
        if block.users == 0:
            bpy.data.meshes.remove(block)

    for block in bpy.data.materials:
        if block.users == 0:
            bpy.data.materials.remove(block)

    for block in bpy.data.textures:
        if block.users == 0:
            bpy.data.textures.remove(block)

    for block in bpy.data.images:
        if block.users == 0:
            bpy.data.images.remove(block)


def string_hash_code(s: str) -> int:
    h = 0
    for c in s:
        h = (31 * h + ord(c)) & 0xFFFFFFFF
    return ((h + 0x80000000) & 0xFFFFFFFF) - 0x80000000


start = int(time.time() * 1000.0)

uvm = bpy.data.node_groups.get("UV Shader Mix")
tex_shader = bpy.data.node_groups.get("Texture Shader")

if not uvm or not tex_shader:
    with bpy.data.libraries.load(os.path.join(data_dir, "deps.blend")) as (data_from, data_to):
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
    import_umap(json.loads(file.read()), import_collection)

# go back to main scene
bpy.context.window.scene = main_scene
cleanup()

print("All done in " + str(int((time.time() * 1000.0) - start)) + "ms")
