package kotlinx.asm

/**
 * Constructs a new field or method handle.
 *
 * @param tag the kind of field or method designated by this Handle. Must be {@link
 *     Opcodes#H_GETFIELD}, {@link Opcodes#H_GETSTATIC}, {@link Opcodes#H_PUTFIELD}, {@link
 *     Opcodes#H_PUTSTATIC}, {@link Opcodes#H_INVOKEVIRTUAL}, {@link Opcodes#H_INVOKESTATIC},
 *     {@link Opcodes#H_INVOKESPECIAL}, {@link Opcodes#H_NEWINVOKESPECIAL} or {@link
 *     Opcodes#H_INVOKEINTERFACE}.
 * @param owner the internal name of the class that owns the field or method designated by this
 *     handle (see {@link Type#getInternalName()}).
 * @param name the name of the field or method designated by this handle.
 * @param descriptor the descriptor of the field or method designated by this handle.
 * @param isInterface whether the owner is an interface or not.
 */
data class Handle(
    /**
     * The kind of field or method designated by this Handle. Should be {@link Opcodes#H_GETFIELD},
     * {@link Opcodes#H_GETSTATIC}, {@link Opcodes#H_PUTFIELD}, {@link Opcodes#H_PUTSTATIC}, {@link
     * Opcodes#H_INVOKEVIRTUAL}, {@link Opcodes#H_INVOKESTATIC}, {@link Opcodes#H_INVOKESPECIAL},
     * {@link Opcodes#H_NEWINVOKESPECIAL} or {@link Opcodes#H_INVOKEINTERFACE}.
     */
    val tag: Int,
    /** The internal name of the class that owns the field or method designated by this handle. */
    val owner: String,
    /** The name of the field or method designated by this handle. */
    val name: String,
    /** The descriptor of the field or method designated by this handle. */
    val descriptor: String,
    /** Whether the owner is an interface or not. */
    val isInterface: Boolean,
) {
    constructor(
        tag: Int,
        owner: String,
        name: String,
        descriptor: String
    ) : this(tag, owner, name, descriptor, tag == Opcodes.H_INVOKEINTERFACE)

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Handle) {
            return false
        }
        return tag == other.tag
                && isInterface == other.isInterface
                && owner == other.owner
                && name == other.name
                && descriptor == other.descriptor
    }

    override fun hashCode(): Int {
        return (tag
                + (if (isInterface) 64 else 0)
                + owner.hashCode() * name.hashCode() * descriptor.hashCode())
    }

    /**
     * Returns the textual representation of this handle. The textual representation is:
     *
     *
     *  * for a reference to a class: owner "." name descriptor " (" tag ")",
     *  * for a reference to an interface: owner "." name descriptor " (" tag " itf)".
     *
     */
    override fun toString(): String {
        return owner + '.' + name + descriptor + " (" + tag + (if (isInterface) " itf" else "") + ')'
    }
}
