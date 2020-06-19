/*
 * (C) 2020 amrsatrio. All rights reserved.
 */
package com.tb24.blenderumap

import com.google.gson.*
import me.fungames.jfortniteparse.ue4.FGuid
import me.fungames.jfortniteparse.ue4.assets.exports.UDataTable
import me.fungames.jfortniteparse.ue4.assets.exports.UExport
import me.fungames.jfortniteparse.ue4.assets.exports.UObject
import me.fungames.jfortniteparse.ue4.assets.objects.*
import me.fungames.jfortniteparse.ue4.assets.util.FName
import me.fungames.jfortniteparse.util.parseHexBinary
import java.lang.reflect.Type
import java.util.*

/**
 * Provides John Wick Parse JSON data structure for JFortniteParse objects.
 * @author amrsatrio
 */
@ExperimentalUnsignedTypes
object JWPSerializer {
	@JvmField
	val GSON: Gson = GsonBuilder()
			.disableHtmlEscaping()
			.setPrettyPrinting()
			.serializeNulls()
			.registerTypeAdapter(ByteArray::class.java, ByteArraySerializer())
			.registerTypeAdapter(UByte::class.java, JsonSerializer<UByte> { src, typeOfSrc, context -> JsonPrimitive(src.toByte()) })
			.registerTypeHierarchyAdapter(UExport::class.java, ExportSerializer())
			.registerTypeHierarchyAdapter(FPropertyTagType::class.java, JsonSerializer<FPropertyTagType> { src, typeOfSrc, context ->
				if (src is FPropertyTagType.DelegateProperty) {
					JsonObject().apply {
						addProperty("object", src.`object`)
						add("name", context.serialize(src.name))
					}
				} else {
					context.serialize(src.getTagTypeValue())
				}
			})
			.registerTypeAdapter(FBox::class.java, JsonSerializer<FBox> { src, typeOfSrc, context ->
				JsonObject().apply {
					add("min", context.serialize(src.min))
					add("max", context.serialize(src.max))
				}
			})
			.registerTypeAdapter(FGameplayTagContainer::class.java, JsonSerializer<FGameplayTagContainer> { src, typeOfSrc, context ->
				JsonObject().apply {
					add("gameplay_tags", JsonArray().apply { src.gameplayTags.forEach { add(context.serialize(it)) } })
				}
			})
			.registerTypeAdapter(FGuid::class.java, GuidSerializer())
			.registerTypeAdapter(FLinearColor::class.java, JsonSerializer<FLinearColor> { src, typeOfSrc, context ->
				JsonObject().apply {
					addProperty("r", src.r)
					addProperty("g", src.g)
					addProperty("b", src.b)
					addProperty("a", src.a)
				}
			})
			.registerTypeAdapter(FName::class.java, JsonSerializer<FName> { src, typeOfSrc, context ->
				JsonPrimitive(src.text)
			})
			.registerTypeAdapter(FPackageIndex::class.java, JsonSerializer<FPackageIndex> { src, typeOfSrc, context ->
				var out: JsonElement? = null
				val importObject = src.importObject

				if (src.index >= 0) {
					out = JsonObject()
					out.addProperty("export", src.index)
				} else if (importObject != null) {
					out = JsonArray()
					out.add(context.serialize(importObject.objectName))
					out.add(context.serialize(src.outerImportObject!!.objectName))

					if (src.outerImportObject!!.outerIndex.importObject != null) {
						out.add(context.serialize(src.outerImportObject!!.outerIndex.importObject!!.objectName))
					}
				}

				out
			})
			.registerTypeAdapter(FRotator::class.java, JsonSerializer<FRotator> { src, typeOfSrc, context ->
				JsonObject().apply {
					addProperty("pitch", src.pitch)
					addProperty("yaw", src.yaw)
					addProperty("roll", src.roll)
				}
			})
			.registerTypeAdapter(UScriptArray::class.java, JsonSerializer<UScriptArray> { src, typeOfSrc, context ->
				JsonArray().apply { src.contents.forEach { add(context.serialize(it)) } }
			})
			.registerTypeAdapter(UScriptMap::class.java, JsonSerializer<UScriptMap> { src, typeOfSrc, context ->
				JsonArray().apply {
					for ((k, v) in src.mapData) {
						add(JsonObject().apply {
							add("key", context.serialize(k))
							add("value", context.serialize(v))
						})
					}
				}
			})
			.registerTypeAdapter(FSoftObjectPath::class.java, JsonSerializer<FSoftObjectPath> { src, typeOfSrc, context ->
				JsonObject().apply {
					addProperty("asset_path_name", src.assetPathName.text)
					addProperty("sub_path_string", src.subPathString)
				}
			})
			.registerTypeAdapter(FStructFallback::class.java, JsonSerializer<FStructFallback> { src, typeOfSrc, context ->
				JsonObject().apply { serializeProperties(this, src.properties, context) }
			})
			.registerTypeAdapter(FText::class.java, JsonSerializer<FText> { src, typeOfSrc, context ->
				val h = if (src.textHistory is FTextHistory.None) FTextHistory.Base("", "", "") else src.textHistory
				JsonObject().apply {
					addProperty("string", h.text)
					when (h) {
						is FTextHistory.Base -> {
							addProperty("namespace", h.nameSpace)
							addProperty("key", h.key)
						}
						is FTextHistory.StringTableEntry -> {
							add("table_id", context.serialize(h.tableId))
							addProperty("key", h.key)
						}
					}
				}
			})
			.registerTypeAdapter(FVector::class.java, JsonSerializer<FVector> { src, typeOfSrc, context ->
				JsonObject().apply {
					addProperty("x", src.x)
					addProperty("y", src.y)
					addProperty("z", src.z)
				}
			})
			.registerTypeAdapter(FVector2D::class.java, JsonSerializer<FVector2D> { src, typeOfSrc, context ->
				JsonObject().apply {
					addProperty("x", src.x)
					addProperty("y", src.y)
				}
			})
			.create()

	private fun serializeProperties(obj: JsonObject, properties: List<FPropertyTag>, context: JsonSerializationContext) {
		for (propertyTag in properties) {
			obj.add(propertyTag.name.text, context.serialize(propertyTag.tag))
		}
	}

	private class ExportSerializer : JsonSerializer<UExport> {
		override fun serialize(src: UExport, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
			val obj = JsonObject()
			obj.addProperty("export_type", src.exportType)

			if (src is UObject) {
				serializeProperties(obj, src.properties, context)
			} else if (src is UDataTable) {
				for ((key, value) in src.rows) {
					obj.add(key.text, context.serialize(value))
				}
			}

			return obj
		}
	}

	private class GuidSerializer : JsonSerializer<FGuid>, JsonDeserializer<FGuid> {
		override fun serialize(src: FGuid, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
			return JsonPrimitive("%08x%08x%08x%08x".format(src.part1.toInt(), src.part2.toInt(), src.part3.toInt(), src.part4.toInt()))
		}

		override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FGuid? {
			return FGuid(json.asString)
		}
	}

	private class ByteArraySerializer : JsonSerializer<ByteArray>, JsonDeserializer<ByteArray> {
		override fun serialize(src: ByteArray, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
			return context.serialize(ByteArrayUtils.encode(src))
		}

		override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ByteArray? {
			val s = json.asString

			return if (s.startsWith("0x")) {
				s.substring(2).parseHexBinary()
			} else {
				Base64.getDecoder().decode(s);
			}
		}
	}
}
