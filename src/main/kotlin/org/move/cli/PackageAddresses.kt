package org.move.cli

import org.move.lang.core.types.NumericAddress
import org.move.openapiext.singleSegmentOrNull
import org.move.openapiext.stringValue
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue

typealias RawAddressVal = Pair<String, TomlKeyValue>

data class TomlAddress(
    val value: String,
    val keyValue: TomlKeyValue?,
    val placeholderKeyValue: TomlKeyValue?,
    val packageName: String
) {
    val tomlKeySegment: TomlKeySegment?
        get() {
            return this.placeholderKeyValue?.singleSegmentOrNull()
                ?: this.keyValue?.singleSegmentOrNull()
        }

    val numericAddress: NumericAddress get() = NumericAddress(value)
}

data class PlaceholderVal(
    val keyValue: TomlKeyValue,
    val packageName: String,
)

typealias RawAddressMap = MutableMap<String, RawAddressVal>

typealias AddressMap = MutableMap<String, TomlAddress>
typealias PlaceholderMap = MutableMap<String, PlaceholderVal>

fun mutableRawAddressMap(): RawAddressMap = mutableMapOf()
fun mutableAddressMap(): AddressMap = mutableMapOf()
fun placeholderMap(): PlaceholderMap = mutableMapOf()

data class PackageAddresses(
    val values: AddressMap,
    val placeholders: PlaceholderMap,
) {
    fun placeholdersAsValues(): AddressMap {
        val values = mutableAddressMap()
        for ((name, pVal) in placeholders.entries) {
            val value = pVal.keyValue.value?.stringValue() ?: continue
            values[name] = TomlAddress(value, pVal.keyValue, pVal.keyValue, pVal.packageName)
        }
        return values
    }

    fun get(name: String): TomlAddress? {
        if (name in this.values) return this.values[name]
        return this.placeholders[name]
            ?.let {
                TomlAddress(MvConstants.ADDR_PLACEHOLDER, null, it.keyValue, it.packageName)
            }
    }

    fun applySubstitution(subst: RawAddressMap, packageName: String) {
        val localSubst = subst.toMutableMap()
        for ((pName, pVal) in this.placeholders.entries) {
            val pSubst = localSubst.remove(pName) ?: continue
            val (value, keyValue) = pSubst
            this.values[pName] = TomlAddress(value, keyValue, pVal.keyValue, packageName)
        }
        // renames
        for ((newName, oldNameVal) in localSubst.entries) {
            val (oldName, keyValue) = oldNameVal
            // pop old AddressVal for this name, it shouldn't be present anymore
            val oldAddressVal = this.values.remove(oldName) ?: continue
            // rename with new name and old value
            this.values[newName] = TomlAddress(oldAddressVal.value, keyValue, keyValue, packageName)
        }
    }

    fun extendWith(addresses: PackageAddresses) {
        this.values.putAll(addresses.values)
        this.placeholders.putAll(addresses.placeholders)
    }
}
