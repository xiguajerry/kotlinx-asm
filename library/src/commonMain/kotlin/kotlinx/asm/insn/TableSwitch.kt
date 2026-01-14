package kotlinx.asm.insn

import kotlinx.asm.MethodVisitor
import kotlinx.asm.Opcodes

class TABLESWITCH(
    var min: Int,
    var max: Int,
    var default: LabelNode,
    labels: List<LabelNode>
) : AbstractInsnNode(Opcodes.TABLESWITCH) {
    val labels = labels.toMutableList()

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitTableSwitchInsn(min, max, default.label, labels.map { it.label })
        acceptAnnotations(methodVisitor)
    }
}