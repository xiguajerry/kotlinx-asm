package kotlinx.asm.insn

import kotlinx.asm.MethodVisitor
import kotlinx.asm.Opcodes

sealed class Method(
    val owner: String,
    val name: String,
    val descriptor: String,
    val isInterface: Boolean,
    opcode: Int
) : AbstractInsnNode(opcode) {
    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        acceptAnnotations(methodVisitor)
    }
}

class INVOKEVIRTUAL(owner: String, name: String, descriptor: String, isInterface: Boolean) : Method(owner, name, descriptor, isInterface, Opcodes.INVOKEVIRTUAL)
class INVOKESPECIAL(owner: String, name: String, descriptor: String, isInterface: Boolean) : Method(owner, name, descriptor, isInterface, Opcodes.INVOKESPECIAL)
class INVOKESTATIC(owner: String, name: String, descriptor: String, isInterface: Boolean) : Method(owner, name, descriptor, isInterface, Opcodes.INVOKESTATIC)
class INVOKEINTERFACE(owner: String, name: String, descriptor: String, isInterface: Boolean) : Method(owner, name, descriptor, isInterface, Opcodes.INVOKEINTERFACE)
