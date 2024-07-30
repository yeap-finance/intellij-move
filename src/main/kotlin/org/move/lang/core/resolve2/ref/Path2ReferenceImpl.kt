package org.move.lang.core.resolve2.ref

import com.intellij.psi.ResolveResult
import org.move.cli.MoveProject
import org.move.lang.core.psi.*
import org.move.lang.core.psi.ext.useSpeck
import org.move.lang.core.resolve.*
import org.move.lang.core.resolve.ref.*
import org.move.lang.core.resolve2.*
import org.move.lang.core.resolve2.PathKind.NamedAddress
import org.move.lang.core.resolve2.PathKind.ValueAddress
import org.move.lang.moveProject
import kotlin.LazyThreadSafetyMode.NONE

class Path2ReferenceImpl(element: MvPath):
    MvPolyVariantReferenceBase<MvPath>(element), MvPath2Reference {

    override fun resolve(): MvNamedElement? =
        rawMultiResolveIfVisible().singleOrNull()?.element as? MvNamedElement

    override fun multiResolve(): List<MvNamedElement> =
        rawMultiResolveIfVisible().mapNotNull { it.element as? MvNamedElement }

    override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> =
        rawMultiResolve().toTypedArray()

//    fun multiResolveIfVisible(): List<MvElement> =
//        rawMultiResolve().mapNotNull {
//            if (!it.isVisible) return@mapNotNull null
//            it.element
//        }

    fun rawMultiResolveIfVisible(): List<RsPathResolveResult<MvElement>> =
        rawMultiResolve().filter { it.isVisible }

    //    fun rawMultiResolve(): List<RsPathResolveResult<MvElement>> = Resolver.invoke(this.element)
    fun rawMultiResolve(): List<RsPathResolveResult<MvElement>> = rawCachedMultiResolve()

    private fun rawCachedMultiResolve(): List<RsPathResolveResult<MvElement>> {
        val rawResult = MvResolveCache.getInstance(element.project)
            .resolveWithCaching(element, ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE, Resolver)
        return rawResult.orEmpty()
    }

    private object Resolver: (MvElement) -> List<RsPathResolveResult<MvElement>> {
        override fun invoke(path: MvElement): List<RsPathResolveResult<MvElement>> {
            // should not really happen
            if (path !is MvPath) return emptyList()
            val resolutionCtx = ResolutionContext(path)
            return resolvePath(resolutionCtx, path)
        }
    }
}

fun processPathResolveVariants(
    ctx: ResolutionContext,
    pathKind: PathKind,
    processor: RsResolveProcessor
): Boolean {
//    val contextProcessor = ctx.contextScopeInfo.wrapWithContextFilter(processor)
    val contextProcessor = processor
    return when (pathKind) {
        is NamedAddress, is ValueAddress -> false
        is PathKind.UnqualifiedPath -> {
            // Self::
            if (processor.lazy("Self") { ctx.containingModule }) return true
            // local
            processNestedScopesUpwards(ctx.element, pathKind.ns, ctx, contextProcessor)
        }
        is PathKind.QualifiedPath.Module -> {
            processModulePathResolveVariants(ctx.element, ctx.moveProject, pathKind.address, contextProcessor)
        }
        is PathKind.QualifiedPath -> {
            processQualifiedPathResolveVariants(ctx, pathKind.ns, pathKind.path, pathKind.qualifier, processor)
//            processQualifiedPathResolveVariants(ctx, pathKind.ns, pathKind.path, pathKind.qualifier, contextProcessor)
        }
    }
//    return when (pathKind) {
//        is UnqualifiedPath -> {
//            // Self::
//            if (processor.lazy("Self") { ctx.containingModule }) return true
//            // local
//            processNestedScopesUpwards(ctx.path, pathKind.ns, ctx, contextProcessor)
//        }
//        is ModulePath -> {
//            // 0x1::bar
//            processModulePathResolveVariants(
//                ctx.path,
//                ctx.moveProject,
//                pathKind.address,
//                contextProcessor
//            )
//        }
//        is QualifiedPath -> {
//            // foo::bar
//            processQualifiedPathResolveVariants(ctx, pathKind.ns, pathKind.path, pathKind.qualifier, processor)
//        }
//        is NamedAddressPath -> return false
//        is ValueAddressPath -> return false
//    }
}

//fun processPathResolveVariants(
//    path: MvPath,
//    contextScopeInfo: ContextScopeInfo = ContextScopeInfo.from(path),
//    processor: RsResolveProcessor,
//): Boolean {
//    val ctx = PathResolutionContext(path, contextScopeInfo = contextScopeInfo)
//    val pathKind = classifyPath(path)
//    return processPathResolveVariants(ctx, pathKind, processor)
//}

/**
 * foo::bar
 * |    |
 * |    [path]
 * [qualifier]
 */
fun processQualifiedPathResolveVariants(
    ctx: ResolutionContext,
    ns: Set<Namespace>,
    path: MvPath,
    qualifier: MvPath,
    processor: RsResolveProcessor
): Boolean {
    val resolvedQualifier = qualifier.reference?.resolveFollowingAliases()
    if (resolvedQualifier == null) {
        if (Namespace.MODULE in ns) {
            // can be module, try for named address as a qualifier
            val addressName = qualifier.referenceName ?: return false
            val address = ctx.moveProject?.getNamedAddressTestAware(addressName) ?: return false
            if (processModulePathResolveVariants(ctx.element, ctx.moveProject, address, processor)) return true
        }
        return false
    }
    if (resolvedQualifier is MvModule) {
        if (processor.process("Self", resolvedQualifier)) return true

        val moduleBlock = resolvedQualifier.moduleBlock
        if (moduleBlock != null) {
            if (processItemDeclarations(moduleBlock, ns, processor)) return true
        }
    }
    return false
}

class ResolutionContext(val element: MvElement) {

    private var lazyContainingMoveProject: Lazy<MoveProject?> = lazy(NONE) {
        element.moveProject
    }
    val moveProject: MoveProject? get() = lazyContainingMoveProject.value

    private var lazyContainingModule: Lazy<MvModule?> = lazy(NONE) {
        element.containingModule
    }
    val containingModule: MvModule? get() = lazyContainingModule.value

    private var lazyUseSpeck: Lazy<MvUseSpeck?> = lazy(NONE) { path?.useSpeck }
    val useSpeck: MvUseSpeck? get() = lazyUseSpeck.value
    val isUseSpeck: Boolean get() = useSpeck != null

    val path: MvPath? get() = element as? MvPath
}

//// todo: use in inference later
//fun resolvePathRaw(path: MvPath): List<ScopeEntry> {
//    return collectResolveVariantsAsScopeEntries(path.referenceName) {
//        processPathResolveVariants(path, it)
//    }
//}

private fun resolvePath(
    ctx: ResolutionContext,
    path: MvPath,
//    kind: RsPathResolveKind
): List<RsPathResolveResult<MvElement>> {
    val pathKind = path.pathKind()
    val result =
        // matches resolve variants against referenceName from path
        collectPathResolveVariants(ctx, path) {
            // actually emits resolve variants
            processPathResolveVariants(ctx, pathKind, it)
        }
    return result
//    return when (result.size) {
//        0 -> emptyList()
//        1 -> listOf(result.single())
//        else -> result
//    }
}

