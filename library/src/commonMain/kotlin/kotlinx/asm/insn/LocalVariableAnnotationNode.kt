package kotlinx.asm.insn

import kotlinx.asm.MethodVisitor
import kotlinx.asm.TypePath

/**
 * A node that represents a type annotation on a local or resource variable.
 * 
 * @author Eric Bruneton
 */
class LocalVariableAnnotationNode(
    typeRef: Int,
    typePath: TypePath,
    start: List<LabelNode>,
    end: List<LabelNode>,
    index: IntArray,
    descriptor: String
) : TypeAnnotationNode(typeRef, typePath, descriptor) {
    /**
     * The fist instructions corresponding to the continuous ranges that make the scope of this local
     * variable (inclusive). Must not be null.
     */
    var start = start.toMutableList()

    /**
     * The last instructions corresponding to the continuous ranges that make the scope of this local
     * variable (exclusive). This list must have the same size as the 'start' list. Must not be
     * null.
     */
    var end = end.toMutableList()

    /**
     * The local variable's index in each range. This list must have the same size as the 'start'
     * list. Must not be null.
     */
    var index = index.toMutableList()

    /**
     * Makes the given visitor visit this type annotation.
     * 
     * @param methodVisitor the visitor that must visit this annotation.
     * @param visible true if the annotation is visible at runtime.
     */
    fun accept(methodVisitor: MethodVisitor, visible: Boolean) {
        accept(
            methodVisitor.visitLocalVariableAnnotation(
                typeRef, typePath, start.map { it.label }, end.map { it.label }, index.toList(), desc, visible
            )
        )
    }
}
