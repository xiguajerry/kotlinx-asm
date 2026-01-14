package kotlinx.asm.insn

import kotlinx.asm.MethodVisitor
import kotlinx.asm.Opcodes

class MULTIANEWARRAY(
    val desc: String,
    val dims: Int
) : AbstractInsnNode(Opcodes.MULTIANEWARRAY) {
    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitMultiANewArrayInsn(desc, dims)
        acceptAnnotations(methodVisitor)
    }
}