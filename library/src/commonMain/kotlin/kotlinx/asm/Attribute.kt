package kotlinx.asm

open class Attribute(val type: String) {
    private lateinit var cachedContent: ByteVector
    var nextAttribute: Attribute? = null

    open val isUnknown: Boolean get() = true
    open val isCodeAttribute: Boolean get() = false

    fun read(
        classReader: ClassReader,
        offset: Int,
        length: Int,
        charBuffer: CharArray?,
        codeAttributeOffset: Int,
        labels: Array<Label>?
    ): Attribute {
        val attribute = Attribute(type)
        attribute.cachedContent = ByteVector(classReader.readBytes(offset, length))
        return attribute
    }

    /**
     * Reads an attribute with the same [.type] as the given attribute. This method returns a
     * new [Attribute] object, corresponding to the 'length' bytes starting at 'offset', in the
     * given ClassReader.
     * 
     * @param attribute The attribute prototype that is used for reading.
     * @param classReader the class that contains the attribute to be read.
     * @param offset index of the first byte of the attribute's content in [ClassReader]. The 6
     * attribute header bytes (attribute_name_index and attribute_length) are not taken into
     * account here.
     * @param length the length of the attribute's content (excluding the 6 attribute header bytes).
     * @param charBuffer the buffer to be used to call the ClassReader methods requiring a
     * 'charBuffer' parameter.
     * @param codeAttributeOffset index of the first byte of content of the enclosing Code attribute
     * in [ClassReader], or -1 if the attribute to be read is not a Code attribute. The 6
     * attribute header bytes (attribute_name_index and attribute_length) are not taken into
     * account here.
     * @param labels the labels of the method's code, or null if the attribute to be read
     * is not a Code attribute. Labels defined in the attribute are added to this array, if not
     * already present.
     * @return a new [Attribute] object corresponding to the specified bytes.
     */
    fun read(
        attribute: Attribute,
        classReader: ClassReader,
        offset: Int,
        length: Int,
        charBuffer: CharArray?,
        codeAttributeOffset: Int,
        labels: Array<Label>?
    ): Attribute {
        return attribute.read(classReader, offset, length, charBuffer, codeAttributeOffset, labels)
    }

    /**
     * Returns the label corresponding to the given bytecode offset by calling [ ][ClassReader.readLabel]. This creates and adds the label to the given array if it is not already
     * present. Note that this created label may be a [Label] subclass instance, if the given
     * ClassReader overrides [ClassReader.readLabel]. Hence [.read] must not manually create [Label] instances.
     * 
     * @param classReader the class that contains the attribute to be read.
     * @param bytecodeOffset a bytecode offset in a method.
     * @param labels the already created labels, indexed by their offset. If a label already exists
     * for bytecodeOffset this method does not create a new one. Otherwise it stores the new label
     * in this array.
     * @return a label for the given bytecode offset.
     */
    fun readLabel(
        classReader: ClassReader, bytecodeOffset: Int, labels: Array<Label?>
    ): Label {
        return classReader.readLabel(bytecodeOffset, labels)
    }

    /**
     * Calls [.write] if it has not already been called and
     * returns its result or its (cached) previous result.
     * 
     * @param classWriter the class to which this attribute must be added. This parameter can be used
     * to add the items that corresponds to this attribute to the constant pool of this class.
     * @param code the bytecode of the method corresponding to this Code attribute, or null
     * if this attribute is not a Code attribute. Corresponds to the 'code' field of the Code
     * attribute.
     * @param codeLength the length of the bytecode of the method corresponding to this code
     * attribute, or 0 if this attribute is not a Code attribute. Corresponds to the 'code_length'
     * field of the Code attribute.
     * @param maxStack the maximum stack size of the method corresponding to this Code attribute, or
     * -1 if this attribute is not a Code attribute.
     * @param maxLocals the maximum number of local variables of the method corresponding to this code
     * attribute, or -1 if this attribute is not a Code attribute.
     * @return the byte array form of this attribute.
     */
    private fun maybeWrite(
        classWriter: ClassWriter?,
        code: ByteArray?,
        codeLength: Int,
        maxStack: Int,
        maxLocals: Int
    ): ByteVector {
        if (!::cachedContent.isInitialized) {
            cachedContent = write(classWriter, code, codeLength, maxStack, maxLocals)
        }
        return cachedContent
    }

    /**
     * Returns the byte array form of the content of this attribute. The 6 header bytes
     * (attribute_name_index and attribute_length) must *not* be added in the returned
     * ByteVector.
     * 
     * 
     * This method is only invoked once to compute the binary form of this attribute. Subsequent
     * changes to the attribute after it was written for the first time will not be considered.
     * 
     * @param classWriter the class to which this attribute must be added. This parameter can be used
     * to add the items that corresponds to this attribute to the constant pool of this class.
     * @param code the bytecode of the method corresponding to this Code attribute, or null
     * if this attribute is not a Code attribute. Corresponds to the 'code' field of the Code
     * attribute.
     * @param codeLength the length of the bytecode of the method corresponding to this code
     * attribute, or 0 if this attribute is not a Code attribute. Corresponds to the 'code_length'
     * field of the Code attribute.
     * @param maxStack the maximum stack size of the method corresponding to this Code attribute, or
     * -1 if this attribute is not a Code attribute.
     * @param maxLocals the maximum number of local variables of the method corresponding to this code
     * attribute, or -1 if this attribute is not a Code attribute.
     * @return the byte array form of this attribute.
     */
    protected open fun write(
        classWriter: ClassWriter?,
        code: ByteArray?,
        codeLength: Int,
        maxStack: Int,
        maxLocals: Int
    ): ByteVector {
        return cachedContent
    }

    /**
     * Returns the byte array form of the content of the given attribute. The 6 header bytes
     * (attribute_name_index and attribute_length) are *not* added in the returned byte array.
     * 
     * @param attribute The attribute that should be written.
     * @param classWriter the class to which this attribute must be added. This parameter can be used
     * to add the items that corresponds to this attribute to the constant pool of this class.
     * @param code the bytecode of the method corresponding to this Code attribute, or null
     * if this attribute is not a Code attribute. Corresponds to the 'code' field of the Code
     * attribute.
     * @param codeLength the length of the bytecode of the method corresponding to this code
     * attribute, or 0 if this attribute is not a Code attribute. Corresponds to the 'code_length'
     * field of the Code attribute.
     * @param maxStack the maximum stack size of the method corresponding to this Code attribute, or
     * -1 if this attribute is not a Code attribute.
     * @param maxLocals the maximum number of local variables of the method corresponding to this code
     * attribute, or -1 if this attribute is not a Code attribute.
     * @return the byte array form of this attribute.
     */
    fun write(
        attribute: Attribute,
        classWriter: ClassWriter?,
        code: ByteArray?,
        codeLength: Int,
        maxStack: Int,
        maxLocals: Int
    ): ByteArray {
        val content = attribute.maybeWrite(classWriter, code, codeLength, maxStack, maxLocals)
        val result = ByteArray(content.length)
        content.data.copyInto(result, endIndex = content.length)
        return result
    }

    /**
     * Returns the number of attributes of the attribute list that begins with this attribute.
     * 
     * @return the number of attributes of the attribute list that begins with this attribute.
     */
    fun getAttributeCount(): Int {
        var count = 0
        var attribute: Attribute? = this
        while (attribute != null) {
            count += 1
            attribute = attribute.nextAttribute
        }
        return count
    }

    /**
     * Returns the total size in bytes of all the attributes in the attribute list that begins with
     * this attribute. This size includes the 6 header bytes (attribute_name_index and
     * attribute_length) per attribute. Also adds the attribute type names to the constant pool.
     * 
     * @param symbolTable where the constants used in the attributes must be stored.
     * @return the size of all the attributes in this attribute list. This size includes the size of
     * the attribute headers.
     */
    fun computeAttributesSize(symbolTable: SymbolTable): Int {
        val code: ByteArray? = null
        val codeLength = 0
        val maxStack = -1
        val maxLocals = -1
        return computeAttributesSize(symbolTable, code, codeLength, maxStack, maxLocals)
    }

    /**
     * Returns the total size in bytes of all the attributes in the attribute list that begins with
     * this attribute. This size includes the 6 header bytes (attribute_name_index and
     * attribute_length) per attribute. Also adds the attribute type names to the constant pool.
     * 
     * @param symbolTable where the constants used in the attributes must be stored.
     * @param code the bytecode of the method corresponding to these Code attributes, or null if they are not Code attributes. Corresponds to the 'code' field of the Code
     * attribute.
     * @param codeLength the length of the bytecode of the method corresponding to these code
     * attributes, or 0 if they are not Code attributes. Corresponds to the 'code_length' field of
     * the Code attribute.
     * @param maxStack the maximum stack size of the method corresponding to these Code attributes, or
     * -1 if they are not Code attributes.
     * @param maxLocals the maximum number of local variables of the method corresponding to these
     * Code attributes, or -1 if they are not Code attribute.
     * @return the size of all the attributes in this attribute list. This size includes the size of
     * the attribute headers.
     */
    fun computeAttributesSize(
        symbolTable: SymbolTable,
        code: ByteArray?,
        codeLength: Int,
        maxStack: Int,
        maxLocals: Int
    ): Int {
        val classWriter: ClassWriter? = symbolTable.classWriter
        var size = 0
        var attribute: Attribute? = this
        while (attribute != null) {
            symbolTable.addConstantUtf8(attribute.type)
            size += 6 + attribute.maybeWrite(classWriter, code, codeLength, maxStack, maxLocals).length
            attribute = attribute.nextAttribute
        }
        return size
    }

    /**
     * Returns the total size in bytes of all the attributes that correspond to the given field,
     * method or class access flags and signature. This size includes the 6 header bytes
     * (attribute_name_index and attribute_length) per attribute. Also adds the attribute type names
     * to the constant pool.
     * 
     * @param symbolTable where the constants used in the attributes must be stored.
     * @param accessFlags some field, method or class access flags.
     * @param signatureIndex the constant pool index of a field, method of class signature.
     * @return the size of all the attributes in bytes. This size includes the size of the attribute
     * headers.
     */
    fun computeAttributesSize(
        symbolTable: SymbolTable, accessFlags: Int, signatureIndex: Int
    ): Int {
        var size = 0
        // Before Java 1.5, synthetic fields are represented with a Synthetic attribute.
        if ((accessFlags and Opcodes.ACC_SYNTHETIC) != 0
            && symbolTable.majorVersion < Opcodes.V1_5
        ) {
            // Synthetic attributes always use 6 bytes.
            symbolTable.addConstantUtf8(Constants.SYNTHETIC)
            size += 6
        }
        if (signatureIndex != 0) {
            // Signature attributes always use 8 bytes.
            symbolTable.addConstantUtf8(Constants.SIGNATURE)
            size += 8
        }
        // ACC_DEPRECATED is ASM specific, the ClassFile format uses a Deprecated attribute instead.
        if ((accessFlags and Opcodes.ACC_DEPRECATED) != 0) {
            // Deprecated attributes always use 6 bytes.
            symbolTable.addConstantUtf8(Constants.DEPRECATED)
            size += 6
        }
        return size
    }

    /**
     * Puts all the attributes of the attribute list that begins with this attribute, in the given
     * byte vector. This includes the 6 header bytes (attribute_name_index and attribute_length) per
     * attribute.
     * 
     * @param symbolTable where the constants used in the attributes must be stored.
     * @param output where the attributes must be written.
     */
    fun putAttributes(symbolTable: SymbolTable, output: ByteVector) {
        val code: ByteArray? = null
        val codeLength = 0
        val maxStack = -1
        val maxLocals = -1
        putAttributes(symbolTable, code, codeLength, maxStack, maxLocals, output)
    }

    /**
     * Puts all the attributes of the attribute list that begins with this attribute, in the given
     * byte vector. This includes the 6 header bytes (attribute_name_index and attribute_length) per
     * attribute.
     * 
     * @param symbolTable where the constants used in the attributes must be stored.
     * @param code the bytecode of the method corresponding to these Code attributes, or null if they are not Code attributes. Corresponds to the 'code' field of the Code
     * attribute.
     * @param codeLength the length of the bytecode of the method corresponding to these code
     * attributes, or 0 if they are not Code attributes. Corresponds to the 'code_length' field of
     * the Code attribute.
     * @param maxStack the maximum stack size of the method corresponding to these Code attributes, or
     * -1 if they are not Code attributes.
     * @param maxLocals the maximum number of local variables of the method corresponding to these
     * Code attributes, or -1 if they are not Code attribute.
     * @param output where the attributes must be written.
     */
    fun putAttributes(
        symbolTable: SymbolTable,
        code: ByteArray?,
        codeLength: Int,
        maxStack: Int,
        maxLocals: Int,
        output: ByteVector
    ) {
        val classWriter: ClassWriter? = symbolTable.classWriter
        var attribute: Attribute? = this
        while (attribute != null) {
            val attributeContent: ByteVector =
                attribute.maybeWrite(classWriter, code, codeLength, maxStack, maxLocals)
            // Put attribute_name_index and attribute_length.
            output.putShort(symbolTable.addConstantUtf8(attribute.type)).putInt(attributeContent.length)
            output.putByteArray(attributeContent.data, 0, attributeContent.length)
            attribute = attribute.nextAttribute
        }
    }

    /**
     * Puts all the attributes that correspond to the given field, method or class access flags and
     * signature, in the given byte vector. This includes the 6 header bytes (attribute_name_index and
     * attribute_length) per attribute.
     * 
     * @param symbolTable where the constants used in the attributes must be stored.
     * @param accessFlags some field, method or class access flags.
     * @param signatureIndex the constant pool index of a field, method of class signature.
     * @param output where the attributes must be written.
     */
    fun putAttributes(
        symbolTable: SymbolTable,
        accessFlags: Int,
        signatureIndex: Int,
        output: ByteVector
    ) {
        // Before Java 1.5, synthetic fields are represented with a Synthetic attribute.
        if ((accessFlags and Opcodes.ACC_SYNTHETIC) != 0
            && symbolTable.majorVersion < Opcodes.V1_5
        ) {
            output.putShort(symbolTable.addConstantUtf8(Constants.SYNTHETIC)).putInt(0)
        }
        if (signatureIndex != 0) {
            output
                .putShort(symbolTable.addConstantUtf8(Constants.SIGNATURE))
                .putInt(2)
                .putShort(signatureIndex)
        }
        if ((accessFlags and Opcodes.ACC_DEPRECATED) != 0) {
            output.putShort(symbolTable.addConstantUtf8(Constants.DEPRECATED)).putInt(0)
        }
    }

    /** A set of attribute prototypes (attributes with the same type are considered equal).  */
    class Set {
        private var size = 0
        private var data = arrayOfNulls<Attribute>(SIZE_INCREMENT)

        fun addAttributes(attributeList: Attribute?) {
            var attribute: Attribute? = attributeList
            while (attribute != null) {
                if (!contains(attribute)) {
                    add(attribute)
                }
                attribute = attribute.nextAttribute
            }
        }

        fun toArray(): Array<Attribute?> {
            return data.copyOf()
        }

        private fun contains(attribute: Attribute): Boolean {
            for (i in 0..<size) {
                if (data[i]?.type == attribute.type) {
                    return true
                }
            }
            return false
        }

        private fun add(attribute: Attribute?) {
            if (size >= data.size) {
                data = data.copyOf(data.size + SIZE_INCREMENT)
            }
            data[size++] = attribute
        }

        companion object {
            private const val SIZE_INCREMENT = 6
        }
    }
}