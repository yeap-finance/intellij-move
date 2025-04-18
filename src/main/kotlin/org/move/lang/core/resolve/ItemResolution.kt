package org.move.lang.core.resolve

import com.intellij.psi.util.CachedValueProvider
import org.move.lang.core.psi.MvFunction
import org.move.lang.core.psi.MvModule
import org.move.lang.core.psi.MvModuleSpec
import org.move.lang.core.psi.ext.*
import org.move.lang.core.psi.loweredTy
import org.move.lang.core.psi.selfParam
import org.move.lang.core.resolve.scopeEntry.ScopeEntry
import org.move.lang.core.resolve.scopeEntry.asEntries
import org.move.lang.core.resolve.scopeEntry.itemEntries
import org.move.lang.core.types.infer.deepFoldTyTypeParameterWith
import org.move.lang.core.types.ty.Ty
import org.move.lang.core.types.ty.TyInfer
import org.move.lang.core.types.ty.TyReference
import org.move.lang.moveProject
import org.move.utils.PsiCachedValueProvider
import org.move.utils.getResults
import org.move.utils.psiCacheResult
import kotlin.collections.plus

fun getMethodResolveVariants(
    methodOrField: MvMethodOrField,
    receiverTy: Ty,
    msl: Boolean
): List<ScopeEntry> {
    return buildList {
        val moveProject = methodOrField.moveProject ?: return@buildList
        val itemModule = receiverTy.itemModule(moveProject) ?: return@buildList

        val functionEntries = itemModule.allNonTestFunctions().asEntries()
        for (functionEntry in functionEntries) {
            val f = functionEntry.element() as? MvFunction ?: continue
            val selfParam = f.selfParam ?: continue
            val selfParamTy = selfParam.loweredTy(msl) ?: continue
            // need to use TyVar here, loweredType() erases them
            val selfTyWithTyVars =
                selfParamTy.deepFoldTyTypeParameterWith { tp -> TyInfer.TyVar(tp) }
            if (TyReference.isCompatibleWithAutoborrow(receiverTy, selfTyWithTyVars, msl)) {
                add(functionEntry)
            }
        }
    }
}

val MvModule.allScopesImportableEntries: List<ScopeEntry> get() {
    return AllScopesImportableEntries(this).getResults()
}

class AllScopesImportableEntries(override val owner: MvModule): PsiCachedValueProvider<List<ScopeEntry>> {
    override fun compute(): CachedValueProvider.Result<List<ScopeEntry>> {
        val entries = owner.importableEntries + owner.itemEntriesFromRelatedModuleSpecs
        return owner.psiCacheResult(entries)
    }
}

//fun getImportableItemsAsEntries(module: MvModule): List<ScopeEntry> {
//    return ImportableItemsAsEntries(module).getResults()
////    return module.itemEntries + module.globalVariableEntries + module.itemEntriesFromModuleSpecs
//}

val MvModule.itemEntriesFromRelatedModuleSpecs: List<ScopeEntry>
    get() {
        val module = this
        return buildList {
            val specs = module.getModuleSpecsFromIndex()
            for (spec in specs) {
                addAll(spec.importableEntries)
            }
        }
    }

val MvModule.importableEntries: List<ScopeEntry> get() {
    return this.itemEntries + this.globalVariableEntries
}

val MvModuleSpec.importableEntries: List<ScopeEntry> get() {
    val spec = this
    return buildList {
        addAll(spec.schemas().asEntries())
        addAll(spec.specFunctions().asEntries())
        spec.moduleItemSpecs().forEach {
            addAll(it.globalVariableEntries)
            addAll(it.specInlineFunctions().asEntries())
        }
    }
}
