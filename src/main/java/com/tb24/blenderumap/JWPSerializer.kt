/*
 * (C) 2020 amrsatrio. All rights reserved.
 */
package com.tb24.blenderumap

import com.google.gson.*
import me.fungames.jfortniteparse.ue4.assets.IoPackage
import me.fungames.jfortniteparse.ue4.assets.IoPackage.*
import me.fungames.jfortniteparse.ue4.assets.PakPackage
import me.fungames.jfortniteparse.ue4.assets.UProperty
import me.fungames.jfortniteparse.ue4.assets.exports.ECurveTableMode
import me.fungames.jfortniteparse.ue4.assets.exports.UCurveTable
import me.fungames.jfortniteparse.ue4.assets.exports.UDataTable
import me.fungames.jfortniteparse.ue4.assets.exports.UObject
import me.fungames.jfortniteparse.ue4.assets.objects.*
import me.fungames.jfortniteparse.ue4.objects.core.i18n.FText
import me.fungames.jfortniteparse.ue4.objects.core.i18n.FTextHistory
import me.fungames.jfortniteparse.ue4.objects.core.math.FBox
import me.fungames.jfortniteparse.ue4.objects.core.math.FBox2D
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid
import me.fungames.jfortniteparse.ue4.objects.gameplaytags.FGameplayTagContainer
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageIndex
import me.fungames.jfortniteparse.ue4.objects.uobject.FSoftObjectPath
import me.fungames.jfortniteparse.util.parseHexBinary
import me.fungames.jfortniteparse.util.printHexBinary
import java.lang.reflect.Type
import java.math.BigInteger
import java.util.*

/**
 * Provides John Wick Parse JSON data structure for JFortniteParse objects.
 * @author amrsatrio
 */
object JWPSerializer {
	@JvmField
	var sUseNonstandardFormat = false

	@JvmField
	val GSON: Gson = GsonBuilder()
		.disableHtmlEscaping()
		//.setPrettyPrinting()
		.serializeNulls()
		.setFieldNamingStrategy {
			it.getAnnotation(UProperty::class.java)?.name
				?: separateCamelCase(it.name, "_").toLowerCase(Locale.ENGLISH)
		}
		.registerTypeAdapter(ByteArray::class.java, ByteArraySerializer())
		.registerTypeAdapter(UByte::class.java, JsonSerializer<UByte> { src, typeOfSrc, context -> JsonPrimitive(src.toShort()) })
		.registerTypeAdapter(UShort::class.java, JsonSerializer<UShort> { src, typeOfSrc, context -> JsonPrimitive(src.toInt()) })
		.registerTypeAdapter(UInt::class.java, JsonSerializer<UInt> { src, typeOfSrc, context -> JsonPrimitive(src.toLong()) })
		.registerTypeAdapter(ULong::class.java, JsonSerializer<ULong> { src, typeOfSrc, context -> JsonPrimitive(BigInteger(src.toString())) })
		.registerTypeHierarchyAdapter(UObject::class.java, ExportSerializer())
		.registerTypeHierarchyAdapter(FProperty::class.java, JsonSerializer<FProperty> { src, typeOfSrc, context ->
			context.serialize(src.getTagTypeValue())
		})
		.registerTypeAdapter(FBox::class.java, JsonSerializer<FBox> { src, typeOfSrc, context ->
			JsonObject().apply {
				add("min", context.serialize(src.min))
				add("max", context.serialize(src.max))
				add("valid", context.serialize(src.isValid))
			}
		})
		.registerTypeAdapter(FBox2D::class.java, JsonSerializer<FBox2D> { src, typeOfSrc, context ->
			JsonObject().apply {
				add("min", context.serialize(src.min))
				add("max", context.serialize(src.max))
				add("valid", context.serialize(src.isValid))
			}
		})
		.registerTypeAdapter(FGameplayTagContainer::class.java, JsonSerializer<FGameplayTagContainer> { src, typeOfSrc, context ->
			JsonObject().apply {
				add("gameplay_tags", JsonArray().apply { src.gameplayTags.forEach { add(context.serialize(it)) } })
			}
		})
		.registerTypeAdapter(FGuid::class.java, GuidSerializer())
		.registerTypeHierarchyAdapter(FName::class.java, JsonSerializer<FName> { src, typeOfSrc, context ->
			JsonPrimitive(src.text)
		})
		.registerTypeAdapter(FPackageIndex::class.java, JsonSerializer<FPackageIndex> { src, typeOfSrc, context ->
			if (src.isImport()) {
				val pkg = src.owner
				if (pkg is PakPackage) {
					JsonArray().apply {
						var current = pkg.run { src.getResource() }
						while (current != null) {
							add(current.objectName.text)
							current = pkg.run { current!!.outerIndex.getResource() }
						}
					}
				} else {
					when (val resolved = (pkg as IoPackage).resolveObjectIndex(pkg.importMap[src.toImport()])) {
						is ResolvedScriptObject -> JsonArray().apply {
							var current: ResolvedObject = resolved
							do {
								add(current.getName().text)
							} while (current.getOuter()?.also { current = it } != null)
						}
						is ResolvedExportObject -> JsonArray().apply {
							add(resolved.getName().text)
							add(resolved.pkg.fileName)
						}
						else -> null
					}
				}
			} else {
				JsonObject().apply {
					addProperty("export", src.index)
				}
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
		.registerTypeHierarchyAdapter(FSoftObjectPath::class.java, JsonSerializer<FSoftObjectPath> { src, typeOfSrc, context ->
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
						addProperty("namespace", h.namespace)
						addProperty("key", h.key)
					}
					is FTextHistory.StringTableEntry -> {
						add("table_id", context.serialize(h.tableId))
						addProperty("key", h.key)
					}
				}
			}
		})
		.create()

	private fun serializeProperties(obj: JsonObject, properties: List<FPropertyTag>, context: JsonSerializationContext) {
		properties.forEach {
			obj.add(it.name.text + (if (it.arrayIndex != 0) "[${it.arrayIndex}]" else ""), context.serialize(it.prop))
		}
	}

	private class ExportSerializer : JsonSerializer<UObject> {
		override fun serialize(src: UObject, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
			if (src is UCurveTable) {
				return JsonObject().apply {
					add("super_object", normal(src, context))
					addProperty("curve_table_mode", src.curveTableMode.name)
					add("row_map", JsonArray(src.rowMap.size).apply {
						for ((k, v) in src.rowMap) {
							add(JsonArray(2).apply {
								add(context.serialize(k))
								add(JsonObject().apply {
									addProperty("export_type", when (src.curveTableMode) {
										ECurveTableMode.Empty -> "Empty"
										ECurveTableMode.SimpleCurves -> "SimpleCurveKey"
										ECurveTableMode.RichCurves -> "RichCurveKey"
									}) // this is actually wrong but we're serializing it anyways to keep compatibility
									context.serialize(v).asJsonObject.entrySet().forEach {
										add(it.key, it.value)
									}
								})
							})
						}
					})
				}
			} else {
				return normal(src, context)
			}
		}

		private fun normal(src: UObject, context: JsonSerializationContext): JsonObject {
			val obj = JsonObject()
			//if (sUseNonstandardFormat && src.export != null) obj.addProperty("object_name", src.export!!.objectName.text)
			obj.addProperty("export_type", src.exportType)

			if (src !is UDataTable || sUseNonstandardFormat)
				serializeProperties(obj, (src as UObject).properties, context)

			if (src is UDataTable) {
				if (sUseNonstandardFormat) {
					obj.add("rows", JsonObject().apply {
						for ((key, value) in src.rows) {
							add(key.text, JsonObject().apply {
								addProperty("export_type", "RowStruct")
								serializeProperties(this, value.properties, context)
							})
						}
					})
				} else {
					for ((key, value) in src.rows) {
						obj.add(key.text, JsonObject().apply {
							addProperty("export_type", "RowStruct")
							serializeProperties(this, value.properties, context)
						})
					}
				}
			}

			return obj
		}
	}

	private class GuidSerializer : JsonSerializer<FGuid>, JsonDeserializer<FGuid> {
		override fun serialize(src: FGuid, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
			return JsonPrimitive("%08x%08x%08x%08x".format(src.a.toInt(), src.b.toInt(), src.c.toInt(), src.d.toInt()))
		}

		override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FGuid? {
			return FGuid(json.asString)
		}
	}

	private class ByteArraySerializer : JsonSerializer<ByteArray>, JsonDeserializer<ByteArray> {
		override fun serialize(src: ByteArray, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
			return JsonPrimitive("0x" + src.printHexBinary())
		}

		override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ByteArray? {
			val s = json.asString

			return if (s.startsWith("0x")) {
				s.substring(2).parseHexBinary()
			} else {
				Base64.getDecoder().decode(s)
			}
		}
	}

	fun separateCamelCase(name: String, separator: String): String {
		val translation = StringBuilder()
		var i = 0
		val length = name.length
		while (i < length) {
			val character = name[i]
			if (Character.isUpperCase(character) && translation.isNotEmpty()) {
				translation.append(separator)
			}
			translation.append(character)
			i++
		}
		return translation.toString()
	}
}
