package kotlinx.asm.insn

import kotlinx.asm.MethodVisitor
import kotlinx.asm.Opcodes

sealed class NoArg(opcode: Int) : AbstractInsnNode(opcode) {
    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitInsn(opcode)
        acceptAnnotations(methodVisitor)
    }
}

data object ARRAYLENGTH : NoArg(Opcodes.ARRAYLENGTH)
data object ATHROW : NoArg(Opcodes.ATHROW)
data object MONITORENTER : NoArg(Opcodes.MONITORENTER)
data object MONITOREXIT : NoArg(Opcodes.MONITOREXIT)