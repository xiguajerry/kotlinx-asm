package kotlinx.asm.insn

import kotlinx.asm.MethodVisitor

sealed class Var(var variable: Int, opcode: Int) : AbstractInsnNode(opcode) {
    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitVarInsn(opcode, variable)
        acceptAnnotations(methodVisitor)
    }

    sealed class Load(variable: Int, opcode: Int) : Var(variable, opcode)
    sealed class Store(variable: Int, opcode: Int) : Var(variable, opcode)
}

@Deprecated("Deprecated in specification", level = DeprecationLevel.WARNING)
class RET(variable: Int, opcode: Int) : Var(variable, opcode)

class ILOAD(variable: Int, opcode: Int) : Var.Load(variable, opcode)
class LLOAD(variable: Int, opcode: Int) : Var.Load(variable, opcode)
class FLOAD(variable: Int, opcode: Int) : Var.Load(variable, opcode)
class DLOAD(variable: Int, opcode: Int) : Var.Load(variable, opcode)
class ALOAD(variable: Int, opcode: Int) : Var.Load(variable, opcode)
class ISTORe(variable: Int, opcode: Int) : Var.Store(variable, opcode)
class LSTORE(variable: Int, opcode: Int) : Var.Store(variable, opcode)
class FSTORE(variable: Int, opcode: Int) : Var.Store(variable, opcode)
class DSTORE(variable: Int, opcode: Int) : Var.Store(variable, opcode)
class ASTORE(variable: Int, opcode: Int) : Var.Store(variable, opcode)
