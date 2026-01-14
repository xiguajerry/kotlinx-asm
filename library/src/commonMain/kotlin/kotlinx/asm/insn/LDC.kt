package kotlinx.asm.insn

import kotlinx.asm.MethodVisitor
import kotlinx.asm.Opcodes

class LDC(var operand: Any) : AbstractInsnNode(Opcodes.LDC) {
    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitLdcInsn(operand)
        acceptAnnotations(methodVisitor)
    }
}