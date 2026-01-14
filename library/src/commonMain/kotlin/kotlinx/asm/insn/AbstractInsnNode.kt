package kotlinx.asm.insn

import kotlinx.asm.MethodVisitor

sealed class AbstractInsnNode(val opcode: Int) {
    val visibleTypeAnnotations = mutableListOf<TypeAnnotationNode>()
    val invisibleTypeAnnotations = mutableListOf<TypeAnnotationNode>()

    /** The previous instruction in the list to which this instruction belongs.  */
    var previousInsn: AbstractInsnNode? = null

    /** The next instruction in the list to which this instruction belongs.  */
    var nextInsn: AbstractInsnNode? = null

    /**
     * The index of this instruction in the list to which it belongs. The value of this field is
     * correct only when [InsnList.cache] is not null. A value of -1 indicates that this
     * instruction does not belong to any [InsnList].
     */
    var index: Int = -1

    /**
     * Makes the given method visitor visit this instruction.
     * 
     * @param methodVisitor a method visitor.
     */
    abstract fun accept(methodVisitor: MethodVisitor)

    /**
     * Makes the given visitor visit the annotations of this instruction.
     * 
     * @param methodVisitor a method visitor.
     */
    protected fun acceptAnnotations(methodVisitor: MethodVisitor) {
        if (visibleTypeAnnotations.isNotEmpty()) {
            var i = 0
            val n = visibleTypeAnnotations.size
            while (i < n) {
                val typeAnnotation = visibleTypeAnnotations[i]
                typeAnnotation.accept(
                    methodVisitor.visitInsnAnnotation(
                        typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, true
                    )
                )
                ++i
            }
        }
        if (invisibleTypeAnnotations.isNotEmpty()) {
            var i = 0
            val n = invisibleTypeAnnotations.size
            while (i < n) {
                val typeAnnotation = invisibleTypeAnnotations[i]
                typeAnnotation.accept(
                    methodVisitor.visitInsnAnnotation(
                        typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false
                    )
                )
                ++i
            }
        }
    }

    /**
     * Returns a copy of this instruction.
     * 
     * @param clonedLabels a map from LabelNodes to cloned LabelNodes.
     * @return a copy of this instruction. The returned instruction does not belong to any [     ].
     */
    fun clone(clonedLabels: MutableMap<LabelNode, LabelNode>): AbstractInsnNode {
        TODO()
    }
}