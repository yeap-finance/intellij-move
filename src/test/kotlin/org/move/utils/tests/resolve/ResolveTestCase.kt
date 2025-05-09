package org.move.utils.tests.resolve

import com.intellij.testFramework.VfsTestUtil
import org.intellij.lang.annotations.Language
import org.move.lang.core.psi.MvNamedElement
import org.move.lang.core.psi.containingModule
import org.move.lang.core.resolve.ref.MvReferenceElement
import org.move.utils.tests.MvTestBase
import org.move.utils.tests.base.TestCase
import org.move.utils.tests.base.findElementInEditor
import org.move.utils.tests.base.findElementWithDataAndOffsetInEditor
import org.move.utils.tests.base.findElementsWithDataAndOffsetInEditor
import kotlin.io.path.Path

abstract class ResolveTestCase: MvTestBase() {

    protected fun checkByCode(
        @Language("Move") code: String,
    ) {
//        val testClass = this.javaClass.simpleName
//        if (!testClass.endsWith("ProjectTest")) {
//            val testClassDir =
//                TestCase.camelOrWordsToSnake(
//                    testClass.removePrefix("Resolve").removeSuffix("Test")
//                ).trimStart('_')
//            createTestMoveFileFromCode("resolve", testClassDir, code)
//        }

        InlineFile(code, "main.move")

        val (refElement, data, offset) =
            myFixture.findElementWithDataAndOffsetInEditor<MvReferenceElement>("^")

        if (data == "unresolved") {
            val resolved = refElement.reference?.resolve()
            check(resolved == null) {
                "$refElement `${refElement.text}`should be unresolved, was resolved to\n$resolved `${resolved?.text}`"
            }
            return
        }

        val resolved = refElement.checkedResolve(offset)

        if (data.startsWith("spec_builtins::")) {
            check(resolved is MvNamedElement)
            val resolvedModule = resolved.containingModule
            check(resolvedModule?.name == "spec_builtins") {
                "Resolved element ${resolved.text} does not belong to 'spec_builtins'"
            }
            val targetName = data.substringAfter("spec_builtins::")
            check(resolved.name == targetName) {
                "$refElement `${refElement.text}` should resolve to $targetName, was $resolved (${resolved.text}) instead"
            }
            return
        }

        val target = myFixture.findElementInEditor(MvNamedElement::class.java, "X")
        check(resolved == target) {
            "$refElement `${refElement.text}` should resolve to $target (${target.text}), was $resolved (${resolved.text}) instead"
        }
    }

    protected fun checkMultiResolveByCode(@Language("Move") code: String) {
        InlineFile(code, "main.move")
        val element = myFixture.findElementInEditor<MvReferenceElement>()
        val ref = element.reference ?: error("Failed to get reference for `${element.text}`")

        val expectedItems = myFixture
            .findElementsWithDataAndOffsetInEditor(MvNamedElement::class.java, "//X")
            .map { it.first }

        val resolveVariants = ref.multiResolve()

        val notFoundItems = mutableListOf<MvNamedElement>()
        for (item in expectedItems) {
            val foundResolved = resolveVariants.find { it == item }
            if (foundResolved == null) {
                notFoundItems.add(item)
            }
        }

        check(notFoundItems.isEmpty()) {
            "Resolution error. \n" +
                    "Expected $expectedItems, \nactual $resolveVariants. " +
                    "\nMissing $notFoundItems"
        }

        if (resolveVariants.size > expectedItems.size) {
            "Too many variants ${resolveVariants.size}, expected ${expectedItems.size}"
        }

    }
}
