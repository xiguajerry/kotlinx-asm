package kotlinx.asm.insn

import kotlinx.asm.Opcodes

sealed class Stack(opcode: Int) : NoArg(opcode)

data object POP : Stack(Opcodes.POP)
data object POP2 : Stack(Opcodes.POP2)
data object DUP : Stack(Opcodes.DUP)
data object DUP_X1 : Stack(Opcodes.DUP_X1)
data object DUP_X2 : Stack(Opcodes.DUP_X2)
data object DUP2 : Stack(Opcodes.DUP2)
data object DUP2_X1 : Stack(Opcodes.DUP2_X1)
data object DUP2_X2 : Stack(Opcodes.DUP2_X2)
data object SWAP : Stack(Opcodes.SWAP)