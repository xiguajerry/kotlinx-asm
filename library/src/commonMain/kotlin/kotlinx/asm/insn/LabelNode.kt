package kotlinx.asm.insn

import kotlinx.asm.Label
import kotlinx.asm.MethodVisitor

/** An [AbstractInsnNode] that encapsulates a [Label].  */
class LabelNode : AbstractInsnNode {
    private var label0: Label? = null

    constructor() : super(-1)

    constructor(label: Label) : super(-1) {
        this.label0 = label
    }

    val label: Label
        /**
         * Returns the label encapsulated by this node. A new label is created and associated with this
         * node if it was created without an encapsulated label.
         *
         * @return the label encapsulated by this node.
         */
        get() {
            val label0 = label0
            if (label0 == null) {
                val new = Label()
                this.label0 = new
                return new
            }
            return label0
        }

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitLabel(this.label)
    }

    fun resetLabel() {
        label0 = null
    }
}
