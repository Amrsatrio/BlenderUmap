package com.tb24.blenderumap

import me.fungames.jfortniteparse.ue4.FGuid
import me.fungames.jfortniteparse.ue4.assets.exports.UExport
import me.fungames.jfortniteparse.ue4.assets.exports.UObject
import me.fungames.jfortniteparse.ue4.assets.objects.FPropertyTag
import java.util.*

fun <T> getProp(properties: List<FPropertyTag>, name: String, clazz: Class<T>): T? {
	for (prop in properties) {
		if (name == prop.name.text) {
			return prop.getTagTypeValue(clazz, null) as T?
		}
	}
	return null
}

fun <T> getProps(properties: List<FPropertyTag>, name: String, clazz: Class<T>): Array<T?> {
	val collected: MutableList<FPropertyTag> = ArrayList()
	var maxIndex = -1
	for (prop in properties) {
		if (prop.name.text == name) {
			collected.add(prop)
			maxIndex = Math.max(maxIndex, prop.arrayIndex)
		}
	}
	val out = java.lang.reflect.Array.newInstance(clazz, maxIndex + 1) as Array<T?>
	for (prop in collected) {
		out[prop.arrayIndex] = prop.getTagTypeValue(clazz, null) as T?
	}
	return out
}

fun <T> UExport.getProp(name: String, clazz: Class<T>) = baseObject.getProp(name, clazz)
fun <T> UObject.getProp(name: String, clazz: Class<T>) = getProp(properties, name, clazz)
inline operator fun <reified T> UExport.get(name: String): T? = getProp(name, T::class.java)
inline operator fun <reified T> UObject.get(name: String): T? = getProp(name, T::class.java)

fun FGuid?.asString() = if (this == null) null else "%08x%08x%08x%08x".format(part1, part2, part3, part4)
