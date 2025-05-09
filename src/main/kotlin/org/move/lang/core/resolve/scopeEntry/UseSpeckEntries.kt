package org.move.lang.core.resolve.scopeEntry

import com.intellij.psi.util.CachedValueProvider
import org.move.lang.core.psi.NamedItemScope
import org.move.lang.core.psi.ext.MvItemsOwner
import org.move.lang.core.resolve.ref.MODULES
import org.move.lang.core.resolve.ref.Ns
import org.move.lang.index.MvItemNamespaceIndex
import org.move.lang.moveProject
import org.move.utils.PsiCachedValueProvider
import org.move.utils.getResults
import org.move.utils.psiCacheResult

val MvItemsOwner.useSpeckEntries: List<ScopeEntry> get() = UseSpeckEntries(this).getResults()

class UseSpeckEntries(override val owner: MvItemsOwner): PsiCachedValueProvider<List<ScopeEntry>> {
    override fun compute(): CachedValueProvider.Result<List<ScopeEntry>> {
        return owner.psiCacheResult(
            owner.useSpeckEntries()
        )
    }
}

private fun MvItemsOwner.useSpeckEntries(): List<ScopeEntry> {
    val moveProject = this.moveProject ?: return emptyList()
    val useItems = this.useStmtList.useItems

    val distinctUseItems = useItems.distinctBy { it.nameOrAlias to it.type }
    return buildList(distinctUseItems.size) {
        for (useItem in distinctUseItems) {
            val itemNs = when (useItem.type) {
                is UseItemType.Module, is UseItemType.SelfModule -> Ns.MODULE
                is UseItemType.Item -> {
                    val fqName = useItem.type.fqName
                    MvItemNamespaceIndex.getItemNs(moveProject, fqName)
                }
            }
            if (itemNs != null) {
                add(
                    ScopeEntry(
                        useItem.nameOrAlias,
                        lazy { useItem.alias ?: useItem.useSpeck.path.reference?.resolve() },
                        itemNs,
                        customItemScope = useItem.scope.takeIf { it != NamedItemScope.MAIN },
                    )
                )
            }
        }
    }
}
