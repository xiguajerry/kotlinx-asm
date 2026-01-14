package kotlinx.asm

/**
 * An edge in the control flow graph of a method. Each node of this graph is a basic block,
 * represented with the Label corresponding to its first instruction. Each edge goes from one node
 * to another, i.e. from one basic block to another (called the predecessor and successor blocks,
 * respectively). An edge corresponds either to a jump or ret instruction or to an exception
 * handler.
 *
 * @see Label
 *
 * @author Eric Bruneton
 */
internal data class Edge(
    val info: Int,
    /** The successor block of this control flow graph edge.  */
    val successor: Label?,
    val nextEdge: Edge?
) {

    companion object {
        /**
         * A control flow graph edge corresponding to a jump or ret instruction. Only used with [ ][ClassWriter.COMPUTE_FRAMES].
         */
        const val JUMP: Int = 0

        /**
         * A control flow graph edge corresponding to an exception handler. Only used with [ ][ClassWriter.COMPUTE_MAXS].
         */
        const val EXCEPTION: Int = 0x7FFFFFFF
    }
}
