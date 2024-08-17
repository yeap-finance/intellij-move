package org.move.lang.core.types.infer

import org.move.lang.core.psi.*
import org.move.lang.core.psi.ext.*
import org.move.lang.core.psi.ext.RsBindingModeKind.BindByReference
import org.move.lang.core.psi.ext.RsBindingModeKind.BindByValue
import org.move.lang.core.resolve.pickFirstResolveVariant
import org.move.lang.core.resolve.processAll
import org.move.lang.core.resolve.ref.MvReferenceElement
import org.move.lang.core.resolve.ref.TYPES
import org.move.lang.core.types.ty.*
import org.move.lang.core.types.ty.Mutability.IMMUTABLE

//fun collectBindings(pattern: MvPat, inferredTy: Ty, parentCtx: InferenceContext) {
//    fun bind(pat: MvPat, ty: Ty) {
//        when (pat) {
//            is MvBindingPat -> {
//                parentCtx.patTypes[pat] = ty
////                parentCtx.bindingTypes[pat] = ty
//            }
//            is MvTuplePat -> {
//                if (ty is TyTuple && pat.patList.size == ty.types.size) {
//                    pat.patList.zip(ty.types)
//                        .forEach { (pat, ty) -> bind(pat, ty) }
//                } else {
//                    pat.patList.map { bind(it, TyUnknown) }
//                }
//            }
//            is MvStructPat -> {
//                val patTy = inferPatTy(pat, parentCtx, ty)
//                when (patTy) {
//                    is TyStruct -> {
//                        when {
//                            ty is TyUnknown -> pat.patFields.map {
//                                it.pat?.let { pat -> bind(pat, TyUnknown) }
//                            }
//                            ty is TyStruct && pat.patFields.size == ty.fieldTys.size -> {
//                                for (field in pat.patFields) {
//                                    val fieldTy = ty.fieldTy(field.referenceName)
//                                    field.pat?.let { bind(it, fieldTy) }
//                                }
//                            }
//                        }
//                    }
//                    is TyUnknown -> {
//                        pat.patFields.map {
//                            it.pat?.let { pat -> bind(pat, TyUnknown) }
//                        }
//                    }
//                    else -> error("unreachable with type ${patTy.fullname()}")
//                }
//                if (ty is TyStruct && pat.patFields.size == ty.fieldTys.size) {
//                    for (field in pat.patFields) {
//                        val fieldTy = ty.fieldTy(field.referenceName)
//                        field.pat?.let { bind(it, fieldTy) }
//                    }
//                } else {
//                    pat.patFields.map {
//                        it.pat?.let { pat -> bind(pat, TyUnknown) }
//                    }
//                }
//            }
//        }
//    }
//    bind(pattern, inferredTy)
//}

fun MvPat.anonymousTyVar(): Ty {
    return when (this) {
        is MvBindingPat -> TyInfer.TyVar()
        is MvTuplePat -> TyTuple(this.patList.map { TyInfer.TyVar() })
        else -> TyUnknown
    }
}

fun MvPat.collectBindings(fctx: TypeInferenceWalker, ty: Ty, defBm: RsBindingModeKind = BindByValue) {
    val msl = this.isMsl()
    when (this) {
        is MvBindingPat -> {
//            val resolved = this.reference.resolve()
//            val bindingType = if (resolved is MvEnumVariant) {
            // it should properly check for the enum variant, but now it's just checks for the proper parent
            val bindingType = if (this.parent is MvMatchArm) {
                ty.stripReferences(defBm).first
            } else {
                ty.applyBm(defBm, msl)
            }
            fctx.ctx.writePatTy(this, bindingType)
        }
        is MvConstPat -> {
            fctx.ctx.writePatTy(this, ty)
        }
        is MvStructPat -> {
            val (expected, patBm) = ty.stripReferences(defBm)
            fctx.ctx.writePatTy(this, expected)

            val item = when {
                this.parent is MvMatchArm && path.path == null -> {
                    // if we're inside match arm and no qualifier,
                    // StructPat can only be a enum variant, resolve through type.
                    // Otherwise there's a resolution cycle when we call the .resolve() method

                    // NOTE: I think it can be replaced moving path resolution to the inference,
                    // like it's done in intellij-rust
                    val referenceName = path.referenceName ?: return
                    val enumItem = (expected as? TyAdt)?.item as? MvEnum ?: return
                    pickFirstResolveVariant(referenceName) {
                        it.processAll(TYPES, enumItem.variants)
                    } as? MvFieldsOwner
                }
                else -> {
                    path.reference?.resolveFollowingAliases() as? MvFieldsOwner
                        ?: ((expected as? TyAdt)?.item as? MvStruct)
                }
            } ?: return
//            val item = path.reference?.resolveFollowingAliases() as? MvFieldsOwner
//                ?: ((ty as? TyAdt)?.item as? MvStruct)
//                ?: return

//            if (item is MvTypeParametersOwner) {
//                val (patTy, _) = fctx.ctx.instantiateMethodOrPath<TyAdt>(this.path, item) ?: return
//                if (!isCompatible(expected, patTy, fctx.msl)) {
//                    fctx.reportTypeError(TypeError.InvalidUnpacking(this, ty))
//                }
//            }

            val structFields = item.fields.associateBy { it.name }
            for (fieldPat in this.fieldPatList) {
                val kind = fieldPat.kind
                val fieldType = structFields[kind.fieldName]
                    ?.type
                    ?.loweredType(fctx.msl)
                    ?.substituteOrUnknown(ty.typeParameterValues)
//                    ?.let { if (ty is TyReference) ty.transferReference(it) else it }
                    ?: TyUnknown

                when (kind) {
                    is PatFieldKind.Full -> {
                        kind.pat.collectBindings(fctx, fieldType, patBm)
                        fctx.ctx.writeFieldPatTy(fieldPat, fieldType)
                    }
                    is PatFieldKind.Shorthand -> {
//                        kind.binding.collectBindings(fctx, fieldType)
                        fctx.ctx.writeFieldPatTy(fieldPat, fieldType.applyBm(patBm, msl))
                    }
                }
//                fctx.ctx.writeFieldPatTy(fieldPat, fieldType)
            }
        }
        is MvTuplePat -> {
            if (patList.size == 1 && ty !is TyTuple) {
                // let (a) = 1;
                // let (a,) = 1;
                patList.single().collectBindings(fctx, ty)
                return
            }
            val patTy = TyTuple.unknown(patList.size)
            val expectedTypes = if (!isCompatible(ty, patTy, fctx.msl)) {
                fctx.reportTypeError(TypeError.InvalidUnpacking(this, ty))
                emptyList()
            } else {
                (ty as? TyTuple)?.types.orEmpty()
            }
            for ((idx, p) in patList.withIndex()) {
                val patType = expectedTypes.getOrNull(idx) ?: TyUnknown
                p.collectBindings(fctx, patType)
            }
        }
    }
}

private fun Ty.applyBm(defBm: RsBindingModeKind, msl: Boolean): Ty =
    if (defBm is BindByReference) TyReference(this, defBm.mutability, msl) else this

//private fun MvBindingPat.inferType(expected: Ty, defBm: RsBindingModeKind, msl: Boolean): Ty {
//    return if (defBm is BindByReference) TyReference(expected, defBm.mutability, msl) else expected
//}

private fun Ty.stripReferences(defBm: RsBindingModeKind): Pair<Ty, RsBindingModeKind> {
    var bm = defBm
    var ty = this
    while (ty is TyReference) {
        bm = when (bm) {
            is BindByValue -> BindByReference(ty.mutability)
            is BindByReference -> BindByReference(
                if (bm.mutability == IMMUTABLE) IMMUTABLE else ty.mutability
            )
        }
        ty = ty.referenced
    }
    return ty to bm
}
