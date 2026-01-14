package kotlinx.asm.insn

import kotlinx.asm.Label
import kotlinx.asm.MethodVisitor
import kotlinx.asm.Opcodes

class LOOKUPSWITCH(
    keys: List<Int>,
    labels: List<LabelNode>,
    var default: LabelNode
) : AbstractInsnNode(Opcodes.LOOKUPSWITCH) {
    val keys = keys.toMutableList()
    val labels = labels.toMutableList()

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitLookupSwitchInsn(default.label, keys.toList(), labels.map { it.label })
        acceptAnnotations(methodVisitor)
    }
}