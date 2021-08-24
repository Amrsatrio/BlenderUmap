package com.tb24.blenderumap

import me.fungames.jfortniteparse.ue4.assets.objects.FPropertyTag
import me.fungames.jfortniteparse.ue4.assets.objects.IPropertyHolder
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.math.max

fun <T> IPropertyHolder.getProp(name: String, type: Type): T? {
	for (prop in properties) {
		if (name == prop.name.text) {
			val clazz = (if (type is ParameterizedType) type.rawType else type) as Class<T>
			return prop.getTagTypeValue(clazz, type)
		}
	}
	return null
}

inline fun <T> IPropertyHolder.getProp(name: String, clazz: Class<T>): T? = getProp(name, clazz as Type)
inline fun <reified T> IPropertyHolder.getProp(name: String): T? = getProp(name, T::class.java as Type)

fun <T> IPropertyHolder.getProps(name: String, clazz: Class<T>): Array<T?> {
	val collected = mutableListOf<FPropertyTag>()
	var maxIndex = -1
	for (prop in properties) {
		if (prop.name.text == name) {
			collected.add(prop)
			maxIndex = max(maxIndex, prop.arrayIndex)
		}
	}
	val out = java.lang.reflect.Array.newInstance(clazz, maxIndex + 1) as Array<T?>
	for (prop in collected) {
		out[prop.arrayIndex] = prop.getTagTypeValue(clazz)
	}
	return out
}

inline fun <reified T> IPropertyHolder.getProps(name: String) = getProps(name, T::class.java)