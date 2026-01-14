package kotlinx.asm

class TypePath internal constructor(val typePathContainer: ByteArray, val typePathOffset: Int) {
    val length: Int
        get() = typePathContainer[typePathOffset].toInt()

    fun getStep(index: Int): Int =
        typePathContainer[typePathOffset + 2 * index + 1].toInt()

    fun getStepArgument(index: Int): Int =
        typePathContainer[typePathOffset + 2 * index + 2].toInt()

    override fun toString(): String {
        val length = this.length
        return buildString(length * 2) {
            repeat(length) {
                when (getStep(it)) {
                    ARRAY_ELEMENT -> append('[')
                    INNER_TYPE -> append(".")
                    WILDCARD_BOUND -> append("*")
                    TYPE_ARGUMENT -> append(getStepArgument(it)).append(".")
                }
            }
        }
    }

    companion object Constants {
        const val ARRAY_ELEMENT: Int = 0
        const val INNER_TYPE: Int = 1
        const val WILDCARD_BOUND: Int = 2
        const val TYPE_ARGUMENT: Int = 3

        operator fun invoke(typePath: String?): TypePath? {
            if (typePath.isNullOrEmpty()) {
                return null
            }
            val typePathLength = typePath.length
            val output = ByteVector(typePathLength)
            output.putByte(0)
            var typePathIndex = 0
            while (typePathIndex < typePathLength) {
                var c = typePath[typePathIndex++]
                if (c == '[') {
                    output.put11(ARRAY_ELEMENT, 0)
                } else if (c == '.') {
                    output.put11(INNER_TYPE, 0)
                } else if (c == '*') {
                    output.put11(WILDCARD_BOUND, 0)
                } else if (c in '0'..'9') {
                    var typeArg = c.code - '0'.code
                    while (typePathIndex < typePathLength) {
                        c = typePath[typePathIndex++]
                        if (c in '0'..'9') {
                            typeArg = typeArg * 10 + c.code - '0'.code
                        } else if (c == ';') {
                            break
                        } else {
                            throw IllegalArgumentException()
                        }
                    }
                    output.put11(TYPE_ARGUMENT, typeArg)
                } else {
                    throw IllegalArgumentException()
                }
            }
            output.data[0] = (output.length / 2).toByte()
            return TypePath(output.data, 0)
        }

        /**
         * Puts the type_path JVMS structure corresponding to the given TypePath into the given
         * ByteVector.
         * 
         * @param typePath a TypePath instance, or null for empty paths.
         * @param output where the type path must be put.
         */
        fun put(typePath: TypePath?, output: ByteVector) {
            if (typePath == null) {
                output.putByte(0)
            } else {
                val length = typePath.typePathContainer[typePath.typePathOffset] * 2 + 1
                output.putByteArray(typePath.typePathContainer, typePath.typePathOffset, length)
            }
        }
    }
}