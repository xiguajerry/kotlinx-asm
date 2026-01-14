package kotlinx.asm.insn

import kotlinx.asm.Opcodes

sealed class Return(opcode: Int) : NoArg(opcode)

data object IRETURN : Return(Opcodes.IRETURN)
data object LRETURN : Return(Opcodes.LRETURN)
data object FRETURN : Return(Opcodes.FRETURN)
data object DRETURN : Return(Opcodes.DRETURN)
data object ARETURN : Return(Opcodes.ARETURN)
data object RETURN : Return(Opcodes.RETURN)