package kotlinx.asm

class MethodWriter : MethodVisitor {


    // -----------------------------------------------------------------------------------------------
    // Utility methods
    // -----------------------------------------------------------------------------------------------

    /**
     * Returns whether the attributes of this method can be copied from the attributes of the given
     * method (assuming there is no method visitor between the given ClassReader and this
     * MethodWriter). This method should only be called just after this MethodWriter has been created,
     * and before any content is visited. It returns true if the attributes corresponding to the
     * constructor arguments (at most a Signature, an Exception, a Deprecated and a Synthetic
     * attribute) are the same as the corresponding attributes in the given method.
     *
     * @param source the source ClassReader from which the attributes of this method might be copied.
     * @param hasSyntheticAttribute whether the method_info JVMS structure from which the attributes
     * of this method might be copied contains a Synthetic attribute.
     * @param hasDeprecatedAttribute whether the method_info JVMS structure from which the attributes
     * of this method might be copied contains a Deprecated attribute.
     * @param descriptorIndex the descriptor_index field of the method_info JVMS structure from which
     * the attributes of this method might be copied.
     * @param signatureIndex the constant pool index contained in the Signature attribute of the
     * method_info JVMS structure from which the attributes of this method might be copied, or 0.
     * @param exceptionsOffset the offset in 'source.b' of the Exceptions attribute of the method_info
     * JVMS structure from which the attributes of this method might be copied, or 0.
     * @return whether the attributes of this method can be copied from the attributes of the
     * method_info JVMS structure in 'source.b', between 'methodInfoOffset' and 'methodInfoOffset'
     * + 'methodInfoLength'.
     */
    fun canCopyMethodAttributes(
        source: ClassReader,
        hasSyntheticAttribute: Boolean,
        hasDeprecatedAttribute: Boolean,
        descriptorIndex: Int,
        signatureIndex: Int,
        exceptionsOffset: Int
    ): Boolean = TODO()

    /**
     * Sets the source from which the attributes of this method will be copied.
     *
     * @param methodInfoOffset the offset in 'symbolTable.getSource()' of the method_info JVMS
     * structure from which the attributes of this method will be copied.
     * @param methodInfoLength the length in 'symbolTable.getSource()' of the method_info JVMS
     * structure from which the attributes of this method will be copied.
     */
    fun setMethodAttributesSource(methodInfoOffset: Int, methodInfoLength: Int) {
        TODO()
    }
}