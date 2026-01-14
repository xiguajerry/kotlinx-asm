package kotlinx.asm

import kotlin.math.max

sealed class Type(
    sort0: kotlin.Int,
    val internalName: String
) {
    val sort = if (sort0 == INTERNAL) OBJECT else sort0
    abstract val className: String
    open val descriptor get() = internalName
    open val size: kotlin.Int = 1

    companion object Constants {
        /** The sort of the `void` type. See [.getSort].  */
        const val VOID = 0

        /** The sort of the `boolean` type. See [.getSort].  */
        const val BOOLEAN = 1

        /** The sort of the `char` type. See [.getSort].  */
        const val CHAR = 2

        /** The sort of the `byte` type. See [.getSort].  */
        const val BYTE = 3

        /** The sort of the `short` type. See [.getSort].  */
        const val SHORT = 4

        /** The sort of the `int` type. See [.getSort].  */
        const val INT = 5

        /** The sort of the `float` type. See [.getSort].  */
        const val FLOAT = 6

        /** The sort of the `long` type. See [.getSort].  */
        const val LONG = 7

        /** The sort of the `double` type. See [.getSort].  */
        const val DOUBLE = 8

        /** The sort of array reference types. See [.getSort].  */
        const val ARRAY = 9

        /** The sort of object reference types. See [.getSort].  */
        const val OBJECT = 10

        /** The sort of method types. See [.getSort].  */
        const val METHOD = 11

        /** The (private) sort of object reference types represented with an internal name.  */
        private const val INTERNAL = 12

        /** The `void` type.  */
        val VOID_TYPE: Type = Void

        /** The `boolean` type.  */
        val BOOLEAN_TYPE: Type = Boolean

        /** The `char` type.  */
        val CHAR_TYPE: Type = Char

        /** The `byte` type.  */
        val BYTE_TYPE: Type = Byte

        /** The `short` type.  */
        val SHORT_TYPE: Type = Short

        /** The `int` type.  */
        val INT_TYPE: Type = Int

        /** The `float` type.  */
        val FLOAT_TYPE: Type = Float

        /** The `long` type.  */
        val LONG_TYPE: Type = Long

        /** The `double` type.  */
        val DOUBLE_TYPE: Type = Double

        fun getType(descriptorBuffer: String): Type = when (descriptorBuffer.first()) {
            'V' -> VOID_TYPE
            'Z' -> BOOLEAN_TYPE
            'C' -> CHAR_TYPE
            'B' -> BYTE_TYPE
            'S' -> SHORT_TYPE
            'I' -> INT_TYPE
            'F' -> FLOAT_TYPE
            'J' -> LONG_TYPE
            'D' -> DOUBLE_TYPE
            '[' -> Array(
                descriptorBuffer
            )

            'L' -> Object(
                descriptorBuffer.removePrefix("L").removeSuffix(";")
            )

            '(' -> Method(
                descriptorBuffer
            )

            else -> throw IllegalArgumentException("Invalid descriptor: $descriptorBuffer")
        }

        fun getObjectType(internalName: String): Type =
            if (internalName[0] == '[') Array(internalName)
            else Internal(internalName)

        fun getMethodType(internalName: String): Method = getType(internalName) as Method

        fun getMethodType(
            returnType: Type,
            vararg argumentTypes: Type
        ): Method {
            return getType(
                getMethodDescriptor(
                    returnType,
                    *argumentTypes
                )
            ) as Method
        }

        fun getMethodDescriptor(
            returnType: Type,
            vararg argumentTypes: Type
        ): String {
            return argumentTypes.joinToString("", "(", ")") { it.descriptor } + returnType.descriptor
        }

        fun getArgumentTypes(desc: String) = getMethodType(desc).argumentTypes

        fun getReturnType(desc: String) = getMethodType(desc).returnType

        fun getArgumentsAndReturnSizes(desc: String) = getMethodType(desc).argumentsAndReturnSizes
    }

    class Method(internalName: String) : Type(METHOD, internalName) {
        override val className: String
            get() = TODO("Not yet implemented")

        val argumentCount: kotlin.Int = run {
            var argumentCount = 0
            // Skip the first character, which is always a '('.
            var currentOffset = 1
            // Parse the argument types, one at each loop iteration.
            while (internalName[currentOffset] != ')') {
                while (internalName[currentOffset] == '[') {
                    currentOffset++
                }
                if (internalName[currentOffset++] == 'L') {
                    // Skip the argument descriptor content.
                    val semiColumnOffset = internalName.indexOf(';', currentOffset)
                    currentOffset = max(currentOffset, semiColumnOffset + 1)
                }
                ++argumentCount
            }
            argumentCount
        }

        val argumentTypes: kotlin.Array<Type>
            get() {
                // First step: compute the number of argument types in methodDescriptor.
                val numArgumentTypes = argumentCount

                val sequence = sequence {
                    // Skip the first character, which is always a '('.
                    var currentOffset = 1
                    while (internalName[currentOffset] != ')') {
                        val currentArgumentTypeOffset = currentOffset
                        while (internalName[currentOffset] == '[') {
                            currentOffset++
                        }
                        if (internalName[currentOffset++] == 'L') {
                            // Skip the argument descriptor content.
                            val semiColumnOffset = internalName.indexOf(';', currentOffset)
                            currentOffset = max(currentOffset, semiColumnOffset + 1)
                        }
                        yield(getType(internalName.substring(currentArgumentTypeOffset, currentOffset)))
                    }
                }.iterator()

                // Second step: create a Type instance for each argument type.
                val argumentTypes = Array(numArgumentTypes) {
                    sequence.next()
                }

                return argumentTypes
            }

        val argumentsAndReturnSizes: kotlin.Int = run {
            var argumentsSize = 1
            // Skip the first character, which is always a '('.
            var currentOffset = 1
            var currentChar = internalName[currentOffset].code
            // Parse the argument types and compute their size, one at a each loop iteration.
            while (currentChar != ')'.code) {
                if (currentChar == 'J'.code || currentChar == 'D'.code) {
                    currentOffset++
                    argumentsSize += 2
                } else {
                    while (internalName[currentOffset] == '[') {
                        currentOffset++
                    }
                    if (internalName[currentOffset++] == 'L') {
                        // Skip the argument descriptor content.
                        val semiColumnOffset = internalName.indexOf(';', currentOffset)
                        currentOffset = max(currentOffset, semiColumnOffset + 1)
                    }
                    argumentsSize += 1
                }
                currentChar = internalName[currentOffset].code
            }
            currentChar = internalName[currentOffset + 1].code
            if (currentChar == 'V'.code) {
                argumentsSize shl 2
            } else {
                val returnSize = if (currentChar == 'J'.code || currentChar == 'D'.code) 2 else 1
                argumentsSize shl 2 or returnSize
            }
        }

        val returnTypeOffset: kotlin.Int = run {
            // Skip the first character, which is always a '('.
            var currentOffset = 1
            // Skip the argument types, one at each loop iteration.
            while (internalName[currentOffset] != ')') {
                while (internalName[currentOffset] == '[') {
                    currentOffset++
                }
                if (internalName[currentOffset++] == 'L') {
                    // Skip the argument descriptor content.
                    val semiColumnOffset = internalName.indexOf(';', currentOffset)
                    currentOffset = max(currentOffset, semiColumnOffset + 1)
                }
            }
            currentOffset + 1
        }

        val returnType: Type = getType(internalName.substring(returnTypeOffset))
    }

    class Object(internalName: String) : Type(OBJECT, internalName) {
        override val className = internalName.replace("/", ".")
        override val descriptor = "L$internalName;"
    }

    class Internal(internalName: String) : Type(INTERNAL, internalName) {
        override val className = internalName.replace("/", ".")
        override val descriptor = "L$internalName;"
    }

    class Array(internalName: String) : Type(ARRAY, internalName) {
        val dimensions = internalName.lastIndexOf('[') + 1
        val elementType: Type get() = getType(internalName.substring(dimensions))
        override val className = elementType.className + "[]".repeat(dimensions)
    }

    object Void : Type(VOID, "V") {
        override val className = "void"
    }

    object Boolean : Type(BOOLEAN, "Z") {
        override val className = "boolean"
    }

    object Char : Type(CHAR, "C") {
        override val className = "char"
    }

    object Byte : Type(BYTE, "B") {
        override val className = "byte"
    }

    object Short : Type(SHORT, "S") {
        override val className = "short"
    }

    object Int : Type(INT, "I") {
        override val className = "int"
    }

    object Float : Type(FLOAT, "F") {
        override val className = "float"
    }

    object Long : Type(LONG, "J") {
        override val className = "long"
        override val size = 2
    }

    object Double : Type(DOUBLE, "D") {
        override val className = "double"
        override val size = 2
    }
}