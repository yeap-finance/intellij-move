package org.move.ide.inspections.imports

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValuesManager.getProjectPsiDependentCache
import com.intellij.psi.util.PsiTreeUtil
import org.move.ide.inspections.imports.PathStart.Companion.pathStart
import org.move.lang.core.psi.*
import org.move.lang.core.psi.ext.*
import org.move.utils.cache
import org.move.utils.cacheManager
import org.move.utils.psiFileTrackedCachedResult

typealias ItemUsages = MutableMap<String, MutableSet<MvNamedElement>>

data class ScopePathUsages(
    val nameUsages: ItemUsages,
    val typeUsages: ItemUsages,
) {
    fun updateFrom(other: ScopePathUsages) {
        nameUsages.updateFromOther(other.nameUsages)
        typeUsages.updateFromOther(other.typeUsages)
    }

    fun all(): ItemUsages {
        val allUsages: ItemUsages = mutableMapOf()
        allUsages.updateFromOther(nameUsages)
        allUsages.updateFromOther(typeUsages)
        return allUsages
    }

    companion object {
        private fun ItemUsages.updateFromOther(other: ItemUsages) {
            for ((otherKey, otherValue) in other.entries) {
                val usages = this.getOrDefault(otherKey, mutableSetOf())
                usages.addAll(otherValue)
                this[otherKey] = usages
            }
        }
    }
}

data class PathUsages(
    val mainScopeUsages: ScopePathUsages,
    val testScopeUsages: ScopePathUsages,
    val verifyScopeUsages: ScopePathUsages,
) {
    fun updateFrom(other: PathUsages) {
        mainScopeUsages.updateFrom(other.mainScopeUsages)
        testScopeUsages.updateFrom(other.testScopeUsages)
        verifyScopeUsages.updateFrom(other.verifyScopeUsages)
    }

    fun getScopeUsages(itemScope: ItemScope): ScopePathUsages {
        return when (itemScope) {
            ItemScope.MAIN -> mainScopeUsages
            ItemScope.TEST -> testScopeUsages
            ItemScope.VERIFY -> verifyScopeUsages
        }
    }
}

val MvImportsOwner.pathUsages: PathUsages
    get() {
        val localPathUsages = this.localPathUsages()
        when (this) {
            is MvModuleBlock -> {
                for (specBlock in this.module.allModuleSpecBlocks()) {
                    localPathUsages.updateFrom(specBlock.localPathUsages())
                }
            }
            is MvModuleSpecBlock -> {
                val module = this.moduleSpec.moduleItem ?: return localPathUsages
                val moduleBlock = module.moduleBlock
                if (moduleBlock != null) {
                    localPathUsages.updateFrom(moduleBlock.localPathUsages())
                }
                for (specBlock in module.allModuleSpecBlocks().filter { it != this }) {
                    localPathUsages.updateFrom(specBlock.localPathUsages())
                }
            }
        }
        return localPathUsages
    }

private fun MvImportsOwner.localPathUsages(): PathUsages {
    return getProjectPsiDependentCache(this) { importsOwner ->

        val mainNameUsages = mutableMapOf<String, MutableSet<MvNamedElement>>()
        val mainTypeUsages = mutableMapOf<String, MutableSet<MvNamedElement>>()

        val testNameUsages = mutableMapOf<String, MutableSet<MvNamedElement>>()
        val testTypeUsages = mutableMapOf<String, MutableSet<MvNamedElement>>()

        val verifyNameUsages = mutableMapOf<String, MutableSet<MvNamedElement>>()
        val verifyTypeUsages = mutableMapOf<String, MutableSet<MvNamedElement>>()

        for (child in importsOwner.children) {
            PsiTreeUtil.processElements(child, MvPath::class.java) { path ->
                val (nameUsages, typeUsages) =
                    when (path.itemScope) {
                        ItemScope.MAIN -> Pair(mainNameUsages, mainTypeUsages)
                        ItemScope.TEST -> Pair(testNameUsages, testTypeUsages)
                        ItemScope.VERIFY -> Pair(verifyNameUsages, verifyTypeUsages)
                    }
                when {
                    path.moduleRef != null -> addUsage(path, nameUsages)
                    path.parent is MvPathType -> addUsage(path, typeUsages)
                    else -> addUsage(path, nameUsages)
                }
                true
            }
        }
        PathUsages(
            ScopePathUsages(mainNameUsages, mainTypeUsages),
            ScopePathUsages(testNameUsages, testTypeUsages),
            ScopePathUsages(verifyNameUsages, verifyTypeUsages),
        )
    }
}

sealed class PathStart {
    data class Address(val addressRef: MvAddressRef): PathStart()
    data class Module(val modName: String, val moduleRef: MvModuleRef): PathStart()
    data class Item(val itemName: String): PathStart()

    companion object {
        val MvPath.pathStart: PathStart?
            get() {
                val pathModuleRef = this.moduleRef
                if (pathModuleRef != null) {
                    if (pathModuleRef is MvFQModuleRef) {
                        return Address(pathModuleRef.addressRef)
                    } else {
                        val modName = pathModuleRef.referenceName ?: return null
                        return Module(modName, pathModuleRef)
                    }
                } else {
                    val itemName = this.referenceName ?: return null
                    return Item(itemName)
                }
            }
    }
}

private fun addUsage(path: MvPath, itemUsages: ItemUsages) {
    val moduleRef = path.moduleRef
    when {
        // MODULE::ITEM
        moduleRef != null && moduleRef !is MvFQModuleRef -> {
            val modName = moduleRef.referenceName ?: return
            val targets = moduleRef.reference?.multiResolve().orEmpty()
            if (targets.isEmpty()) {
                itemUsages.putIfAbsent(modName, mutableSetOf())
            } else {
                val items = itemUsages.getOrPut(modName) { mutableSetOf() }
                targets.forEach {
                    items.add(it)
                }
            }
        }
        // ITEM_NAME
        moduleRef == null -> {
            val name = path.referenceName ?: return
            val targets = path.reference?.multiResolve().orEmpty()
            if (targets.isEmpty()) {
                itemUsages.putIfAbsent(name, mutableSetOf())
            } else {
                val items = itemUsages.getOrPut(name) { mutableSetOf() }
                targets.forEach {
                    items.add(it)
                }
            }
        }
    }
}

val MvImportsOwner.nameUsages: NameUsagesMap
    get() =
        project.cacheManager.cache(this, NAME_USAGES) {
            val map = mutableMapOf<String, MutableList<PathStart>>()
            for (path in this.descendantsOfType<MvPath>()) {
                val pathStart = path.pathStart ?: continue
                when (pathStart) {
                    is PathStart.Module -> {
                        val mods = map.getOrPut(pathStart.modName) { mutableListOf() }
                        mods.add(pathStart)
                    }
                    is PathStart.Item -> {
                        val items = map.getOrPut(pathStart.itemName) { mutableListOf() }
                        items.add(pathStart)
                    }
                    else -> continue
                }
            }
            return@cache psiFileTrackedCachedResult(map)
        }

private typealias NameUsagesMap = Map<String, MutableList<PathStart>>

private val NAME_USAGES = Key.create<CachedValue<NameUsagesMap>>("org.move.NAME_USAGES")

