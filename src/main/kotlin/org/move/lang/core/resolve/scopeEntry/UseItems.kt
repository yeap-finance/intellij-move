package org.move.lang.core.resolve.scopeEntry

import org.move.ide.inspections.imports.usageScope
import org.move.lang.core.psi.MvUseAlias
import org.move.lang.core.psi.MvUseSpeck
import org.move.lang.core.psi.MvUseStmt
import org.move.lang.core.psi.NamedItemScope
import org.move.lang.core.psi.ext.MvItemsOwner
import org.move.lang.core.resolve.PathKind
import org.move.lang.core.resolve.pathKind

sealed class UseItemType2 {
    object Module: UseItemType2()
    object SelfModule: UseItemType2()
    data class Item(val fqName: String): UseItemType2()
}

data class UseItem(
    val useSpeck: MvUseSpeck,
    val alias: MvUseAlias?,
    val nameOrAlias: String,
    val type: UseItemType2,
    val scope: NamedItemScope,
)

val MvItemsOwner.useItems: List<UseItem> get() = this.useStmtList.useItems
val List<MvUseStmt>.useItems: List<UseItem> get() = this.flatMap { it.useItems }

val MvUseStmt.useItems: List<UseItem>
    get() {
        val useItems = mutableListOf<UseItem>()

        val stmtUsageScope = this.usageScope
        val rootUseSpeck = this.useSpeck ?: return useItems

        val useGroup = rootUseSpeck.useGroup
        if (useGroup != null) {
            for (childUseSpeck in useGroup.useSpeckList) {
                val qualifierPath = rootUseSpeck.path
                val moduleName = qualifierPath.referenceName ?: continue

                val childName = childUseSpeck.path.referenceName ?: continue
                val childAlias = childUseSpeck.useAlias
                val childAliasName = childAlias?.name

                if (childName == "Self") {
                    useItems.add(
                        UseItem(
                            childUseSpeck,
                            childAlias,
                            childAliasName ?: moduleName,
                            UseItemType2.SelfModule,
                            stmtUsageScope
                        )
                    )
                    continue
                }

                val qualifierKind =
                    qualifierPath.pathKind(isCompletion = false) as? PathKind.QualifiedPath ?: continue
                val address = when (qualifierKind) {
                    is PathKind.QualifiedPath.Module -> qualifierKind.address
                    is PathKind.QualifiedPath.ModuleOrItem -> qualifierKind.address
                    else -> continue
                }
                val fqName = "${address.universalShortText()}::$moduleName::$childName"
                useItems.add(
                    UseItem(
                        childUseSpeck,
                        childAlias,
                        childAliasName ?: childName,
                        UseItemType2.Item(fqName),
                        stmtUsageScope,
                    )
                )
            }
            return useItems
        }

        val rootUseSpeckAlias = rootUseSpeck.useAlias

        val rootName = rootUseSpeck.path.referenceName ?: return useItems
        val aliasName = rootUseSpeckAlias?.name

        val pathKind = rootUseSpeck.path.pathKind(isCompletion = false)
        when (pathKind) {
            // use 0x1::m;
            // use aptos_std::m;
            is PathKind.QualifiedPath.Module -> {
                useItems.add(
                    UseItem(
                        rootUseSpeck,
                        rootUseSpeckAlias,
                        aliasName ?: rootName,
                        UseItemType2.Module,
                        stmtUsageScope
                    )
                )
            }
            // use 0x1::m::call;
            // use aptos_std::m::call as mycall;
            // use aptos_std::m::Self;
            is PathKind.QualifiedPath.FQModuleItem -> {
                val moduleName = pathKind.qualifier.referenceName ?: return useItems
                if (rootName == "Self") {
                    useItems.add(
                        UseItem(
                            rootUseSpeck,
                            rootUseSpeckAlias,
                            aliasName ?: moduleName,
                            UseItemType2.SelfModule,
                            stmtUsageScope,
                        )
                    )
                } else {
                    val address = pathKind.baseAddress() ?: return useItems
                    val fqName = "${address.universalShortText()}::$moduleName::$rootName"
                    useItems.add(
                        UseItem(
                            rootUseSpeck,
                            rootUseSpeckAlias,
                            aliasName ?: rootName,
                            UseItemType2.Item(fqName),
                            stmtUsageScope,
                        )
                    )
                }
            }
            else -> Unit
        }
        return useItems
    }

