package kotlinx.asm.insn

sealed class Constants(opcode: Int) : NoArg(opcode)

data object ACONST_NULL : Constants(1) {}
data object ICONST_M1 : Constants(2)
data object ICONST_0 : Constants(3)
data object ICONST_1 : Constants(4)
data object ICONST_2 : Constants(5)
data object ICONST_3 : Constants(6)
data object ICONST_4 : Constants(7)
data object ICONST_5 : Constants(8)
data object LCONST_0 : Constants(9)
data object LCONST_1 : Constants(10)
data object FCONST_0 : Constants(11)
data object FCONST_1 : Constants(12)
data object FCONST_2 : Constants(13)
data object DCONST_0 : Constants(14)
data object DCONST_1 : Constants(15)