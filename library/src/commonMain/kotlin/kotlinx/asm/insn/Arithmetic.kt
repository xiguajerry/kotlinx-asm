package kotlinx.asm.insn

import kotlinx.asm.Opcodes

sealed class Arithmetic(opcode: Int) : NoArg(opcode) {
    sealed class Binary(opcode: Int) : Arithmetic(opcode) {
        sealed class Add(opcode: Int) : Binary(opcode)
        sealed class Sub(opcode: Int) : Binary(opcode)
        sealed class Mul(opcode: Int) : Binary(opcode)
        sealed class Div(opcode: Int) : Binary(opcode)
        sealed class Rem(opcode: Int) : Binary(opcode)

        sealed class Shl(opcode: Int) : Binary(opcode)
        sealed class Shr(opcode: Int) : Binary(opcode)
        sealed class UShr(opcode: Int) : Binary(opcode)
        sealed class And(opcode: Int) : Binary(opcode)
        sealed class Or(opcode: Int) : Binary(opcode)
        sealed class Xor(opcode: Int) : Binary(opcode)

        sealed class Compare(opcode: Int) : Binary(opcode)
    }
    sealed class Unary(opcode: Int) : Arithmetic(opcode) {
        sealed class Neg(opcode: Int) : Unary(opcode)
        sealed class Convert(opcode: Int) : Unary(opcode)
    }
}

data object IADD : Arithmetic.Binary.Add(Opcodes.IADD)
data object LADD : Arithmetic.Binary.Add(Opcodes.LADD)
data object FADD : Arithmetic.Binary.Add(Opcodes.FADD)
data object DADD : Arithmetic.Binary.Add(Opcodes.DADD)

data object ISUB : Arithmetic.Binary.Sub(Opcodes.ISUB)
data object LSUB : Arithmetic.Binary.Sub(Opcodes.LSUB)
data object FSUB : Arithmetic.Binary.Sub(Opcodes.FSUB)
data object DSUB : Arithmetic.Binary.Sub(Opcodes.DSUB)

data object IMUL : Arithmetic.Binary.Mul(Opcodes.IMUL)
data object LMUL : Arithmetic.Binary.Mul(Opcodes.LMUL)
data object FMUL : Arithmetic.Binary.Mul(Opcodes.FMUL)
data object DMUL : Arithmetic.Binary.Mul(Opcodes.DMUL)

data object IDIV : Arithmetic.Binary.Div(Opcodes.IDIV)
data object LDIV : Arithmetic.Binary.Div(Opcodes.LDIV)
data object FDIV : Arithmetic.Binary.Div(Opcodes.FDIV)
data object DDIV : Arithmetic.Binary.Div(Opcodes.DDIV)

data object IREM : Arithmetic.Binary.Rem(Opcodes.IREM)
data object LREM : Arithmetic.Binary.Rem(Opcodes.LREM)
data object FREM : Arithmetic.Binary.Rem(Opcodes.FREM)
data object DREM : Arithmetic.Binary.Rem(Opcodes.DREM)

data object INEG : Arithmetic.Unary.Neg(Opcodes.INEG)
data object LNEG : Arithmetic.Unary.Neg(Opcodes.LNEG)
data object FNEG : Arithmetic.Unary.Neg(Opcodes.FNEG)
data object DNEG : Arithmetic.Unary.Neg(Opcodes.DNEG)

data object ISHL : Arithmetic.Binary.Shl(Opcodes.ISHL)
data object LSHL : Arithmetic.Binary.Shr(Opcodes.LSHL)

data object ISHR : Arithmetic.Binary.Shr(Opcodes.ISHR)
data object LSHR : Arithmetic.Binary.Shr(Opcodes.LSHR)

data object IUSHR : Arithmetic.Binary.UShr(Opcodes.IUSHR)
data object LUSHR : Arithmetic.Binary.UShr(Opcodes.LUSHR)

data object IAND : Arithmetic.Binary.And(Opcodes.IAND)
data object LAND : Arithmetic.Binary.And(Opcodes.LAND)

data object IOR : Arithmetic.Binary.Or(Opcodes.IOR)
data object LOR : Arithmetic.Binary.Or(Opcodes.LOR)

data object IXOR : Arithmetic.Binary.Xor(Opcodes.IXOR)
data object LXOR : Arithmetic.Binary.Xor(Opcodes.LXOR)

data object IINC : Arithmetic.Unary(Opcodes.IINC)

data object I2L : Arithmetic.Unary.Convert(Opcodes.I2L)
data object I2F : Arithmetic.Unary.Convert(Opcodes.I2F)
data object I2D : Arithmetic.Unary.Convert(Opcodes.I2D)
data object L2I : Arithmetic.Unary.Convert(Opcodes.L2I)
data object L2F : Arithmetic.Unary.Convert(Opcodes.L2F)
data object L2D : Arithmetic.Unary.Convert(Opcodes.L2D)
data object F2I : Arithmetic.Unary.Convert(Opcodes.F2I)
data object F2L : Arithmetic.Unary.Convert(Opcodes.F2L)
data object F2D : Arithmetic.Unary.Convert(Opcodes.F2D)
data object D2I : Arithmetic.Unary.Convert(Opcodes.D2I)
data object D2L : Arithmetic.Unary.Convert(Opcodes.D2L)
data object D2F : Arithmetic.Unary.Convert(Opcodes.D2F)
data object I2B : Arithmetic.Unary.Convert(Opcodes.I2B)
data object I2C : Arithmetic.Unary.Convert(Opcodes.I2C)
data object I2S : Arithmetic.Unary.Convert(Opcodes.I2S)

data object LCMP : Arithmetic.Binary.Compare(Opcodes.LCMP)
data object FCMPL : Arithmetic.Binary.Compare(Opcodes.FCMPL)
data object FCMPG : Arithmetic.Binary.Compare(Opcodes.FCMPG)
data object DCMPL : Arithmetic.Binary.Compare(Opcodes.DCMPL)
data object DCMPG : Arithmetic.Binary.Compare(Opcodes.DCMPG)