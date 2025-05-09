package org.move.lang.core.psi.ext

import org.move.lang.core.psi.MvElement
import org.move.lang.core.psi.MvVisibilityModifier
import org.move.lang.core.psi.ext.VisKind.*
import org.move.lang.core.resolve.ref.Visibility

interface MvVisibilityOwner: MvElement {
    val visibilityModifier: MvVisibilityModifier? get() = childOfType<MvVisibilityModifier>()
}

// todo: add VisibilityModifier to stubs, rename this one to VisStubKind
enum class VisKind(val keyword: String) {
    PUBLIC("public"),
    FRIEND("public(friend)"),
    PACKAGE("public(package)"),
    SCRIPT("public(script)");
}

val MvVisibilityModifier.visKind: VisKind
    get() = when {
        hasFriend -> FRIEND
        hasPackage -> PACKAGE
        hasPublic -> PUBLIC
        // deprecated, should be at the end
        hasScript -> SCRIPT
        else -> error("exhaustive")
    }

val MvVisibilityOwner.visibility: Visibility
    get() {
        val kind = this.visibilityModifier?.visKind ?: return Visibility.Private
        return when (kind) {
            PACKAGE -> Visibility.Restricted.Package()
            FRIEND -> {
                Visibility.Restricted.Friend(/*lazy { module.friendModules }*/)
            }
            // public(script) == public entry
            SCRIPT -> Visibility.Public
            PUBLIC -> Visibility.Public
        }
    }




