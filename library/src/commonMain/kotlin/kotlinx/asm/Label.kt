package kotlinx.asm

import kotlin.experimental.and
import kotlin.experimental.or

class Label {
    companion object {
        internal const val FLAG_DEBUG_ONLY: Short = 1
        internal const val FLAG_JUMP_TARGET: Short = 2
        internal const val FLAG_RESOLVED: Short = 4
        internal const val FLAG_REACHABLE: Short = 8
        internal const val FLAG_SUBROUTINE_CALLER: Short = 16
        internal const val FLAG_SUBROUTINE_START: Short = 32
        internal const val FLAG_SUBROUTINE_END: Short = 64
        internal const val FLAG_LINE_NUMBER: Short = 128
        internal const val LINE_NUMBERS_CAPACITY_INCREMENT: Int = 4
        internal const val FORWARD_REFERENCES_CAPACITY_INCREMENT: Int = 6
        internal const val FORWARD_REFERENCE_TYPE_MASK: Int = -0x10000000
        internal const val FORWARD_REFERENCE_TYPE_SHORT: Int = 0x10000000
        internal const val FORWARD_REFERENCE_TYPE_WIDE: Int = 0x20000000
        internal const val FORWARD_REFERENCE_TYPE_STACK_MAP: Int = 0x30000000
        internal const val FORWARD_REFERENCE_HANDLE_MASK: Int = 0x0FFFFFFF
        internal val EMPTY_LIST = Label()
    }
    
    lateinit var info: Any
    var flags: Short = 0
    private var lineNumber: Short = 0
    private var otherLineNumbers: IntArray? = null
    var bytecodeOffset: Int = 0
    private var forwardReferences: IntArray? = null
    var inputStackSize: Short = 0
    var outputStackSize: Short = 0
    var outputStackMax: Short = 0
    var subroutineId: Short = 0

    /**
     * The input and output stack map frames of the basic block corresponding to this label. This
     * field is only used when the [MethodWriter.COMPUTE_ALL_FRAMES] or [ ][MethodWriter.COMPUTE_INSERTED_FRAMES] option is used.
     */
    var frame: Frame? = null

    /**
     * The successor of this label, in the order they are visited in [MethodVisitor.visitLabel].
     * This linked list does not include labels used for debug info only. If the [ ][MethodWriter.COMPUTE_ALL_FRAMES] or [MethodWriter.COMPUTE_INSERTED_FRAMES] option is used
     * then it does not contain either successive labels that denote the same bytecode offset (in this
     * case only the first label appears in this list).
     */
    var nextBasicBlock: Label? = null

    /**
     * The outgoing edges of the basic block corresponding to this label, in the control flow graph of
     * its method. These edges are stored in a linked list of [Edge] objects, linked to each
     * other by their [Edge.nextEdge] field.
     */
    internal var outgoingEdges: Edge? = null

    /**
     * The next element in the list of labels to which this label belongs, or null if it
     * does not belong to any list. All lists of labels must end with the [.EMPTY_LIST]
     * sentinel, in order to ensure that this field is null if and only if this label does not belong
     * to a list of labels. Note that there can be several lists of labels at the same time, but that
     * a label can belong to at most one list at a time (unless some lists share a common tail, but
     * this is not used in practice).
     * 
     * 
     * List of labels are used in [MethodWriter.computeAllFrames] and [ ][MethodWriter.computeMaxStackAndLocal] to compute stack map frames and the maximum stack size,
     * respectively, as well as in [.markSubroutine] and [.addSubroutineRetSuccessors] to
     * compute the basic blocks belonging to subroutines and their outgoing edges. Outside of these
     * methods, this field should be null (this property is a precondition and a postcondition of
     * these methods).
     */
    var nextListElement: Label? = null

    /**
     * Returns the bytecode offset corresponding to this label. This offset is computed from the start
     * of the method's bytecode. *This method is intended for [Attribute] sub classes, and is
     * normally not needed by class generators or adapters.*
     *
     * @return the bytecode offset corresponding to this label.
     * @throws IllegalStateException if this label is not resolved yet.
     */
    fun getOffset(): Int {
        check((flags and FLAG_RESOLVED) != 0.toShort()) { "Label offset position has not been resolved yet" }
        return bytecodeOffset
    }

    /**
     * Returns the "canonical" [Label] instance corresponding to this label's bytecode offset,
     * if known, otherwise the label itself. The canonical instance is the first label (in the order
     * of their visit by [MethodVisitor.visitLabel]) corresponding to this bytecode offset. It
     * cannot be known for labels which have not been visited yet.
     *
     *
     * *This method should only be used when the [MethodWriter.COMPUTE_ALL_FRAMES] option
     * is used.*
     *
     * @return the label itself if [.frame] is null, otherwise the Label's frame owner. This
     * corresponds to the "canonical" label instance described above thanks to the way the label
     * frame is set in [MethodWriter.visitLabel].
     */
    fun getCanonicalInstance(): Label {
        return frame?.owner ?: this
    }


    // -----------------------------------------------------------------------------------------------
    // Methods to manage line numbers
    // -----------------------------------------------------------------------------------------------
    /**
     * Adds a source line number corresponding to this label.
     *
     * @param lineNumber a source line number (which should be strictly positive).
     */
    fun addLineNumber(lineNumber: Int) {
        if ((flags and FLAG_LINE_NUMBER) == 0.toShort()) {
            flags = flags or FLAG_LINE_NUMBER
            this.lineNumber = lineNumber.toShort()
        } else {
            var otherLineNumbers = otherLineNumbers
            if (otherLineNumbers == null) {
                otherLineNumbers = IntArray(LINE_NUMBERS_CAPACITY_INCREMENT)
                this.otherLineNumbers = otherLineNumbers
            }
            otherLineNumbers[0]++
            val otherLineNumberIndex = otherLineNumbers[0]
            if (otherLineNumberIndex >= otherLineNumbers.size) {
                otherLineNumbers = otherLineNumbers
                    .copyOf(otherLineNumbers.size + LINE_NUMBERS_CAPACITY_INCREMENT)
                this.otherLineNumbers = otherLineNumbers
            }
            otherLineNumbers[otherLineNumberIndex] = lineNumber
        }
    }

    /**
     * Makes the given visitor visit this label and its source line numbers, if applicable.
     *
     * @param methodVisitor a method visitor.
     * @param visitLineNumbers whether to visit of the label's source line numbers, if any.
     */
    fun accept(methodVisitor: MethodVisitor, visitLineNumbers: Boolean) {
        methodVisitor.visitLabel(this)
        if (visitLineNumbers && (flags and FLAG_LINE_NUMBER) != 0.toShort()) {
            methodVisitor.visitLineNumber(lineNumber.toInt() and 0xFFFF, this)
            if (otherLineNumbers != null) {
                for (i in 1..otherLineNumbers!![0]) {
                    methodVisitor.visitLineNumber(otherLineNumbers!![i], this)
                }
            }
        }
    }


    // -----------------------------------------------------------------------------------------------
    // Methods to compute offsets and to manage forward references
    // -----------------------------------------------------------------------------------------------
    /**
     * Puts a reference to this label in the bytecode of a method. If the bytecode offset of the label
     * is known, the relative bytecode offset between the label and the instruction referencing it is
     * computed and written directly. Otherwise, a null relative offset is written and a new forward
     * reference is declared for this label.
     *
     * @param code the bytecode of the method. This is where the reference is appended.
     * @param sourceInsnBytecodeOffset the bytecode offset of the instruction that contains the
     * reference to be appended.
     * @param wideReference whether the reference must be stored in 4 bytes (instead of 2 bytes).
     */
    fun put(
        code: ByteVector, sourceInsnBytecodeOffset: Int, wideReference: Boolean
    ) {
        if ((flags and FLAG_RESOLVED) == 0.toShort()) {
            if (wideReference) {
                addForwardReference(sourceInsnBytecodeOffset, FORWARD_REFERENCE_TYPE_WIDE, code.length)
                code.putInt(-1)
            } else {
                addForwardReference(sourceInsnBytecodeOffset, FORWARD_REFERENCE_TYPE_SHORT, code.length)
                code.putShort(-1)
            }
        } else {
            if (wideReference) {
                code.putInt(bytecodeOffset - sourceInsnBytecodeOffset)
            } else {
                code.putShort(bytecodeOffset - sourceInsnBytecodeOffset)
            }
        }
    }

    /**
     * Puts a reference to this label in the *stack map table* of a method. If the bytecode
     * offset of the label is known, it is written directly. Otherwise, a null relative offset is
     * written and a new forward reference is declared for this label.
     *
     * @param stackMapTableEntries the stack map table where the label offset must be added.
     */
    fun put(stackMapTableEntries: ByteVector) {
        if ((flags and FLAG_RESOLVED) == 0.toShort()) {
            addForwardReference(0, FORWARD_REFERENCE_TYPE_STACK_MAP, stackMapTableEntries.length)
        }
        stackMapTableEntries.putShort(bytecodeOffset)
    }

    /**
     * Adds a forward reference to this label. This method must be called only for a true forward
     * reference, i.e. only if this label is not resolved yet. For backward references, the relative
     * bytecode offset of the reference can be, and must be, computed and stored directly.
     *
     * @param sourceInsnBytecodeOffset the bytecode offset of the instruction that contains the
     * reference stored at referenceHandle.
     * @param referenceType either [.FORWARD_REFERENCE_TYPE_SHORT] or [     ][.FORWARD_REFERENCE_TYPE_WIDE].
     * @param referenceHandle the offset in the bytecode where the forward reference value must be
     * stored.
     */
    private fun addForwardReference(
        sourceInsnBytecodeOffset: Int, referenceType: Int, referenceHandle: Int
    ) {
        var forwardReferences = forwardReferences
        if (forwardReferences == null) {
            forwardReferences = IntArray(FORWARD_REFERENCES_CAPACITY_INCREMENT)
            this.forwardReferences = forwardReferences
        }
        var lastElementIndex = forwardReferences[0]
        if (lastElementIndex + 2 >= forwardReferences.size) {
            forwardReferences = forwardReferences
                .copyOf(forwardReferences.size + FORWARD_REFERENCES_CAPACITY_INCREMENT)
            this.forwardReferences = forwardReferences
        }
        forwardReferences[++lastElementIndex] = sourceInsnBytecodeOffset
        forwardReferences[++lastElementIndex] = referenceType or referenceHandle
        forwardReferences[0] = lastElementIndex
    }

    /**
     * Sets the bytecode offset of this label to the given value and resolves the forward references
     * to this label, if any. This method must be called when this label is added to the bytecode of
     * the method, i.e. when its bytecode offset becomes known. This method fills in the blanks that
     * where left in the bytecode (and optionally in the stack map table) by each forward reference
     * previously added to this label.
     *
     * @param code the bytecode of the method.
     * @param stackMapTableEntries the 'entries' array of the StackMapTable code attribute of the
     * method. Maybe null.
     * @param bytecodeOffset the bytecode offset of this label.
     * @return true if a blank that was left for this label was too small to store the
     * offset. In such a case the corresponding jump instruction is replaced with an equivalent
     * ASM specific instruction using an unsigned two bytes offset. These ASM specific
     * instructions are later replaced with standard bytecode instructions with wider offsets (4
     * bytes instead of 2), in ClassReader.
     */
    fun resolve(
        code: ByteArray, stackMapTableEntries: ByteVector, bytecodeOffset: Int
    ): Boolean {
        this.flags = this.flags or FLAG_RESOLVED
        this.bytecodeOffset = bytecodeOffset
        val forwardReferences = forwardReferences ?: return false
        var hasAsmInstructions = false
        for (i in forwardReferences[0] downTo 1 step 2) {
            val sourceInsnBytecodeOffset = forwardReferences[i - 1]
            val reference = forwardReferences[i]
            val relativeOffset = bytecodeOffset - sourceInsnBytecodeOffset
            var handle = reference and FORWARD_REFERENCE_HANDLE_MASK
            if ((reference and FORWARD_REFERENCE_TYPE_MASK) == FORWARD_REFERENCE_TYPE_SHORT) {
                if (relativeOffset < Short.MIN_VALUE || relativeOffset > Short.MAX_VALUE) {
                    // Change the opcode of the jump instruction, in order to be able to find it later in
                    // ClassReader. These ASM specific opcodes are similar to jump instruction opcodes, except
                    // that the 2 bytes offset is unsigned (and can therefore represent values from 0 to
                    // 65535, which is sufficient since the size of a method is limited to 65535 bytes).
                    val opcode = code[sourceInsnBytecodeOffset].toInt() and 0xFF
                    if (opcode < Opcodes.IFNULL) {
                        // Change IFEQ ... JSR to ASM_IFEQ ... ASM_JSR.
                        code[sourceInsnBytecodeOffset] = (opcode + Constants.ASM_OPCODE_DELTA).toByte()
                    } else {
                        // Change IFNULL and IFNONNULL to ASM_IFNULL and ASM_IFNONNULL.
                        code[sourceInsnBytecodeOffset] =
                            (opcode + Constants.ASM_IFNULL_OPCODE_DELTA).toByte()
                    }
                    hasAsmInstructions = true
                }
                code[handle++] = (relativeOffset ushr 8).toByte()
                code[handle] = relativeOffset.toByte()
            } else if ((reference and FORWARD_REFERENCE_TYPE_MASK) == FORWARD_REFERENCE_TYPE_WIDE) {
                code[handle++] = (relativeOffset ushr 24).toByte()
                code[handle++] = (relativeOffset ushr 16).toByte()
                code[handle++] = (relativeOffset ushr 8).toByte()
                code[handle] = relativeOffset.toByte()
            } else {
                stackMapTableEntries.data[handle++] = (bytecodeOffset ushr 8).toByte()
                stackMapTableEntries.data[handle] = bytecodeOffset.toByte()
            }
        }
        return hasAsmInstructions
    }


    // -----------------------------------------------------------------------------------------------
    // Methods related to subroutines
    // -----------------------------------------------------------------------------------------------
    /**
     * Finds the basic blocks that belong to the subroutine starting with the basic block
     * corresponding to this label, and marks these blocks as belonging to this subroutine. This
     * method follows the control flow graph to find all the blocks that are reachable from the
     * current basic block WITHOUT following any jsr target.
     *
     *
     * Note: a precondition and postcondition of this method is that all labels must have a null
     * [.nextListElement].
     *
     * @param subroutineId the id of the subroutine starting with the basic block corresponding to
     * this label.
     */
    fun markSubroutine(subroutineId: Short) {
        // Data flow algorithm: put this basic block in a list of blocks to process (which are blocks
        // belonging to subroutine subroutineId) and, while there are blocks to process, remove one from
        // the list, mark it as belonging to the subroutine, and add its successor basic blocks in the
        // control flow graph to the list of blocks to process (if not already done).
        var listOfBlocksToProcess: Label? = this
        listOfBlocksToProcess?.nextListElement = EMPTY_LIST
        while (listOfBlocksToProcess != EMPTY_LIST) {
            // Remove a basic block from the list of blocks to process.
            val basicBlock = listOfBlocksToProcess
            listOfBlocksToProcess = listOfBlocksToProcess?.nextListElement
            basicBlock?.nextListElement = null

            // If it is not already marked as belonging to a subroutine, mark it as belonging to
            // subroutineId and add its successors to the list of blocks to process (unless already done).
            if (basicBlock?.subroutineId?.toInt() == 0) {
                basicBlock.subroutineId = subroutineId
                listOfBlocksToProcess = basicBlock.pushSuccessors(listOfBlocksToProcess!!)
            }
        }
    }

    /**
     * Finds the basic blocks that end a subroutine starting with the basic block corresponding to
     * this label and, for each one of them, adds an outgoing edge to the basic block following the
     * given subroutine call. In other words, completes the control flow graph by adding the edges
     * corresponding to the return from this subroutine, when called from the given caller basic
     * block.
     *
     *
     * Note: a precondition and postcondition of this method is that all labels must have a null
     * [.nextListElement].
     *
     * @param subroutineCaller a basic block that ends with a jsr to the basic block corresponding to
     * this label. This label is supposed to correspond to the start of a subroutine.
     */
    fun addSubroutineRetSuccessors(subroutineCaller: Label) {
        // Data flow algorithm: put this basic block in a list blocks to process (which are blocks
        // belonging to a subroutine starting with this label) and, while there are blocks to process,
        // remove one from the list, put it in a list of blocks that have been processed, add a return
        // edge to the successor of subroutineCaller if applicable, and add its successor basic blocks
        // in the control flow graph to the list of blocks to process (if not already done).
        var listOfProcessedBlocks: Label? = EMPTY_LIST
        var listOfBlocksToProcess: Label? = this
        listOfBlocksToProcess?.nextListElement = EMPTY_LIST
        while (listOfBlocksToProcess != EMPTY_LIST) {
            // Move a basic block from the list of blocks to process to the list of processed blocks.
            val basicBlock = listOfBlocksToProcess
            listOfBlocksToProcess = basicBlock?.nextListElement
            basicBlock?.nextListElement = listOfProcessedBlocks
            listOfProcessedBlocks = basicBlock

            // Add an edge from this block to the successor of the caller basic block, if this block is
            // the end of a subroutine and if this block and subroutineCaller do not belong to the same
            // subroutine.
            if ((basicBlock?.flags?.and(FLAG_SUBROUTINE_END)) != 0.toShort()
                && basicBlock?.subroutineId != subroutineCaller.subroutineId
            ) {
                basicBlock?.outgoingEdges =
                    Edge(
                        basicBlock.outputStackSize.toInt(),
                        // By construction, the first outgoing edge of a basic block that ends with a jsr
                        // instruction leads to the jsr continuation block, i.e. where execution continues
                        // when ret is called (see {@link #FLAG_SUBROUTINE_CALLER}).
                        subroutineCaller.outgoingEdges?.successor,
                        basicBlock.outgoingEdges
                    )
            }
            // Add its successors to the list of blocks to process. Note that {@link #pushSuccessors} does
            // not push basic blocks which are already in a list. Here this means either in the list of
            // blocks to process, or in the list of already processed blocks. This second list is
            // important to make sure we don't reprocess an already processed block.
            listOfBlocksToProcess = basicBlock?.pushSuccessors(listOfBlocksToProcess!!)
        }
        // Reset the {@link #nextListElement} of all the basic blocks that have been processed to null,
        // so that this method can be called again with a different subroutine or subroutine caller.
        while (listOfProcessedBlocks != EMPTY_LIST) {
            val newListOfProcessedBlocks = listOfProcessedBlocks?.nextListElement
            listOfProcessedBlocks?.nextListElement = null
            listOfProcessedBlocks = newListOfProcessedBlocks
        }
    }

    /**
     * Adds the successors of this label in the method's control flow graph (except those
     * corresponding to a jsr target, and those already in a list of labels) to the given list of
     * blocks to process, and returns the new list.
     *
     * @param listOfLabelsToProcess a list of basic blocks to process, linked together with their
     * [.nextListElement] field.
     * @return the new list of blocks to process.
     */
    private fun pushSuccessors(listOfLabelsToProcess: Label): Label? {
        var newListOfLabelsToProcess: Label? = listOfLabelsToProcess
        var outgoingEdge: Edge? = outgoingEdges
        while (outgoingEdge != null) {
            // By construction, the second outgoing edge of a basic block that ends with a jsr instruction
            // leads to the jsr target (see {@link #FLAG_SUBROUTINE_CALLER}).
            val isJsrTarget =
                (flags and FLAG_SUBROUTINE_CALLER) != 0.toShort() && outgoingEdge == outgoingEdges?.nextEdge
            if (!isJsrTarget && outgoingEdge.successor?.nextListElement == null) {
                // Add this successor to the list of blocks to process, if it does not already belong to a
                // list of labels.
                outgoingEdge.successor?.nextListElement = newListOfLabelsToProcess
                newListOfLabelsToProcess = outgoingEdge.successor
            }
            outgoingEdge = outgoingEdge.nextEdge
        }
        return newListOfLabelsToProcess
    }


    // -----------------------------------------------------------------------------------------------
    // Overridden Object methods
    // -----------------------------------------------------------------------------------------------
    /**
     * Returns a string representation of this label.
     *
     * @return a string representation of this label.
     */
    override fun toString(): String {
        return "L" + this.hashCode()
    }
}