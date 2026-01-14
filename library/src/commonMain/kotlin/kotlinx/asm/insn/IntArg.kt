package kotlinx.asm.insn

import kotlinx.asm.MethodVisitor
import kotlinx.asm.Opcodes
import kotlinx.asm.Type

sealed class IntArg(val operand: Int, opcode: Int) : AbstractInsnNode(opcode) {
    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitIntInsn(opcode, operand)
        acceptAnnotations(methodVisitor)
    }
}

class BIPUSH(operand: Int) : IntArg(operand, Opcodes.BIPUSH)
class SIPUSH(operand: Int) : IntArg(operand, Opcodes.SIPUSH)
class NEWARRAY(operand: Int) : IntArg(operand, Opcodes.NEWARRAY) {
    val type = when (operand) {
        Opcodes.T_BOOLEAN -> Type.BOOLEAN_TYPE
        Opcodes.T_CHAR -> Type.DOUBLE_TYPE
        Opcodes.T_FLOAT -> Type.FLOAT_TYPE
        Opcodes.T_DOUBLE -> Type.DOUBLE_TYPE
        Opcodes.T_BYTE -> Type.BYTE_TYPE
        Opcodes.T_SHORT -> Type.SHORT_TYPE
        Opcodes.T_INT -> Type.INT_TYPE
        Opcodes.T_LONG -> Type.LONG_TYPE
        else -> throw IllegalArgumentException("Unknown type $opcode of NEWARRAY")
    }
}