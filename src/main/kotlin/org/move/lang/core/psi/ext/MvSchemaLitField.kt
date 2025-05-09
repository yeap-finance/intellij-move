package org.move.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.move.lang.MvElementTypes
import org.move.lang.core.psi.*
import org.move.lang.core.resolve.getEntriesFromWalkingScopes
import org.move.lang.core.resolve.ref.MvPolyVariantReference
import org.move.lang.core.resolve.ref.MvPolyVariantReferenceCached
import org.move.lang.core.resolve.ref.NAMES
import org.move.lang.core.resolve.scopeEntry.ScopeEntry
import org.move.lang.core.resolve.scopeEntry.asEntries
import org.move.lang.core.resolve.scopeEntry.filterByName
import org.move.lang.core.resolve.scopeEntry.namedElements

val MvSchemaLitField.isShorthand get() = !hasChild(MvElementTypes.COLON)

val MvSchemaLitField.schemaLit: MvSchemaLit? get() = ancestorStrict(stopAt = MvSpecCodeBlock::class.java)

inline fun <reified T: MvElement> MvSchemaLitField.resolveToElement(): T? =
    reference.multiResolve().filterIsInstance<T>().singleOrNull()

fun MvSchemaLitField.resolveToDeclaration(): MvSchemaFieldStmt? = resolveToElement()
fun MvSchemaLitField.resolveToBinding(): MvPatBinding? = resolveToElement()

abstract class MvSchemaLitFieldMixin(node: ASTNode): MvElementImpl(node),
                                                     MvSchemaLitField {
    override fun getReference(): MvPolyVariantReference =
        MvSchemaLitFieldReferenceImpl(this, shorthand = this.isShorthand)
}

class MvSchemaLitFieldReferenceImpl(
    element: MvSchemaLitField,
    val shorthand: Boolean,
): MvPolyVariantReferenceCached<MvSchemaLitField>(element) {
    override fun multiResolveInner(): List<MvNamedElement> {
        val referenceName = element.referenceName
        val variants = getSchemaLitFieldResolveVariants(element)
            .filterByName(referenceName)
            .toMutableList()
        if (shorthand) {
            variants += getEntriesFromWalkingScopes(element, NAMES)
                .filterByName(referenceName)
//            variants += resolveBindingForFieldShorthand(element)
        }
        return variants.namedElements()
    }
}

fun getSchemaLitFieldResolveVariants(literalField: MvSchemaLitField): List<ScopeEntry> {
    val schema = literalField.schemaLit?.path?.maybeSchema
        ?: return emptyList()
    return schema.fieldsAsBindings.asEntries()
}

