"""
(C) 2020 amrsatrio. All rights reserved.
"""

# Change the value to the working directory of the Java program with the bat. I'm leaving mine here.
# Must end with the path separator (\\ on Windows, / on *nix) or it will fail.
data_dir = "C:\\Users\\satri\\Documents\\AppProjects\\BlenderUmap\\run\\"

# Wondering what makes stuff so long? Or if something weren't right? Flip this to True.
verbose = True

use_gltf = False

# ---------- END INPUTS, DO NOT MODIFY ANYTHING BELOW UNLESS YOU NEED TO ----------
import bpy
import json
import os
import time
from math import *
from mathutils import Vector

all_meshes = {}


def import_umap(comps, attach_parent=None):
	for comp_i, comp in enumerate(comps):
		guid = comp[0]
		export_type = comp[1]
		mesh = comp[2]
		mat = comp[3]
		textures = comp[4]
		comp_location = comp[5] or [0, 0, 0]
		comp_rotation = comp[6] or [0, 0, 0]
		comp_scale = comp[7] or [1, 1, 1]
		child_comps = comp[8]

		name = export_type + ("" if guid is None else ("_" + guid[:4]))
		print("Actor " + str(comp_i + 1) + " of " + str(len(comps)) + ": " + name)

		if child_comps is not None and len(child_comps) > 0:
			bpy.ops.mesh.primitive_plane_add(size=100)
			bpy.context.selected_objects[0].data = bpy.data.meshes["__empty"]
		elif mesh is None:
			print("WARNING: No mesh, defaulting to cube")
			cube()
		else:
			if mesh.startswith("/"): mesh = mesh[1:]

			key = mesh + ":" + str(mat)
			existing_mesh = all_meshes.get(key)

			if existing_mesh is not None:
				if verbose: print("Using existing mesh")
				bpy.ops.mesh.primitive_plane_add(size=100)
				bpy.context.selected_objects[0].data = bpy.data.meshes[existing_mesh]
			else:
				mesh_import_result = None

				if use_gltf:
					if os.path.exists(os.path.join(data_dir, mesh + ".gltf")):
						mesh += ".gltf"
					final_dir = os.path.join(data_dir, mesh)
					if verbose: print("Mesh:", final_dir)
					if os.path.exists(final_dir):
						mesh_import_result = bpy.ops.import_scene.gltf(filepath=final_dir)
					else:
						print("WARNING: Mesh not found, defaulting to cube")
						cube()
				else:
					if os.path.exists(os.path.join(data_dir, mesh + ".psk")):
						mesh += ".psk"
					elif os.path.exists(os.path.join(data_dir, mesh + ".pskx")):
						mesh += ".pskx"
					final_dir = os.path.join(data_dir, mesh)
					if verbose: print("Mesh:", final_dir)
					if os.path.exists(final_dir):
						mesh_import_result = bpy.ops.import_scene.psk(bReorientBones=True, directory=data_dir, files=[{"name": mesh}])
					else:
						print("WARNING: Mesh not found, defaulting to cube")
						cube()

				if mesh_import_result == {"FINISHED"}:
					if verbose: print("Mesh imported")
					bpy.ops.object.shade_smooth()
				else:
					print("WARNING: Failure importing mesh, defaulting to cube")
					cube()

				if mat is not None:
					import_and_apply_material(os.path.join(data_dir, mat[1:] + ".mat"), textures, True)

				all_meshes[key] = bpy.context.selected_objects[0].data.name

		created = bpy.context.selected_objects[0]
		created.name = name

		if verbose: print("Applying transformation properties")
		created.location = [comp_location[0] * 0.01,
							comp_location[1] * 0.01 * -1,
							comp_location[2] * 0.01]
		created.rotation_mode = "XYZ"
		created.rotation_euler = [radians(comp_rotation[2] + (90 if use_gltf else 0)),
								  radians(comp_rotation[0] * -1),
								  radians(comp_rotation[1] * -1)]
		created.scale = comp_scale

		if attach_parent is not None:
			if verbose: print("Attaching to parent", attach_parent.name)
			created.parent = attach_parent

		if child_comps is not None:
			if use_gltf:
				print("Nested worlds aren't supported yet with GLTF")
			else:
				for child_comp in child_comps:
					import_umap(child_comp, created)

		print("")


# credit Lucas7yoshi
def import_and_apply_material(dot_mat_path, textures, apply_to_selected):
	# make the material
	outputMaterialName = os.path.basename(dot_mat_path)

	if outputMaterialName in bpy.data.materials:
		if apply_to_selected:
			bpy.context.active_object.data.materials[0] = bpy.data.materials[outputMaterialName]
		return

	mat = bpy.data.materials.new(name=outputMaterialName)
	mat.use_nodes = True
	materialOutput = mat.node_tree.nodes.get('Material Output')
	principleBSDF = mat.node_tree.nodes.get('Principled BSDF')
	mat.node_tree.links.remove(principleBSDF.outputs[0].links[0])  # remove inital link

	addShader = mat.node_tree.nodes.new("ShaderNodeAddShader")
	mat.node_tree.links.new(principleBSDF.outputs[0], addShader.inputs[0])
	mat.node_tree.links.new(addShader.outputs[0], materialOutput.inputs[0])
	addShader.location = Vector((400, -250))
	materialOutput.location = Vector((650, -250))

	if textures[0] is not None:  # diffuse
		diffuseImgPath = os.path.join(data_dir, textures[0][1:] + ".tga")
		if verbose: print(diffuseImgPath)

		if os.path.exists(diffuseImgPath):
			diffuseTex = mat.node_tree.nodes.new("ShaderNodeTexImage")
			diffuseImg = bpy.data.images.load(filepath=diffuseImgPath)
			diffuseTex.image = diffuseImg
			diffuseTex.location = Vector((-400, 450))
			# diffuseTex.hide = True
			# connect diffuseTexture to principle
			mat.node_tree.links.new(diffuseTex.outputs[0], principleBSDF.inputs[0])
		else:
			print("WARNING: " + diffuseImgPath + " not found")

	if textures[1] is not None:  # normal
		normalImgPath = os.path.join(data_dir, textures[1][1:] + ".tga")
		if verbose: print(normalImgPath)

		if os.path.exists(normalImgPath):
			normY = -125

			normTex = mat.node_tree.nodes.new("ShaderNodeTexImage")
			normCurve = mat.node_tree.nodes.new("ShaderNodeRGBCurve")
			normMap = mat.node_tree.nodes.new("ShaderNodeNormalMap")
			normImage = bpy.data.images.load(filepath=normalImgPath)
			# location crap
			normTex.location = Vector((-800, normY))
			normCurve.location = Vector((-500, normY))
			normMap.location = Vector((-200, normY))

			normImage.colorspace_settings.name = 'Non-Color'
			normTex.image = normImage
			# normTex.hide = True
			# setup rgb curve
			normCurve.mapping.curves[1].points[0].location = (0, 1)
			normCurve.mapping.curves[1].points[1].location = (1, 0)
			# connect everything
			mat.node_tree.links.new(normTex.outputs[0], normCurve.inputs[1])
			mat.node_tree.links.new(normCurve.outputs[0], normMap.inputs[1])
			mat.node_tree.links.new(normMap.outputs[0], principleBSDF.inputs['Normal'])
		else:
			print("WARNING: " + normalImgPath + " not found")

	if textures[2] is not None:  # specular
		specularImgPath = os.path.join(data_dir, textures[2][1:] + ".tga")
		if verbose: print(specularImgPath)

		if os.path.exists(specularImgPath):
			specY = 140

			specTex = mat.node_tree.nodes.new("ShaderNodeTexImage")

			specSeperateRGB = mat.node_tree.nodes.new("ShaderNodeSeparateRGB")
			specSeperateRGB.location = Vector((-250, specY))
			# specSeperateRGB.hide = True

			specImage = bpy.data.images.load(filepath=specularImgPath)
			specImage.colorspace_settings.name = 'Non-Color'

			specTex.image = specImage
			specTex.location = Vector((-600, specY))
			# specTex.hide = True
			# connect spec texture to rgb split
			mat.node_tree.links.new(specTex.outputs[0], specSeperateRGB.inputs[0])
			# connect rgb splits to principle
			mat.node_tree.links.new(specSeperateRGB.outputs[0], principleBSDF.inputs['Specular'])
			mat.node_tree.links.new(specSeperateRGB.outputs[1], principleBSDF.inputs['Metallic'])
			mat.node_tree.links.new(specSeperateRGB.outputs[2], principleBSDF.inputs['Roughness'])
		else:
			print("WARNING: " + specularImgPath + " not found")

	if textures[3] is not None:  # emission
		emissiveImgPath = os.path.join(data_dir, textures[3][1:] + ".tga")
		if verbose: print(emissiveImgPath)

		if os.path.exists(emissiveImgPath):
			emiTex = mat.node_tree.nodes.new("ShaderNodeTexImage")
			emiShader = mat.node_tree.nodes.new("ShaderNodeEmission")
			emiImage = bpy.data.images.load(filepath=emissiveImgPath)
			emiTex.image = emiImage
			# emission - location
			emiTex.location = Vector((-200, -425))
			emiShader.location = Vector((100, -425))
			# connecting
			mat.node_tree.links.new(emiTex.outputs[0], emiShader.inputs[0])
			mat.node_tree.links.new(emiShader.outputs[0], addShader.inputs[1])
		else:
			print("WARNING: " + emissiveImgPath + " not found")

	if apply_to_selected:
		bpy.context.active_object.data.materials[0] = mat

	print("Material imported")


def cube():
	bpy.ops.mesh.primitive_cube_add(size=100)
	bpy.context.selected_objects[0].data = bpy.data.meshes["__fallback"]


start = int(time.time() * 1000.0)

# clear all objects except camera
for obj in bpy.context.scene.objects:
	if obj.type == "CAMERA": continue
	obj.select_set(True)

bpy.ops.object.delete()

# clear materials
for block in [block for block in bpy.data.materials if block.users == 0]:
	bpy.data.materials.remove(block)

# clear meshes
for block in [block for block in bpy.data.meshes if block.users == 0]:
	bpy.data.meshes.remove(block)

# setup helper objects
# 1. fallback cube
bpy.ops.mesh.primitive_cube_add(size=100)
fallback_cube = bpy.context.selected_objects[0]
fallback_cube.name = "__fallback"
fallback_cube.data.name = "__fallback"

# 2. empty mesh
bpy.ops.mesh.primitive_cube_add(size=1)
empty_mesh = bpy.context.selected_objects[0]
empty_mesh.name = "__empty"
empty_mesh.data.name = "__empty"

# do it!
with open(os.path.join(data_dir, "processed.json")) as file:
	import_umap(json.loads(file.read()))

# delete helper objects
bpy.ops.object.select_all(action="DESELECT")
fallback_cube.select_set(True)
empty_mesh.select_set(True)
bpy.ops.object.delete()

print("All done in " + str(int((time.time() * 1000.0) - start)) + "ms")
