package org.move.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.move.ide.MoveIcons
import org.move.lang.core.psi.*
import org.move.lang.core.psi.impl.MvNameIdentifierOwnerImpl
import javax.swing.Icon

val MslOnlyElement.parentModuleOrModuleSpec: MvElement?
    get() {
        for (ancestor in this.ancestors) {
            if (ancestor is MvModule || ancestor is MvModuleSpec) {
                return ancestor
            }
        }
        return null
    }

val MvSpecFunction.parentModule: MvModule?
    get() {
        val parent = this.parent
        if (parent is MvModule) return parent
        if (parent is MvModuleSpecBlock) {
            return parent.moduleSpec.moduleItem
        }
        return null
    }

val MvSpecInlineFunction.parentModule: MvModule?
    get() {
        val functionStmt = this.parent as MvSpecInlineFunctionStmt
        val specCodeBlock = functionStmt.parent as MvSpecCodeBlock
        val moduleSpec = specCodeBlock.parent as? MvModuleItemSpec ?: return null
        return moduleSpec.definitionModule
    }

abstract class MvSpecFunctionMixin(node: ASTNode): MvNameIdentifierOwnerImpl(node),
                                                   MvSpecFunction {

    override fun getIcon(flags: Int): Icon = MoveIcons.FUNCTION
}

abstract class MvSpecInlineFunctionMixin(node: ASTNode): MvNameIdentifierOwnerImpl(node),
                                                         MvSpecInlineFunction {

    override fun getIcon(flags: Int): Icon = MoveIcons.FUNCTION
}
