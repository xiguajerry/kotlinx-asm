package kotlinx.asm

data class ConstantDynamic(
    /** The constant name (can be arbitrary). */
    val name: String,
    /** The constant type (must be a field descriptor). */
    val descriptor: String,
    /** The bootstrap method to use to compute the constant value at runtime. */
    val bootstrapMethod: Handle,
    /**
     * The arguments to pass to the bootstrap method, in order to compute the constant value at
     * runtime.
     */
    val bootstrapMethodArguments: Array<Any>
) {
    /**
     * Returns the number of arguments passed to the bootstrap method, in order to compute the value
     * of this constant.
     *
     * @return the number of arguments passed to the bootstrap method, in order to compute the value
     * of this constant.
     */
    val bootstrapMethodArgumentCount: Int
        get() {
            return bootstrapMethodArguments.size
        }

    /**
     * Returns the size of this constant.
     *
     * @return the size of this constant, i.e., 2 for {@code long} and {@code double}, 1 otherwise.
     */
    val size: Int
        get() {
            val firstCharOfDescriptor = descriptor[0]
            return if (firstCharOfDescriptor == 'J' || firstCharOfDescriptor == 'D') 2 else 1
        }

    /**
     * Returns an argument passed to the bootstrap method, in order to compute the value of this
     * constant.
     *
     * @param index an argument index, between 0 and [.getBootstrapMethodArgumentCount]
     * (exclusive).
     * @return the argument passed to the bootstrap method, with the given index.
     */
    fun getBootstrapMethodArgument(index: Int): Any {
        return bootstrapMethodArguments[index]
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ConstantDynamic) {
            return false
        }
        return name == other.name
                && descriptor == other.descriptor
                && bootstrapMethod == other.bootstrapMethod
                && bootstrapMethodArguments.contentEquals(other.bootstrapMethodArguments)
    }

    override fun hashCode(): Int {
        return (name.hashCode()
                xor descriptor.hashCode().rotateLeft(8)
                xor bootstrapMethod.hashCode().rotateLeft(16)
                xor bootstrapMethodArguments.contentHashCode().rotateLeft(24))
    }

    override fun toString(): String {
        return (name
                + " : "
                + descriptor
                + ' '
                + bootstrapMethod
                + ' '
                + bootstrapMethodArguments.contentToString())
    }
}
