package kotlinx.asm

open class Frame internal constructor(val owner: Label) {
    companion object {
        internal const val SAME_FRAME: Int = 0
        internal const val SAME_LOCALS_1_STACK_ITEM_FRAME: Int = 64
        internal const val RESERVED: Int = 128
        internal const val SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED: Int = 247
        internal const val CHOP_FRAME: Int = 248
        internal const val SAME_FRAME_EXTENDED: Int = 251
        internal const val APPEND_FRAME: Int = 252
        internal const val FULL_FRAME: Int = 255
        internal const val ITEM_TOP: Int = 0
        internal const val ITEM_INTEGER: Int = 1
        internal const val ITEM_FLOAT: Int = 2
        internal const val ITEM_DOUBLE: Int = 3
        internal const val ITEM_LONG: Int = 4
        internal const val ITEM_NULL: Int = 5
        internal const val ITEM_UNINITIALIZED_THIS: Int = 6
        internal const val ITEM_OBJECT: Int = 7
        internal const val ITEM_UNINITIALIZED: Int = 8
        internal const val ITEM_ASM_BOOLEAN: Int = 9
        internal const val ITEM_ASM_BYTE: Int = 10
        internal const val ITEM_ASM_CHAR: Int = 11
        internal const val ITEM_ASM_SHORT: Int = 12
        internal const val DIM_SIZE: Int = 6
        internal const val KIND_SIZE: Int = 4
        internal const val FLAGS_SIZE: Int = 2
        internal const val VALUE_SIZE: Int = 32 - DIM_SIZE - KIND_SIZE - FLAGS_SIZE
        internal const val DIM_SHIFT: Int = KIND_SIZE + FLAGS_SIZE + VALUE_SIZE
        internal const val KIND_SHIFT: Int = FLAGS_SIZE + VALUE_SIZE
        internal const val FLAGS_SHIFT: Int = VALUE_SIZE
        internal const val DIM_MASK: Int = ((1 shl DIM_SIZE) - 1) shl DIM_SHIFT
        internal const val KIND_MASK: Int = ((1 shl KIND_SIZE) - 1) shl KIND_SHIFT
        internal const val VALUE_MASK: Int = (1 shl VALUE_SIZE) - 1
        internal const val ARRAY_OF: Int = +1 shl DIM_SHIFT
        internal const val ELEMENT_OF: Int = -1 shl DIM_SHIFT
        internal const val CONSTANT_KIND: Int = 1 shl KIND_SHIFT
        internal const val REFERENCE_KIND: Int = 2 shl KIND_SHIFT
        internal const val UNINITIALIZED_KIND: Int = 3 shl KIND_SHIFT
        internal const val FORWARD_UNINITIALIZED_KIND: Int = 4 shl KIND_SHIFT
        internal const val LOCAL_KIND: Int = 5 shl KIND_SHIFT
        internal const val STACK_KIND: Int = 6 shl KIND_SHIFT
        internal const val TOP_IF_LONG_OR_DOUBLE_FLAG: Int = 1 shl FLAGS_SHIFT
        internal const val TOP: Int = CONSTANT_KIND or ITEM_TOP
        internal const val BOOLEAN: Int = CONSTANT_KIND or ITEM_ASM_BOOLEAN
        internal const val BYTE: Int = CONSTANT_KIND or ITEM_ASM_BYTE
        internal const val CHAR: Int = CONSTANT_KIND or ITEM_ASM_CHAR
        internal const val SHORT: Int = CONSTANT_KIND or ITEM_ASM_SHORT
        internal const val INTEGER: Int = CONSTANT_KIND or ITEM_INTEGER
        internal const val FLOAT: Int = CONSTANT_KIND or ITEM_FLOAT
        internal const val LONG: Int = CONSTANT_KIND or ITEM_LONG
        internal const val DOUBLE: Int = CONSTANT_KIND or ITEM_DOUBLE
        internal const val NULL: Int = CONSTANT_KIND or ITEM_NULL
        internal const val UNINITIALIZED_THIS: Int = CONSTANT_KIND or ITEM_UNINITIALIZED_THIS

        internal fun getAbstractTypeFromApiFormat(symbolTable: SymbolTable, type: Any): Int {
            return when (type) {
                is Int -> {
                     CONSTANT_KIND or type
                }

                is String -> {
                    val descriptor = Type.getObjectType(type).descriptor
                    getAbstractTypeFromDescriptor(symbolTable, descriptor, 0)
                }

                else -> {
                    val label = type as Label
                    if ((label.flags.toInt() and Label.FLAG_RESOLVED.toInt()) != 0) {
                        UNINITIALIZED_KIND or symbolTable.addUninitializedType("", label.bytecodeOffset)
                    } else {
                        FORWARD_UNINITIALIZED_KIND or symbolTable.addForwardUninitializedType("", label)
                    }
                }
            }
        }

        /**
         * Returns the abstract type corresponding to the given type descriptor.
         *
         * @param symbolTable the type table to use to lookup and store type [Symbol].
         * @param buffer a string ending with a type descriptor.
         * @param offset the start offset of the type descriptor in buffer.
         * @return the abstract type corresponding to the given type descriptor.
         */
        private fun getAbstractTypeFromDescriptor(
            symbolTable: SymbolTable, buffer: String, offset: Int
        ): Int {
            val internalName: String?
            when (buffer[offset]) {
                'V' -> return 0
                'Z', 'C', 'B', 'S', 'I' -> return INTEGER
                'F' -> return FLOAT
                'J' -> return LONG
                'D' -> return DOUBLE
                'L' -> {
                    internalName = buffer.substring(offset + 1, buffer.length - 1)
                    return REFERENCE_KIND or symbolTable.addType(internalName)
                }

                '[' -> {
                    var elementDescriptorOffset = offset + 1
                    while (buffer[elementDescriptorOffset] == '[') {
                        ++elementDescriptorOffset
                    }
                    val typeValue: Int
                    when (buffer[elementDescriptorOffset]) {
                        'Z' -> typeValue = BOOLEAN
                        'C' -> typeValue = CHAR
                        'B' -> typeValue = BYTE
                        'S' -> typeValue = SHORT
                        'I' -> typeValue = INTEGER
                        'F' -> typeValue = FLOAT
                        'J' -> typeValue = LONG
                        'D' -> typeValue = DOUBLE
                        'L' -> {
                            internalName = buffer.substring(elementDescriptorOffset + 1, buffer.length - 1)
                            typeValue = REFERENCE_KIND or symbolTable.addType(internalName)
                        }

                        else -> throw IllegalArgumentException(
                            "Invalid descriptor fragment: " + buffer.substring(elementDescriptorOffset)
                        )
                    }
                    return ((elementDescriptorOffset - offset) shl DIM_SHIFT) or typeValue
                }

                else -> throw IllegalArgumentException("Invalid descriptor: " + buffer.substring(offset))
            }
        }
    }

    private lateinit var inputLocals: IntArray
    private lateinit var inputStack: IntArray
    private lateinit var outputLocals: IntArray
    private lateinit var outputStack: IntArray
    private var outputStackStart: Short = 0
    private var outputStackTop: Short = 0
    private var initializationCount = 0
    private lateinit var initializations: IntArray

    fun copyFrom(frame: Frame) {
        inputLocals = frame.inputLocals
        inputStack = frame.inputStack
        outputStackStart = 0
        outputLocals = frame.outputLocals
        outputStack = frame.outputStack
        outputStackTop = frame.outputStackTop
        initializationCount = frame.initializationCount
        initializations = frame.initializations
    }
}