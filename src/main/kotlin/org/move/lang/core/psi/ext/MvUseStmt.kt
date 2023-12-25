package org.move.lang.core.psi.ext

import org.move.ide.inspections.imports.declScope
import org.move.lang.core.psi.*
import org.move.stdext.wrapWithList

val MvUseStmt.addressRef: MvAddressRef?
    get() {
        val moduleUseSpeck = this.moduleUseSpeck
        if (moduleUseSpeck != null) {
            val fqModuleRef = moduleUseSpeck.fqModuleRef
            if (fqModuleRef != null) {
                return fqModuleRef.addressRef
            } else {
                return moduleUseSpeck.addressRef
            }
        }
        val itemUseSpeck = this.itemUseSpeck
        if (itemUseSpeck != null) {
            return itemUseSpeck.fqModuleRef.addressRef
        }
        return null
    }

val MvUseStmt.useGroupLevel: Int
    get() {
        if (this.hasTestOnlyAttr) return 5
        return this.addressRef?.useGroupLevel ?: -1
    }

val MvUseStmt.fqModuleText: String?
    get() {
        val fqModuleRef = this.fqModuleRef ?: return null
        return fqModuleRef.text
    }

val MvUseStmt.fqModuleRef: MvFQModuleRef?
    get() {
        val moduleUseSpeck = this.moduleUseSpeck
        if (moduleUseSpeck != null) {
            return moduleUseSpeck.fqModuleRef
        }
        val itemUseSpeck = this.itemUseSpeck
        if (itemUseSpeck != null) {
            return itemUseSpeck.fqModuleRef
        }
        return null
    }

val MvUseStmt.childUseItems: List<MvUseItem>
    get() {
        return this.itemUseSpeck?.useItems.orEmpty()
//        if (itemUseSpeck != null) {
//            return itemUseSpeck.useItems
////            val group = itemUseSpeck.useItemGroup
////            if (group != null) {
////                return group.useItemList
////            }
////            return itemUseSpeck.useItem.wrapWithList()
//        }
//        return emptyList()
    }

val MvItemUseSpeck.useItems: List<MvUseItem>
    get() {
        val group = this.useItemGroup
        if (group != null) {
            return group.useItemList
        }
        return this.useItem.wrapWithList()
    }

sealed class UseSpeck(open val nameOrAlias: String, open val scope: ItemScope) {
    data class Module(
        override val nameOrAlias: String,
        override val scope: ItemScope,
        val moduleUseSpeck: MvModuleUseSpeck,
    ): UseSpeck(nameOrAlias, scope)

    data class SelfModule(
        override val nameOrAlias: String,
        override val scope: ItemScope,
        val useItem: MvUseItem,
    ): UseSpeck(nameOrAlias, scope)

    data class Item(
        override val nameOrAlias: String,
        override val scope: ItemScope,
        val useItem: MvUseItem,
    ): UseSpeck(nameOrAlias, scope)
}

val MvUseStmt.useSpecks: List<UseSpeck>
    get() {
        val useScope = this.declScope
        val moduleUseSpeck = this.moduleUseSpeck
        if (moduleUseSpeck != null) {
            val nameOrAlias = moduleUseSpeck.nameElement?.text ?: return emptyList()
            return listOf(UseSpeck.Module(nameOrAlias, useScope, moduleUseSpeck))
        }
        return this.itemUseSpeck?.useItems.orEmpty()
            .mapNotNull {
                if (it.isSelf) {
                    val useAlias = it.useAlias
                    val nameOrAlias =
                        if (useAlias != null) {
                            val aliasName = useAlias.name ?: return@mapNotNull null
                            aliasName
                        } else {
                            it.moduleName
                        }
                    UseSpeck.SelfModule(nameOrAlias, useScope, it)
                } else {
                    val nameOrAlias = it.nameOrAlias ?: return@mapNotNull null
                    UseSpeck.Item(nameOrAlias, useScope, it)
                }
            }
    }

val MvUseStmt.useSpeckText: String
    get() {
        val moduleUseSpeck = this.moduleUseSpeck
        if (moduleUseSpeck != null) {
            return moduleUseSpeck.text
        }
        val itemUseSpeck = this.itemUseSpeck
        if (itemUseSpeck != null) {
            return itemUseSpeck.text
        }
        return ""
    }
