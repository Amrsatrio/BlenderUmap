import bpy
import json
import os
from math import *
from mathutils import Vector

global_scale = 1.0  # 0.1
data_dir = "C:\\Users\\satri\\Documents\\AppProjects\\BlenderUmap\\run\\"

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

	if textures[0] is not None:
		diffuseImgPath = os.path.join(data_dir, textures[0][1:] + ".tga")
		print(diffuseImgPath)
		# diffuse texture
		diffuseTex = mat.node_tree.nodes.new("ShaderNodeTexImage")
		diffuseImg = bpy.data.images.load(filepath=diffuseImgPath)
		diffuseTex.image = diffuseImg
		diffuseTex.location = Vector((-400, 450))
		# diffuseTex.hide = True
		# connect diffuseTexture to principle
		mat.node_tree.links.new(diffuseTex.outputs[0], principleBSDF.inputs[0])
	# diffuse end

	if textures[1] is not None:
		normalImgPath = os.path.join(data_dir, textures[1][1:] + ".tga")
		print(normalImgPath)
		# normal begin
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
	# normal end

	if textures[2] is not None:
		specularImgPath = os.path.join(data_dir, textures[2][1:] + ".tga")
		print(specularImgPath)
		# specular start
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
	# specular end

	if textures[3] is not None:
		emissiveImgPath = os.path.join(data_dir, textures[3][1:] + ".tga")
		print(emissiveImgPath)
		# emission start
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
	# emission end

	if apply_to_selected:
		bpy.context.active_object.data.materials[0] = mat


bpy.context.scene.unit_settings.scale_length = 0.01
# bpy.context.space_data.clip_end = 100000

for obj in bpy.context.scene.objects:
	if obj.type is not "CAMERA":
		obj.select_set(True)
		mats = obj.data.materials

		for mat in mats:
			mat.user_clear()

		matLength = len(mats)
		for i in range(matLength):
			obj.data.materials.pop(index=0)

bpy.ops.object.delete()

leftOverMatBlocks = [block for block in bpy.data.materials if block.users == 0]
for block in leftOverMatBlocks:
	bpy.data.materials.remove(block)

with open(os.path.join(data_dir, "processed.json")) as handle:
	comps = json.loads(handle.read())

for comp in comps:
	guid = comp[0]
	export_type = comp[1]
	mesh = comp[2]
	mat = comp[3]
	textures = comp[4]
	comp_location = comp[5]
	comp_rotation = comp[6]
	comp_scale = comp[7]

	if comp_location is None:
		comp_location = [0, 0, 0]
	if comp_rotation is None:
		comp_rotation = [0, 0, 0]
	if comp_scale is None:
		comp_scale = [1, 1, 1]

	if mesh is None:
		bpy.ops.mesh.primitive_cube_add(size=3, enter_editmode=False)
	else:
		if mesh.startswith("/"):
			mesh = mesh[1:]
		if (os.path.exists(os.path.join(data_dir, mesh + ".psk"))):
			mesh += ".psk"
		elif (os.path.exists(os.path.join(data_dir, mesh + ".pskx"))):
			mesh += ".pskx"
		# if (os.path.exists(os.path.join(kek, mesh + "_1.psk"))):
		#	mesh += "_1.psk"
		# elif (os.path.exists(os.path.join(kek, mesh + "_1.pskx"))):
		#	mesh += "_1.pskx"

		bpy.ops.import_scene.psk(
			bReorientBones=True,
			directory=data_dir,
			files=[{"name": mesh}]
		)

		if mat is not None:
			import_and_apply_material(os.path.join(data_dir, mat[1:] + ".mat"), textures, True)

	created = bpy.context.selected_objects[0]

	if guid is not None:
		created.name = export_type + "_" + guid[:4]
	else:
		created.name = export_type

	# CONVERT UE TO BLENDER COORDS SYSTEM BY FLIPPING Y AXIS
	created.location = (comp_location[0] * global_scale,
						comp_location[1] * global_scale * -1,
						comp_location[2] * global_scale)
	created.rotation_euler = (comp_rotation[2] * (pi / 180),
							  comp_rotation[0] * (pi / 180) * -1,
							  comp_rotation[1] * (pi / 180) * -1)
	created.scale = comp_scale
	bpy.ops.object.shade_smooth()
