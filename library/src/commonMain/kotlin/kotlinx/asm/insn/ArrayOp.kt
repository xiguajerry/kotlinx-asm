package kotlinx.asm.insn

import kotlinx.asm.Opcodes

sealed class ArrayOp(opcode: Int) : NoArg(opcode) {
    sealed class Load(opcode: Int) : NoArg(opcode)
    sealed class Store(opcode: Int) : NoArg(opcode)
}

data object IALOAD : ArrayOp.Load(Opcodes.IALOAD)
data object LALOAD : ArrayOp.Load(Opcodes.LALOAD)
data object FALOAD : ArrayOp.Load(Opcodes.FALOAD)
data object DALOAD : ArrayOp.Load(Opcodes.DALOAD)
data object AALOAD : ArrayOp.Load(Opcodes.AALOAD)
data object BALOAD : ArrayOp.Load(Opcodes.BALOAD)
data object CALOAD : ArrayOp.Load(Opcodes.CALOAD)
data object SALOAD : ArrayOp.Load(Opcodes.SALOAD)

data object IASTORE : ArrayOp.Store(Opcodes.IASTORE)
data object LASTORE : ArrayOp.Store(Opcodes.LASTORE)
data object FASTORE : ArrayOp.Store(Opcodes.FASTORE)
data object DASTORE : ArrayOp.Store(Opcodes.DASTORE)
data object AASTORE : ArrayOp.Store(Opcodes.AASTORE)
data object BASTORE : ArrayOp.Store(Opcodes.BASTORE)
data object CASTORE : ArrayOp.Store(Opcodes.CASTORE)
data object SASTORE : ArrayOp.Store(Opcodes.SASTORE)