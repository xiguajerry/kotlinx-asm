package kotlinx.asm.insn

import kotlinx.asm.TypePath

open class TypeAnnotationNode(
    val typeRef: Int, val typePath: TypePath, descriptor: String
) : AnnotationNode(descriptor)