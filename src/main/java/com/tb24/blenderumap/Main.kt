/*
 * (C) amrsatrio. All rights reserved.
 */
@file:JvmName("Main")

package com.tb24.blenderumap

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.tb24.blenderumap.JWPSerializer.GSON
import me.fungames.jfortniteparse.fort.exports.BuildingTextureData
import me.fungames.jfortniteparse.ue4.assets.Package
import me.fungames.jfortniteparse.ue4.assets.exports.UBlueprintGeneratedClass
import me.fungames.jfortniteparse.ue4.assets.exports.UObject
import me.fungames.jfortniteparse.ue4.assets.exports.UStaticMesh
import me.fungames.jfortniteparse.ue4.assets.exports.UWorld
import me.fungames.jfortniteparse.ue4.assets.exports.mats.UMaterialInstance
import me.fungames.jfortniteparse.ue4.assets.exports.mats.UMaterialInterface
import me.fungames.jfortniteparse.ue4.assets.exports.tex.UTexture
import me.fungames.jfortniteparse.ue4.assets.exports.tex.UTexture2D
import me.fungames.jfortniteparse.ue4.assets.mappings.UsmapTypeMappingsProvider
import me.fungames.jfortniteparse.ue4.converters.meshes.convertMesh
import me.fungames.jfortniteparse.ue4.converters.meshes.psk.export
import me.fungames.jfortniteparse.ue4.converters.textures.getDdsFourCC
import me.fungames.jfortniteparse.ue4.converters.textures.toBufferedImage
import me.fungames.jfortniteparse.ue4.converters.textures.toDdsArray
import me.fungames.jfortniteparse.ue4.objects.core.math.FQuat
import me.fungames.jfortniteparse.ue4.objects.core.math.FRotator
import me.fungames.jfortniteparse.ue4.objects.core.math.FVector
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.objects.uobject.FSoftObjectPath
import me.fungames.jfortniteparse.ue4.versions.Ue4Version
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.*
import javax.imageio.ImageIO
import kotlin.system.exitProcess

private val LOGGER = LoggerFactory.getLogger("BlenderUmap")
private lateinit var config: Config
private lateinit var provider: MyFileProvider
private val start = System.currentTimeMillis()

fun main() {
	try {
		val configFile = File("config.json")
		if (!configFile.exists()) {
			LOGGER.error("config.json not found")
			return
		}
		LOGGER.info("Reading config file " + configFile.absolutePath)
		config = FileReader(configFile).use { GSON.newBuilder().setFieldNamingStrategy(FieldNamingPolicy.IDENTITY).create().fromJson(it, Config::class.java) }
		val paksDir = File(config.PaksDirectory)
		if (!paksDir.exists()) {
			throw MainException("Directory " + paksDir.absolutePath + " not found.")
		}
		if (config.UEVersion == null) {
			throw MainException("Please specify a valid UE version. Must be either of: " + Ue4Version.values().joinToString())
		}
		if (config.ExportPackage.isNullOrEmpty()) {
			throw MainException("Please specify ExportPackage.")
		}
		provider = MyFileProvider(paksDir, config.UEVersion, config.EncryptionKeys, config.bDumpAssets, config.ObjectCacheSize)
		getNewestUsmap("mappings")?.let {
			provider.mappingsProvider = UsmapTypeMappingsProvider(it).apply { reload() }
		}
		val pkg = exportAndProduceProcessed(config.ExportPackage) ?: return
		val file = File("processed.json")
		LOGGER.info("Writing to " + file.absolutePath)
		FileWriter(file).use { GSON.toJson(provider.compactFilePath(pkg.name), it) }
		LOGGER.info("All done in %,.1f sec. In the Python script, replace the line with data_dir with this line below:\n\ndata_dir = r\"%s\"".format((System.currentTimeMillis() - start) / 1000.0f, File("").absolutePath))
	} catch (e: Exception) {
		when (e) {
			is MainException -> LOGGER.info(e.message)
			is JsonSyntaxException -> LOGGER.error("Please check your config.json for syntax errors.\n{}", e.cause?.message)
			else -> LOGGER.error("An unexpected error has occurred, please check your config.json or report to the author", e)
		}
		exitProcess(1)
	}
}

fun getNewestUsmap(directoryFilePath: String): File? {
	val directory = File(directoryFilePath)
	val files = directory.listFiles()
	var lastModifiedTime = Long.MIN_VALUE
	var chosenFile: File? = null
	files?.forEach {
		if (it.isFile && it.name.endsWith(".usmap") && it.lastModified() > lastModifiedTime) {
			chosenFile = it
			lastModifiedTime = it.lastModified()
		}
	}
	return chosenFile
}

private fun exportAndProduceProcessed(path: String): Package? {
	val world = provider.loadObject(path)
	if (world == null) {
		LOGGER.warn("Not found: $path")
		return null
	}
	if (world !is UWorld) {
		LOGGER.warn(world.getFullName() + " is not a World, won't try to export")
		return null
	}
	val persistentLevel = world.persistentLevel!!.value
	val comps = JsonArray()
	for (actorLazy in persistentLevel.actors) {
		val actor = actorLazy?.value ?: continue
		if (actor.exportType == "LODActor") continue
		val staticMeshComponent = actor.getOrNull<Lazy<UObject>>("StaticMeshComponent")?.value ?: continue // /Script/Engine.StaticMeshActor:StaticMeshComponent or /Script/FortniteGame.BuildingSMActor:StaticMeshComponent

		// identifiers
		val comp = JsonArray()
		comps.add(comp)
		val guid = actor.getOrNull<FGuid>("MyGuid") // /Script/FortniteGame.BuildingActor:MyGuid
		comp.add(guid?.toString()?.lowercase() ?: UUID.randomUUID().toString().replace("-", ""))
		comp.add(actor.name)

		// region mesh
		var staticMeshLazy = staticMeshComponent.getOrNull<Lazy<UStaticMesh>>("StaticMesh") // /Script/Engine.StaticMeshComponent:StaticMesh
		if (staticMeshLazy == null) { // read the actor class to find the mesh
			val actorBlueprint = actor.clazz
			if (actorBlueprint is UBlueprintGeneratedClass) {
				staticMeshLazy = actorBlueprint.owner!!.exports.firstNotNullOfOrNull { it.getOrNull<Lazy<UStaticMesh>>("StaticMesh") }
			}
		}
		// endregion

		val matsObj = JsonObject()
		val textureDataArr = JsonArray()
		val materials = mutableListOf<Mat>()
		val staticMesh = staticMeshLazy?.value
		if (staticMesh != null) {
			staticMesh.convertMesh().export(exportMaterials = false)!!.writeToDir(staticMesh.getExportDir())
			if (config.bReadMaterials) {
				staticMesh.StaticMaterials?.mapTo(materials) { Mat(it.materialInterface) }
			}
		}
		if (config.bReadMaterials /*&& actor is BuildingSMActor*/) {
			var baseMaterial = actor.getOrNull<Lazy<UMaterialInterface>>("BaseMaterial") // /Script/FortniteGame.BuildingSMActor:BaseMaterial
			val overrideMaterials = staticMeshComponent.getProp<List<Lazy<UMaterialInterface>>>("OverrideMaterials", TypeToken.getParameterized(List::class.java, UMaterialInterface::class.java).type) // /Script/Engine.MeshComponent:OverrideMaterials
			for (textureDataLazy in actor.getProps<Lazy<BuildingTextureData>>("TextureData")) { // /Script/FortniteGame.BuildingSMActor:TextureData
				val textureData = textureDataLazy?.value
				if (textureData == null) {
					textureDataArr.add(JsonNull.INSTANCE)
					continue
				}
				textureDataArr.add(JsonArray().apply {
					add(textureDataLazy.toDirPath())
					add(JsonArray().apply {
						addLazy(textureData.Diffuse)
						addLazy(textureData.Normal)
						addLazy(textureData.Specular)
					})
				})
				textureData.OverrideMaterial?.let { baseMaterial = it }
			}
			materials.forEachIndexed { i, mat ->
				if (baseMaterial != null) {
					mat.material = overrideMaterials?.getOrNull(i) ?: baseMaterial
				}
				mat.populateTextures()
				mat.addToObj(matsObj)
			}
		}

		// region additional worlds
		val children = JsonArray()
		val additionalWorlds = actor.getProp<List<FSoftObjectPath>>("AdditionalWorlds", TypeToken.getParameterized(List::class.java, FSoftObjectPath::class.java).type) // /Script/FortniteGame.BuildingFoundation:AdditionalWorlds
		if (config.bExportBuildingFoundations && additionalWorlds != null) {
			for (additionalWorld in additionalWorlds) {
				val worldPackage = exportAndProduceProcessed(additionalWorld.assetPathName.text)
				children.add(worldPackage?.let { provider.compactFilePath(it.name) })
			}
		}
		// endregion

		comp.add(staticMeshLazy.toDirPath())
		comp.add(matsObj)
		comp.add(textureDataArr)
		comp.add(staticMeshComponent.getOrNull<FVector>("RelativeLocation").array()) // /Script/Engine.SceneComponent:RelativeLocation
		comp.add(staticMeshComponent.getOrNull<FRotator>("RelativeRotation").array()) // /Script/Engine.SceneComponent:RelativeRotation
		comp.add(staticMeshComponent.getOrNull<FVector>("RelativeScale3D").array()) // /Script/Engine.SceneComponent:RelativeScale3D
		comp.add(children)
	}
	/*if (config.bExportBuildingFoundations) {
		for (streamingLevelLazy in world.streamingLevels) {
			val streamingLevel = streamingLevelLazy?.value ?: continue
			val children = JsonArray()
			val worldPackage = exportAndProduceProcessed(streamingLevel.get<FSoftObjectPath>("WorldAsset").assetPathName.text)
			children.add(if (worldPackage != null) provider.compactFilePath(worldPackage.name) else null)
			val transform = streamingLevel.get<FTransform>("LevelTransform")
			val comp = JsonArray()
			comp.add(JsonNull.INSTANCE) // GUID
			comp.add(streamingLevel.name)
			comp.add(JsonNull.INSTANCE) // mesh path
			comp.add(JsonNull.INSTANCE) // materials
			comp.add(JsonNull.INSTANCE) // texture data
			comp.add(transform.translation.array()) // location
			comp.add(transform.rotation.array()) // rotation
			comp.add(transform.scale3D.array()) // scale
			comp.add(children)
			comps.add(comp)
		}
	}*/
	val pkg = world.owner!!
	val pkgName = provider.compactFilePath(pkg.name)
	val file = File(MyFileProvider.JSONS_FOLDER, "$pkgName.processed.json")
	file.parentFile.mkdirs()
	LOGGER.info("Writing to {}", file.absolutePath)
	try {
		FileWriter(file).use { writer -> GSON.toJson(comps, writer) }
	} catch (e: IOException) {
		LOGGER.error("Writing failed", e)
	}
	return pkg
}

private fun JsonArray.addLazy(lazy: Lazy<UTexture>?) {
	if (lazy != null) {
		exportTexture(lazy)
		add(lazy.toDirPath())
	} else {
		add(JsonNull.INSTANCE)
	}
}

private fun exportTexture(lazy: Lazy<UTexture>) {
	try {
		val texture = lazy.value as? UTexture2D ?: return
		val platformData = texture.getFirstTexture()
		val fourCC = if (config.bExportToDDSWhenPossible) platformData.getDdsFourCC() else null
		val output = File(texture.getExportDir(), texture.name + if (fourCC != null) ".dds" else ".png")
		if (output.exists()) {
			LOGGER.debug("Texture already exists, skipping: {}", output.absolutePath)
		} else {
			LOGGER.info("Saving texture to {}", output.absolutePath)
			if (fourCC != null) {
				output.writeBytes(texture.toDdsArray(platformData, platformData.getFirstLoadedMip()))
			} else {
				ImageIO.write(texture.toBufferedImage(platformData, platformData.getFirstLoadedMip()), "png", output)
			}
		}
	} catch (e: Exception) {
		LOGGER.warn("Failed to save texture", e)
	}
}

fun UObject.getExportDir(): File {
	var pkgPath = provider.compactFilePath(owner!!.name)
	pkgPath = pkgPath.substringBeforeLast('.', pkgPath)
	if (pkgPath.startsWith("/")) {
		pkgPath = pkgPath.substring(1)
	}
	var outputDir = File(pkgPath).parentFile
	val pkgName = pkgPath.substringAfterLast('/', pkgPath)
	if (name != pkgName) {
		outputDir = File(outputDir, pkgName)
	}
	outputDir.mkdirs()
	return outputDir
}

fun Lazy<UObject>?.toDirPath(): String? {
	if (this == null) return null
	val obj = try {
		value
	} catch (e: Exception) {
		LOGGER.warn("Failed to load object", e)
		return null
	}
	var pkgPath = provider.compactFilePath(obj.owner!!.name)
	pkgPath = pkgPath.substringBeforeLast('.', pkgPath)
	val objectName = obj.name
	return if (pkgPath.substringAfterLast('/', pkgPath) == objectName) pkgPath else "$pkgPath/$objectName"
}

private fun FVector?.array() = this?.run { JsonArray(3).apply { add(x); add(y); add(z) } }
private fun FRotator?.array() = this?.run { JsonArray(3).apply { add(pitch); add(yaw); add(roll) } }
private fun FQuat?.array() = this?.run { JsonArray(4).apply { add(x); add(y); add(z); add(w) } }

private class Mat(var material: Lazy<UMaterialInterface>?) {
	var textureMap = hashMapOf<String, Lazy<UTexture>>()

	@JvmOverloads
	fun populateTextures(lazy: Lazy<UMaterialInterface>? = material) {
		if (lazy == null) return
		val material = lazy.value as? UMaterialInstance ?: return
		material.TextureParameterValues?.forEach {
			val name = it.ParameterInfo.Name ?: FName.NAME_None
			if (name.text !in textureMap) {
				textureMap[name.text] = it.ParameterValue
			}
		}
		material.Parent?.let(::populateTextures)
	}

	fun addToObj(obj: JsonObject) {
		if (material == null) {
			obj.add(Integer.toHexString(hashCode()), null)
			return
		}
		val textures = arrayOf(
			arrayOf(
				textureMap["Trunk_BaseColor"] ?: textureMap["Diffuse"] ?: textureMap["DiffuseTexture"],
				textureMap["Trunk_Normal"] ?: textureMap["Normals"],
				textureMap["Trunk_Specular"] ?: textureMap["SpecularMasks"],
				textureMap["EmissiveTexture"],
				textureMap["MaskTexture"]
			), arrayOf(
				textureMap["Diffuse_Texture_3"],
				textureMap["Normals_Texture_3"],
				textureMap["SpecularMasks_3"],
				textureMap["EmissiveTexture_3"],
				textureMap["MaskTexture_3"]
			), arrayOf(
				textureMap["Diffuse_Texture_4"],
				textureMap["Normals_Texture_4"],
				textureMap["SpecularMasks_4"],
				textureMap["EmissiveTexture_4"],
				textureMap["MaskTexture_4"]
			), arrayOf(
				textureMap["Diffuse_Texture_2"],
				textureMap["Normals_Texture_2"],
				textureMap["SpecularMasks_2"],
				textureMap["EmissiveTexture_2"],
				textureMap["MaskTexture_2"]
			)
		)
		val array = JsonArray(textures.size)
		for (texture in textures) {
			var exported = 0
			for (lazy in texture) {
				if (lazy != null) {
					exportTexture(lazy)
					++exported
				}
			}
			val subArray = JsonArray(texture.size)
			if (exported > 0) {
				for (lazy in texture) {
					subArray.add(lazy.toDirPath())
				}
			}
			array.add(subArray)
		}
		obj.add(material.toDirPath(), array)
	}
}

private class MainException(message: String?) : Exception(message)