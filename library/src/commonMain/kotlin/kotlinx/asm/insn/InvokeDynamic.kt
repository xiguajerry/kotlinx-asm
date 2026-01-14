package kotlinx.asm.insn

import kotlinx.asm.Handle
import kotlinx.asm.MethodVisitor
import kotlinx.asm.Opcodes

class INVOKEDYNAMIC(
    val name: String,
    val descriptor: String,
    val bsm: Handle,
    val bsmArgs: Array<Any>,
) : AbstractInsnNode(Opcodes.INVOKEDYNAMIC) {
    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitInvokeDynamicInsn(name, descriptor, bsm, bsmArgs)
        acceptAnnotations(methodVisitor)
    }
}