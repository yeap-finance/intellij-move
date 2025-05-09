package org.move.ide.docs

import com.intellij.codeEditor.printing.HTMLTextPainter
import com.intellij.psi.PsiElement
import org.move.ide.docs.MvColorUtils.asConst
import org.move.ide.docs.MvColorUtils.asEnum
import org.move.ide.docs.MvColorUtils.asEnumVariant
import org.move.ide.docs.MvColorUtils.asField
import org.move.ide.docs.MvColorUtils.asFunction
import org.move.ide.docs.MvColorUtils.asStruct
import org.move.ide.docs.MvColorUtils.colored
import org.move.ide.docs.MvColorUtils.keyword
import org.move.ide.presentation.presentableQualifiedName
import org.move.ide.presentation.text
import org.move.lang.core.psi.*
import org.move.lang.core.psi.ext.*
import org.move.lang.core.types.fqName
import org.move.lang.core.types.ty.TyUnknown
import org.move.stdext.joinToWithBuffer

fun MvDocAndAttributeOwner.header(buffer: StringBuilder) {
    val headerLine = when (this) {
        is MvNamedFieldDecl -> this.fieldOwner.fqName()?.identifierText()
        is MvStructOrEnumItemElement,
        is MvFunctionLike,
        is MvConst,
        is MvSchema -> this.fqName()?.moduleIdentifierText()
        else -> null
    }
        ?: return

    buffer += headerLine
    buffer += "\n"
//    rawLines.joinTo(buffer, "<br>")
//    if (rawLines.isNotEmpty()) {
//        buffer += "\n"
//    }
}

fun MvDocAndAttributeOwner.signature(builder: StringBuilder) {
    // no need for msl type conversion in docs
    val buffer = StringBuilder()
    when (this) {
        is MvFunction -> buffer.generateFunction(this)
        is MvSpecFunction -> buffer.generateSpecFunction(this)
        is MvSpecInlineFunction -> buffer.generateSpecInlineFunction(this)
        is MvModule -> buffer.generateModule(this)
        is MvStructOrEnumItemElement -> buffer.generateStructOrEnum(this)
        is MvSchema -> buffer.generateSchema(this)
        is MvNamedFieldDecl -> buffer.generateNamedField(this)
        is MvConst -> buffer.generateConst(this)
        is MvEnumVariant -> buffer.generateEnumVariant(this)
        else -> return
    }
    listOf(buffer.toString()).joinTo(builder, "<br>")
}

private fun StringBuilder.generateFunction(fn: MvFunction) {
    for (modifier in fn.modifiers) {
        keyword(modifier)
        this += " "
    }
    keyword("fun")
    this += " "
    colored(fn.name, asFunction)

    fn.typeParameterList?.generateDoc(this)
    fn.functionParameterList?.generateDoc(this)
    fn.returnType?.generateDoc(this)
}

private fun StringBuilder.generateSpecFunction(specFn: MvSpecFunction) {
    for (modifier in specFn.modifiers) {
        keyword(modifier)
        this += " "
    }
    keyword("spec")
    this += " "
    keyword("fun")
    this += " "
    colored(specFn.name, asFunction)

    specFn.typeParameterList?.generateDoc(this)
    specFn.functionParameterList?.generateDoc(this)
    specFn.returnType?.generateDoc(this)
}

private fun StringBuilder.generateSpecInlineFunction(specInlineFn: MvSpecInlineFunction) {
    for (modifier in specInlineFn.modifiers) {
        keyword(modifier)
        this += " "
    }
    keyword("fun")
    this += " "
    colored(specInlineFn.name, asFunction)

    specInlineFn.typeParameterList?.generateDoc(this)
    specInlineFn.functionParameterList?.generateDoc(this)
    specInlineFn.returnType?.generateDoc(this)
}

private fun StringBuilder.generateModule(mod: MvModule) {
    keyword("module")
    this += " "
    this += mod.fqName()?.identifierText() ?: "unknown"
}

private fun StringBuilder.generateStructOrEnum(structOrEnum: MvStructOrEnumItemElement) {
    when (structOrEnum) {
        is MvStruct -> {
            keyword("struct")
            this += " "
            colored(structOrEnum.name, asStruct)
        }
        is MvEnum -> {
            keyword("enum")
            this += " "
            colored(structOrEnum.name, asEnum)
        }
    }
    structOrEnum.typeParameterList?.generateDoc(this)

    val abilities = structOrEnum.abilitiesList?.abilityList
    if (abilities != null && abilities.isNotEmpty()) {
        this += " "
        this.keyword("has")
        this += " "
        abilities.joinToWithBuffer(this, ", ") { generateDoc(it) }
    }
}

private fun StringBuilder.generateSchema(schema: MvSchema) {
    this.keyword("spec")
    this += " "
    this.keyword("schema")
    this += " "
    this += schema.name
    schema.typeParameterList?.generateDoc(this)
}

private fun StringBuilder.generateEnumVariant(variant: MvEnumVariant) {
    this += variant.enumItem.presentableQualifiedName
    this += "::"
    this.colored(variant.name, asEnumVariant)
}

private fun StringBuilder.generateNamedField(field: MvNamedFieldDecl) {
    colored(field.name, asField)
    this += ": "
    val fieldType = field.type
    if (fieldType == null) {
        this += TyUnknown.text(colors = true)
        return
    }
    fieldType.generateDoc(this)
}

private fun StringBuilder.generateConst(const: MvConst) {
    keyword("const")
    this += " "
    colored(const.name, asConst)

    this += ": "
    val constType = const.type
    if (constType == null) {
        this += TyUnknown.text(colors = true)
        return
    }
    constType.generateDoc(this)

    const.initializer?.expr?.let { expr ->
        this += " = "
        this += highlightWithLexer(expr, expr.text)
    }
}

private fun highlightWithLexer(context: PsiElement, text: String): String {
    val highlighed =
        HTMLTextPainter.convertCodeFragmentToHTMLFragmentWithInlineStyles(context, text)
    return highlighed.trimEnd()
        .removeSurrounding("<pre>", "</pre>")
}
