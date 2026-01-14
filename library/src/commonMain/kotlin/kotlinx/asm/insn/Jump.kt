package kotlinx.asm.insn

import kotlinx.asm.MethodVisitor
import kotlinx.asm.Opcodes

sealed class Jump(var label: LabelNode, opcode: Int) : AbstractInsnNode(opcode) {
    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitJumpInsn(opcode, label.label)
        acceptAnnotations(methodVisitor)
    }
}

@Deprecated("Deprecated in specification", level = DeprecationLevel.WARNING)
class JSR(label: LabelNode) : Jump(label, Opcodes.JSR)
class GOTO(label: LabelNode) : Jump(label, Opcodes.GOTO)
class IFEQ(label: LabelNode) : Jump(label, Opcodes.IFEQ)
class IFNE(label: LabelNode) : Jump(label, Opcodes.IFNE)
class IFLT(label: LabelNode) : Jump(label, Opcodes.IFLT)
class IFGE(label: LabelNode) : Jump(label, Opcodes.IFGE)
class IFGT(label: LabelNode) : Jump(label, Opcodes.IFGT)
class IFLE(label: LabelNode) : Jump(label, Opcodes.IFLE)
class IF_ICMPEQ(label: LabelNode) : Jump(label, Opcodes.IF_ICMPEQ)
class IF_ICMPNE(label: LabelNode) : Jump(label, Opcodes.IF_ICMPNE)
class IF_ICMPLT(label: LabelNode) : Jump(label, Opcodes.IF_ICMPLT)
class IF_ICMPGE(label: LabelNode) : Jump(label, Opcodes.IF_ICMPGE)
class IF_ICMPGT(label: LabelNode) : Jump(label, Opcodes.IF_ICMPGT)
class IF_ICMPLE(label: LabelNode) : Jump(label, Opcodes.IF_ICMPLE)
class IF_ACMPEQ(label: LabelNode) : Jump(label, Opcodes.IF_ACMPEQ)
class IF_ACMPNE(label: LabelNode) : Jump(label, Opcodes.IF_ACMPNE)
class IFNULL(label: LabelNode) : Jump(label, Opcodes.IFNULL)
class IFNONNULL(label: LabelNode) : Jump(label, Opcodes.IFNONNULL)