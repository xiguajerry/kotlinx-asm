package kotlinx.asm.insn

import kotlinx.asm.MethodVisitor
import kotlinx.asm.Opcodes
import kotlinx.asm.Type

sealed class TypeArg(val type: Type, opcode: Int) : AbstractInsnNode(opcode) {
    init {
        type as? Type.Object
            ?: type as Type.Array
    }

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitTypeInsn(opcode, type.internalName)
        acceptAnnotations(methodVisitor)
    }
}

class NEW(type: Type) : TypeArg(type, Opcodes.NEW)
class ANEWARRAY(type: Type) : TypeArg(type, Opcodes.ANEWARRAY)
class CHECKCAST(type: Type) : TypeArg(type, Opcodes.CHECKCAST)
class INSTANCEOF(type: Type) : TypeArg(type, Opcodes.INSTANCEOF)