"""
(C) 2020 amrsatrio. All rights reserved.
"""

# Change the value to the run folder of the Java program. I'm leaving mine here.
data_dir = "C:\\Users\\satri\\Documents\\AppProjects\\BlenderUmap\\run\\"

# Wondering what makes stuff so long? Or if something weren't right? Flip this to True.
verbose = True

use_gltf = False

# ---------- END INPUTS, DO NOT MODIFY ANYTHING BELOW UNLESS YOU NEED TO ----------
import bpy
import json
import os
from math import *
from mathutils import Vector


def import_umap(comps, attach_parent=None):
	for comp_i, comp in enumerate(comps):
		print("Importing actor " + str(comp_i) + " of " + str(len(comps)))
		guid = comp[0]
		export_type = comp[1]
		mesh = comp[2]
		mat = comp[3]
		textures = comp[4]
		comp_location = comp[5] or [0, 0, 0]
		comp_rotation = comp[6] or [0, 0, 0]
		comp_scale = comp[7] or [1, 1, 1]
		child_comps = comp[8]

		if mesh is None:
			print("WARNING: No mesh, defaulting to cube")
			bpy.ops.mesh.primitive_cube_add(size=10)
		else:
			if mesh.startswith("/"):
				mesh = mesh[1:]

			if use_gltf:
				if (os.path.exists(os.path.join(data_dir, mesh + ".gltf"))):
					mesh += ".gltf"
				mesh_import_result = bpy.ops.import_scene.gltf(filepath=os.path.join(data_dir, mesh))
			else:
				if (os.path.exists(os.path.join(data_dir, mesh + ".psk"))):
					mesh += ".psk"
				elif (os.path.exists(os.path.join(data_dir, mesh + ".pskx"))):
					mesh += ".pskx"
				mesh_import_result = bpy.ops.import_scene.psk(bReorientBones=True, directory=data_dir, files=[{"name": mesh}])

			if mesh_import_result == {"FINISHED"}:
				if verbose: print("Mesh imported")
				bpy.ops.object.shade_smooth()
			else:
				print("WARNING: Failure importing mesh, defaulting to cube")
				bpy.ops.mesh.primitive_cube_add(size=10)

			if mat is not None:
				import_and_apply_material(os.path.join(data_dir, mat[1:] + ".mat"), textures, True)

		created = bpy.context.selected_objects[0]

		if guid is not None:
			created.name = export_type + "_" + guid[:4]
		else:
			created.name = export_type

		if verbose: print("Applying transformation properties")
		created.location = (comp_location[0],
							comp_location[1] * -1,
							comp_location[2])
		created.rotation_mode = "XYZ"
		created.rotation_euler = (radians(comp_rotation[2] + (90 if use_gltf else 0)),
								  radians(comp_rotation[0] * -1),
								  radians(comp_rotation[1] * -1))
		created.scale = [comp_scale[0] * 100,
						 comp_scale[1] * 100,
						 comp_scale[2] * 100] if use_gltf else comp_scale

		if attach_parent is not None:
			if verbose: print("Attaching to parent", attach_parent.name)
			created.parent = attach_parent

		if child_comps is not None:
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
		diffuseTex = mat.node_tree.nodes.new("ShaderNodeTexImage")
		diffuseImg = bpy.data.images.load(filepath=diffuseImgPath)
		diffuseTex.image = diffuseImg
		diffuseTex.location = Vector((-400, 450))
		# diffuseTex.hide = True
		# connect diffuseTexture to principle
		mat.node_tree.links.new(diffuseTex.outputs[0], principleBSDF.inputs[0])

	if textures[1] is not None:  # normal
		normalImgPath = os.path.join(data_dir, textures[1][1:] + ".tga")
		if verbose: print(normalImgPath)
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

	if textures[2] is not None:  # specular
		specularImgPath = os.path.join(data_dir, textures[2][1:] + ".tga")
		if verbose: print(specularImgPath)
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

	if textures[3] is not None:  # emission
		emissiveImgPath = os.path.join(data_dir, textures[3][1:] + ".tga")
		if verbose: print(emissiveImgPath)
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

	if apply_to_selected:
		bpy.context.active_object.data.materials[0] = mat

	print("Material imported")


bpy.context.scene.unit_settings.scale_length = 0.01
# bpy.context.space_data.clip_end = 100000

for obj in bpy.context.scene.objects:
	if obj.type == "CAMERA": continue

	obj.select_set(True)
	mats = obj.data.materials

	for mat in mats:
		mat.user_clear()

	matLength = len(mats)
	for i in range(matLength):
		obj.data.materials.pop(index=0)

bpy.ops.object.delete()

for block in [block for block in bpy.data.materials if block.users == 0]:
	bpy.data.materials.remove(block)

with open(os.path.join(data_dir, "processed.json")) as file:
	import_umap(json.loads(file.read()))
