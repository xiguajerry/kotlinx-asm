package kotlinx.asm

/**
 * The constant pool entries, the BootstrapMethods attribute entries and the (ASM specific) type
 * table entries of a class.
 *
 * @author Eric Bruneton
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4">JVMS
 *     4.4</a>
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.23">JVMS
 *     4.7.23</a>
 */
class SymbolTable(
    /**
     * The ClassWriter to which this SymbolTable belongs. This is only used to get access to {@link
     * ClassWriter#getCommonSuperClass} and to serialize custom attributes with {@link
     * Attribute#write}.
     */
    val classWriter: ClassWriter,
    /**
     * The ClassReader from which this SymbolTable was constructed, or {@literal null} if it was
     * constructed from scratch.
     */
    private val classReader: ClassReader? = null
) {
    /** The major version number of the class to which this symbol table belongs.  */
    var majorVersion = 0; private set

    /** The internal name of the class to which this symbol table belongs.  */
    private lateinit var className: String

    /**
     * The total number of [Entry] instances in [entries]. This includes entries that are
     * accessible (recursively) via [Entry.next].
     */
    private var entryCount = 0

    /**
     * A hash set of all the entries in this SymbolTable (this includes the constant pool entries, the
     * bootstrap method entries and the type table entries). Each [Entry] instance is stored at
     * the array index given by its hash code modulo the array size. If several entries must be stored
     * at the same array index, they are linked together via their [Entry.next] field. The
     * factory methods of this class make sure that this table does not contain duplicated entries.
     */
    private var entries: Array<Entry?>

    /**
     * The number of constant pool items in [.constantPool], plus 1. The first constant pool
     * item has index 1, and long and double items count for two items.
     */
    private var constantPoolCount = 0

    /**
     * The content of the ClassFile's constant_pool JVMS structure corresponding to this SymbolTable.
     * The ClassFile's constant_pool_count field is *not* included.
     */
    private val constantPool: ByteVector


    /**
     * The number of bootstrap methods in [.bootstrapMethods]. Corresponds to the
     * BootstrapMethods_attribute's num_bootstrap_methods field value.
     */
    private var bootstrapMethodCount = 0

    /**
     * The content of the BootstrapMethods attribute 'bootstrap_methods' array corresponding to this
     * SymbolTable. Note that the first 6 bytes of the BootstrapMethods_attribute, and its
     * num_bootstrap_methods field, are *not* included.
     */
    private var bootstrapMethods: ByteVector? = null

    /**
     * The actual number of elements in {@link #typeTable}. These elements are stored from index 0 to
     * typeCount (excluded). The other array entries are empty.
     */
    private var typeCount = 0

    /**
     * An ASM specific type table used to temporarily store internal names that will not necessarily
     * be stored in the constant pool. This type table is used by the control flow and data flow
     * analysis algorithm used to compute stack map frames from scratch. This array stores {@link
     * Symbol#TYPE_TAG}, {@link Symbol#UNINITIALIZED_TYPE_TAG},{@link
     * Symbol#FORWARD_UNINITIALIZED_TYPE_TAG} and {@link Symbol#MERGED_TYPE_TAG} entries. The type
     * symbol at index {@code i} has its {@link Symbol#index} equal to {@code i} (and vice versa).
     */
    private lateinit var typeTable: Array<Entry?>

    /**
     * The actual number of {@link LabelEntry} in {@link #labelTable}. These elements are stored from
     * index 0 to labelCount (excluded). The other array entries are empty. These label entries are
     * also stored in the {@link #labelEntries} hash set.
     */
    private var labelCount = 0

    /**
     * The labels corresponding to the "forward uninitialized" types in the ASM specific {@link
     * typeTable} (see {@link Symbol#FORWARD_UNINITIALIZED_TYPE_TAG}). The label entry at index {@code
     * i} has its {@link LabelEntry#index} equal to {@code i} (and vice versa).
     */
    private lateinit var labelTable: Array<LabelEntry?>

    /**
     * A hash set of all the {@link LabelEntry} elements in the {@link #labelTable}. Each {@link
     * LabelEntry} instance is stored at the array index given by its hash code modulo the array size.
     * If several entries must be stored at the same array index, they are linked together via their
     * {@link LabelEntry#next} field. The {@link #getOrAddLabelEntry(Label)} method ensures that this
     * table does not contain duplicated entries.
     */
    private lateinit var labelEntries: Array<LabelEntry?>

    init {
        if (classReader != null) {
            val classReader = classReader
            // Copy the constant pool binary content.
            val inputBytes = classReader.classFileBuffer
            val constantPoolOffset = classReader.getItem(1) - 1
            val constantPoolLength = classReader.header - constantPoolOffset
            constantPoolCount = classReader.itemCount
            constantPool = ByteVector(constantPoolLength)
            constantPool.putByteArray(inputBytes, constantPoolOffset, constantPoolLength)


            // Add the constant pool items in the symbol table entries. Reserve enough space in 'entries' to
            // avoid too many hash set collisions (entries is not dynamically resized by the addConstant*
            // method calls below), and to account for bootstrap method entries.
            entries = arrayOfNulls(constantPoolCount * 2)
            val charBuffer = CharArray(classReader.maxStringLength)
            var hasBootstrapMethods = false
            var itemIndex = 1
            while (itemIndex < constantPoolCount) {
                val itemOffset = classReader.getItem(itemIndex)
                val itemTag = inputBytes[itemOffset - 1].toInt()
                val nameAndTypeItemOffset: Int
                when (itemTag) {
                    Symbol.CONSTANT_FIELDREF_TAG,
                    Symbol.CONSTANT_METHODREF_TAG,
                    Symbol.CONSTANT_INTERFACE_METHODREF_TAG -> {
                        nameAndTypeItemOffset =
                            classReader.getItem(classReader.readUnsignedShort(itemOffset + 2))
                        addConstantMemberReference(
                            itemIndex,
                            itemTag,
                            classReader.readClass(itemOffset, charBuffer),
                            classReader.readUTF8(nameAndTypeItemOffset, charBuffer),
                            classReader.readUTF8(nameAndTypeItemOffset + 2, charBuffer)
                        )
                    }

                    Symbol.CONSTANT_INTEGER_TAG, Symbol.CONSTANT_FLOAT_TAG -> addConstantIntegerOrFloat(
                        itemIndex,
                        itemTag,
                        classReader.readInt(itemOffset)
                    )

                    Symbol.CONSTANT_NAME_AND_TYPE_TAG -> addConstantNameAndType(
                        itemIndex,
                        classReader.readUTF8(itemOffset, charBuffer),
                        classReader.readUTF8(itemOffset + 2, charBuffer)
                    )

                    Symbol.CONSTANT_LONG_TAG, Symbol.CONSTANT_DOUBLE_TAG -> addConstantLongOrDouble(
                        itemIndex,
                        itemTag,
                        classReader.readLong(itemOffset)
                    )

                    Symbol.CONSTANT_UTF8_TAG -> addConstantUtf8(
                        itemIndex, classReader.readUtf(itemIndex, charBuffer))
                    Symbol.CONSTANT_METHOD_HANDLE_TAG -> {
                        val memberRefItemOffset =
                            classReader.getItem(classReader.readUnsignedShort(itemOffset + 1))
                        nameAndTypeItemOffset =
                            classReader.getItem(classReader.readUnsignedShort(memberRefItemOffset + 2))
                        addConstantMethodHandle(
                            itemIndex,
                            classReader.readByte(itemOffset),
                            classReader.readClass(memberRefItemOffset, charBuffer),
                            classReader.readUTF8(nameAndTypeItemOffset, charBuffer),
                            classReader.readUTF8(nameAndTypeItemOffset + 2, charBuffer),
                            classReader.readByte(memberRefItemOffset - 1)
                                    == Symbol.CONSTANT_INTERFACE_METHODREF_TAG
                        )
                    }

                    Symbol.CONSTANT_DYNAMIC_TAG, Symbol.CONSTANT_INVOKE_DYNAMIC_TAG -> {
                        hasBootstrapMethods = true
                        nameAndTypeItemOffset =
                            classReader.getItem(classReader.readUnsignedShort(itemOffset + 2))
                        addConstantDynamicOrInvokeDynamicReference(
                            itemTag,
                            itemIndex,
                            classReader.readUTF8(nameAndTypeItemOffset, charBuffer),
                            classReader.readUTF8(nameAndTypeItemOffset + 2, charBuffer),
                            classReader.readUnsignedShort(itemOffset)
                        )
                    }

                    Symbol.CONSTANT_STRING_TAG, Symbol.CONSTANT_CLASS_TAG, Symbol.CONSTANT_METHOD_TYPE_TAG, Symbol.CONSTANT_MODULE_TAG, Symbol.CONSTANT_PACKAGE_TAG -> addConstantUtf8Reference(
                        itemIndex, itemTag, classReader.readUTF8(itemOffset, charBuffer)
                    )

                    else -> throw IllegalArgumentException()
                }
                itemIndex +=
                    if (itemTag == Symbol.CONSTANT_LONG_TAG || itemTag == Symbol.CONSTANT_DOUBLE_TAG) 2 else 1
            }

            // Copy the BootstrapMethods, if any.
            if (hasBootstrapMethods) {
                copyBootstrapMethods(classReader, charBuffer)
            }
        }
        else {
            this.entries = arrayOfNulls(256)
            this.constantPoolCount = 1
            this.constantPool = ByteVector()
        }
    }

    /**
     * Read the BootstrapMethods 'bootstrap_methods' array binary content and add them as entries of
     * the SymbolTable.
     *
     * @param classReader the ClassReader whose bootstrap methods must be copied to initialize the
     * SymbolTable.
     * @param charBuffer a buffer used to read strings in the constant pool.
     */
    private fun copyBootstrapMethods(classReader: ClassReader, charBuffer: CharArray) {
        // Find attributOffset of the 'bootstrap_methods' array.
        val inputBytes = classReader.classFileBuffer
        var currentAttributeOffset = classReader.getFirstAttributeOffset()
        for (i in classReader.readUnsignedShort(currentAttributeOffset - 2) downTo 1) {
            val attributeName = classReader.readUTF8(currentAttributeOffset, charBuffer)
            if (Constants.BOOTSTRAP_METHODS == attributeName) {
                bootstrapMethodCount = classReader.readUnsignedShort(currentAttributeOffset + 6)
                break
            }
            currentAttributeOffset += 6 + classReader.readInt(currentAttributeOffset + 2)
        }
        if (bootstrapMethodCount > 0) {
            // Compute the offset and the length of the BootstrapMethods 'bootstrap_methods' array.
            val bootstrapMethodsOffset = currentAttributeOffset + 8
            val bootstrapMethodsLength = classReader.readInt(currentAttributeOffset + 2) - 2
            bootstrapMethods = ByteVector(bootstrapMethodsLength)
            bootstrapMethods?.putByteArray(inputBytes, bootstrapMethodsOffset, bootstrapMethodsLength)

            // Add each bootstrap method in the symbol table entries.
            var currentOffset = bootstrapMethodsOffset
            repeat(bootstrapMethodCount) {
                val offset = currentOffset - bootstrapMethodsOffset
                val bootstrapMethodRef = classReader.readUnsignedShort(currentOffset)
                currentOffset += 2
                var numBootstrapArguments = classReader.readUnsignedShort(currentOffset)
                currentOffset += 2
                var hashCode = classReader.readConst(bootstrapMethodRef, charBuffer).hashCode()
                while (numBootstrapArguments-- > 0) {
                    val bootstrapArgument = classReader.readUnsignedShort(currentOffset)
                    currentOffset += 2
                    hashCode = hashCode xor classReader.readConst(bootstrapArgument, charBuffer).hashCode()
                }
                add(Entry(it, Symbol.BOOTSTRAP_METHOD_TAG, offset.toLong(), hashCode and 0x7FFFFFFF))
            }
        }
    }


    /**
     * Returns the internal name of the class to which this symbol table belongs.
     *
     * @return the internal name of the class to which this symbol table belongs.
     */
    fun getClassName(): String {
        return className
    }

    /**
     * Sets the major version and the name of the class to which this symbol table belongs. Also adds
     * the class name to the constant pool.
     *
     * @param majorVersion a major ClassFile version number.
     * @param className an internal class name.
     * @return the constant pool index of a new or already existing Symbol with the given class name.
     */
    fun setMajorVersionAndClassName(majorVersion: Int, className: String): Int {
        this.majorVersion = majorVersion
        this.className = className
        return addConstantClass(className).index
    }

    /**
     * Returns the number of items in this symbol table's constant_pool array (plus 1).
     *
     * @return the number of items in this symbol table's constant_pool array (plus 1).
     */
    fun getConstantPoolCount(): Int {
        return constantPoolCount
    }

    /**
     * Returns the length in bytes of this symbol table's constant_pool array.
     *
     * @return the length in bytes of this symbol table's constant_pool array.
     */
    fun getConstantPoolLength(): Int {
        return constantPool.length
    }

    /**
     * Puts this symbol table's constant_pool array in the given ByteVector, preceded by the
     * constant_pool_count value.
     *
     * @param output where the JVMS ClassFile's constant_pool array must be put.
     */
    fun putConstantPool(output: ByteVector) {
        output.putShort(constantPoolCount).putByteArray(constantPool.data, 0, constantPool.length)
    }

    /**
     * Returns the size in bytes of this symbol table's BootstrapMethods attribute. Also adds the
     * attribute name in the constant pool.
     *
     * @return the size in bytes of this symbol table's BootstrapMethods attribute.
     */
    fun computeBootstrapMethodsSize(): Int {
        return bootstrapMethods?.let {
            addConstantUtf8(Constants.BOOTSTRAP_METHODS)
            8 + it.length
        } ?: 0
    }

    /**
     * Puts this symbol table's BootstrapMethods attribute in the given ByteVector. This includes the
     * 6 attribute header bytes and the num_bootstrap_methods value.
     *
     * @param output where the JVMS BootstrapMethods attribute must be put.
     */
    fun putBootstrapMethods(output: ByteVector) {
        bootstrapMethods?.let {
            output.putShort(addConstantUtf8(Constants.BOOTSTRAP_METHODS))
                .putInt(it.length + 2)
                .putShort(bootstrapMethodCount)
                .putByteArray(it.data, 0, it.length)
        }
    }


    // -----------------------------------------------------------------------------------------------
    // Generic symbol table entries management.
    // -----------------------------------------------------------------------------------------------
    /**
     * Returns the list of entries which can potentially have the given hash code.
     *
     * @param hashCode a [Entry.hashCode] value.
     * @return the list of entries which can potentially have the given hash code. The list is stored
     * via the [Entry.next] field.
     */
    private fun get(hashCode: Int): Entry? {
        return entries[hashCode % entries.size]
    }

    /**
     * Puts the given entry in the [.entries] hash set. This method does *not* check
     * whether [.entries] already contains a similar entry or not. [.entries] is resized
     * if necessary to avoid hash collisions (multiple entries needing to be stored at the same [ ][.entries] array index) as much as possible, with reasonable memory usage.
     *
     * @param entry an Entry (which must not already be contained in [.entries]).
     * @return the given entry
     */
    private fun put(entry: Entry): Entry {
        if (entryCount > (entries.size * 3) / 4) {
            val currentCapacity = entries.size
            val newCapacity = currentCapacity * 2 + 1
            val newEntries = arrayOfNulls<Entry>(newCapacity)
            for (i in currentCapacity - 1 downTo 0) {
                var currentEntry = entries[i]
                while (currentEntry != null) {
                    val newCurrentEntryIndex = currentEntry.hashCode % newCapacity
                    val nextEntry = currentEntry.next
                    currentEntry.next = newEntries[newCurrentEntryIndex]
                    newEntries[newCurrentEntryIndex] = currentEntry
                    currentEntry = nextEntry
                }
            }
            entries = newEntries
        }
        entryCount++
        val index = entry.hashCode % entries.size
        entry.next = entries[index]
        return entry.also { entries[index] = it }
    }

    /**
     * Adds the given entry in the [.entries] hash set. This method does *not* check
     * whether [.entries] already contains a similar entry or not, and does *not* resize
     * [.entries] if necessary.
     *
     * @param entry an Entry (which must not already be contained in [.entries]).
     */
    private fun add(entry: Entry) {
        entryCount++
        val index = entry.hashCode % entries.size
        entry.next = entries[index]
        entries[index] = entry
    }


    /**
     * Adds a number or string constant to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param value the value of the constant to be added to the constant pool. This parameter must be
     *     an {@link Integer}, {@link Byte}, {@link Character}, {@link Short}, {@link Boolean}, {@link
     *     Float}, {@link Long}, {@link Double}, {@link String}, {@link Type} or {@link Handle}.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstant(value: Any?): Symbol {
        when (value) {
            is Int -> {
                return addConstantInteger(value)
            }

            is Byte -> {
                return addConstantInteger(value.toInt())
            }

            is Char -> {
                return addConstantInteger(value.code)
            }

            is Short -> {
                return addConstantInteger(value.toInt())
            }

            is Boolean -> {
                return addConstantInteger(if (value) 1 else 0)
            }

            is Float -> {
                return addConstantFloat(value)
            }

            is Long -> {
                return addConstantLong(value)
            }

            is Double -> {
                return addConstantDouble(value)
            }

            is String -> {
                return addConstantString(value)
            }

            is Type -> {
                val typeSort = value.sort
                return when (typeSort) {
                    Type.OBJECT -> {
                        addConstantClass(value.internalName)
                    }

                    Type.METHOD -> {
                        addConstantMethodType(value.descriptor)
                    }

                    else -> {
                        // type is a primitive or array type.
                        addConstantClass(value.descriptor)
                    }
                }
            }

            is Handle -> {
                return addConstantMethodHandle(
                    value.tag,
                    value.owner,
                    value.name,
                    value.descriptor,
                    value.isInterface
                )
            }

            is ConstantDynamic -> {
                return addConstantDynamic(
                    value.name,
                    value.descriptor,
                    value.bootstrapMethod,
                    value.bootstrapMethodArguments
                )
            }

            else -> {
                throw IllegalArgumentException("value $value")
            }
        }
    }

    /**
     * Adds a CONSTANT_Class_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param value the internal name of a class.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantClass(value: String): Symbol {
        return addConstantUtf8Reference(Symbol.CONSTANT_CLASS_TAG, value)
    }

    /**
     * Adds a CONSTANT_Fieldref_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param owner the internal name of a class.
     * @param name a field name.
     * @param descriptor a field descriptor.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantFieldref(owner: String, name: String, descriptor: String): Symbol {
        return addConstantMemberReference(Symbol.CONSTANT_FIELDREF_TAG, owner, name, descriptor)
    }

    /**
     * Adds a CONSTANT_Methodref_info or CONSTANT_InterfaceMethodref_info to the constant pool of this
     * symbol table. Does nothing if the constant pool already contains a similar item.
     *
     * @param owner the internal name of a class.
     * @param name a method name.
     * @param descriptor a method descriptor.
     * @param isInterface whether owner is an interface or not.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantMethodref(
        owner: String, name: String, descriptor: String, isInterface: Boolean
    ): Symbol {
        val tag = if (isInterface) Symbol.CONSTANT_INTERFACE_METHODREF_TAG else Symbol.CONSTANT_METHODREF_TAG
        return addConstantMemberReference(tag, owner, name, descriptor)
    }

    /**
     * Adds a CONSTANT_Fieldref_info, CONSTANT_Methodref_info or CONSTANT_InterfaceMethodref_info to
     * the constant pool of this symbol table. Does nothing if the constant pool already contains a
     * similar item.
     *
     * @param tag one of [Symbol.CONSTANT_FIELDREF_TAG], [Symbol.CONSTANT_METHODREF_TAG]
     * or [Symbol.CONSTANT_INTERFACE_METHODREF_TAG].
     * @param owner the internal name of a class.
     * @param name a field or method name.
     * @param descriptor a field or method descriptor.
     * @return a new or already existing Symbol with the given value.
     */
    private fun addConstantMemberReference(
        tag: Int, owner: String, name: String, descriptor: String
    ): Entry {
        val hashCode = hash(tag, owner, name, descriptor)
        var entry = get(hashCode)
        while (entry != null) {
            if (entry.tag == tag && entry.hashCode == hashCode && entry.owner.equals(owner)
                && entry.name.equals(name)
                && entry.value.equals(descriptor)
            ) {
                return entry
            }
            entry = entry.next
        }
        constantPool.put122(
            tag, addConstantClass(owner).index, addConstantNameAndType(name, descriptor)
        )
        return put(Entry(constantPoolCount++, tag, owner, name, descriptor, 0, hashCode))
    }

    /**
     * Adds a new CONSTANT_Fieldref_info, CONSTANT_Methodref_info or CONSTANT_InterfaceMethodref_info
     * to the constant pool of this symbol table.
     *
     * @param index the constant pool index of the new Symbol.
     * @param tag one of [Symbol.CONSTANT_FIELDREF_TAG], [Symbol.CONSTANT_METHODREF_TAG]
     * or [Symbol.CONSTANT_INTERFACE_METHODREF_TAG].
     * @param owner the internal name of a class.
     * @param name a field or method name.
     * @param descriptor a field or method descriptor.
     */
    private fun addConstantMemberReference(
        index: Int,
        tag: Int,
        owner: String,
        name: String,
        descriptor: String
    ) {
        add(Entry(index, tag, owner, name, descriptor, 0, hash(tag, owner, name, descriptor)))
    }

    /**
     * Adds a CONSTANT_String_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param value a string.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantString(value: String): Symbol {
        return addConstantUtf8Reference(Symbol.CONSTANT_STRING_TAG, value)
    }

    /**
     * Adds a CONSTANT_Integer_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param value an int.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantInteger(value: Int): Symbol {
        return addConstantIntegerOrFloat(Symbol.CONSTANT_INTEGER_TAG, value)
    }

    /**
     * Adds a CONSTANT_Float_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param value a float.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantFloat(value: Float): Symbol {
        return addConstantIntegerOrFloat(Symbol.CONSTANT_FLOAT_TAG, value.toRawBits())
    }

    /**
     * Adds a CONSTANT_Integer_info or CONSTANT_Float_info to the constant pool of this symbol table.
     * Does nothing if the constant pool already contains a similar item.
     *
     * @param tag one of [Symbol.CONSTANT_INTEGER_TAG] or [Symbol.CONSTANT_FLOAT_TAG].
     * @param value an int or float.
     * @return a constant pool constant with the given tag and primitive values.
     */
    private fun addConstantIntegerOrFloat(tag: Int, value: Int): Symbol {
        val hashCode = hash(tag, value)
        var entry = get(hashCode)
        while (entry != null) {
            if (entry.tag == tag && entry.hashCode == hashCode && entry.data == value.toLong()) {
                return entry
            }
            entry = entry.next
        }
        constantPool.putByte(tag).putInt(value)
        return put(Entry(constantPoolCount++, tag, value.toLong(), hashCode))
    }

    /**
     * Adds a new CONSTANT_Integer_info or CONSTANT_Float_info to the constant pool of this symbol
     * table.
     *
     * @param index the constant pool index of the new Symbol.
     * @param tag one of [Symbol.CONSTANT_INTEGER_TAG] or [Symbol.CONSTANT_FLOAT_TAG].
     * @param value an int or float.
     */
    private fun addConstantIntegerOrFloat(index: Int, tag: Int, value: Int) {
        add(Entry(index, tag, value.toLong(), hash(tag, value)))
    }

    /**
     * Adds a CONSTANT_Long_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param value a long.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantLong(value: Long): Symbol {
        return addConstantLongOrDouble(Symbol.CONSTANT_LONG_TAG, value)
    }

    /**
     * Adds a CONSTANT_Double_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param value a double.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantDouble(value: Double): Symbol {
        return addConstantLongOrDouble(Symbol.CONSTANT_DOUBLE_TAG, value.toRawBits())
    }

    /**
     * Adds a CONSTANT_Long_info or CONSTANT_Double_info to the constant pool of this symbol table.
     * Does nothing if the constant pool already contains a similar item.
     *
     * @param tag one of [Symbol.CONSTANT_LONG_TAG] or [Symbol.CONSTANT_DOUBLE_TAG].
     * @param value a long or double.
     * @return a constant pool constant with the given tag and primitive values.
     */
    private fun addConstantLongOrDouble(tag: Int, value: Long): Symbol {
        val hashCode = hash(tag, value)
        var entry = get(hashCode)
        while (entry != null) {
            if (entry.tag == tag && entry.hashCode == hashCode && entry.data == value) {
                return entry
            }
            entry = entry.next
        }
        val index = constantPoolCount
        constantPool.putByte(tag).putLong(value)
        constantPoolCount += 2
        return put(Entry(index, tag, value, hashCode))
    }

    /**
     * Adds a new CONSTANT_Long_info or CONSTANT_Double_info to the constant pool of this symbol
     * table.
     *
     * @param index the constant pool index of the new Symbol.
     * @param tag one of [Symbol.CONSTANT_LONG_TAG] or [Symbol.CONSTANT_DOUBLE_TAG].
     * @param value a long or double.
     */
    private fun addConstantLongOrDouble(index: Int, tag: Int, value: Long) {
        add(Entry(index, tag, value, hash(tag, value)))
    }

    /**
     * Adds a CONSTANT_NameAndType_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param name a field or method name.
     * @param descriptor a field or method descriptor.
     * @return a new or already existing Symbol with the given value.
     */
    fun addConstantNameAndType(name: String, descriptor: String): Int {
        val tag = Symbol.CONSTANT_NAME_AND_TYPE_TAG
        val hashCode = hash(tag, name, descriptor)
        var entry = get(hashCode)
        while (entry != null) {
            if (entry.tag == tag
                && entry.hashCode == hashCode
                && entry.name == name
                && entry.value.equals(descriptor)
            ) {
                return entry.index
            }
            entry = entry.next
        }
        constantPool.put122(tag, addConstantUtf8(name), addConstantUtf8(descriptor))
        return put(Entry(constantPoolCount++, tag, name, descriptor, hashCode)).index
    }

    /**
     * Adds a new CONSTANT_NameAndType_info to the constant pool of this symbol table.
     *
     * @param index the constant pool index of the new Symbol.
     * @param name a field or method name.
     * @param descriptor a field or method descriptor.
     */
    private fun addConstantNameAndType(index: Int, name: String, descriptor: String) {
        val tag = Symbol.CONSTANT_NAME_AND_TYPE_TAG
        add(Entry(index, tag, name, descriptor, hash(tag, name, descriptor)))
    }

    /**
     * Adds a CONSTANT_Utf8_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param value a string.
     * @return a new or already existing Symbol with the given value.
     */
    fun addConstantUtf8(value: String): Int {
        val hashCode = hash(Symbol.CONSTANT_UTF8_TAG, value)
        var entry = get(hashCode)
        while (entry != null) {
            if (entry.tag == Symbol.CONSTANT_UTF8_TAG && entry.hashCode == hashCode && entry.value == value) {
                return entry.index
            }
            entry = entry.next
        }
        constantPool.putByte(Symbol.CONSTANT_UTF8_TAG).putUTF8(value)
        return put(Entry(constantPoolCount++, Symbol.CONSTANT_UTF8_TAG, value, hashCode)).index
    }

    /**
     * Adds a new CONSTANT_String_info to the constant pool of this symbol table.
     *
     * @param index the constant pool index of the new Symbol.
     * @param value a string.
     */
    private fun addConstantUtf8(index: Int, value: String) {
        add(Entry(index, Symbol.CONSTANT_UTF8_TAG, value, hash(Symbol.CONSTANT_UTF8_TAG, value)))
    }

    /**
     * Adds a CONSTANT_MethodHandle_info to the constant pool of this symbol table. Does nothing if
     * the constant pool already contains a similar item.
     *
     * @param referenceKind one of [Opcodes.H_GETFIELD], [Opcodes.H_GETSTATIC], [     ][Opcodes.H_PUTFIELD], [Opcodes.H_PUTSTATIC], [Opcodes.H_INVOKEVIRTUAL], [     ][Opcodes.H_INVOKESTATIC], [Opcodes.H_INVOKESPECIAL], [     ][Opcodes.H_NEWINVOKESPECIAL] or [Opcodes.H_INVOKEINTERFACE].
     * @param owner the internal name of a class of interface.
     * @param name a field or method name.
     * @param descriptor a field or method descriptor.
     * @param isInterface whether owner is an interface or not.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantMethodHandle(
        referenceKind: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
    ): Symbol {
        val tag = Symbol.CONSTANT_METHOD_HANDLE_TAG
        val data = getConstantMethodHandleSymbolData(referenceKind, isInterface)
        // Note that we don't need to include isInterface in the hash computation, because it is
        // redundant with owner (we can't have the same owner with different isInterface values).
        val hashCode = hash(tag, owner, name, descriptor, data)
        var entry = get(hashCode)
        while (entry != null) {
            if (entry.tag == tag
                && entry.hashCode == hashCode
                && entry.data == data.toLong()
                && entry.owner.equals(owner)
                && entry.name.equals(name)
                && entry.value.equals(descriptor)
            ) {
                return entry
            }
            entry = entry.next
        }
        if (referenceKind <= Opcodes.H_PUTSTATIC) {
            constantPool.put112(
                tag,
                referenceKind,
                addConstantFieldref(owner, name, descriptor).index
            )
        } else {
            constantPool.put112(
                tag,
                referenceKind,
                addConstantMethodref(owner, name, descriptor, isInterface).index
            )
        }
        return put(Entry(constantPoolCount++, tag, owner, name, descriptor, data.toLong(), hashCode))
    }

    /**
     * Adds a new CONSTANT_MethodHandle_info to the constant pool of this symbol table.
     *
     * @param index the constant pool index of the new Symbol.
     * @param referenceKind one of [Opcodes.H_GETFIELD], [Opcodes.H_GETSTATIC], [     ][Opcodes.H_PUTFIELD], [Opcodes.H_PUTSTATIC], [Opcodes.H_INVOKEVIRTUAL], [     ][Opcodes.H_INVOKESTATIC], [Opcodes.H_INVOKESPECIAL], [     ][Opcodes.H_NEWINVOKESPECIAL] or [Opcodes.H_INVOKEINTERFACE].
     * @param owner the internal name of a class of interface.
     * @param name a field or method name.
     * @param descriptor a field or method descriptor.
     * @param isInterface whether owner is an interface or not.
     */
    private fun addConstantMethodHandle(
        index: Int,
        referenceKind: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
    ) {
        val tag = Symbol.CONSTANT_METHOD_HANDLE_TAG
        val data = getConstantMethodHandleSymbolData(referenceKind, isInterface)
        val hashCode = hash(tag, owner, name, descriptor, data)
        add(Entry(index, tag, owner, name, descriptor, data.toLong(), hashCode))
    }

    /**
     * Returns the [Symbol.data] field for a CONSTANT_MethodHandle_info Symbol.
     *
     * @param referenceKind one of [Opcodes.H_GETFIELD], [Opcodes.H_GETSTATIC], [     ][Opcodes.H_PUTFIELD], [Opcodes.H_PUTSTATIC], [Opcodes.H_INVOKEVIRTUAL], [     ][Opcodes.H_INVOKESTATIC], [Opcodes.H_INVOKESPECIAL], [     ][Opcodes.H_NEWINVOKESPECIAL] or [Opcodes.H_INVOKEINTERFACE].
     * @param isInterface whether owner is an interface or not.
     */
    private fun getConstantMethodHandleSymbolData(
        referenceKind: Int, isInterface: Boolean
    ): Int {
        if (referenceKind > Opcodes.H_PUTSTATIC && isInterface) {
            return referenceKind shl 8
        }
        return referenceKind
    }

    /**
     * Adds a CONSTANT_MethodType_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param methodDescriptor a method descriptor.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantMethodType(methodDescriptor: String): Symbol {
        return addConstantUtf8Reference(Symbol.CONSTANT_METHOD_TYPE_TAG, methodDescriptor)
    }

    /**
     * Adds a CONSTANT_Dynamic_info to the constant pool of this symbol table. Also adds the related
     * bootstrap method to the BootstrapMethods of this symbol table. Does nothing if the constant
     * pool already contains a similar item.
     *
     * @param name a method name.
     * @param descriptor a field descriptor.
     * @param bootstrapMethodHandle a bootstrap method handle.
     * @param bootstrapMethodArguments the bootstrap method arguments.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantDynamic(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        vararg bootstrapMethodArguments: Any
    ): Symbol {
        val bootstrapMethod = addBootstrapMethod(bootstrapMethodHandle, *bootstrapMethodArguments)
        return addConstantDynamicOrInvokeDynamicReference(
            Symbol.CONSTANT_DYNAMIC_TAG, name, descriptor, bootstrapMethod.index
        )
    }

    /**
     * Adds a CONSTANT_InvokeDynamic_info to the constant pool of this symbol table. Also adds the
     * related bootstrap method to the BootstrapMethods of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param name a method name.
     * @param descriptor a method descriptor.
     * @param bootstrapMethodHandle a bootstrap method handle.
     * @param bootstrapMethodArguments the bootstrap method arguments.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantInvokeDynamic(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        vararg bootstrapMethodArguments: Any
    ): Symbol {
        val bootstrapMethod = addBootstrapMethod(bootstrapMethodHandle, *bootstrapMethodArguments)
        return addConstantDynamicOrInvokeDynamicReference(
            Symbol.CONSTANT_INVOKE_DYNAMIC_TAG, name, descriptor, bootstrapMethod.index
        )
    }

    /**
     * Adds a CONSTANT_Dynamic or a CONSTANT_InvokeDynamic_info to the constant pool of this symbol
     * table. Does nothing if the constant pool already contains a similar item.
     *
     * @param tag one of [Symbol.CONSTANT_DYNAMIC_TAG] or [     ][Symbol.CONSTANT_INVOKE_DYNAMIC_TAG].
     * @param name a method name.
     * @param descriptor a field descriptor for CONSTANT_DYNAMIC_TAG) or a method descriptor for
     * CONSTANT_INVOKE_DYNAMIC_TAG.
     * @param bootstrapMethodIndex the index of a bootstrap method in the BootstrapMethods attribute.
     * @return a new or already existing Symbol with the given value.
     */
    private fun addConstantDynamicOrInvokeDynamicReference(
        tag: Int,
        name: String,
        descriptor: String,
        bootstrapMethodIndex: Int
    ): Symbol {
        val hashCode = hash(tag, name, descriptor, bootstrapMethodIndex)
        var entry = get(hashCode)
        while (entry != null) {
            if (entry.tag == tag
                && entry.hashCode == hashCode
                && entry.data == bootstrapMethodIndex.toLong()
                && entry.name == name
                && entry.value == descriptor
            ) {
                return entry
            }
            entry = entry.next
        }
        constantPool.put122(
            tag,
            bootstrapMethodIndex,
            addConstantNameAndType(name, descriptor)
        )
        return put(
            Entry(
                constantPoolCount++,
                tag,
                null,
                name,
                descriptor,
                bootstrapMethodIndex.toLong(),
                hashCode
            )
        )
    }

    /**
     * Adds a new CONSTANT_Dynamic_info or CONSTANT_InvokeDynamic_info to the constant pool of this
     * symbol table.
     *
     * @param tag one of [Symbol.CONSTANT_DYNAMIC_TAG] or [     ][Symbol.CONSTANT_INVOKE_DYNAMIC_TAG].
     * @param index the constant pool index of the new Symbol.
     * @param name a method name.
     * @param descriptor a field descriptor for CONSTANT_DYNAMIC_TAG or a method descriptor for
     * CONSTANT_INVOKE_DYNAMIC_TAG.
     * @param bootstrapMethodIndex the index of a bootstrap method in the BootstrapMethods attribute.
     */
    private fun addConstantDynamicOrInvokeDynamicReference(
        tag: Int,
        index: Int,
        name: String,
        descriptor: String,
        bootstrapMethodIndex: Int
    ) {
        val hashCode = hash(tag, name, descriptor, bootstrapMethodIndex)
        add(Entry(index, tag, null, name, descriptor, bootstrapMethodIndex.toLong(), hashCode))
    }

    /**
     * Adds a CONSTANT_Module_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param moduleName a fully qualified name (using dots) of a module.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantModule(moduleName: String): Symbol {
        return addConstantUtf8Reference(Symbol.CONSTANT_MODULE_TAG, moduleName)
    }

    /**
     * Adds a CONSTANT_Package_info to the constant pool of this symbol table. Does nothing if the
     * constant pool already contains a similar item.
     *
     * @param packageName the internal name of a package.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addConstantPackage(packageName: String): Symbol {
        return addConstantUtf8Reference(Symbol.CONSTANT_PACKAGE_TAG, packageName)
    }

    /**
     * Adds a CONSTANT_Class_info, CONSTANT_String_info, CONSTANT_MethodType_info,
     * CONSTANT_Module_info or CONSTANT_Package_info to the constant pool of this symbol table. Does
     * nothing if the constant pool already contains a similar item.
     *
     * @param tag one of [Symbol.CONSTANT_CLASS_TAG], [Symbol.CONSTANT_STRING_TAG], [     ][Symbol.CONSTANT_METHOD_TYPE_TAG], [Symbol.CONSTANT_MODULE_TAG] or [     ][Symbol.CONSTANT_PACKAGE_TAG].
     * @param value an internal class name, an arbitrary string, a method descriptor, a module or a
     * package name, depending on tag.
     * @return a new or already existing Symbol with the given value.
     */
    private fun addConstantUtf8Reference(tag: Int, value: String): Symbol {
        val hashCode = hash(tag, value)
        var entry: Entry? = get(hashCode)
        while (entry != null) {
            if (entry.tag == tag && entry.hashCode == hashCode && entry.value.equals(value)) {
                return entry
            }
            entry = entry.next
        }
        constantPool.put12(tag, addConstantUtf8(value))
        return put(Entry(constantPoolCount++, tag, value, hashCode))
    }

    /**
     * Adds a new CONSTANT_Class_info, CONSTANT_String_info, CONSTANT_MethodType_info,
     * CONSTANT_Module_info or CONSTANT_Package_info to the constant pool of this symbol table.
     *
     * @param index the constant pool index of the new Symbol.
     * @param tag one of [Symbol.CONSTANT_CLASS_TAG], [Symbol.CONSTANT_STRING_TAG], [     ][Symbol.CONSTANT_METHOD_TYPE_TAG], [Symbol.CONSTANT_MODULE_TAG] or [     ][Symbol.CONSTANT_PACKAGE_TAG].
     * @param value an internal class name, an arbitrary string, a method descriptor, a module or a
     * package name, depending on tag.
     */
    private fun addConstantUtf8Reference(index: Int, tag: Int, value: String) {
        add(Entry(index, tag, value, hash(tag, value)))
    }


    // -----------------------------------------------------------------------------------------------
    // Bootstrap method entries management.
    // -----------------------------------------------------------------------------------------------
    /**
     * Adds a bootstrap method to the BootstrapMethods attribute of this symbol table. Does nothing if
     * the BootstrapMethods already contains a similar bootstrap method.
     *
     * @param bootstrapMethodHandle a bootstrap method handle.
     * @param bootstrapMethodArguments the bootstrap method arguments.
     * @return a new or already existing Symbol with the given value.
     */
    internal fun addBootstrapMethod(
        bootstrapMethodHandle: Handle, vararg bootstrapMethodArguments: Any
    ): Symbol {
        var bootstrapMethodsAttribute: ByteVector? = bootstrapMethods
        if (bootstrapMethodsAttribute == null) {
            bootstrapMethods = ByteVector()
            bootstrapMethodsAttribute = bootstrapMethods
        }

        // The bootstrap method arguments can be Constant_Dynamic values, which reference other
        // bootstrap methods. We must therefore add the bootstrap method arguments to the constant pool
        // and BootstrapMethods attribute first, so that the BootstrapMethods attribute is not modified
        // while adding the given bootstrap method to it, in the rest of this method.
        val numBootstrapArguments = bootstrapMethodArguments.size
        val bootstrapMethodArgumentIndexes = IntArray(numBootstrapArguments)
        for (i in 0..<numBootstrapArguments) {
            bootstrapMethodArgumentIndexes[i] = addConstant(bootstrapMethodArguments[i]).index
        }

        // Write the bootstrap method in the BootstrapMethods table. This is necessary to be able to
        // compare it with existing ones, and will be reverted below if there is already a similar
        // bootstrap method.
        val bootstrapMethodOffset = bootstrapMethodsAttribute!!.length
        bootstrapMethodsAttribute.putShort(
            addConstantMethodHandle(
                bootstrapMethodHandle.tag,
                bootstrapMethodHandle.owner,
                bootstrapMethodHandle.name,
                bootstrapMethodHandle.descriptor,
                bootstrapMethodHandle.isInterface
            ).index
        )

        bootstrapMethodsAttribute.putShort(numBootstrapArguments)
        for (i in 0..<numBootstrapArguments) {
            bootstrapMethodsAttribute.putShort(bootstrapMethodArgumentIndexes[i])
        }

        // Compute the length and the hash code of the bootstrap method.
        val bootstrapMethodLength = bootstrapMethodsAttribute.length - bootstrapMethodOffset
        var hashCode = bootstrapMethodHandle.hashCode()
        for (bootstrapMethodArgument in bootstrapMethodArguments) {
            hashCode = hashCode xor bootstrapMethodArgument.hashCode()
        }
        hashCode = hashCode and 0x7FFFFFFF

        // Add the bootstrap method to the symbol table or revert the above changes.
        return addBootstrapMethod(bootstrapMethodOffset, bootstrapMethodLength, hashCode)
    }

    /**
     * Adds a bootstrap method to the BootstrapMethods attribute of this symbol table. Does nothing if
     * the BootstrapMethods already contains a similar bootstrap method (more precisely, reverts the
     * content of [.bootstrapMethods] to remove the last, duplicate bootstrap method).
     *
     * @param offset the offset of the last bootstrap method in [.bootstrapMethods], in bytes.
     * @param length the length of this bootstrap method in [.bootstrapMethods], in bytes.
     * @param hashCode the hash code of this bootstrap method.
     * @return a new or already existing Symbol with the given value.
     */
    private fun addBootstrapMethod(offset: Int, length: Int, hashCode: Int): Symbol {
        val bootstrapMethods = bootstrapMethods!!
        val bootstrapMethodsData = bootstrapMethods.data
        var entry: Entry? = get(hashCode)
        while (entry != null) {
            if (entry.tag == Symbol.BOOTSTRAP_METHOD_TAG && entry.hashCode == hashCode) {
                val otherOffset = entry.data.toInt()
                var isSameBootstrapMethod = true
                for (i in 0..<length) {
                    if (bootstrapMethodsData[offset + i] != bootstrapMethodsData[otherOffset + i]) {
                        isSameBootstrapMethod = false
                        break
                    }
                }
                if (isSameBootstrapMethod) {
                    bootstrapMethods.length = offset // Revert to old position.
                    return entry
                }
            }
            entry = entry.next
        }
        return put(Entry(bootstrapMethodCount++, Symbol.BOOTSTRAP_METHOD_TAG, offset.toLong(), hashCode))
    }


    // -----------------------------------------------------------------------------------------------
    // Type table entries management.
    // -----------------------------------------------------------------------------------------------
    /**
     * Returns the type table element whose index is given.
     *
     * @param typeIndex a type table index.
     * @return the type table element whose index is given.
     */
    internal fun getType(typeIndex: Int): Symbol {
        return typeTable[typeIndex]!!
    }

    /**
     * Returns the label corresponding to the "forward uninitialized" type table element whose index
     * is given.
     *
     * @param typeIndex the type table index of a "forward uninitialized" type table element.
     * @return the label corresponding of the NEW instruction which created this "forward
     * uninitialized" type.
     */
    fun getForwardUninitializedLabel(typeIndex: Int): Label {
        return labelTable[typeTable[typeIndex]!!.data.toInt()]!!.label
    }

    /**
     * Adds a type in the type table of this symbol table. Does nothing if the type table already
     * contains a similar type.
     *
     * @param value an internal class name.
     * @return the index of a new or already existing type Symbol with the given value.
     */
    fun addType(value: String): Int {
        val hashCode = hash(Symbol.TYPE_TAG, value)
        var entry = get(hashCode)
        while (entry != null) {
            if (entry.tag == Symbol.TYPE_TAG && entry.hashCode == hashCode && entry.value == value) {
                return entry.index
            }
            entry = entry.next
        }
        return addTypeInternal(Entry(typeCount, Symbol.TYPE_TAG, value, hashCode))
    }

    /**
     * Adds an uninitialized type in the type table of this symbol table. Does nothing if the type
     * table already contains a similar type.
     *
     * @param value an internal class name.
     * @param bytecodeOffset the bytecode offset of the NEW instruction that created this
     * uninitialized type value.
     * @return the index of a new or already existing type #@link Symbol} with the given value.
     */
    fun addUninitializedType(value: String, bytecodeOffset: Int): Int {
        val hashCode = hash(Symbol.UNINITIALIZED_TYPE_TAG, value, bytecodeOffset)
        var entry: Entry? = get(hashCode)
        while (entry != null) {
            if (entry.tag == Symbol.UNINITIALIZED_TYPE_TAG
                && entry.hashCode == hashCode
                && entry.data == bytecodeOffset.toLong()
                && entry.value == value
            ) {
                return entry.index
            }
            entry = entry.next
        }
        return addTypeInternal(
            Entry(typeCount, Symbol.UNINITIALIZED_TYPE_TAG, value, bytecodeOffset.toLong(), hashCode)
        )
    }

    /**
     * Adds a "forward uninitialized" type in the type table of this symbol table. Does nothing if the
     * type table already contains a similar type.
     *
     * @param value an internal class name.
     * @param label the label of the NEW instruction that created this uninitialized type value. If
     * the label is resolved, use the [.addUninitializedType] method instead.
     * @return the index of a new or already existing type [Symbol] with the given value.
     */
    fun addForwardUninitializedType(value: String, label: Label?): Int {
        val labelIndex = getOrAddLabelEntry(label).index
        val hashCode = hash(Symbol.FORWARD_UNINITIALIZED_TYPE_TAG, value, labelIndex)
        var entry = get(hashCode)
        while (entry != null) {
            if (entry.tag == Symbol.FORWARD_UNINITIALIZED_TYPE_TAG
                && entry.hashCode == hashCode
                && entry.data == labelIndex.toLong()
                && entry.value == value
            ) {
                return entry.index
            }
            entry = entry.next
        }
        return addTypeInternal(
            Entry(typeCount, Symbol.FORWARD_UNINITIALIZED_TYPE_TAG, value, labelIndex.toLong(), hashCode)
        )
    }

    /**
     * Adds a merged type in the type table of this symbol table. Does nothing if the type table
     * already contains a similar type.
     *
     * @param typeTableIndex1 a [Symbol.TYPE_TAG] type, specified by its index in the type
     * table.
     * @param typeTableIndex2 another [Symbol.TYPE_TAG] type, specified by its index in the type
     * table.
     * @return the index of a new or already existing [Symbol.TYPE_TAG] type Symbol,
     * corresponding to the common super class of the given types.
     */
    fun addMergedType(typeTableIndex1: Int, typeTableIndex2: Int): Int {
        val data =
            if (typeTableIndex1 < typeTableIndex2)
                typeTableIndex1.toLong() or ((typeTableIndex2.toLong()) shl 32)
            else
                typeTableIndex2.toLong() or ((typeTableIndex1.toLong()) shl 32)
        val hashCode = hash(Symbol.MERGED_TYPE_TAG, typeTableIndex1 + typeTableIndex2)
        var entry = get(hashCode)
        while (entry != null) {
            if (entry.tag == Symbol.MERGED_TYPE_TAG && entry.hashCode == hashCode && entry.data == data) {
                return entry.info
            }
            entry = entry.next
        }
        val type1 = typeTable[typeTableIndex1]!!.value!!
        val type2 = typeTable[typeTableIndex2]!!.value!!
        val commonSuperTypeIndex = addType(classWriter.getCommonSuperClass(type1, type2))
        put(Entry(typeCount, Symbol.MERGED_TYPE_TAG, data, hashCode)).info = commonSuperTypeIndex
        return commonSuperTypeIndex
    }

    /**
     * Adds the given type Symbol to [.typeTable].
     *
     * @param entry a [Symbol.TYPE_TAG] or [Symbol.UNINITIALIZED_TYPE_TAG] type symbol.
     * The index of this Symbol must be equal to the current value of [.typeCount].
     * @return the index in [.typeTable] where the given type was added, which is also equal to
     * entry's index by hypothesis.
     */
    private fun addTypeInternal(entry: Entry): Int {
        if (!::typeTable.isInitialized) {
            typeTable = arrayOfNulls(16)
        }
        if (typeCount == typeTable.size) {
            typeTable = typeTable.copyOf(2 * typeTable.size)
        }
        typeTable[typeCount++] = entry
        return put(entry).index
    }

    /**
     * Returns the [LabelEntry] corresponding to the given label. Creates a new one if there is
     * no such entry.
     *
     * @param label the [Label] of a NEW instruction which created an uninitialized type, in the
     * case where this NEW instruction is after the &lt;init&gt; constructor call (in bytecode
     * offset order). See [Symbol.FORWARD_UNINITIALIZED_TYPE_TAG].
     * @return the [LabelEntry] corresponding to `label`.
     */
    private fun getOrAddLabelEntry(label: Label?): LabelEntry {
        if (!::labelEntries.isInitialized) {
            labelEntries = arrayOfNulls(16)
            labelTable = arrayOfNulls(16)
        }
        val hashCode = label.hashCode()
        var labelEntry = labelEntries[hashCode % labelEntries.size]
        while (labelEntry != null && labelEntry.label != label) {
            labelEntry = labelEntry.next
        }
        if (labelEntry != null) {
            return labelEntry
        }

        if (labelCount > (labelEntries.size * 3) / 4) {
            val currentCapacity: Int = labelEntries.size
            val newCapacity = currentCapacity * 2 + 1
            val newLabelEntries = arrayOfNulls<LabelEntry>(newCapacity)
            for (i in currentCapacity - 1 downTo 0) {
                var currentEntry: LabelEntry? = labelEntries[i]
                while (currentEntry != null) {
                    val newCurrentEntryIndex: Int = currentEntry.label.hashCode() % newCapacity
                    val nextEntry = currentEntry.next
                    currentEntry.next = newLabelEntries[newCurrentEntryIndex]
                    newLabelEntries[newCurrentEntryIndex] = currentEntry
                    currentEntry = nextEntry
                }
            }
            labelEntries = newLabelEntries
        }
        if (labelCount == labelTable.size) {
            labelTable = labelTable.let {
                it.copyOf(2 * it.size)
            }
        }

        labelEntry = LabelEntry(labelCount, label!!)
        val index = hashCode % labelEntries.size
        labelEntry.next = labelEntries[index]
        labelEntries[index] = labelEntry
        labelTable[labelCount++] = labelEntry
        return labelEntry
    }


    // -----------------------------------------------------------------------------------------------
    // Static helper methods to compute hash codes.
    // -----------------------------------------------------------------------------------------------
    private fun hash(tag: Int, value: Int): Int {
        return 0x7FFFFFFF and (tag + value)
    }

    private fun hash(tag: Int, value: Long): Int {
        return 0x7FFFFFFF and (tag + value.toInt() + (value ushr 32).toInt())
    }

    private fun hash(tag: Int, value: String): Int {
        return 0x7FFFFFFF and (tag + value.hashCode())
    }

    private fun hash(tag: Int, value1: String, value2: Int): Int {
        return 0x7FFFFFFF and (tag + value1.hashCode() + value2)
    }

    private fun hash(tag: Int, value1: String, value2: String): Int {
        return 0x7FFFFFFF and (tag + value1.hashCode() * value2.hashCode())
    }

    private fun hash(
        tag: Int,
        value1: String,
        value2: String,
        value3: Int
    ): Int {
        return 0x7FFFFFFF and (tag + value1.hashCode() * value2.hashCode() * (value3 + 1))
    }

    private fun hash(
        tag: Int,
        value1: String,
        value2: String,
        value3: String
    ): Int {
        return 0x7FFFFFFF and (tag + value1.hashCode() * value2.hashCode() * value3.hashCode())
    }

    private fun hash(
        tag: Int,
        value1: String,
        value2: String,
        value3: String,
        value4: Int
    ): Int {
        return 0x7FFFFFFF and (tag + value1.hashCode() * value2.hashCode() * value3.hashCode() * value4)
    }

    /**
     * An entry of a SymbolTable. This concrete and private subclass of [Symbol] adds two fields
     * which are only used inside SymbolTable, to implement hash sets of symbols (in order to avoid
     * duplicate symbols). See [.entries].
     *
     * @author Eric Bruneton
     */
    private class Entry(
        index: Int,
        tag: Int,
        owner: String? = null,
        name: String? = null,
        value: String?,
        data: Long,
        val hashCode: Int
    ) : Symbol(index, tag, owner, name, value, data) {
        /**
         * Another entry (and so on recursively) having the same hash code (modulo the size of [ ][.entries]) as this one.
         */
        var next: Entry? = null

        constructor(index: Int, tag: Int, value: String, hashCode: Int) : this(
            index,
            tag,
            null,
            null,
            value,
            0,
            hashCode
        )

        constructor(index: Int, tag: Int, value: String, data: Long, hashCode: Int) : this(
            index,
            tag,
            null,
            null,
            value,
            data,
            hashCode
        )

        constructor(index: Int, tag: Int, name: String?, value: String, hashCode: Int) : this(
            index,
            tag,
            null,
            name,
            value,
            0,
            hashCode
        )

        constructor(index: Int, tag: Int, data: Long, hashCode: Int) : this(
            index,
            tag,
            null,
            null,
            null,
            data,
            hashCode
        )
    }

    /**
     * An entry of a SymbolTable. This concrete and private subclass of {@link Symbol} adds two fields
     * which are only used inside SymbolTable, to implement hash sets of symbols (in order to avoid
     * duplicate symbols). See {@link #entries}.
     *
     * @author Eric Bruneton
     */
    private class LabelEntry(
        /** The index of this label entry in the [SymbolTable.labelTable] array.  */
        val index: Int,
        val label: Label
    ) {
        /**
         * Another entry (and so on recursively) having the same hash code (modulo the size of [ ][SymbolTable.labelEntries]) as this one.
         */
        var next: LabelEntry? = null
    }
}