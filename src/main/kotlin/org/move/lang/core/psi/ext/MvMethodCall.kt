package org.move.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.move.cli.MoveProject
import org.move.lang.core.psi.*
import org.move.lang.core.resolve.ref.MvPolyVariantReference
import org.move.lang.core.resolve.ref.MvMethodCallReferenceImpl
import org.move.lang.core.types.Address
import org.move.lang.core.types.address
import org.move.lang.core.types.ty.Ty
import org.move.lang.core.types.ty.TyAdt
import org.move.lang.core.types.ty.TyVector
import org.move.lang.index.MvModuleFileIndex

fun Ty.itemModule(moveProject: MoveProject): MvModule? {
    val norefTy = this.derefIfNeeded()
    return when (norefTy) {
        is TyVector -> {
            MvModuleFileIndex
                .getModulesForId(moveProject, Address.Value("0x1"), "vector")
                .firstOrNull()
        }
        is TyAdt -> norefTy.item.module
        else -> null
    }
}

fun MvModule.is0x1Address(moveProject: MoveProject): Boolean = this.address(moveProject)?.is0x1 ?: false

abstract class MvMethodCallMixin(node: ASTNode): MvElementImpl(node), MvMethodCall {

    override fun getReference(): MvPolyVariantReference = MvMethodCallReferenceImpl(this)
}

