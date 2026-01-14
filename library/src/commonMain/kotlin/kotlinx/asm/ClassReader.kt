package kotlinx.asm

import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.ExperimentalExtendedContracts
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

open class ClassReader(
    /**
     * A byte array containing the JVMS ClassFile structure to be parsed. *The content of this array
     * must not be modified. This field is intended for [Attribute] sub classes, and is normally
     * not needed by class visitors.*
     *
     *
     * NOTE: the ClassFile structure can start at any offset within this array, i.e. it does not
     * necessarily start at offset 0. Use [.getItem] and [.header] to get correct
     * ClassFile element offsets within this byte array.
     */
    val classFileBuffer: ByteArray,
    classFileOffset: Int,
    checkClassVersion: Boolean
) {
    constructor(classFile: ByteArray, classFileOffset: Int) : this(classFile, classFileOffset, true)
    constructor(classFile: ByteArray) : this(classFile, 0)

    constructor(classFile: Source) : this(classFile.readByteArray())

    companion object {
        /**
         * A flag to skip the Code attributes. If this flag is set the Code attributes are neither parsed
         * nor visited.
         */
        const val SKIP_CODE: Int = 1

        /**
         * A flag to skip the SourceFile, SourceDebugExtension, LocalVariableTable,
         * LocalVariableTypeTable, LineNumberTable and MethodParameters attributes. If this flag is set
         * these attributes are neither parsed nor visited (i.e. [ClassVisitor.visitSource], [ ][MethodVisitor.visitLocalVariable], [MethodVisitor.visitLineNumber] and [ ][MethodVisitor.visitParameter] are not called).
         */
        const val SKIP_DEBUG: Int = 2

        /**
         * A flag to skip the StackMap and StackMapTable attributes. If this flag is set these attributes
         * are neither parsed nor visited (i.e. [MethodVisitor.visitFrame] is not called). This flag
         * is useful when the [ClassWriter.COMPUTE_FRAMES] option is used: it avoids visiting frames
         * that will be ignored and recomputed from scratch.
         */
        const val SKIP_FRAMES: Int = 4

        /**
         * A flag to expand the stack map frames. By default stack map frames are visited in their
         * original format (i.e. "expanded" for classes whose version is less than V1_6, and "compressed"
         * for the other classes). If this flag is set, stack map frames are always visited in expanded
         * format (this option adds a decompression/compression step in ClassReader and ClassWriter which
         * degrades performance quite a lot).
         */
        const val EXPAND_FRAMES: Int = 8

        /**
         * A flag to expand the ASM specific instructions into an equivalent sequence of standard bytecode
         * instructions. When resolving a forward jump it may happen that the signed 2 bytes offset
         * reserved for it is not sufficient to store the bytecode offset. In this case the jump
         * instruction is replaced with a temporary ASM specific instruction using an unsigned 2 bytes
         * offset (see [Label.resolve]). This internal flag is used to re-read classes containing
         * such instructions, in order to replace them with standard instructions. In addition, when this
         * flag is used, goto_w and jsr_w are *not* converted into goto and jsr, to make sure that
         * infinite loops where a goto_w is replaced with a goto in ClassReader and converted back to a
         * goto_w in ClassWriter cannot occur.
         */
        const val EXPAND_ASM_INSNS: Int = 256

        /** The maximum size of array to allocate.  */
        private const val MAX_BUFFER_SIZE: Int = 1024 * 1024

        /** The size of the temporary byte array used to read class input streams chunk by chunk.  */
        private const val INPUT_STREAM_DATA_CHUNK_SIZE: Int = 4096
    }

    /** The offset in bytes of the ClassFile's access_flags field.  */
    var header = 0

    /**
     * The offset in bytes, in [.classFileBuffer], of each cp_info entry of the ClassFile's
     * constant_pool array, *plus one*. In other words, the offset of constant pool entry i is
     * given by cpInfoOffsets[i] - 1, i.e. its cp_info's tag field is given by b[cpInfoOffsets[i] -
     * 1].
     */
    private val cpInfoOffsets: IntArray

    /**
     * The String objects corresponding to the CONSTANT_Utf8 constant pool items. This cache avoids
     * multiple parsing of a given CONSTANT_Utf8 constant pool item.
     */
    private val constantUtf8Values: Array<String?>

    /**
     * The ConstantDynamic objects corresponding to the CONSTANT_Dynamic constant pool items. This
     * cache avoids multiple parsing of a given CONSTANT_Dynamic constant pool item.
     */
    private val constantDynamicValues: Array<ConstantDynamic?>?

    /**
     * The start offsets in [.classFileBuffer] of each element of the bootstrap_methods array
     * (in the BootstrapMethods attribute).
     *
     * @see [JVMS
     * 4.7.23](https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html.jvms-4.7.23)
     */
    private val bootstrapMethodOffsets: IntArray?

    /**
     * A conservative estimate of the maximum length of the strings contained in the constant pool of
     * the class.
     */
    var maxStringLength = 0; private set

    init {
        // Check the class' major_version. This field is after the magic and minor_version fields, which
        // use 4 and 2 bytes respectively.
        require(!(checkClassVersion && readShort(classFileOffset + 6) > Opcodes.V27)) {
            "Unsupported class file major version " + readShort(
                classFileOffset + 6
            )
        }

        // Create the constant pool arrays. The constant_pool_count field is after the magic,
        // minor_version and major_version fields, which use 4, 2 and 2 bytes respectively.
        val constantPoolCount = readUnsignedShort(classFileOffset + 8)
        cpInfoOffsets = IntArray(constantPoolCount)
        constantUtf8Values = arrayOfNulls(constantPoolCount)


        // Compute the offset of each constant pool entry, as well as a conservative estimate of the
        // maximum length of the constant pool strings. The first constant pool entry is after the
        // magic, minor_version, major_version and constant_pool_count fields, which use 4, 2, 2 and 2
        // bytes respectively.
        var currentCpInfoIndex = 1
        var currentCpInfoOffset = classFileOffset + 10
        var currentMaxStringLength = 0
        var hasBootstrapMethods = false
        var hasConstantDynamic = false

        // The offset of the other entries depend on the total size of all the previous entries.
        while (currentCpInfoIndex < constantPoolCount) {
            cpInfoOffsets[currentCpInfoIndex++] = currentCpInfoOffset + 1
            val cpInfoSize: Int
            when (classFileBuffer[currentCpInfoOffset].toInt()) {
                Symbol.CONSTANT_FIELDREF_TAG, Symbol.CONSTANT_METHODREF_TAG, Symbol.CONSTANT_INTERFACE_METHODREF_TAG,
                Symbol.CONSTANT_INTEGER_TAG, Symbol.CONSTANT_FLOAT_TAG, Symbol.CONSTANT_NAME_AND_TYPE_TAG ->
                    cpInfoSize = 5

                Symbol.CONSTANT_DYNAMIC_TAG -> {
                    cpInfoSize = 5
                    hasBootstrapMethods = true
                    hasConstantDynamic = true
                }

                Symbol.CONSTANT_INVOKE_DYNAMIC_TAG -> {
                    cpInfoSize = 5
                    hasBootstrapMethods = true
                }

                Symbol.CONSTANT_LONG_TAG, Symbol.CONSTANT_DOUBLE_TAG -> {
                    cpInfoSize = 9
                    currentCpInfoIndex++
                }

                Symbol.CONSTANT_UTF8_TAG -> {
                    cpInfoSize = 3 + readUnsignedShort(currentCpInfoOffset + 1)
                    if (cpInfoSize > currentMaxStringLength) {
                        // The size in bytes of this CONSTANT_Utf8 structure provides a conservative estimate
                        // of the length in characters of the corresponding string, and is much cheaper to
                        // compute than this exact length.
                        currentMaxStringLength = cpInfoSize
                    }
                }

                Symbol.CONSTANT_METHOD_HANDLE_TAG -> cpInfoSize = 4
                Symbol.CONSTANT_CLASS_TAG, Symbol.CONSTANT_STRING_TAG, Symbol.CONSTANT_METHOD_TYPE_TAG,
                Symbol.CONSTANT_PACKAGE_TAG, Symbol.CONSTANT_MODULE_TAG ->
                    cpInfoSize = 3

                else -> throw IllegalArgumentException()
            }
            currentCpInfoOffset += cpInfoSize
        }
        maxStringLength = currentMaxStringLength

        // The Classfile's access_flags field is just after the last constant pool entry.
        header = currentCpInfoOffset

        // Allocate the cache of ConstantDynamic values, if there is at least one.
        constantDynamicValues =
            if (hasConstantDynamic)
                arrayOfNulls(constantPoolCount)
            else null

        // Read the BootstrapMethods attribute, if any (only get the offset of each method).
        bootstrapMethodOffsets =
            if (hasBootstrapMethods)
                readBootstrapMethodsAttribute(currentMaxStringLength)
            else null
    }

    // -----------------------------------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------------------------------
    /**
     * Returns the class's access flags (see [Opcodes]). This value may not reflect Deprecated
     * and Synthetic flags when bytecode is before 1.5 and those flags are represented by attributes.
     *
     * @return the class access flags.
     * @see ClassVisitor.visit
     */
    val access: Int
        get() {
            return readUnsignedShort(header)
        }

    /**
     * Returns the internal name of the class (see [Type.getInternalName]).
     *
     * @return the internal class name.
     * @see ClassVisitor.visit
     */
    val className: String
        get() {
            // this_class is just after the access_flags field (using 2 bytes).
            return readClass(header + 2, CharArray(maxStringLength))
        }

    /**
     * Returns the internal name of the super class (see [Type.getInternalName]). For
     * interfaces, the super class is [Object].
     *
     * @return the internal name of the super class, or null for [Object] class.
     * @see ClassVisitor.visit
     */
    val superName: String
        get() {
            // super_class is after the access_flags and this_class fields (2 bytes each).
            return readClass(header + 4, CharArray(maxStringLength))
        }

    /**
     * Returns the internal names of the implemented interfaces (see [Type.getInternalName]).
     *
     * @return the internal names of the directly implemented interfaces. Inherited implemented
     * interfaces are not returned.
     * @see ClassVisitor.visit
     */
    val interfaces: Array<String>
        get() {
            // interfaces_count is after the access_flags, this_class and super_class fields (2 bytes each).
            var currentOffset = header + 6
            val interfacesCount = readUnsignedShort(currentOffset)
            if (interfacesCount == 0) return arrayOf()
            else {
                val charBuffer = CharArray(maxStringLength)
                return Array(interfacesCount) {
                    currentOffset += 2
                    readClass(currentOffset, charBuffer)
                }
            }
        }

    // -----------------------------------------------------------------------------------------------
    // Public methods
    // -----------------------------------------------------------------------------------------------
    /**
     * Makes the given visitor visit the JVMS ClassFile structure passed to the constructor of this
     * [ClassReader].
     * 
     * @param classVisitor the visitor that must visit this class.
     * @param parsingOptions the options to use to parse this class. One or more of [     ][.SKIP_CODE], [.SKIP_DEBUG], [.SKIP_FRAMES] or [.EXPAND_FRAMES].
     */
    fun accept(classVisitor: ClassVisitor, parsingOptions: Int) {
        accept(classVisitor, arrayOf(), parsingOptions)
    }

    /**
     * Makes the given visitor visit the JVMS ClassFile structure passed to the constructor of this
     * [ClassReader].
     * 
     * @param classVisitor the visitor that must visit this class.
     * @param attributePrototypes prototypes of the attributes that must be parsed during the visit of
     * the class. Any attribute whose type is not equal to the type of one the prototypes will not
     * be parsed: its byte array value will be passed unchanged to the ClassWriter. *This may
     * corrupt it if this value contains references to the constant pool, or has syntactic or
     * semantic links with a class element that has been transformed by a class adapter between
     * the reader and the writer*.
     * @param parsingOptions the options to use to parse this class. One or more of [     ][.SKIP_CODE], [.SKIP_DEBUG], [.SKIP_FRAMES] or [.EXPAND_FRAMES].
     */
    fun accept(
        classVisitor: ClassVisitor,
        attributePrototypes: Array<Attribute>,
        parsingOptions: Int
    ) {
        val context = Context()
        context.attributePrototypes = attributePrototypes
        context.parsingOptions = parsingOptions
        context.charBuffer = CharArray(maxStringLength)

        // Read the access_flags, this_class, super_class, interface_count and interfaces fields.
        val charBuffer = context.charBuffer
        var currentOffset = header
        var accessFlags = readUnsignedShort(currentOffset)
        val thisClass = readClass(currentOffset + 2, charBuffer)
        val superClass = readClass(currentOffset + 4, charBuffer)
        run {
            val interfaceCount = readUnsignedShort(currentOffset + 6)
            currentOffset += 8
            Array(interfaceCount) {
                val result = readClass(currentOffset, charBuffer)
                currentOffset += 2
                result
            }
        }
        val interfaces = run {
            val interfaceCount = readUnsignedShort(currentOffset + 6)
            currentOffset += 8
            Array(interfaceCount) {
                val result = readClass(currentOffset, charBuffer)
                currentOffset += 2
                result
            }
        }

        // Read the class attributes (the variables are ordered as in Section 4.7 of the JVMS).
        // Attribute offsets exclude the attribute_name_index and attribute_length fields.
        // - The offset of the InnerClasses attribute, or 0.
        var innerClassesOffset = 0
        // - The offset of the EnclosingMethod attribute, or 0.
        var enclosingMethodOffset = 0
        // - The string corresponding to the Signature attribute, or null.
        var signature: String? = null
        // - The string corresponding to the SourceFile attribute, or null.
        var sourceFile: String? = null
        // - The string corresponding to the SourceDebugExtension attribute, or null.
        var sourceDebugExtension: String? = null
        // - The offset of the RuntimeVisibleAnnotations attribute, or 0.
        var runtimeVisibleAnnotationsOffset = 0
        // - The offset of the RuntimeInvisibleAnnotations attribute, or 0.
        var runtimeInvisibleAnnotationsOffset = 0
        // - The offset of the RuntimeVisibleTypeAnnotations attribute, or 0.
        var runtimeVisibleTypeAnnotationsOffset = 0
        // - The offset of the RuntimeInvisibleTypeAnnotations attribute, or 0.
        var runtimeInvisibleTypeAnnotationsOffset = 0
        // - The offset of the Module attribute, or 0.
        var moduleOffset = 0
        // - The offset of the ModulePackages attribute, or 0.
        var modulePackagesOffset = 0
        // - The string corresponding to the ModuleMainClass attribute, or null.
        var moduleMainClass: String? = null
        // - The string corresponding to the NestHost attribute, or null.
        var nestHostClass: String? = null
        // - The offset of the NestMembers attribute, or 0.
        var nestMembersOffset = 0
        // - The offset of the PermittedSubclasses attribute, or 0
        var permittedSubclassesOffset = 0
        // - The offset of the Record attribute, or 0.
        var recordOffset = 0
        // - The non standard attributes (linked with their {@link Attribute#nextAttribute} field).
        //   This list in the <i>reverse order</i> or their order in the ClassFile structure.
        var attributes: Attribute? = null

        var currentAttributeOffset = getFirstAttributeOffset()
        repeat(readUnsignedShort(currentAttributeOffset - 2)) {
            // Read the attribute_info's attribute_name and attribute_length fields.
            val attributeName = readUTF8(currentAttributeOffset, charBuffer)
            val attributeLength = readInt(currentAttributeOffset + 2)
            currentAttributeOffset += 6
            // The tests are sorted in decreasing frequency order (based on frequencies observed on
            // typical classes).
            when (attributeName) {
                Constants.SOURCE_FILE -> {
                    sourceFile = readUTF8(currentAttributeOffset, charBuffer)
                }
                Constants.INNER_CLASSES -> {
                    innerClassesOffset = currentAttributeOffset
                }
                Constants.ENCLOSING_METHOD -> {
                    enclosingMethodOffset = currentAttributeOffset
                }
                Constants.NEST_HOST -> {
                    nestHostClass = readClass(currentAttributeOffset, charBuffer)
                }
                Constants.NEST_MEMBERS -> {
                    nestMembersOffset = currentAttributeOffset
                }
                Constants.PERMITTED_SUBCLASSES -> {
                    permittedSubclassesOffset = currentAttributeOffset
                }
                Constants.SIGNATURE -> {
                    signature = readUTF8(currentAttributeOffset, charBuffer)
                }
                Constants.RUNTIME_VISIBLE_ANNOTATIONS -> {
                    runtimeVisibleAnnotationsOffset = currentAttributeOffset
                }
                Constants.RUNTIME_VISIBLE_TYPE_ANNOTATIONS -> {
                    runtimeVisibleTypeAnnotationsOffset = currentAttributeOffset
                }
                Constants.DEPRECATED -> {
                    accessFlags = accessFlags or Opcodes.ACC_DEPRECATED
                }
                Constants.SYNTHETIC -> {
                    accessFlags = accessFlags or Opcodes.ACC_SYNTHETIC
                }
                Constants.SOURCE_DEBUG_EXTENSION -> {
                    require(attributeLength <= classFileBuffer.size - currentAttributeOffset)
                    sourceDebugExtension = readUtf(
                        currentAttributeOffset, 
                        attributeLength, 
                        CharArray(attributeLength)
                    )
                }
                Constants.RUNTIME_INVISIBLE_ANNOTATIONS -> {
                    runtimeInvisibleAnnotationsOffset = currentAttributeOffset
                }
                Constants.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS -> {
                    runtimeInvisibleTypeAnnotationsOffset = currentAttributeOffset
                }
                Constants.RECORD -> {
                    recordOffset = currentAttributeOffset
                    accessFlags = accessFlags or Opcodes.ACC_RECORD
                }
                Constants.MODULE -> {
                    moduleOffset = currentAttributeOffset
                }
                Constants.MODULE_MAIN_CLASS -> {
                    moduleMainClass = readClass(currentAttributeOffset, charBuffer)
                }
                Constants.MODULE_PACKAGES -> {
                    modulePackagesOffset = currentAttributeOffset
                }
                else -> {
                    if (Constants.BOOTSTRAP_METHODS != attributeName) {
                        // The BootstrapMethods attribute is read in the constructor.
                        val attribute = readAttribute(
                            attributePrototypes,
                            attributeName,
                            currentAttributeOffset,
                            attributeLength,
                            charBuffer,
                            -1,
                            null
                        )
                        attribute.nextAttribute = attributes
                        attributes = attribute
                    }
                }
            }
            currentAttributeOffset += attributeLength
        }

        // Visit the class declaration. The minor_version and major_version fields start 6 bytes before
        // the first constant pool entry, which itself starts at cpInfoOffsets[1] - 1 (by definition).
        classVisitor.visit(
            readInt(cpInfoOffsets[1] - 7), 
            accessFlags, 
            thisClass, 
            signature, 
            superClass,
            interfaces.toList()
        )

        // Visit the SourceFile and SourceDebugExtenstion attributes.
        if ((parsingOptions and SKIP_DEBUG) == 0
            && (sourceFile != null || sourceDebugExtension != null)
        ) {
            classVisitor.visitSource(sourceFile, sourceDebugExtension)
        }

        // Visit the Module, ModulePackages and ModuleMainClass attributes.
        if (moduleOffset != 0) {
            readModuleAttributes(
                classVisitor, context, moduleOffset, modulePackagesOffset, moduleMainClass
            )
        }

        // Visit the NestHost attribute.
        if (nestHostClass != null) {
            classVisitor.visitNestHost(nestHostClass)
        }

        // Visit the EnclosingMethod attribute.
        if (enclosingMethodOffset != 0) {
            val className = readClass(enclosingMethodOffset, charBuffer)
            val methodIndex = readUnsignedShort(enclosingMethodOffset + 2)
            val name = if (methodIndex == 0) null else readUTF8(cpInfoOffsets[methodIndex], charBuffer)
            val type = if (methodIndex == 0) null else readUTF8(cpInfoOffsets[methodIndex] + 2, charBuffer)
            classVisitor.visitOuterClass(className, name, type)
        }

        // Visit the RuntimeVisibleAnnotations attribute.
        if (runtimeVisibleAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeVisibleAnnotationsOffset)
            var currentAnnotationOffset = runtimeVisibleAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    classVisitor.visitAnnotation(annotationDescriptor, true),
                    currentAnnotationOffset,
                    true,
                    charBuffer
                )
            }
        }

        // Visit the RuntimeInvisibleAnnotations attribute.
        if (runtimeInvisibleAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeInvisibleAnnotationsOffset)
            var currentAnnotationOffset = runtimeInvisibleAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    classVisitor.visitAnnotation(annotationDescriptor, false),
                    currentAnnotationOffset,
                    true,
                    charBuffer
                )
            }
        }

        // Visit the RuntimeVisibleTypeAnnotations attribute.
        if (runtimeVisibleTypeAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeVisibleTypeAnnotationsOffset)
            var currentAnnotationOffset = runtimeVisibleTypeAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the target_type, target_info and target_path fields.
                currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset)
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    classVisitor.visitTypeAnnotation(
                        context.currentTypeAnnotationTarget,
                        context.currentTypeAnnotationTargetPath,
                        annotationDescriptor,
                        true
                    ),
                    currentAnnotationOffset,
                    true,
                    charBuffer
                )
            }
        }

        // Visit the RuntimeInvisibleTypeAnnotations attribute.
        if (runtimeInvisibleTypeAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeInvisibleTypeAnnotationsOffset)
            var currentAnnotationOffset = runtimeInvisibleTypeAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the target_type, target_info and target_path fields.
                currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset)
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    classVisitor.visitTypeAnnotation(
                        context.currentTypeAnnotationTarget,
                        context.currentTypeAnnotationTargetPath,
                        annotationDescriptor,
                        false
                    ),
                    currentAnnotationOffset,
                    true,
                    charBuffer
                )
            }
        }

        // Visit the non standard attributes.
        while (attributes != null) {
            // Copy and reset the nextAttribute field so that it can also be used in ClassWriter.
            val nextAttribute = attributes.nextAttribute
            attributes.nextAttribute = null
            classVisitor.visitAttribute(attributes)
            attributes = nextAttribute
        }

        // Visit the NestedMembers attribute.
        if (nestMembersOffset != 0) {
            val numberOfNestMembers = readUnsignedShort(nestMembersOffset)
            var currentNestMemberOffset = nestMembersOffset + 2
            repeat(numberOfNestMembers) {
                classVisitor.visitNestMember(readClass(currentNestMemberOffset, charBuffer))
                currentNestMemberOffset += 2
            }
        }

        // Visit the PermittedSubclasses attribute.
        if (permittedSubclassesOffset != 0) {
            val numberOfPermittedSubclasses = readUnsignedShort(permittedSubclassesOffset)
            var currentPermittedSubclassesOffset = permittedSubclassesOffset + 2
            repeat(numberOfPermittedSubclasses) {
                classVisitor.visitPermittedSubclass(
                    readClass(currentPermittedSubclassesOffset, charBuffer)
                )
                currentPermittedSubclassesOffset += 2
            }
        }

        // Visit the InnerClasses attribute.
        if (innerClassesOffset != 0) {
            val numberOfClasses = readUnsignedShort(innerClassesOffset)
            var currentClassesOffset = innerClassesOffset + 2
            repeat(numberOfClasses) {
                classVisitor.visitInnerClass(
                    readClass(currentClassesOffset, charBuffer),
                    readClass(currentClassesOffset + 2, charBuffer),
                    readUTF8(currentClassesOffset + 4, charBuffer),
                    readUnsignedShort(currentClassesOffset + 6)
                )
                currentClassesOffset += 8
            }
        }

        // Visit Record components.
        if (recordOffset != 0) {
            val recordComponentsCount = readUnsignedShort(recordOffset)
            recordOffset += 2
            repeat(recordComponentsCount) {
                recordOffset = readRecordComponent(classVisitor, context, recordOffset)
            }
        }

        // Visit the fields and methods.
        val fieldsCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(fieldsCount) {
            currentOffset = readField(classVisitor, context, currentOffset)
        }
        val methodsCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(methodsCount) {
            currentOffset = readMethod(classVisitor, context, currentOffset)
        }

        // Visit the end of the class.
        classVisitor.visitEnd()
    }

    // ----------------------------------------------------------------------------------------------
    // Methods to parse modules, fields and methods
    // ----------------------------------------------------------------------------------------------
    /**
     * Reads the Module, ModulePackages and ModuleMainClass attributes and visit them.
     *
     * @param classVisitor the current class visitor
     * @param context information about the class being parsed.
     * @param moduleOffset the offset of the Module attribute (excluding the attribute_info's
     * attribute_name_index and attribute_length fields).
     * @param modulePackagesOffset the offset of the ModulePackages attribute (excluding the
     * attribute_info's attribute_name_index and attribute_length fields), or 0.
     * @param moduleMainClass the string corresponding to the ModuleMainClass attribute, or null.
     */
    private fun readModuleAttributes(
        classVisitor: ClassVisitor,
        context: Context,
        moduleOffset: Int,
        modulePackagesOffset: Int,
        moduleMainClass: String?
    ) {
        val buffer = context.charBuffer

        // Read the module_name_index, module_flags and module_version_index fields and visit them.
        var currentOffset = moduleOffset
        val moduleName = readModule(currentOffset, buffer)
        val moduleFlags = readUnsignedShort(currentOffset + 2)
        val moduleVersion = readUTF8(currentOffset + 4, buffer)
        currentOffset += 6
        val moduleVisitor = classVisitor.visitModule(moduleName, moduleFlags, moduleVersion) ?: return

        // Visit the ModuleMainClass attribute.
        if (moduleMainClass != null) {
            moduleVisitor.visitMainClass(moduleMainClass)
        }

        // Visit the ModulePackages attribute.
        if (modulePackagesOffset != 0) {
            val packageCount = readUnsignedShort(modulePackagesOffset)
            var currentPackageOffset = modulePackagesOffset + 2
            repeat(packageCount) {
                moduleVisitor.visitPackage(readPackage(currentPackageOffset, buffer))
                currentPackageOffset += 2
            }
        }

        // Read the 'requires_count' and 'requires' fields.
        val requiresCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(requiresCount) {
            // Read the requires_index, requires_flags and requires_version fields and visit them.
            val requires = readModule(currentOffset, buffer)
            val requiresFlags = readUnsignedShort(currentOffset + 2)
            val requiresVersion = readUTF8(currentOffset + 4, buffer)
            currentOffset += 6
            moduleVisitor.visitRequire(requires, requiresFlags, requiresVersion)
        }

        // Read the 'exports_count' and 'exports' fields.
        val exportsCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(exportsCount) {
            // Read the exports_index, exports_flags, exports_to_count and exports_to_index fields
            // and visit them.
            val exports = readPackage(currentOffset, buffer)
            val exportsFlags = readUnsignedShort(currentOffset + 2)
            val exportsToCount = readUnsignedShort(currentOffset + 4)
            currentOffset += 6
            var exportsTo = arrayOf<String>()
            if (exportsToCount != 0) {
                exportsTo = Array(exportsToCount) {
                    val result = readModule(currentOffset, buffer)
                    currentOffset += 2
                    result
                }
            }
            moduleVisitor.visitExport(exports, exportsFlags, exportsTo)
        }

        // Reads the 'opens_count' and 'opens' fields.
        val opensCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(opensCount) {
            // Read the opens_index, opens_flags, opens_to_count and opens_to_index fields and visit them.
            val opens = readPackage(currentOffset, buffer)
            val opensFlags = readUnsignedShort(currentOffset + 2)
            val opensToCount = readUnsignedShort(currentOffset + 4)
            currentOffset += 6
            var opensTo = arrayOf<String>()
            if (opensToCount != 0) {
                opensTo = Array(opensToCount) {
                    val result = readModule(currentOffset, buffer)
                    currentOffset += 2
                    result
                }
            }
            moduleVisitor.visitOpen(opens, opensFlags, opensTo)
        }

        // Read the 'uses_count' and 'uses' fields.
        val usesCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(usesCount) {
            moduleVisitor.visitUse(readClass(currentOffset, buffer))
            currentOffset += 2
        }

        // Read the 'provides_count' and 'provides' fields.
        val providesCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(providesCount) {
            // Read the provides_index, provides_with_count and provides_with_index fields and visit them.
            val provides = readClass(currentOffset, buffer)
            val providesWithCount = readUnsignedShort(currentOffset + 2)
            currentOffset += 4
            val providesWith = Array(providesWithCount) {
                val result = readClass(currentOffset, buffer)
                currentOffset += 2
                result
            }
            moduleVisitor.visitProvide(provides, providesWith)
        }

        // Visit the end of the module attributes.
        moduleVisitor.visitEnd()
    }

    /**
     * Reads a record component and visit it.
     *
     * @param classVisitor the current class visitor
     * @param context information about the class being parsed.
     * @param recordComponentOffset the offset of the current record component.
     * @return the offset of the first byte following the record component.
     */
    private fun readRecordComponent(
        classVisitor: ClassVisitor,
        context: Context,
        recordComponentOffset: Int
    ): Int {
        val charBuffer = context.charBuffer

        var currentOffset = recordComponentOffset
        val name = readUTF8(currentOffset, charBuffer)
        val descriptor = readUTF8(currentOffset + 2, charBuffer)
        currentOffset += 4

        // Read the record component attributes (the variables are ordered as in Section 4.7 of the
        // JVMS).

        // Attribute offsets exclude the attribute_name_index and attribute_length fields.
        // - The string corresponding to the Signature attribute, or null.
        var signature: String? = null
        // - The offset of the RuntimeVisibleAnnotations attribute, or 0.
        var runtimeVisibleAnnotationsOffset = 0
        // - The offset of the RuntimeInvisibleAnnotations attribute, or 0.
        var runtimeInvisibleAnnotationsOffset = 0
        // - The offset of the RuntimeVisibleTypeAnnotations attribute, or 0.
        var runtimeVisibleTypeAnnotationsOffset = 0
        // - The offset of the RuntimeInvisibleTypeAnnotations attribute, or 0.
        var runtimeInvisibleTypeAnnotationsOffset = 0
        // - The non standard attributes (linked with their {@link Attribute#nextAttribute} field).
        //   This list in the <i>reverse order</i> or their order in the ClassFile structure.
        var attributes: Attribute? = null

        val attributesCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(attributesCount) {
            // Read the attribute_info's attribute_name and attribute_length fields.
            val attributeName = readUTF8(currentOffset, charBuffer)
            val attributeLength = readInt(currentOffset + 2)
            currentOffset += 6
            // The tests are sorted in decreasing frequency order (based on frequencies observed on
            // typical classes).
            if (Constants.SIGNATURE == attributeName) {
                signature = readUTF8(currentOffset, charBuffer)
            } else if (Constants.RUNTIME_VISIBLE_ANNOTATIONS == attributeName) {
                runtimeVisibleAnnotationsOffset = currentOffset
            } else if (Constants.RUNTIME_VISIBLE_TYPE_ANNOTATIONS == attributeName) {
                runtimeVisibleTypeAnnotationsOffset = currentOffset
            } else if (Constants.RUNTIME_INVISIBLE_ANNOTATIONS == attributeName) {
                runtimeInvisibleAnnotationsOffset = currentOffset
            } else if (Constants.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS == attributeName) {
                runtimeInvisibleTypeAnnotationsOffset = currentOffset
            } else {
                val attribute =
                    readAttribute(
                        context.attributePrototypes,
                        attributeName,
                        currentOffset,
                        attributeLength,
                        charBuffer,
                        -1,
                        null
                    )
                attribute.nextAttribute = attributes
                attributes = attribute
            }
            currentOffset += attributeLength
        }

        val recordComponentVisitor =
            classVisitor.visitRecordComponent(name, descriptor, signature) ?: return currentOffset

        // Visit the RuntimeVisibleAnnotations attribute.
        if (runtimeVisibleAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeVisibleAnnotationsOffset)
            var currentAnnotationOffset = runtimeVisibleAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    recordComponentVisitor.visitAnnotation(annotationDescriptor, true),
                    currentAnnotationOffset, 
                    true,
                    charBuffer
                )
            }
        }

        // Visit the RuntimeInvisibleAnnotations attribute.
        if (runtimeInvisibleAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeInvisibleAnnotationsOffset)
            var currentAnnotationOffset = runtimeInvisibleAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    recordComponentVisitor.visitAnnotation(annotationDescriptor, false),
                    currentAnnotationOffset, 
                    true,
                    charBuffer
                )
            }
        }

        // Visit the RuntimeVisibleTypeAnnotations attribute.
        if (runtimeVisibleTypeAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeVisibleTypeAnnotationsOffset)
            var currentAnnotationOffset = runtimeVisibleTypeAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the target_type, target_info and target_path fields.
                currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset)
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    recordComponentVisitor.visitTypeAnnotation(
                        context.currentTypeAnnotationTarget,
                        context.currentTypeAnnotationTargetPath,
                        annotationDescriptor,
                        true
                    ),
                    currentAnnotationOffset,
                    true,
                    charBuffer
                )
            }
        }

        // Visit the RuntimeInvisibleTypeAnnotations attribute.
        if (runtimeInvisibleTypeAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeInvisibleTypeAnnotationsOffset)
            var currentAnnotationOffset = runtimeInvisibleTypeAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the target_type, target_info and target_path fields.
                currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset)
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    recordComponentVisitor.visitTypeAnnotation(
                        context.currentTypeAnnotationTarget,
                        context.currentTypeAnnotationTargetPath,
                        annotationDescriptor,
                        false
                    ),
                    currentAnnotationOffset,
                    true,
                    charBuffer
                )
            }
        }

        // Visit the non standard attributes.
        while (attributes != null) {
            // Copy and reset the nextAttribute field so that it can also be used in FieldWriter.
            val nextAttribute = attributes.nextAttribute
            attributes.nextAttribute = null
            recordComponentVisitor.visitAttribute(attributes)
            attributes = nextAttribute
        }

        // Visit the end of the field.
        recordComponentVisitor.visitEnd()
        return currentOffset
    }

    /**
     * Reads a JVMS field_info structure and makes the given visitor visit it.
     *
     * @param classVisitor the visitor that must visit the field.
     * @param context information about the class being parsed.
     * @param fieldInfoOffset the start offset of the field_info structure.
     * @return the offset of the first byte following the field_info structure.
     */
    private fun readField(
        classVisitor: ClassVisitor,
        context: Context,
        fieldInfoOffset: Int
    ): Int {
        val charBuffer = context.charBuffer

        // Read the access_flags, name_index and descriptor_index fields.
        var currentOffset = fieldInfoOffset
        var accessFlags = readUnsignedShort(currentOffset)
        val name = readUTF8(currentOffset + 2, charBuffer)
        val descriptor = readUTF8(currentOffset + 4, charBuffer)
        currentOffset += 6

        // Read the field attributes (the variables are ordered as in Section 4.7 of the JVMS).
        // Attribute offsets exclude the attribute_name_index and attribute_length fields.
        // - The value corresponding to the ConstantValue attribute, or null.
        var constantValue: Any? = null
        // - The string corresponding to the Signature attribute, or null.
        var signature: String? = null
        // - The offset of the RuntimeVisibleAnnotations attribute, or 0.
        var runtimeVisibleAnnotationsOffset = 0
        // - The offset of the RuntimeInvisibleAnnotations attribute, or 0.
        var runtimeInvisibleAnnotationsOffset = 0
        // - The offset of the RuntimeVisibleTypeAnnotations attribute, or 0.
        var runtimeVisibleTypeAnnotationsOffset = 0
        // - The offset of the RuntimeInvisibleTypeAnnotations attribute, or 0.
        var runtimeInvisibleTypeAnnotationsOffset = 0
        // - The non standard attributes (linked with their {@link Attribute#nextAttribute} field).
        //   This list in the <i>reverse order</i> or their order in the ClassFile structure.
        var attributes: Attribute? = null

        val attributesCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(attributesCount) {
            // Read the attribute_info's attribute_name and attribute_length fields.
            val attributeName = readUTF8(currentOffset, charBuffer)
            val attributeLength = readInt(currentOffset + 2)
            currentOffset += 6
            // The tests are sorted in decreasing frequency order (based on frequencies observed on
            // typical classes).
            if (Constants.CONSTANT_VALUE == attributeName) {
                val constantvalueIndex = readUnsignedShort(currentOffset)
                constantValue =
                    if (constantvalueIndex == 0) null
                    else readConst(constantvalueIndex, charBuffer)
            } else if (Constants.SIGNATURE == attributeName) {
                signature = readUTF8(currentOffset, charBuffer)
            } else if (Constants.DEPRECATED == attributeName) {
                accessFlags = accessFlags or Opcodes.ACC_DEPRECATED
            } else if (Constants.SYNTHETIC == attributeName) {
                accessFlags = accessFlags or Opcodes.ACC_SYNTHETIC
            } else if (Constants.RUNTIME_VISIBLE_ANNOTATIONS == attributeName) {
                runtimeVisibleAnnotationsOffset = currentOffset
            } else if (Constants.RUNTIME_VISIBLE_TYPE_ANNOTATIONS == attributeName) {
                runtimeVisibleTypeAnnotationsOffset = currentOffset
            } else if (Constants.RUNTIME_INVISIBLE_ANNOTATIONS == attributeName) {
                runtimeInvisibleAnnotationsOffset = currentOffset
            } else if (Constants.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS == attributeName) {
                runtimeInvisibleTypeAnnotationsOffset = currentOffset
            } else {
                val attribute = readAttribute(
                    context.attributePrototypes,
                    attributeName,
                    currentOffset,
                    attributeLength,
                    charBuffer,
                    -1,
                    null
                )
                attribute.nextAttribute = attributes
                attributes = attribute
            }
            currentOffset += attributeLength
        }

        // Visit the field declaration.
        val fieldVisitor = classVisitor.visitField(accessFlags, name, descriptor, signature, constantValue)
            ?: return currentOffset

        // Visit the RuntimeVisibleAnnotations attribute.
        if (runtimeVisibleAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeVisibleAnnotationsOffset)
            var currentAnnotationOffset = runtimeVisibleAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    fieldVisitor.visitAnnotation(annotationDescriptor, true),
                    currentAnnotationOffset, 
                    true,
                    charBuffer
                )
            }
        }

        // Visit the RuntimeInvisibleAnnotations attribute.
        if (runtimeInvisibleAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeInvisibleAnnotationsOffset)
            var currentAnnotationOffset = runtimeInvisibleAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    fieldVisitor.visitAnnotation(annotationDescriptor, false),
                    currentAnnotationOffset,
                    true,
                    charBuffer
                )
            }
        }

        // Visit the RuntimeVisibleTypeAnnotations attribute.
        if (runtimeVisibleTypeAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeVisibleTypeAnnotationsOffset)
            var currentAnnotationOffset = runtimeVisibleTypeAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the target_type, target_info and target_path fields.
                currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset)
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    fieldVisitor.visitTypeAnnotation(
                        context.currentTypeAnnotationTarget,
                        context.currentTypeAnnotationTargetPath,
                        annotationDescriptor,
                        true
                    ),
                    currentAnnotationOffset,
                    true,
                    charBuffer
                )
            }
        }

        // Visit the RuntimeInvisibleTypeAnnotations attribute.
        if (runtimeInvisibleTypeAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeInvisibleTypeAnnotationsOffset)
            var currentAnnotationOffset = runtimeInvisibleTypeAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the target_type, target_info and target_path fields.
                currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset)
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset = readElementValues(
                    fieldVisitor.visitTypeAnnotation(
                        context.currentTypeAnnotationTarget,
                        context.currentTypeAnnotationTargetPath,
                        annotationDescriptor, 
                        false
                    ),
                    currentAnnotationOffset, 
                    true,
                    charBuffer
                )
            }
        }

        // Visit the non standard attributes.
        while (attributes != null) {
            // Copy and reset the nextAttribute field so that it can also be used in FieldWriter.
            val nextAttribute = attributes.nextAttribute
            attributes.nextAttribute = null
            fieldVisitor.visitAttribute(attributes)
            attributes = nextAttribute
        }

        // Visit the end of the field.
        fieldVisitor.visitEnd()
        return currentOffset
    }

    /**
     * Reads a JVMS method_info structure and makes the given visitor visit it.
     *
     * @param classVisitor the visitor that must visit the method.
     * @param context information about the class being parsed.
     * @param methodInfoOffset the start offset of the method_info structure.
     * @return the offset of the first byte following the method_info structure.
     */
    private fun readMethod(
        classVisitor: ClassVisitor,
        context: Context,
        methodInfoOffset: Int
    ): Int {
        val charBuffer = context.charBuffer

        // Read the access_flags, name_index and descriptor_index fields.
        var currentOffset = methodInfoOffset
        context.currentMethodAccessFlags = readUnsignedShort(currentOffset)
        context.currentMethodName = readUTF8(currentOffset + 2, charBuffer)
        context.currentMethodDescriptor = readUTF8(currentOffset + 4, charBuffer)
        currentOffset += 6

        // Read the method attributes (the variables are ordered as in Section 4.7 of the JVMS).
        // Attribute offsets exclude the attribute_name_index and attribute_length fields.
        // - The offset of the Code attribute, or 0.
        var codeOffset = 0
        // - The offset of the Exceptions attribute, or 0.
        var exceptionsOffset = 0
        // - The strings corresponding to the Exceptions attribute, or null.
        var exceptions: Array<String>? = null
        // - Whether the method has a Synthetic attribute.
        var synthetic = false
        // - The constant pool index contained in the Signature attribute, or 0.
        var signatureIndex = 0
        // - The offset of the RuntimeVisibleAnnotations attribute, or 0.
        var runtimeVisibleAnnotationsOffset = 0
        // - The offset of the RuntimeInvisibleAnnotations attribute, or 0.
        var runtimeInvisibleAnnotationsOffset = 0
        // - The offset of the RuntimeVisibleParameterAnnotations attribute, or 0.
        var runtimeVisibleParameterAnnotationsOffset = 0
        // - The offset of the RuntimeInvisibleParameterAnnotations attribute, or 0.
        var runtimeInvisibleParameterAnnotationsOffset = 0
        // - The offset of the RuntimeVisibleTypeAnnotations attribute, or 0.
        var runtimeVisibleTypeAnnotationsOffset = 0
        // - The offset of the RuntimeInvisibleTypeAnnotations attribute, or 0.
        var runtimeInvisibleTypeAnnotationsOffset = 0
        // - The offset of the AnnotationDefault attribute, or 0.
        var annotationDefaultOffset = 0
        // - The offset of the MethodParameters attribute, or 0.
        var methodParametersOffset = 0
        // - The non standard attributes (linked with their {@link Attribute#nextAttribute} field).
        //   This list in the <i>reverse order</i> or their order in the ClassFile structure.
        var attributes: Attribute? = null

        val attributesCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(attributesCount) {
            // Read the attribute_info's attribute_name and attribute_length fields.
            val attributeName = readUTF8(currentOffset, charBuffer)
            val attributeLength = readInt(currentOffset + 2)
            currentOffset += 6
            // The tests are sorted in decreasing frequency order (based on frequencies observed on
            // typical classes).
            if (Constants.CODE == attributeName) {
                if ((context.parsingOptions and SKIP_CODE) == 0) {
                    codeOffset = currentOffset
                }
            } else if (Constants.EXCEPTIONS == attributeName) {
                exceptionsOffset = currentOffset
                var currentExceptionOffset = exceptionsOffset + 2
                exceptions = Array(readUnsignedShort(exceptionsOffset)) {
                    val result = readClass(currentExceptionOffset, charBuffer)
                    currentExceptionOffset += 2
                    result
                }
            } else if (Constants.SIGNATURE == attributeName) {
                signatureIndex = readUnsignedShort(currentOffset)
            } else if (Constants.DEPRECATED == attributeName) {
                context.currentMethodAccessFlags = context.currentMethodAccessFlags or Opcodes.ACC_DEPRECATED
            } else if (Constants.RUNTIME_VISIBLE_ANNOTATIONS == attributeName) {
                runtimeVisibleAnnotationsOffset = currentOffset
            } else if (Constants.RUNTIME_VISIBLE_TYPE_ANNOTATIONS == attributeName) {
                runtimeVisibleTypeAnnotationsOffset = currentOffset
            } else if (Constants.ANNOTATION_DEFAULT == attributeName) {
                annotationDefaultOffset = currentOffset
            } else if (Constants.SYNTHETIC == attributeName) {
                synthetic = true
                context.currentMethodAccessFlags = context.currentMethodAccessFlags or Opcodes.ACC_SYNTHETIC
            } else if (Constants.RUNTIME_INVISIBLE_ANNOTATIONS == attributeName) {
                runtimeInvisibleAnnotationsOffset = currentOffset
            } else if (Constants.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS == attributeName) {
                runtimeInvisibleTypeAnnotationsOffset = currentOffset
            } else if (Constants.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS == attributeName) {
                runtimeVisibleParameterAnnotationsOffset = currentOffset
            } else if (Constants.RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS == attributeName) {
                runtimeInvisibleParameterAnnotationsOffset = currentOffset
            } else if (Constants.METHOD_PARAMETERS == attributeName) {
                methodParametersOffset = currentOffset
            } else {
                val attribute =
                    readAttribute(
                        context.attributePrototypes,
                        attributeName,
                        currentOffset,
                        attributeLength,
                        charBuffer,
                        -1,
                        null
                    )
                attribute.nextAttribute = attributes
                attributes = attribute
            }
            currentOffset += attributeLength
        }

        // Visit the method declaration.
        val methodVisitor =
            classVisitor.visitMethod(
                context.currentMethodAccessFlags,
                context.currentMethodName!!,
                context.currentMethodDescriptor!!,
                if (signatureIndex == 0) null else readUtf(signatureIndex, charBuffer),
                exceptions?.toList()
            )
        if (methodVisitor == null) {
            return currentOffset
        }

        // If the returned MethodVisitor is in fact a MethodWriter, it means there is no method
        // adapter between the reader and the writer. In this case, it might be possible to copy
        // the method attributes directly into the writer. If so, return early without visiting
        // the content of these attributes.
        if (methodVisitor is MethodWriter) {
            val methodWriter = methodVisitor as MethodWriter
            if (methodWriter.canCopyMethodAttributes(
                    this,
                    synthetic,
                    (context.currentMethodAccessFlags and Opcodes.ACC_DEPRECATED) != 0,
                    readUnsignedShort(methodInfoOffset + 4),
                    signatureIndex,
                    exceptionsOffset
                )
            ) {
                methodWriter.setMethodAttributesSource(methodInfoOffset, currentOffset - methodInfoOffset)
                return currentOffset
            }
        }

        // Visit the MethodParameters attribute.
        if (methodParametersOffset != 0 && (context.parsingOptions and SKIP_DEBUG) == 0) {
            val parametersCount = readByte(methodParametersOffset)
            var currentParameterOffset = methodParametersOffset + 1
            repeat(parametersCount) {
                // Read the name_index and access_flags fields and visit them.
                methodVisitor.visitParameter(
                    readUTF8(currentParameterOffset, charBuffer),
                    readUnsignedShort(currentParameterOffset + 2)
                )
                currentParameterOffset += 4
            }
        }

        // Visit the AnnotationDefault attribute.
        if (annotationDefaultOffset != 0) {
            val annotationVisitor = methodVisitor.visitAnnotationDefault()
            readElementValue(annotationVisitor, annotationDefaultOffset, null, charBuffer)
            annotationVisitor?.visitEnd()
        }

        // Visit the RuntimeVisibleAnnotations attribute.
        if (runtimeVisibleAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeVisibleAnnotationsOffset)
            var currentAnnotationOffset = runtimeVisibleAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset =
                    readElementValues(
                        methodVisitor.visitAnnotation(annotationDescriptor, true),
                        currentAnnotationOffset, 
                        true,
                        charBuffer
                    )
            }
        }

        // Visit the RuntimeInvisibleAnnotations attribute.
        if (runtimeInvisibleAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeInvisibleAnnotationsOffset)
            var currentAnnotationOffset = runtimeInvisibleAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset =
                    readElementValues(
                        methodVisitor.visitAnnotation(annotationDescriptor, false),
                        currentAnnotationOffset, 
                        true,
                        charBuffer
                    )
            }
        }

        // Visit the RuntimeVisibleTypeAnnotations attribute.
        if (runtimeVisibleTypeAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeVisibleTypeAnnotationsOffset)
            var currentAnnotationOffset = runtimeVisibleTypeAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the target_type, target_info and target_path fields.
                currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset)
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset =
                    readElementValues(
                        methodVisitor.visitTypeAnnotation(
                            context.currentTypeAnnotationTarget,
                            context.currentTypeAnnotationTargetPath,
                            annotationDescriptor, 
                            true
                        ),
                        currentAnnotationOffset, 
                        true,
                        charBuffer
                    )
            }
        }

        // Visit the RuntimeInvisibleTypeAnnotations attribute.
        if (runtimeInvisibleTypeAnnotationsOffset != 0) {
            val numAnnotations = readUnsignedShort(runtimeInvisibleTypeAnnotationsOffset)
            var currentAnnotationOffset = runtimeInvisibleTypeAnnotationsOffset + 2
            repeat(numAnnotations) {
                // Parse the target_type, target_info and target_path fields.
                currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset)
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                currentAnnotationOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentAnnotationOffset =
                    readElementValues(
                        methodVisitor.visitTypeAnnotation(
                            context.currentTypeAnnotationTarget,
                            context.currentTypeAnnotationTargetPath,
                            annotationDescriptor, 
                            false
                        ),
                        currentAnnotationOffset, 
                        true,
                        charBuffer
                    )
            }
        }

        // Visit the RuntimeVisibleParameterAnnotations attribute.
        if (runtimeVisibleParameterAnnotationsOffset != 0) {
            readParameterAnnotations(
                methodVisitor, context, runtimeVisibleParameterAnnotationsOffset, true
            )
        }

        // Visit the RuntimeInvisibleParameterAnnotations attribute.
        if (runtimeInvisibleParameterAnnotationsOffset != 0) {
            readParameterAnnotations(
                methodVisitor, context, runtimeInvisibleParameterAnnotationsOffset, false
            )
        }

        // Visit the non standard attributes.
        while (attributes != null) {
            // Copy and reset the nextAttribute field so that it can also be used in MethodWriter.
            val nextAttribute = attributes.nextAttribute
            attributes.nextAttribute = null
            methodVisitor.visitAttribute(attributes)
            attributes = nextAttribute
        }

        // Visit the Code attribute.
        if (codeOffset != 0) {
            methodVisitor.visitCode()
            readCode(methodVisitor, context, codeOffset)
        }

        // Visit the end of the method.
        methodVisitor.visitEnd()
        return currentOffset
    }

    // ----------------------------------------------------------------------------------------------
    // Methods to parse a Code attribute
    // ----------------------------------------------------------------------------------------------
    /**
     * Reads a JVMS 'Code' attribute and makes the given visitor visit it.
     *
     * @param methodVisitor the visitor that must visit the Code attribute.
     * @param context information about the class being parsed.
     * @param codeOffset the start offset in [.classFileBuffer] of the Code attribute, excluding
     * its attribute_name_index and attribute_length fields.
     */
    private fun readCode(
        methodVisitor: MethodVisitor,
        context: Context,
        codeOffset: Int
    ) {
        var currentOffset = codeOffset
        // Read the max_stack, max_locals and code_length fields.
        val classBuffer = classFileBuffer
        val charBuffer = context.charBuffer
        val maxStack = readUnsignedShort(currentOffset)
        val maxLocals = readUnsignedShort(currentOffset + 2)
        val codeLength = readInt(currentOffset + 4)
        currentOffset += 8
        require(codeLength <= classFileBuffer.size - currentOffset)


        // Read the bytecode 'code' array to create a label for each referenced instruction.
        val bytecodeStartOffset = currentOffset
        val bytecodeEndOffset = currentOffset + codeLength
        val labels = arrayOfNulls<Label>(codeLength + 1)
        context.currentMethodLabels = labels
        while (currentOffset < bytecodeEndOffset) {
            val bytecodeOffset = currentOffset - bytecodeStartOffset
            val opcode = classBuffer[currentOffset].toInt() and 0xFF
            when (opcode) {
                Opcodes.NOP, Opcodes.ACONST_NULL, Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1,
                Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.LCONST_0,
                Opcodes.LCONST_1, Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2, Opcodes.DCONST_0,
                Opcodes.DCONST_1, Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD,
                Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD, Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE,
                Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE,
                Opcodes.POP, Opcodes.POP2, Opcodes.DUP, Opcodes.DUP_X1, Opcodes.DUP_X2, Opcodes.DUP2, Opcodes.DUP2_X1,
                Opcodes.DUP2_X2, Opcodes.SWAP, Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD, Opcodes.ISUB,
                Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB, Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL,
                Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV, Opcodes.IREM, Opcodes.LREM, Opcodes.FREM,
                Opcodes.DREM, Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG, Opcodes.ISHL, Opcodes.LSHL,
                Opcodes.ISHR, Opcodes.LSHR, Opcodes.IUSHR, Opcodes.LUSHR, Opcodes.IAND, Opcodes.LAND, Opcodes.IOR,
                Opcodes.LOR, Opcodes.IXOR, Opcodes.LXOR, Opcodes.I2L, Opcodes.I2F, Opcodes.I2D, Opcodes.L2I,
                Opcodes.L2F, Opcodes.L2D, Opcodes.F2I, Opcodes.F2L, Opcodes.F2D, Opcodes.D2I, Opcodes.D2L, Opcodes.D2F,
                Opcodes.I2B, Opcodes.I2C, Opcodes.I2S, Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL,
                Opcodes.DCMPG, Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN,
                Opcodes.RETURN, Opcodes.ARRAYLENGTH, Opcodes.ATHROW, Opcodes.MONITORENTER, Opcodes.MONITOREXIT,
                Constants.ILOAD_0, Constants.ILOAD_1, Constants.ILOAD_2, Constants.ILOAD_3, Constants.LLOAD_0,
                Constants.LLOAD_1, Constants.LLOAD_2, Constants.LLOAD_3, Constants.FLOAD_0, Constants.FLOAD_1,
                Constants.FLOAD_2, Constants.FLOAD_3, Constants.DLOAD_0, Constants.DLOAD_1, Constants.DLOAD_2,
                Constants.DLOAD_3, Constants.ALOAD_0, Constants.ALOAD_1, Constants.ALOAD_2, Constants.ALOAD_3,
                Constants.ISTORE_0, Constants.ISTORE_1, Constants.ISTORE_2, Constants.ISTORE_3, Constants.LSTORE_0,
                Constants.LSTORE_1, Constants.LSTORE_2, Constants.LSTORE_3, Constants.FSTORE_0, Constants.FSTORE_1,
                Constants.FSTORE_2, Constants.FSTORE_3, Constants.DSTORE_0, Constants.DSTORE_1, Constants.DSTORE_2,
                Constants.DSTORE_3, Constants.ASTORE_0, Constants.ASTORE_1, Constants.ASTORE_2, Constants.ASTORE_3 ->
                    currentOffset += 1

                Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE, Opcodes.IF_ICMPEQ,
                Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE,
                Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE, Opcodes.GOTO, Opcodes.JSR, Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                    createLabel(bytecodeOffset + readShort(currentOffset + 1), labels)
                    currentOffset += 3
                }

                Constants.ASM_IFEQ, Constants.ASM_IFNE, Constants.ASM_IFLT, Constants.ASM_IFGE,
                Constants.ASM_IFGT, Constants.ASM_IFLE, Constants.ASM_IF_ICMPEQ, Constants.ASM_IF_ICMPNE,
                Constants.ASM_IF_ICMPLT, Constants.ASM_IF_ICMPGE, Constants.ASM_IF_ICMPGT,
                Constants.ASM_IF_ICMPLE, Constants.ASM_IF_ACMPEQ, Constants.ASM_IF_ACMPNE, Constants.ASM_GOTO,
                Constants.ASM_JSR, Constants.ASM_IFNULL, Constants.ASM_IFNONNULL -> {
                    createLabel(bytecodeOffset + readUnsignedShort(currentOffset + 1), labels)
                    currentOffset += 3
                }

                Constants.GOTO_W, Constants.JSR_W, Constants.ASM_GOTO_W -> {
                    createLabel(bytecodeOffset + readInt(currentOffset + 1), labels)
                    currentOffset += 5
                }

                Constants.WIDE -> currentOffset += when (classBuffer[currentOffset + 1].toInt() and 0xFF) {
                    Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD, Opcodes.LLOAD, Opcodes.DLOAD, Opcodes.ISTORE,
                    Opcodes.FSTORE, Opcodes.ASTORE, Opcodes.LSTORE, Opcodes.DSTORE, Opcodes.RET -> 4

                    Opcodes.IINC -> 6

                    else -> throw IllegalArgumentException()
                }

                Opcodes.TABLESWITCH -> {
                    // Skip 0 to 3 padding bytes.
                    currentOffset += 4 - (bytecodeOffset and 3)
                    // Read the default label and the number of table entries.
                    createLabel(bytecodeOffset + readInt(currentOffset), labels)
                    val numTableEntries = readInt(currentOffset + 8) - readInt(currentOffset + 4) + 1
                    currentOffset += 12
                    // Read the table labels.
                    repeat(numTableEntries) {
                        createLabel(bytecodeOffset + readInt(currentOffset), labels)
                        currentOffset += 4
                    }
                }

                Opcodes.LOOKUPSWITCH -> {
                    // Skip 0 to 3 padding bytes.
                    currentOffset += 4 - (bytecodeOffset and 3)
                    // Read the default label and the number of switch cases.
                    createLabel(bytecodeOffset + readInt(currentOffset), labels)
                    val numSwitchCases = readInt(currentOffset + 4)
                    currentOffset += 8
                    // Read the switch labels.
                    repeat(numSwitchCases) {
                        createLabel(bytecodeOffset + readInt(currentOffset + 4), labels)
                        currentOffset += 8
                    }
                }

                Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD, Opcodes.ISTORE,
                Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE, Opcodes.RET, Opcodes.BIPUSH,
                Opcodes.NEWARRAY, Opcodes.LDC ->
                    currentOffset += 2

                Opcodes.SIPUSH, Constants.LDC_W, Constants.LDC2_W, Opcodes.GETSTATIC, Opcodes.PUTSTATIC,
                Opcodes.GETFIELD, Opcodes.PUTFIELD, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC,
                Opcodes.NEW, Opcodes.ANEWARRAY, Opcodes.CHECKCAST, Opcodes.INSTANCEOF, Opcodes.IINC ->
                    currentOffset += 3

                Opcodes.INVOKEINTERFACE, Opcodes.INVOKEDYNAMIC -> currentOffset += 5

                Opcodes.MULTIANEWARRAY -> currentOffset += 4

                else -> throw IllegalArgumentException()
            }
        }

        // Read the 'exception_table_length' and 'exception_table' field to create a label for each
        // referenced instruction, and to make methodVisitor visit the corresponding try catch blocks.
        val exceptionTableLength = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(exceptionTableLength) {
            val start = createLabel(readUnsignedShort(currentOffset), labels)
            val end = createLabel(readUnsignedShort(currentOffset + 2), labels)
            val handler = createLabel(readUnsignedShort(currentOffset + 4), labels)
            val catchType = readUTF8(cpInfoOffsets[readUnsignedShort(currentOffset + 6)], charBuffer)
            currentOffset += 8
            methodVisitor.visitTryCatchBlock(start, end, handler, catchType)
        }

        // Read the Code attributes to create a label for each referenced instruction (the variables
        // are ordered as in Section 4.7 of the JVMS). Attribute offsets exclude the
        // attribute_name_index and attribute_length fields.
        // - The offset of the current 'stack_map_frame' in the StackMap[Table] attribute, or 0.
        // Initially, this is the offset of the first 'stack_map_frame' entry. Then this offset is
        // updated after each stack_map_frame is read.
        var stackMapFrameOffset = 0
        // - The end offset of the StackMap[Table] attribute, or 0.
        var stackMapTableEndOffset = 0
        // - Whether the stack map frames are compressed (i.e. in a StackMapTable) or not.
        var compressedFrames = true
        // - The offset of the LocalVariableTable attribute, or 0.
        var localVariableTableOffset = 0
        // - The offset of the LocalVariableTypeTable attribute, or 0.
        var localVariableTypeTableOffset = 0
        // - The offset of each 'type_annotation' entry in the RuntimeVisibleTypeAnnotations
        // attribute, or null.
        var visibleTypeAnnotationOffsets: IntArray? = null
        // - The offset of each 'type_annotation' entry in the RuntimeInvisibleTypeAnnotations
        // attribute, or null.
        var invisibleTypeAnnotationOffsets: IntArray? = null
        // - The non-standard attributes (linked with their {@link Attribute#nextAttribute} field).
        //   This list in the <i>reverse order</i> or their order in the ClassFile structure.
        var attributes: Attribute? = null

        val attributesCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(attributesCount) {
            // Read the attribute_info's attribute_name and attribute_length fields.
            val attributeName = readUTF8(currentOffset, charBuffer)
            val attributeLength = readInt(currentOffset + 2)
            currentOffset += 6
            if (Constants.LOCAL_VARIABLE_TABLE == attributeName) {
                if ((context.parsingOptions and SKIP_DEBUG) == 0) {
                    localVariableTableOffset = currentOffset
                    // Parse the attribute to find the corresponding (debug only) labels.
                    var currentLocalVariableTableOffset = currentOffset
                    val localVariableTableLength = readUnsignedShort(currentLocalVariableTableOffset)
                    currentLocalVariableTableOffset += 2
                    repeat(localVariableTableLength) {
                        val startPc = readUnsignedShort(currentLocalVariableTableOffset)
                        createDebugLabel(startPc, labels)
                        val length = readUnsignedShort(currentLocalVariableTableOffset + 2)
                        createDebugLabel(startPc + length, labels)
                        // Skip the name_index, descriptor_index and index fields (2 bytes each).
                        currentLocalVariableTableOffset += 10
                    }
                }
            } else if (Constants.LOCAL_VARIABLE_TYPE_TABLE == attributeName) {
                localVariableTypeTableOffset = currentOffset
                // Here we do not extract the labels corresponding to the attribute content. We assume they
                // are the same or a subset of those of the LocalVariableTable attribute.
            } else if (Constants.LINE_NUMBER_TABLE == attributeName) {
                if ((context.parsingOptions and SKIP_DEBUG) == 0) {
                    // Parse the attribute to find the corresponding (debug only) labels.
                    var currentLineNumberTableOffset = currentOffset
                    val lineNumberTableLength = readUnsignedShort(currentLineNumberTableOffset)
                    currentLineNumberTableOffset += 2
                    repeat(lineNumberTableLength) {
                        val startPc = readUnsignedShort(currentLineNumberTableOffset)
                        val lineNumber = readUnsignedShort(currentLineNumberTableOffset + 2)
                        currentLineNumberTableOffset += 4
                        createDebugLabel(startPc, labels)
                        labels[startPc]?.addLineNumber(lineNumber)
                    }
                }
            } else if (Constants.RUNTIME_VISIBLE_TYPE_ANNOTATIONS == attributeName) {
                visibleTypeAnnotationOffsets =
                    readTypeAnnotations(methodVisitor, context, currentOffset, true)
                // Here we do not extract the labels corresponding to the attribute content. This would
                // require a full parsing of the attribute, which would need to be repeated when parsing
                // the bytecode instructions (see below). Instead, the content of the attribute is read one
                // type annotation at a time (i.e. after a type annotation has been visited, the next type
                // annotation is read), and the labels it contains are also extracted one annotation at a
                // time. This assumes that type annotations are ordered by increasing bytecode offset.
            } else if (Constants.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS == attributeName) {
                invisibleTypeAnnotationOffsets =
                    readTypeAnnotations(methodVisitor, context, currentOffset, false)
                // Same comment as above for the RuntimeVisibleTypeAnnotations attribute.
            } else if (Constants.STACK_MAP_TABLE == attributeName) {
                if ((context.parsingOptions and SKIP_FRAMES) == 0) {
                    stackMapFrameOffset = currentOffset + 2
                    stackMapTableEndOffset = currentOffset + attributeLength
                }
                // Here we do not extract the labels corresponding to the attribute content. This would
                // require a full parsing of the attribute, which would need to be repeated when parsing
                // the bytecode instructions (see below). Instead, the content of the attribute is read one
                // frame at a time (i.e. after a frame has been visited, the next frame is read), and the
                // labels it contains are also extracted one frame at a time. Thanks to the ordering of
                // frames, having only a "one frame lookahead" is not a problem, i.e. it is not possible to
                // see an offset smaller than the offset of the current instruction and for which no Label
                // exist. Except for UNINITIALIZED type offsets. We solve this by parsing the stack map
                // table without a full decoding (see below).
            } else if ("StackMap" == attributeName) {
                if ((context.parsingOptions and SKIP_FRAMES) == 0) {
                    stackMapFrameOffset = currentOffset + 2
                    stackMapTableEndOffset = currentOffset + attributeLength
                    compressedFrames = false
                }
                // IMPORTANT! Here we assume that the frames are ordered, as in the StackMapTable attribute,
                // although this is not guaranteed by the attribute format. This allows an incremental
                // extraction of the labels corresponding to this attribute (see the comment above for the
                // StackMapTable attribute).
            } else {
                val attribute =
                    readAttribute(
                        context.attributePrototypes,
                        attributeName,
                        currentOffset,
                        attributeLength,
                        charBuffer,
                        codeOffset,
                        labels.requireNoNulls()
                    )
                attribute.nextAttribute = attributes
                attributes = attribute
            }
            currentOffset += attributeLength
        }


        // Initialize the context fields related to stack map frames, and generate the first
        // (implicit) stack map frame, if needed.
        val expandFrames = (context.parsingOptions and EXPAND_FRAMES) != 0
        if (stackMapFrameOffset != 0) {
            // The bytecode offset of the first explicit frame is not offset_delta + 1 but only
            // offset_delta. Setting the implicit frame offset to -1 allows us to use of the
            // "offset_delta + 1" rule in all cases.
            context.currentFrameOffset = -1
            context.currentFrameType = 0
            context.currentFrameLocalCount = 0
            context.currentFrameLocalCountDelta = 0
            context.currentFrameLocalTypes = arrayOfNulls(maxLocals)
            context.currentFrameStackCount = 0
            context.currentFrameStackTypes = arrayOfNulls(maxStack)
            if (expandFrames) {
                computeImplicitFrame(context)
            }
            // Find the labels for UNINITIALIZED frame types. Instead of decoding each element of the
            // stack map table, we look for 3 consecutive bytes that "look like" an UNINITIALIZED type
            // (tag ITEM_Uninitialized, offset within bytecode bounds, NEW instruction at this offset).
            // We may find false positives (i.e. not real UNINITIALIZED types), but this should be rare,
            // and the only consequence will be the creation of an unneeded label. This is better than
            // creating a label for each NEW instruction, and faster than fully decoding the whole stack
            // map table.
            for (offset in stackMapFrameOffset..<stackMapTableEndOffset - 2) {
                if (classBuffer[offset].toInt() == Frame.ITEM_UNINITIALIZED) {
                    val potentialBytecodeOffset = readUnsignedShort(offset + 1)
                    if (potentialBytecodeOffset in 0..<codeLength
                        && ((classBuffer[bytecodeStartOffset + potentialBytecodeOffset] and 0xFF.toByte()).toInt() == Opcodes.NEW)
                    ) {
                        createLabel(potentialBytecodeOffset, labels)
                    }
                }
            }
        }
        if (expandFrames && (context.parsingOptions and EXPAND_ASM_INSNS) != 0) {
            // Expanding the ASM specific instructions can introduce F_INSERT frames, even if the method
            // does not currently have any frame. These inserted frames must be computed by simulating the
            // effect of the bytecode instructions, one by one, starting from the implicit first frame.
            // For this, MethodWriter needs to know maxLocals before the first instruction is visited. To
            // ensure this, we visit the implicit first frame here (passing only maxLocals - the rest is
            // computed in MethodWriter).
            methodVisitor.visitFrame(Opcodes.F_NEW, maxLocals, null, 0, null)
        }


        // Visit the bytecode instructions. First, introduce state variables for the incremental parsing
        // of the type annotations.

        // Index of the next runtime visible type annotation to read (in the
        // visibleTypeAnnotationOffsets array).
        var currentVisibleTypeAnnotationIndex = 0

        // The bytecode offset of the next runtime visible type annotation to read, or -1.
        var currentVisibleTypeAnnotationBytecodeOffset =
            getTypeAnnotationBytecodeOffset(visibleTypeAnnotationOffsets, 0)


        // Index of the next runtime invisible type annotation to read (in the
        // invisibleTypeAnnotationOffsets array).
        var currentInvisibleTypeAnnotationIndex = 0

        // The bytecode offset of the next runtime invisible type annotation to read, or -1.
        var currentInvisibleTypeAnnotationBytecodeOffset =
            getTypeAnnotationBytecodeOffset(invisibleTypeAnnotationOffsets, 0)


        // Whether a F_INSERT stack map frame must be inserted before the current instruction.
        var insertFrame = false


        // The delta to subtract from a goto_w or jsr_w opcode to get the corresponding goto or jsr
        // opcode, or 0 if goto_w and jsr_w must be left unchanged (i.e. when expanding ASM specific
        // instructions).
        val wideJumpOpcodeDelta =
            if ((context.parsingOptions and EXPAND_ASM_INSNS) == 0)
                Constants.WIDE_JUMP_OPCODE_DELTA
            else 0

        currentOffset = bytecodeStartOffset

        while (currentOffset < bytecodeEndOffset) {
            val currentBytecodeOffset = currentOffset - bytecodeStartOffset
            readBytecodeInstructionOffset(currentBytecodeOffset)


            // Visit the label and the line number(s) for this bytecode offset, if any.
            val currentLabel = labels[currentBytecodeOffset]
            currentLabel?.accept(
                methodVisitor,
                (context.parsingOptions and SKIP_DEBUG) == 0
            )

            // Visit the stack map frame for this bytecode offset, if any.
            while (stackMapFrameOffset != 0
                && (context.currentFrameOffset == currentBytecodeOffset
                        || context.currentFrameOffset == -1)) {
                // If there is a stack map frame for this offset, make methodVisitor visit it, and read the
                // next stack map frame if there is one.
                if (context.currentFrameOffset != -1) {
                    if (!compressedFrames || expandFrames) {
                        methodVisitor.visitFrame(
                            Opcodes.F_NEW,
                            context.currentFrameLocalCount,
                            context.currentFrameLocalTypes.toList(),
                            context.currentFrameStackCount,
                            context.currentFrameStackTypes.toList()
                        )
                    } else {
                        methodVisitor.visitFrame(
                            context.currentFrameType,
                            context.currentFrameLocalCountDelta,
                            context.currentFrameLocalTypes.toList(),
                            context.currentFrameStackCount,
                            context.currentFrameStackTypes.toList()
                        )
                    }
                    // Since there is already a stack map frame for this bytecode offset, there is no need to
                    // insert a new one.
                    insertFrame = false
                }
                stackMapFrameOffset = if (stackMapFrameOffset < stackMapTableEndOffset) {
                    readStackMapFrame(stackMapFrameOffset, compressedFrames, expandFrames, context)
                } else {
                    0
                }
            }


            // Insert a stack map frame for this bytecode offset, if requested by setting insertFrame to
            // true during the previous iteration. The actual frame content is computed in MethodWriter.
            if (insertFrame) {
                if ((context.parsingOptions and EXPAND_FRAMES) != 0) {
                    methodVisitor.visitFrame(
                        Constants.F_INSERT,
                        0,
                        null,
                        0,
                        null
                    )
                }
                insertFrame = false
            }


            // Visit the instruction at this bytecode offset.
            var opcode = classBuffer[currentOffset].toInt() and 0xFF
            when (opcode) {
                Opcodes.NOP, Opcodes.ACONST_NULL, Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1,
                Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.LCONST_0,
                Opcodes.LCONST_1, Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2, Opcodes.DCONST_0,
                Opcodes.DCONST_1, Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD,
                Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD, Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE,
                Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE, Opcodes.POP,
                Opcodes.POP2, Opcodes.DUP, Opcodes.DUP_X1, Opcodes.DUP_X2, Opcodes.DUP2, Opcodes.DUP2_X1,
                Opcodes.DUP2_X2, Opcodes.SWAP, Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD, Opcodes.ISUB,
                Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB, Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL,
                Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV, Opcodes.IREM, Opcodes.LREM, Opcodes.FREM,
                Opcodes.DREM, Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG, Opcodes.ISHL, Opcodes.LSHL,
                Opcodes.ISHR, Opcodes.LSHR, Opcodes.IUSHR, Opcodes.LUSHR, Opcodes.IAND, Opcodes.LAND, Opcodes.IOR,
                Opcodes.LOR, Opcodes.IXOR, Opcodes.LXOR, Opcodes.I2L, Opcodes.I2F, Opcodes.I2D, Opcodes.L2I,
                Opcodes.L2F, Opcodes.L2D, Opcodes.F2I, Opcodes.F2L, Opcodes.F2D, Opcodes.D2I, Opcodes.D2L, Opcodes.D2F,
                Opcodes.I2B, Opcodes.I2C, Opcodes.I2S, Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL,
                Opcodes.DCMPG, Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN,
                Opcodes.RETURN, Opcodes.ARRAYLENGTH, Opcodes.ATHROW, Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> {
                    methodVisitor.visitInsn(opcode)
                    currentOffset += 1
                }

                Constants.ILOAD_0, Constants.ILOAD_1, Constants.ILOAD_2, Constants.ILOAD_3, Constants.LLOAD_0,
                Constants.LLOAD_1, Constants.LLOAD_2, Constants.LLOAD_3, Constants.FLOAD_0, Constants.FLOAD_1,
                Constants.FLOAD_2, Constants.FLOAD_3, Constants.DLOAD_0, Constants.DLOAD_1, Constants.DLOAD_2,
                Constants.DLOAD_3, Constants.ALOAD_0, Constants.ALOAD_1, Constants.ALOAD_2, Constants.ALOAD_3 -> {
                    opcode -= Constants.ILOAD_0
                    methodVisitor.visitVarInsn(Opcodes.ILOAD + (opcode shr 2), opcode and 0x3)
                    currentOffset += 1
                }

                Constants.ISTORE_0, Constants.ISTORE_1, Constants.ISTORE_2, Constants.ISTORE_3, Constants.LSTORE_0,
                Constants.LSTORE_1, Constants.LSTORE_2, Constants.LSTORE_3, Constants.FSTORE_0, Constants.FSTORE_1,
                Constants.FSTORE_2, Constants.FSTORE_3, Constants.DSTORE_0, Constants.DSTORE_1, Constants.DSTORE_2,
                Constants.DSTORE_3, Constants.ASTORE_0, Constants.ASTORE_1, Constants.ASTORE_2, Constants.ASTORE_3 -> {
                    opcode -= Constants.ISTORE_0
                    methodVisitor.visitVarInsn(Opcodes.ISTORE + (opcode shr 2), opcode and 0x3)
                    currentOffset += 1
                }

                Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE, Opcodes.IF_ICMPEQ,
                Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE,
                Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE, Opcodes.GOTO, Opcodes.JSR, Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                    methodVisitor.visitJumpInsn(
                        opcode, labels[currentBytecodeOffset + readShort(currentOffset + 1)]!!
                    )
                    currentOffset += 3
                }

                Constants.GOTO_W, Constants.JSR_W -> {
                    methodVisitor.visitJumpInsn(
                        opcode - wideJumpOpcodeDelta,
                        labels[currentBytecodeOffset + readInt(currentOffset + 1)]!!
                    )
                    currentOffset += 5
                }

                Constants.ASM_IFEQ, Constants.ASM_IFNE, Constants.ASM_IFLT, Constants.ASM_IFGE,
                Constants.ASM_IFGT, Constants.ASM_IFLE, Constants.ASM_IF_ICMPEQ, Constants.ASM_IF_ICMPNE,
                Constants.ASM_IF_ICMPLT, Constants.ASM_IF_ICMPGE, Constants.ASM_IF_ICMPGT,
                Constants.ASM_IF_ICMPLE, Constants.ASM_IF_ACMPEQ, Constants.ASM_IF_ACMPNE,
                Constants.ASM_GOTO, Constants.ASM_JSR, Constants.ASM_IFNULL, Constants.ASM_IFNONNULL -> {
                    // A forward jump with an offset > 32767. In this case we automatically replace ASM_GOTO
                    // with GOTO_W, ASM_JSR with JSR_W and ASM_IFxxx <l> with IFNOTxxx <L> GOTO_W <l> L:...,
                    // where IFNOTxxx is the "opposite" opcode of ASMS_IFxxx (e.g. IFNE for ASM_IFEQ) and
                    // where <L> designates the instruction just after the GOTO_W.
                    // First, change the ASM specific opcodes ASM_IFEQ ... ASM_JSR, ASM_IFNULL and
                    // ASM_IFNONNULL to IFEQ ... JSR, IFNULL and IFNONNULL.
                    opcode =
                        if (opcode < Constants.ASM_IFNULL)
                            opcode - Constants.ASM_OPCODE_DELTA
                        else
                            opcode - Constants.ASM_IFNULL_OPCODE_DELTA
                    val target = labels[currentBytecodeOffset + readUnsignedShort(currentOffset + 1)]
                    if (opcode == Opcodes.GOTO || opcode == Opcodes.JSR) {
                        // Replace GOTO with GOTO_W and JSR with JSR_W.
                        methodVisitor.visitJumpInsn(opcode + Constants.WIDE_JUMP_OPCODE_DELTA, target!!)
                    } else {
                        // Compute the "opposite" of opcode. This can be done by flipping the least
                        // significant bit for IFNULL and IFNONNULL, and similarly for IFEQ ... IF_ACMPEQ
                        // (with a pre and post offset by 1).
                        opcode = if (opcode < Opcodes.GOTO) ((opcode + 1) xor 1) - 1 else opcode xor 1
                        val endif = createLabel(currentBytecodeOffset + 3, labels)
                        methodVisitor.visitJumpInsn(opcode, endif)
                        methodVisitor.visitJumpInsn(Constants.GOTO_W, target!!)
                        // endif designates the instruction just after GOTO_W, and is visited as part of the
                        // next instruction. Since it is a jump target, we need to insert a frame here.
                        insertFrame = true
                    }
                    currentOffset += 3
                }

                Constants.ASM_GOTO_W -> {
                    // Replace ASM_GOTO_W with GOTO_W.
                    methodVisitor.visitJumpInsn(
                        Constants.GOTO_W, labels[currentBytecodeOffset + readInt(currentOffset + 1)]!!
                    )
                    // The instruction just after is a jump target (because ASM_GOTO_W is used in patterns
                    // IFNOTxxx <L> ASM_GOTO_W <l> L:..., see MethodWriter), so we need to insert a frame
                    // here.
                    insertFrame = true
                    currentOffset += 5
                }

                Constants.WIDE -> {
                    opcode = (classBuffer[currentOffset + 1] and 0xFF.toByte()).toInt()
                    if (opcode == Opcodes.IINC) {
                        methodVisitor.visitIincInsn(
                            readUnsignedShort(currentOffset + 2),
                            readShort(currentOffset + 4).toInt()
                        )
                        currentOffset += 6
                    } else {
                        methodVisitor.visitVarInsn(opcode, readUnsignedShort(currentOffset + 2))
                        currentOffset += 4
                    }
                }

                Opcodes.TABLESWITCH -> {
                    // Skip 0 to 3 padding bytes.
                    currentOffset += 4 - (currentBytecodeOffset and 3)
                    // Read the instruction.
                    val defaultLabel = labels[currentBytecodeOffset + readInt(currentOffset)]
                    val low = readInt(currentOffset + 4)
                    val high = readInt(currentOffset + 8)
                    currentOffset += 12
                    val table = Array(high - low + 1) {
                        val result = labels[currentBytecodeOffset + readInt(currentOffset)]!!
                        currentOffset += 4
                        result
                    }
                    methodVisitor.visitTableSwitchInsn(low, high, defaultLabel!!, table.toList())
                }

                Opcodes.LOOKUPSWITCH -> {
                    // Skip 0 to 3 padding bytes.
                    currentOffset += 4 - (currentBytecodeOffset and 3)
                    // Read the instruction.
                    val defaultLabel = labels[currentBytecodeOffset + readInt(currentOffset)]
                    val numPairs = readInt(currentOffset + 4)
                    currentOffset += 8
                    val keys = IntArray(numPairs)
                    val values = arrayOfNulls<Label>(numPairs)
                    repeat(numPairs) {
                        keys[it] = readInt(currentOffset)
                        values[it] = labels[currentBytecodeOffset + readInt(currentOffset + 4)]
                        currentOffset += 8
                    }
                    methodVisitor.visitLookupSwitchInsn(
                        defaultLabel!!,
                        keys.toList(),
                        values.requireNoNulls().toList()
                    )
                }

                Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD, Opcodes.ISTORE,
                Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE, Opcodes.RET -> {
                    methodVisitor.visitVarInsn(opcode, (classBuffer[currentOffset + 1] and 0xFF.toByte()).toInt())
                    currentOffset += 2
                }

                Opcodes.BIPUSH, Opcodes.NEWARRAY -> {
                    methodVisitor.visitIntInsn(opcode, classBuffer[currentOffset + 1].toInt())
                    currentOffset += 2
                }

                Opcodes.SIPUSH -> {
                    methodVisitor.visitIntInsn(opcode, readShort(currentOffset + 1).toInt())
                    currentOffset += 3
                }

                Opcodes.LDC -> {
                    methodVisitor.visitLdcInsn(
                        readConst(
                            (classBuffer[currentOffset + 1] and 0xFF.toByte()).toInt(),
                            charBuffer
                        )
                    )
                    currentOffset += 2
                }

                Constants.LDC_W, Constants.LDC2_W -> {
                    methodVisitor.visitLdcInsn(
                        readConst(
                            readUnsignedShort(currentOffset + 1),
                            charBuffer
                        )
                    )
                    currentOffset += 3
                }

                Opcodes.GETSTATIC, Opcodes.PUTSTATIC, Opcodes.GETFIELD, Opcodes.PUTFIELD, Opcodes.INVOKEVIRTUAL,
                Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE -> {
                    val cpInfoOffset = cpInfoOffsets[readUnsignedShort(currentOffset + 1)]
                    val nameAndTypeCpInfoOffset = cpInfoOffsets[readUnsignedShort(cpInfoOffset + 2)]
                    val owner = readClass(cpInfoOffset, charBuffer)
                    val name = readUTF8(nameAndTypeCpInfoOffset, charBuffer)
                    val descriptor = readUTF8(nameAndTypeCpInfoOffset + 2, charBuffer)
                    if (opcode < Opcodes.INVOKEVIRTUAL) {
                        methodVisitor.visitFieldInsn(opcode, owner, name, descriptor)
                    } else {
                        val isInterface =
                            classBuffer[cpInfoOffset - 1].toInt() == Symbol.CONSTANT_INTERFACE_METHODREF_TAG
                        methodVisitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                    }
                    currentOffset +=
                        if (opcode == Opcodes.INVOKEINTERFACE) 5
                        else 3

                }

                Opcodes.INVOKEDYNAMIC -> {
                    // CONSTANT_InvokeDynamic_info
                    val cpInfoOffset = cpInfoOffsets[readUnsignedShort(currentOffset + 1)]
                    val nameAndTypeCpInfoOffset = cpInfoOffsets[readUnsignedShort(cpInfoOffset + 2)]
                    val name = readUTF8(nameAndTypeCpInfoOffset, charBuffer)
                    val descriptor = readUTF8(nameAndTypeCpInfoOffset + 2, charBuffer)
                    var bootstrapMethodOffset = bootstrapMethodOffsets!![readUnsignedShort(cpInfoOffset)]
                    val handle =
                        readConst(readUnsignedShort(bootstrapMethodOffset), charBuffer) as Handle

                    val bootstrapMethodArguments = run {
                        val bsmArgsCount = readUnsignedShort(bootstrapMethodOffset + 2)
                        bootstrapMethodOffset += 4
                        Array(bsmArgsCount) {
                            val result = readConst(readUnsignedShort(bootstrapMethodOffset), charBuffer)
                            bootstrapMethodOffset += 2
                            result
                        }
                    }
                    methodVisitor.visitInvokeDynamicInsn(
                        name, descriptor, handle, bootstrapMethodArguments
                    )
                    currentOffset += 5
                }

                Opcodes.NEW, Opcodes.ANEWARRAY, Opcodes.CHECKCAST, Opcodes.INSTANCEOF -> {
                    methodVisitor.visitTypeInsn(opcode, readClass(currentOffset + 1, charBuffer))
                    currentOffset += 3
                }

                Opcodes.IINC -> {
                    methodVisitor.visitIincInsn(
                        (classBuffer[currentOffset + 1] and 0xFF.toByte()).toInt(),
                        classBuffer[currentOffset + 2].toInt()
                    )
                    currentOffset += 3
                }

                Opcodes.MULTIANEWARRAY -> {
                    methodVisitor.visitMultiANewArrayInsn(
                        readClass(currentOffset + 1, charBuffer),
                        (classBuffer[currentOffset + 3] and 0xFF.toByte()).toInt()
                    )
                    currentOffset += 4
                }

                else -> throw IllegalStateException("Unexpected opcode $opcode")
            }

            // Visit the runtime visible instruction annotations, if any.
            while (visibleTypeAnnotationOffsets != null
                && currentVisibleTypeAnnotationIndex < visibleTypeAnnotationOffsets.size
                && currentVisibleTypeAnnotationBytecodeOffset <= currentBytecodeOffset) {
                if (currentVisibleTypeAnnotationBytecodeOffset == currentBytecodeOffset) {
                    // Parse the target_type, target_info and target_path fields.
                    var currentAnnotationOffset =
                        readTypeAnnotationTarget(
                            context, visibleTypeAnnotationOffsets[currentVisibleTypeAnnotationIndex]
                        )
                    // Parse the type_index field.
                    val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                    currentAnnotationOffset += 2
                    // Parse num_element_value_pairs and element_value_pairs and visit these values.
                    readElementValues(
                        methodVisitor.visitInsnAnnotation(
                            context.currentTypeAnnotationTarget,
                            context.currentTypeAnnotationTargetPath,
                            annotationDescriptor, 
                            true
                        ),
                        currentAnnotationOffset, 
                        true,
                        charBuffer
                    )
                }
                currentVisibleTypeAnnotationBytecodeOffset =
                    getTypeAnnotationBytecodeOffset(
                        visibleTypeAnnotationOffsets, ++currentVisibleTypeAnnotationIndex
                    )
            }


            // Visit the runtime invisible instruction annotations, if any.
            while (invisibleTypeAnnotationOffsets != null
                && currentInvisibleTypeAnnotationIndex < invisibleTypeAnnotationOffsets.size
                && currentInvisibleTypeAnnotationBytecodeOffset <= currentBytecodeOffset) {
                if (currentInvisibleTypeAnnotationBytecodeOffset == currentBytecodeOffset) {
                    // Parse the target_type, target_info and target_path fields.
                    var currentAnnotationOffset =
                        readTypeAnnotationTarget(
                            context, invisibleTypeAnnotationOffsets[currentInvisibleTypeAnnotationIndex]
                        )
                    // Parse the type_index field.
                    val annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer)
                    currentAnnotationOffset += 2
                    // Parse num_element_value_pairs and element_value_pairs and visit these values.
                    readElementValues(
                        methodVisitor.visitInsnAnnotation(
                            context.currentTypeAnnotationTarget,
                            context.currentTypeAnnotationTargetPath,
                            annotationDescriptor, 
                            false
                        ),
                        currentAnnotationOffset, 
                        true,
                        charBuffer
                    )
                }
                currentInvisibleTypeAnnotationBytecodeOffset =
                    getTypeAnnotationBytecodeOffset(
                        invisibleTypeAnnotationOffsets, ++currentInvisibleTypeAnnotationIndex
                    )
            }
        }

        labels[codeLength]?.let {
            methodVisitor.visitLabel(it)
        }

        // Visit LocalVariableTable and LocalVariableTypeTable attributes.
        if (localVariableTableOffset != 0 && (context.parsingOptions and SKIP_DEBUG) == 0) {
            // The (start_pc, index, signature_index) fields of each entry of the LocalVariableTypeTable.
            var typeTable: IntArray? = null
            if (localVariableTypeTableOffset != 0) {
                typeTable = IntArray(readUnsignedShort(localVariableTypeTableOffset) * 3)
                currentOffset = localVariableTypeTableOffset + 2
                var typeTableIndex = typeTable.size
                while (typeTableIndex > 0) {
                    // Store the offset of 'signature_index', and the value of 'index' and 'start_pc'.
                    typeTable[--typeTableIndex] = currentOffset + 6
                    typeTable[--typeTableIndex] = readUnsignedShort(currentOffset + 8)
                    typeTable[--typeTableIndex] = readUnsignedShort(currentOffset)
                    currentOffset += 10
                }
            }
            val localVariableTableLength = readUnsignedShort(localVariableTableOffset)
            currentOffset = localVariableTableOffset + 2
            repeat(localVariableTableLength) {
                val startPc = readUnsignedShort(currentOffset)
                val length = readUnsignedShort(currentOffset + 2)
                val name = readUTF8(currentOffset + 4, charBuffer)
                val descriptor = readUTF8(currentOffset + 6, charBuffer)
                val index = readUnsignedShort(currentOffset + 8)
                currentOffset += 10
                var signature: String? = null
                if (typeTable != null) {
                    var i = 0
                    while (i < typeTable.size) {
                        if (typeTable[i] == startPc && typeTable[i + 1] == index) {
                            signature = readUTF8(typeTable[i + 2], charBuffer)
                            break
                        }
                        i += 3
                    }
                }
                methodVisitor.visitLocalVariable(
                    name, descriptor, signature, labels[startPc]!!, labels[startPc + length]!!, index
                )
            }
        }


        // Visit the local variable type annotations of the RuntimeVisibleTypeAnnotations attribute.
        if (visibleTypeAnnotationOffsets != null) {
            for (typeAnnotationOffset in visibleTypeAnnotationOffsets) {
                val targetType = readByte(typeAnnotationOffset)
                if (targetType == TypeReference.LOCAL_VARIABLE
                    || targetType == TypeReference.RESOURCE_VARIABLE
                ) {
                    // Parse the target_type, target_info and target_path fields.
                    currentOffset = readTypeAnnotationTarget(context, typeAnnotationOffset)
                    // Parse the type_index field.
                    val annotationDescriptor = readUTF8(currentOffset, charBuffer)
                    currentOffset += 2
                    // Parse num_element_value_pairs and element_value_pairs and visit these values.
                    readElementValues(
                        methodVisitor.visitLocalVariableAnnotation(
                            context.currentTypeAnnotationTarget,
                            context.currentTypeAnnotationTargetPath,
                            context.currentLocalVariableAnnotationRangeStarts.requireNoNulls().toList(),
                            context.currentLocalVariableAnnotationRangeEnds.requireNoNulls().toList(),
                            context.currentLocalVariableAnnotationRangeIndices.toList(),
                            annotationDescriptor, 
                            true
                        ),
                        currentOffset, 
                        true,
                        charBuffer
                    )
                }
            }
        }

        // Visit the local variable type annotations of the RuntimeInvisibleTypeAnnotations attribute.
        if (invisibleTypeAnnotationOffsets != null) {
            for (typeAnnotationOffset in invisibleTypeAnnotationOffsets) {
                val targetType = readByte(typeAnnotationOffset)
                if (targetType == TypeReference.LOCAL_VARIABLE
                    || targetType == TypeReference.RESOURCE_VARIABLE
                ) {
                    // Parse the target_type, target_info and target_path fields.
                    currentOffset = readTypeAnnotationTarget(context, typeAnnotationOffset)
                    // Parse the type_index field.
                    val annotationDescriptor = readUTF8(currentOffset, charBuffer)
                    currentOffset += 2
                    // Parse num_element_value_pairs and element_value_pairs and visit these values.
                    readElementValues(
                        methodVisitor.visitLocalVariableAnnotation(
                            context.currentTypeAnnotationTarget,
                            context.currentTypeAnnotationTargetPath,
                            context.currentLocalVariableAnnotationRangeStarts.requireNoNulls().toList(),
                            context.currentLocalVariableAnnotationRangeEnds.requireNoNulls().toList(),
                            context.currentLocalVariableAnnotationRangeIndices.toList(),
                            annotationDescriptor, 
                            false
                        ),
                        currentOffset, 
                        true,
                        charBuffer
                    )
                }
            }
        }

        // Visit the non-standard attributes.
        while (attributes != null) {
            // Copy and reset the nextAttribute field so that it can also be used in MethodWriter.
            val nextAttribute = attributes.nextAttribute
            attributes.nextAttribute = null
            methodVisitor.visitAttribute(attributes)
            attributes = nextAttribute
        }

        // Visit the max stack and max locals values.
        methodVisitor.visitMaxs(maxStack, maxLocals)
    }

    /**
     * Handles the bytecode offset of the next instruction to be visited in [ ][.accept]. This method is called just before the instruction and before its
     * associated label and stack map frame, if any. The default implementation of this method does
     * nothing. Subclasses can override this method to store the argument in a mutable field, for
     * instance, so that [MethodVisitor] instances can get the bytecode offset of each visited
     * instruction (if so, the usual concurrency issues related to mutable data should be addressed).
     *
     * @param bytecodeOffset the bytecode offset of the next instruction to be visited.
     */
    protected fun readBytecodeInstructionOffset(bytecodeOffset: Int) {
        // Do nothing by default.
    }

    /**
     * Returns the label corresponding to the given bytecode offset. The default implementation of
     * this method creates a label for the given offset if it has not been already created.
     *
     * @param bytecodeOffset a bytecode offset in a method.
     * @param labels the already created labels, indexed by their offset. If a label already exists
     * for bytecodeOffset this method must not create a new one. Otherwise it must store the new
     * label in this array.
     * @return a non null Label, which must be equal to labels[bytecodeOffset].
     */
    fun readLabel(bytecodeOffset: Int, labels: Array<Label?>): Label {
        val current = labels[bytecodeOffset]
        if (current == null) {
            val new = Label()
            labels[bytecodeOffset] = new
            return new
        }
        return current
    }

    /**
     * Creates a label without the [Label.FLAG_DEBUG_ONLY] flag set, for the given bytecode
     * offset. The label is created with a call to [.readLabel] and its [ ][Label.FLAG_DEBUG_ONLY] flag is cleared.
     *
     * @param bytecodeOffset a bytecode offset in a method.
     * @param labels the already created labels, indexed by their offset.
     * @return a Label without the [Label.FLAG_DEBUG_ONLY] flag set.
     */
    private fun createLabel(bytecodeOffset: Int, labels: Array<Label?>): Label {
        val label = readLabel(bytecodeOffset, labels)
        label.flags = label.flags and Label.FLAG_DEBUG_ONLY.inv()
        return label
    }

    /**
     * Creates a label with the [Label.FLAG_DEBUG_ONLY] flag set, if there is no already
     * existing label for the given bytecode offset (otherwise does nothing). The label is created
     * with a call to [.readLabel].
     * 
     * @param bytecodeOffset a bytecode offset in a method.
     * @param labels the already created labels, indexed by their offset.
     */
    private fun createDebugLabel(bytecodeOffset: Int, labels: Array<Label?>) {
        if (labels[bytecodeOffset] == null) {
            readLabel(bytecodeOffset, labels).flags =
                readLabel(bytecodeOffset, labels).flags or Label.FLAG_DEBUG_ONLY
        }
    }


    // ----------------------------------------------------------------------------------------------
    // Methods to parse annotations, type annotations and parameter annotations
    // ----------------------------------------------------------------------------------------------
    /**
     * Parses a Runtime[In]VisibleTypeAnnotations attribute to find the offset of each type_annotation
     * entry it contains, to find the corresponding labels, and to visit the try catch block
     * annotations.
     * 
     * @param methodVisitor the method visitor to be used to visit the try catch block annotations.
     * @param context information about the class being parsed.
     * @param runtimeTypeAnnotationsOffset the start offset of a Runtime[In]VisibleTypeAnnotations
     * attribute, excluding the attribute_info's attribute_name_index and attribute_length fields.
     * @param visible true if the attribute to parse is a RuntimeVisibleTypeAnnotations attribute,
     * false it is a RuntimeInvisibleTypeAnnotations attribute.
     * @return the start offset of each entry of the Runtime[In]VisibleTypeAnnotations_attribute's
     * 'annotations' array field.
     */
    private fun readTypeAnnotations(
        methodVisitor: MethodVisitor,
        context: Context,
        runtimeTypeAnnotationsOffset: Int,
        visible: Boolean
    ): IntArray {
        val charBuffer = context.charBuffer
        var currentOffset = runtimeTypeAnnotationsOffset
        // Read the num_annotations field and create an array to store the type_annotation offsets.
        val typeAnnotationsOffsets = IntArray(readUnsignedShort(currentOffset))
        currentOffset += 2
        // Parse the 'annotations' array field.
        for (i in typeAnnotationsOffsets.indices) {
            typeAnnotationsOffsets[i] = currentOffset
            // Parse the type_annotation's target_type and the target_info fields. The size of the
            // target_info field depends on the value of target_type.
            val targetType = readInt(currentOffset)
            when (targetType ushr 24) {
                TypeReference.LOCAL_VARIABLE, TypeReference.RESOURCE_VARIABLE -> {
                    // A localvar_target has a variable size, which depends on the value of their table_length
                    // field. It also references bytecode offsets, for which we need labels.
                    val tableLength = readUnsignedShort(currentOffset + 1)
                    currentOffset += 3
                    repeat(tableLength) {
                        val startPc = readUnsignedShort(currentOffset)
                        val length = readUnsignedShort(currentOffset + 2)
                        // Skip the index field (2 bytes).
                        currentOffset += 6
                        createLabel(startPc, context.currentMethodLabels)
                        createLabel(startPc + length, context.currentMethodLabels)
                    }
                }

                TypeReference.CAST,
                TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT,
                TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT,
                TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT,
                TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT -> currentOffset += 4

                TypeReference.CLASS_EXTENDS,
                TypeReference.CLASS_TYPE_PARAMETER_BOUND,
                TypeReference.METHOD_TYPE_PARAMETER_BOUND,
                TypeReference.THROWS,
                TypeReference.EXCEPTION_PARAMETER,
                TypeReference.INSTANCEOF,
                TypeReference.NEW,
                TypeReference.CONSTRUCTOR_REFERENCE,
                TypeReference.METHOD_REFERENCE -> currentOffset += 3

                TypeReference.CLASS_TYPE_PARAMETER,
                TypeReference.METHOD_TYPE_PARAMETER,
                TypeReference.METHOD_FORMAL_PARAMETER,
                TypeReference.FIELD,
                TypeReference.METHOD_RETURN,
                TypeReference.METHOD_RECEIVER ->
                    // TypeReference type which can't be used in Code attribute, or which is unknown.
                    throw IllegalArgumentException()

                else ->
                    throw IllegalArgumentException()
            }
            // Parse the rest of the type_annotation structure, starting with the target_path structure
            // (whose size depends on its path_length field).
            val pathLength = readByte(currentOffset)
            if ((targetType ushr 24) == TypeReference.EXCEPTION_PARAMETER) {
                // Parse the target_path structure and create a corresponding TypePath.
                val path =
                    if (pathLength == 0) null
                    else TypePath(classFileBuffer, currentOffset)

                currentOffset += 1 + 2 * pathLength
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentOffset, charBuffer)
                currentOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentOffset =
                    readElementValues(
                        methodVisitor.visitTryCatchAnnotation(
                            targetType and -0x100, path, annotationDescriptor, visible
                        ),
                        currentOffset, 
                        true,
                        charBuffer
                    )
            } else {
                // We don't want to visit the other target_type annotations, so we just skip them (which
                // requires some parsing because the element_value_pairs array has a variable size). First,
                // skip the target_path structure:
                currentOffset += 3 + 2 * pathLength
                // Then skip the num_element_value_pairs and element_value_pairs fields (by reading them
                // with a null AnnotationVisitor).
                currentOffset =
                    readElementValues( /* annotationVisitor= */
                        null, currentOffset, true, charBuffer
                    )
            }
        }
        return typeAnnotationsOffsets
    }

    /**
     * Returns the bytecode offset corresponding to the specified JVMS 'type_annotation' structure, or
     * -1 if there is no such type_annotation of if it does not have a bytecode offset.
     *
     * @param typeAnnotationOffsets the offset of each 'type_annotation' entry in a
     * Runtime[In]VisibleTypeAnnotations attribute, or null.
     * @param typeAnnotationIndex the index a 'type_annotation' entry in typeAnnotationOffsets.
     * @return bytecode offset corresponding to the specified JVMS 'type_annotation' structure, or -1
     * if there is no such type_annotation of if it does not have a bytecode offset.
     */
    private fun getTypeAnnotationBytecodeOffset(
        typeAnnotationOffsets: IntArray?,
        typeAnnotationIndex: Int
    ): Int {
        if (typeAnnotationOffsets == null
            || typeAnnotationIndex >= typeAnnotationOffsets.size
            || readByte(typeAnnotationOffsets[typeAnnotationIndex]) < TypeReference.INSTANCEOF
        ) return -1

        return readUnsignedShort(typeAnnotationOffsets[typeAnnotationIndex] + 1)
    }

    /**
     * Parses the header of a JVMS type_annotation structure to extract its target_type, target_info
     * and target_path (the result is stored in the given context), and returns the start offset of
     * the rest of the type_annotation structure.
     *
     * @param context information about the class being parsed. This is where the extracted
     * target_type and target_path must be stored.
     * @param typeAnnotationOffset the start offset of a type_annotation structure.
     * @return the start offset of the rest of the type_annotation structure.
     */
    private fun readTypeAnnotationTarget(context: Context, typeAnnotationOffset: Int): Int {
        var currentOffset = typeAnnotationOffset
        // Parse and store the target_type structure.
        var targetType = readInt(typeAnnotationOffset)
        when (targetType ushr 24) {
            TypeReference.CLASS_TYPE_PARAMETER,
            TypeReference.METHOD_TYPE_PARAMETER,
            TypeReference.METHOD_FORMAL_PARAMETER -> {
                targetType = targetType and -0x10000
                currentOffset += 2
            }

            TypeReference.FIELD,
            TypeReference.METHOD_RETURN,
            TypeReference.METHOD_RECEIVER -> {
                targetType = targetType and -0x1000000
                currentOffset += 1
            }

            TypeReference.LOCAL_VARIABLE,
            TypeReference.RESOURCE_VARIABLE -> {
                targetType = targetType and -0x1000000
                val tableLength = readUnsignedShort(currentOffset + 1)
                currentOffset += 3
                context.currentLocalVariableAnnotationRangeStarts = arrayOfNulls(tableLength)
                context.currentLocalVariableAnnotationRangeEnds = arrayOfNulls(tableLength)
                context.currentLocalVariableAnnotationRangeIndices = IntArray(tableLength)
                repeat(tableLength) {
                    val startPc = readUnsignedShort(currentOffset)
                    val length = readUnsignedShort(currentOffset + 2)
                    val index = readUnsignedShort(currentOffset + 4)
                    currentOffset += 6
                    context.currentLocalVariableAnnotationRangeStarts[it] =
                        createLabel(startPc, context.currentMethodLabels)
                    context.currentLocalVariableAnnotationRangeEnds[it] =
                        createLabel(startPc + length, context.currentMethodLabels)
                    context.currentLocalVariableAnnotationRangeIndices[it] = index
                }
            }

            TypeReference.CAST,
            TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT,
            TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT,
            TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT,
            TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT -> {
                targetType = targetType and -0xffff01
                currentOffset += 4
            }

            TypeReference.CLASS_EXTENDS,
            TypeReference.CLASS_TYPE_PARAMETER_BOUND,
            TypeReference.METHOD_TYPE_PARAMETER_BOUND,
            TypeReference.THROWS,
            TypeReference.EXCEPTION_PARAMETER -> {
                targetType = targetType and -0x100
                currentOffset += 3
            }

            TypeReference.INSTANCEOF,
            TypeReference.NEW,
            TypeReference.CONSTRUCTOR_REFERENCE,
            TypeReference.METHOD_REFERENCE -> {
                targetType = targetType and -0x1000000
                currentOffset += 3
            }

            else -> throw IllegalArgumentException()
        }
        context.currentTypeAnnotationTarget = targetType
        // Parse and store the target_path structure.
        val pathLength = readByte(currentOffset)
        context.currentTypeAnnotationTargetPath =
            if (pathLength == 0) null
            else TypePath(classFileBuffer, currentOffset)
        // Return the start offset of the rest of the type_annotation structure.
        return currentOffset + 1 + 2 * pathLength
    }

    /**
     * Reads a Runtime[In]VisibleParameterAnnotations attribute and makes the given visitor visit it.
     * 
     * @param methodVisitor the visitor that must visit the parameter annotations.
     * @param context information about the class being parsed.
     * @param runtimeParameterAnnotationsOffset the start offset of a
     * Runtime[In]VisibleParameterAnnotations attribute, excluding the attribute_info's
     * attribute_name_index and attribute_length fields.
     * @param visible true if the attribute to parse is a RuntimeVisibleParameterAnnotations
     * attribute, false it is a RuntimeInvisibleParameterAnnotations attribute.
     */
    private fun readParameterAnnotations(
        methodVisitor: MethodVisitor,
        context: Context,
        runtimeParameterAnnotationsOffset: Int,
        visible: Boolean
    ) {
        var currentOffset = runtimeParameterAnnotationsOffset
        val numParameters: Int = (classFileBuffer[currentOffset++] and 0xFF.toByte()).toInt()
        methodVisitor.visitAnnotableParameterCount(numParameters, visible)
        val charBuffer = context.charBuffer
        for (i in 0..<numParameters) {
            val numAnnotations = readUnsignedShort(currentOffset)
            currentOffset += 2
            repeat(numAnnotations) {
                // Parse the type_index field.
                val annotationDescriptor = readUTF8(currentOffset, charBuffer)
                currentOffset += 2
                // Parse num_element_value_pairs and element_value_pairs and visit these values.
                currentOffset =
                    readElementValues(
                        methodVisitor.visitParameterAnnotation(i, annotationDescriptor, visible),
                        currentOffset, 
                        true,
                        charBuffer
                    )
            }
        }
    }

    /**
     * Reads the element values of a JVMS 'annotation' structure and makes the given visitor visit
     * them. This method can also be used to read the values of the JVMS 'array_value' field of an
     * annotation's 'element_value'.
     * 
     * @param annotationVisitor the visitor that must visit the values.
     * @param annotationOffset the start offset of an 'annotation' structure (excluding its type_index
     * field) or of an 'array_value' structure.
     * @param named if the annotation values are named or not. This should be true to parse the values
     * of a JVMS 'annotation' structure, and false to parse the JVMS 'array_value' of an
     * annotation's element_value.
     * @param charBuffer the buffer used to read strings in the constant pool.
     * @return the end offset of the JVMS 'annotation' or 'array_value' structure.
     */
    private fun readElementValues(
        annotationVisitor: AnnotationVisitor?,
        annotationOffset: Int,
        named: Boolean,
        charBuffer: CharArray
    ): Int {
        var currentOffset = annotationOffset
        // Read the num_element_value_pairs field (or num_values field for an array_value).
        val numElementValuePairs = readUnsignedShort(currentOffset)
        currentOffset += 2
        if (named) {
            // Parse the element_value_pairs array.
            repeat(numElementValuePairs) {
                val elementName = readUTF8(currentOffset, charBuffer)
                currentOffset =
                    readElementValue(annotationVisitor, currentOffset + 2, elementName, charBuffer)
            }
        } else {
            // Parse the array_value array.
            repeat(numElementValuePairs) {
                currentOffset =
                    readElementValue(annotationVisitor, currentOffset, null, charBuffer)
            }
        }
        annotationVisitor?.visitEnd()
        return currentOffset
    }

    /**
     * Reads a JVMS 'element_value' structure and makes the given visitor visit it.
     * 
     * @param annotationVisitor the visitor that must visit the element_value structure.
     * @param elementValueOffset the start offset in [.classFileBuffer] of the element_value
     * structure to be read.
     * @param elementName the name of the element_value structure to be read, or null.
     * @param charBuffer the buffer used to read strings in the constant pool.
     * @return the end offset of the JVMS 'element_value' structure.
     */
    private fun readElementValue(
        annotationVisitor: AnnotationVisitor?,
        elementValueOffset: Int,
        elementName: String?,
        charBuffer: CharArray
    ): Int {
        var currentOffset = elementValueOffset
        if (annotationVisitor == null) {
            return when ((classFileBuffer[currentOffset] and 0xFF.toByte()).toInt().toChar()) {
                'e' -> currentOffset + 5
                '@' -> readElementValues(null, currentOffset + 3, true, charBuffer)
                '[' -> readElementValues(null, currentOffset + 1, false, charBuffer)
                else -> currentOffset + 3
            }
        }
        when ((classFileBuffer[currentOffset++] and 0xFF.toByte()).toInt().toChar()) {
            'B' -> {
                annotationVisitor.visit(
                    elementName, readInt(cpInfoOffsets[readUnsignedShort(currentOffset)]).toByte()
                )
                currentOffset += 2
            }

            'C' -> {
                annotationVisitor.visit(
                    elementName, readInt(cpInfoOffsets[readUnsignedShort(currentOffset)]).toChar()
                )
                currentOffset += 2
            }

            'D', 'F', 'I', 'J' -> {
                annotationVisitor.visit(
                    elementName, readConst(readUnsignedShort(currentOffset), charBuffer)
                )
                currentOffset += 2
            }

            'S' -> {
                annotationVisitor.visit(
                    elementName, readInt(cpInfoOffsets[readUnsignedShort(currentOffset)]).toShort()
                )
                currentOffset += 2
            }

            'Z' -> {
                annotationVisitor.visit(
                    elementName,
                    readInt(cpInfoOffsets[readUnsignedShort(currentOffset)]) != 0
                )
                currentOffset += 2
            }

            's' -> {
                annotationVisitor.visit(elementName, readUTF8(currentOffset, charBuffer))
                currentOffset += 2
            }

            'e' -> {
                annotationVisitor.visitEnum(
                    elementName,
                    readUTF8(currentOffset, charBuffer),
                    readUTF8(currentOffset + 2, charBuffer)
                )
                currentOffset += 4
            }

            'c' -> {
                annotationVisitor.visit(elementName,
                    Type.getType(readUTF8(currentOffset, charBuffer)))
                currentOffset += 2
            }

            '@' -> currentOffset =
                readElementValues(
                    annotationVisitor.visitAnnotation(elementName, readUTF8(currentOffset, charBuffer)),
                    currentOffset + 2,
                    true,
                    charBuffer
                )

            '[' -> {
                val numValues = readUnsignedShort(currentOffset)
                currentOffset += 2
                if (numValues == 0) {
                    return readElementValues(
                        annotationVisitor.visitArray(elementName),
                        currentOffset - 2, 
                        false,
                        charBuffer
                    )
                }
                when ((classFileBuffer[currentOffset] and 0xFF.toByte()).toInt().toChar()) {
                    'B' -> {
                        val byteValues = ByteArray(numValues)
                        repeat(numValues) {
                            byteValues[it] = readInt(
                                cpInfoOffsets[readUnsignedShort(currentOffset + 1)]
                            ).toByte()
                            currentOffset += 3
                        }
                        annotationVisitor.visit(elementName, byteValues)
                    }

                    'Z' -> {
                        val booleanValues = BooleanArray(numValues)
                        repeat(numValues) {
                            booleanValues[it] = readInt(
                                cpInfoOffsets[readUnsignedShort(currentOffset + 1)]
                            ) != 0
                            currentOffset += 3
                        }
                        annotationVisitor.visit(elementName, booleanValues)
                    }

                    'S' -> {
                        val shortValues = ShortArray(numValues)
                        repeat(numValues) {
                            shortValues[it] = readInt(
                                cpInfoOffsets[readUnsignedShort(currentOffset + 1)]
                            ).toShort()
                            currentOffset += 3
                        }
                        annotationVisitor.visit(elementName, shortValues)
                    }

                    'C' -> {
                        val charValues = CharArray(numValues)
                        repeat(numValues) {
                            charValues[it] = readInt(
                                cpInfoOffsets[readUnsignedShort(currentOffset + 1)]
                            ).toChar()
                            currentOffset += 3
                        }
                        annotationVisitor.visit(elementName, charValues)
                    }

                    'I' -> {
                        val intValues = IntArray(numValues)
                        repeat(numValues) {
                            intValues[it] = readInt(cpInfoOffsets[readUnsignedShort(currentOffset + 1)])
                            currentOffset += 3
                        }
                        annotationVisitor.visit(elementName, intValues)
                    }

                    'J' -> {
                        val longValues = LongArray(numValues)
                        repeat(numValues) {
                            longValues[it] = readLong(
                                cpInfoOffsets[readUnsignedShort(currentOffset + 1)]
                            )
                            currentOffset += 3
                        }
                        annotationVisitor.visit(elementName, longValues)
                    }

                    'F' -> {
                        val floatValues = FloatArray(numValues)
                        repeat (numValues) {
                            floatValues[it] =
                                Float.fromBits(
                                    readInt(
                                        cpInfoOffsets[readUnsignedShort(currentOffset + 1)]
                                    )
                                )
                            currentOffset += 3
                        }
                        annotationVisitor.visit(elementName, floatValues)
                    }

                    'D' -> {
                        val doubleValues = DoubleArray(numValues)
                        repeat(numValues) {
                            doubleValues[it] = Double.fromBits(
                                readLong(
                                    cpInfoOffsets[readUnsignedShort(currentOffset + 1)]
                                )
                            )
                            currentOffset += 3
                        }
                        annotationVisitor.visit(elementName, doubleValues)
                    }

                    else -> currentOffset =
                        readElementValues(
                            annotationVisitor.visitArray(elementName),
                            currentOffset - 2, 
                            false,
                            charBuffer
                        )
                }
            }

            else -> throw IllegalArgumentException()
        }
        return currentOffset
    }


    // ----------------------------------------------------------------------------------------------
    // Methods to parse stack map frames
    // ----------------------------------------------------------------------------------------------
    /**
     * Computes the implicit frame of the method currently being parsed (as defined in the given
     * [Context]) and stores it in the given context.
     *
     * @param context information about the class being parsed.
     */
    private fun computeImplicitFrame(context: Context) {
        val methodDescriptor = context.currentMethodDescriptor
        val locals = context.currentFrameLocalTypes
        var numLocal = 0
        if ((context.currentMethodAccessFlags and Opcodes.ACC_STATIC) == 0) {
            if ("<init>" == context.currentMethodName) {
                locals[numLocal++] = Opcodes.UNINITIALIZED_THIS
            } else {
                locals[numLocal++] = readClass(header + 2, context.charBuffer)
            }
        }
        // Parse the method descriptor, one argument type descriptor at each iteration. Start by
        // skipping the first method descriptor character, which is always '('.
        var currentMethodDescritorOffset = 1
        while (true) {
            val currentArgumentDescriptorStartOffset = currentMethodDescritorOffset
            when (methodDescriptor[currentMethodDescritorOffset++]) {
                'Z', 'C', 'B', 'S', 'I' -> locals[numLocal++] = Opcodes.INTEGER
                'F' -> locals[numLocal++] = Opcodes.FLOAT
                'J' -> locals[numLocal++] = Opcodes.LONG
                'D' -> locals[numLocal++] = Opcodes.DOUBLE
                '[' -> {
                    while (methodDescriptor[currentMethodDescritorOffset] == '[') {
                        ++currentMethodDescritorOffset
                    }
                    if (methodDescriptor[currentMethodDescritorOffset] == 'L') {
                        ++currentMethodDescritorOffset
                        while (methodDescriptor[currentMethodDescritorOffset] != ';') {
                            ++currentMethodDescritorOffset
                        }
                    }
                    locals[numLocal++] =
                        methodDescriptor.substring(
                            currentArgumentDescriptorStartOffset, ++currentMethodDescritorOffset
                        )
                }

                'L' -> {
                    while (methodDescriptor[currentMethodDescritorOffset] != ';') {
                        ++currentMethodDescritorOffset
                    }
                    locals[numLocal++] =
                        methodDescriptor.substring(
                            currentArgumentDescriptorStartOffset + 1, currentMethodDescritorOffset++
                        )
                }

                else -> {
                    context.currentFrameLocalCount = numLocal
                    return
                }
            }
        }
    }

    /**
     * Reads a JVMS 'stack_map_frame' structure and stores the result in the given [Context]
     * object. This method can also be used to read a full_frame structure, excluding its frame_type
     * field (this is used to parse the legacy StackMap attributes).
     *
     * @param stackMapFrameOffset the start offset in [.classFileBuffer] of the
     * stack_map_frame_value structure to be read, or the start offset of a full_frame structure
     * (excluding its frame_type field).
     * @param compressed true to read a 'stack_map_frame' structure, false to read a 'full_frame'
     * structure without its frame_type field.
     * @param expand if the stack map frame must be expanded. See [.EXPAND_FRAMES].
     * @param context where the parsed stack map frame must be stored.
     * @return the end offset of the JVMS 'stack_map_frame' or 'full_frame' structure.
     */
    private fun readStackMapFrame(
        stackMapFrameOffset: Int,
        compressed: Boolean,
        expand: Boolean,
        context: Context
    ): Int {
        var currentOffset = stackMapFrameOffset
        val charBuffer = context.charBuffer
        val labels: Array<Label?> = context.currentMethodLabels
        val frameType: Int
        if (compressed) {
            // Read the frame_type field.
            frameType = (classFileBuffer[currentOffset++] and 0xFF.toByte()).toInt()
        } else {
            frameType = Frame.FULL_FRAME
            context.currentFrameOffset = -1
        }
        val offsetDelta: Int
        context.currentFrameLocalCountDelta = 0
        if (frameType < Frame.SAME_LOCALS_1_STACK_ITEM_FRAME) {
            offsetDelta = frameType
            context.currentFrameType = Opcodes.F_SAME
            context.currentFrameStackCount = 0
        } else if (frameType < Frame.RESERVED) {
            offsetDelta = frameType - Frame.SAME_LOCALS_1_STACK_ITEM_FRAME
            currentOffset =
                readVerificationTypeInfo(
                    currentOffset, context.currentFrameStackTypes, 0, charBuffer, labels
                )
            context.currentFrameType = Opcodes.F_SAME1
            context.currentFrameStackCount = 1
        } else if (frameType >= Frame.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
            offsetDelta = readUnsignedShort(currentOffset)
            currentOffset += 2
            if (frameType == Frame.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
                currentOffset =
                    readVerificationTypeInfo(
                        currentOffset, context.currentFrameStackTypes, 0, charBuffer, labels
                    )
                context.currentFrameType = Opcodes.F_SAME1
                context.currentFrameStackCount = 1
            } else if (frameType >= Frame.CHOP_FRAME && frameType < Frame.SAME_FRAME_EXTENDED) {
                context.currentFrameType = Opcodes.F_CHOP
                context.currentFrameLocalCountDelta = Frame.SAME_FRAME_EXTENDED - frameType
                context.currentFrameLocalCount -= context.currentFrameLocalCountDelta
                context.currentFrameStackCount = 0
            } else if (frameType == Frame.SAME_FRAME_EXTENDED) {
                context.currentFrameType = Opcodes.F_SAME
                context.currentFrameStackCount = 0
            } else if (frameType < Frame.FULL_FRAME) {
                var local = if (expand) context.currentFrameLocalCount else 0
                repeat(frameType - Frame.SAME_FRAME_EXTENDED) {
                    currentOffset =
                        readVerificationTypeInfo(
                            currentOffset, context.currentFrameLocalTypes, local++, charBuffer, labels
                        )
                }
                context.currentFrameType = Opcodes.F_APPEND
                context.currentFrameLocalCountDelta = frameType - Frame.SAME_FRAME_EXTENDED
                context.currentFrameLocalCount += context.currentFrameLocalCountDelta
                context.currentFrameStackCount = 0
            } else {
                val numberOfLocals = readUnsignedShort(currentOffset)
                currentOffset += 2
                context.currentFrameType = Opcodes.F_FULL
                context.currentFrameLocalCountDelta = numberOfLocals
                context.currentFrameLocalCount = numberOfLocals
                for (local in 0..<numberOfLocals) {
                    currentOffset =
                        readVerificationTypeInfo(
                            currentOffset, context.currentFrameLocalTypes, local, charBuffer, labels
                        )
                }
                val numberOfStackItems = readUnsignedShort(currentOffset)
                currentOffset += 2
                context.currentFrameStackCount = numberOfStackItems
                for (stack in 0..<numberOfStackItems) {
                    currentOffset =
                        readVerificationTypeInfo(
                            currentOffset, context.currentFrameStackTypes, stack, charBuffer, labels
                        )
                }
            }
        } else {
            throw IllegalArgumentException()
        }
        context.currentFrameOffset += offsetDelta + 1
        createLabel(context.currentFrameOffset, labels)
        return currentOffset
    }


    /**
     * Reads a JVMS 'verification_type_info' structure and stores it at the given index in the given
     * array.
     * 
     * @param verificationTypeInfoOffset the start offset of the 'verification_type_info' structure to
     * read.
     * @param frame the array where the parsed type must be stored.
     * @param index the index in 'frame' where the parsed type must be stored.
     * @param charBuffer the buffer used to read strings in the constant pool.
     * @param labels the labels of the method currently being parsed, indexed by their offset. If the
     * parsed type is an ITEM_Uninitialized, a new label for the corresponding NEW instruction is
     * stored in this array if it does not already exist.
     * @return the end offset of the JVMS 'verification_type_info' structure.
     */
    private fun readVerificationTypeInfo(
        verificationTypeInfoOffset: Int,
        frame: Array<Any?>,
        index: Int,
        charBuffer: CharArray,
        labels: Array<Label?>
    ): Int {
        var currentOffset = verificationTypeInfoOffset
        val tag = classFileBuffer[currentOffset++].toInt() and 0xFF
        when (tag) {
            Frame.ITEM_TOP -> frame[index] = Opcodes.TOP
            Frame.ITEM_INTEGER -> frame[index] = Opcodes.INTEGER
            Frame.ITEM_FLOAT -> frame[index] = Opcodes.FLOAT
            Frame.ITEM_DOUBLE -> frame[index] = Opcodes.DOUBLE
            Frame.ITEM_LONG -> frame[index] = Opcodes.LONG
            Frame.ITEM_NULL -> frame[index] = Opcodes.NULL
            Frame.ITEM_UNINITIALIZED_THIS -> frame[index] =
                Opcodes.UNINITIALIZED_THIS

            Frame.ITEM_OBJECT -> {
                frame[index] = readClass(currentOffset, charBuffer)
                currentOffset += 2
            }

            Frame.ITEM_UNINITIALIZED -> {
                frame[index] = createLabel(readUnsignedShort(currentOffset), labels)
                currentOffset += 2
            }

            else -> throw IllegalArgumentException()
        }
        return currentOffset
    }

    // ----------------------------------------------------------------------------------------------
    // Methods to parse attributes
    // ----------------------------------------------------------------------------------------------
    /**
     * Returns the offset in [.classFileBuffer] of the first ClassFile's 'attributes' array
     * field entry.
     *
     * @return the offset in [.classFileBuffer] of the first ClassFile's 'attributes' array
     * field entry.
     */
    fun getFirstAttributeOffset(): Int {
        // Skip the access_flags, this_class, super_class, and interfaces_count fields (using 2 bytes
        // each), as well as the interfaces array field (2 bytes per interface).
        var currentOffset = header + 8 + readUnsignedShort(header + 6) * 2

        // Read the fields_count field.
        val fieldsCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        // Skip the 'fields' array field.
        repeat(fieldsCount) {
            // Invariant: currentOffset is the offset of a field_info structure.
            // Skip the access_flags, name_index and descriptor_index fields (2 bytes each), and read the
            // attributes_count field.
            val attributesCount = readUnsignedShort(currentOffset + 6)
            currentOffset += 8
            // Skip the 'attributes' array field.
            repeat(attributesCount) {
                // Invariant: currentOffset is the offset of an attribute_info structure.
                // Read the attribute_length field (2 bytes after the start of the attribute_info) and skip
                // this many bytes, plus 6 for the attribute_name_index and attribute_length fields
                // (yielding the total size of the attribute_info structure).
                currentOffset += 6 + readInt(currentOffset + 2)
            }
        }

        // Skip the methods_count and 'methods' fields, using the same method as above.
        val methodsCount = readUnsignedShort(currentOffset)
        currentOffset += 2
        repeat(methodsCount) {
            val attributesCount = readUnsignedShort(currentOffset + 6)
            currentOffset += 8
            repeat(attributesCount) {
                currentOffset += 6 + readInt(currentOffset + 2)
            }
        }

        // Skip the ClassFile's attributes_count field.
        return currentOffset + 2
    }

    /**
     * Reads the BootstrapMethods attribute to compute the offset of each bootstrap method.
     *
     * @param maxStringLength a conservative estimate of the maximum length of the strings contained
     * in the constant pool of the class.
     * @return the offsets of the bootstrap methods.
     */
    private fun readBootstrapMethodsAttribute(maxStringLength: Int): IntArray {
        val charBuffer = CharArray(maxStringLength)
        var currentAttributeOffset: Int = getFirstAttributeOffset()
        repeat(readUnsignedShort(currentAttributeOffset - 2)) {
            // Read the attribute_info's attribute_name and attribute_length fields.
            val attributeName = readUTF8(currentAttributeOffset, charBuffer)
            val attributeLength = readInt(currentAttributeOffset + 2)
            currentAttributeOffset += 6
            if (Constants.BOOTSTRAP_METHODS == attributeName) {
                // Read the num_bootstrap_methods field and create an array of this size.
                val result = IntArray(readUnsignedShort(currentAttributeOffset))
                // Compute and store the offset of each 'bootstrap_methods' array field entry.
                var currentBootstrapMethodOffset = currentAttributeOffset + 2
                for (j in result.indices) {
                    result[j] = currentBootstrapMethodOffset
                    // Skip the bootstrap_method_ref and num_bootstrap_arguments fields (2 bytes each),
                    // as well as the bootstrap_arguments array field (of size num_bootstrap_arguments * 2).
                    currentBootstrapMethodOffset +=
                        4 + readUnsignedShort(currentBootstrapMethodOffset + 2) * 2
                }
                return result
            }
            currentAttributeOffset += attributeLength
        }
        throw IllegalArgumentException()
    }

    /**
     * Reads a non-standard JVMS 'attribute' structure in [.classFileBuffer].
     *
     * @param attributePrototypes prototypes of the attributes that must be parsed during the visit of
     * the class. Any attribute whose type is not equal to the type of one the prototypes will not
     * be parsed: its byte array value will be passed unchanged to the ClassWriter.
     * @param type the type of the attribute.
     * @param offset the start offset of the JVMS 'attribute' structure in [.classFileBuffer].
     * The 6 attribute header bytes (attribute_name_index and attribute_length) are not taken into
     * account here.
     * @param length the length of the attribute's content (excluding the 6 attribute header bytes).
     * @param charBuffer the buffer to be used to read strings in the constant pool.
     * @param codeAttributeOffset the start offset of the enclosing Code attribute in [     ][.classFileBuffer], or -1 if the attribute to be read is not a code attribute. The 6
     * attribute header bytes (attribute_name_index and attribute_length) are not taken into
     * account here.
     * @param labels the labels of the method's code, or null if the attribute to be read
     * is not a code attribute.
     * @return the attribute that has been read.
     */
    private fun readAttribute(
        attributePrototypes: Array<Attribute>,
        type: String,
        offset: Int,
        length: Int,
        charBuffer: CharArray,
        codeAttributeOffset: Int,
        labels: Array<Label>?
    ): Attribute {
        require(length <= classFileBuffer.size - offset)
        for (attributePrototype in attributePrototypes) {
            if (attributePrototype.type == type) {
                return attributePrototype.read(
                    this, offset, length, charBuffer, codeAttributeOffset, labels
                )
            }
        }
        return Attribute(type).read(this, offset, length, null, -1, null)
    }

    // -----------------------------------------------------------------------------------------------
    // Utility methods: low level parsing
    // -----------------------------------------------------------------------------------------------
    /**
     * Returns the number of entries in the class's constant pool table.
     *
     * @return the number of entries in the class's constant pool table.
     */
    val itemCount: Int
        get() {
            return cpInfoOffsets.size
        }

    /**
     * Returns the start offset in this [ClassReader] of a JVMS 'cp_info' structure (i.e. a
     * constant pool entry), plus one. *This method is intended for [Attribute] sub classes,
     * and is normally not needed by class generators or adapters.*
     *
     * @param constantPoolEntryIndex the index a constant pool entry in the class's constant pool
     * table.
     * @return the start offset in this [ClassReader] of the corresponding JVMS 'cp_info'
     * structure, plus one.
     */
    fun getItem(constantPoolEntryIndex: Int): Int {
        return cpInfoOffsets[constantPoolEntryIndex]
    }

    /**
     * Reads a byte value in this [ClassReader]. *This method is intended for [ ] sub classes, and is normally not needed by class generators or adapters.*
     *
     * @param offset the start offset of the value to be read in this [ClassReader].
     * @return the read value.
     */
    fun readByte(offset: Int): Int {
        return classFileBuffer[offset].toInt() and 0xFF
    }

    /**
     * Reads several bytes in this [ClassReader]. *This method is intended for [ ] sub classes, and is normally not needed by class generators or adapters.*
     *
     * @param offset the start offset of the bytes to be read in this [ClassReader].
     * @param length the number of bytes to read.
     * @return the read bytes.
     */
    fun readBytes(offset: Int, length: Int): ByteArray {
        val result = ByteArray(length)
        classFileBuffer.copyInto(result, 0, offset, offset + length)
        return result
    }

    /**
     * Reads an unsigned short value in this [ClassReader]. *This method is intended for
     * [Attribute] sub classes, and is normally not needed by class generators or adapters.*
     *
     * @param offset the start index of the value to be read in this [ClassReader].
     * @return the read value.
     */
    open fun readUnsignedShort(offset: Int): Int {
        val classBuffer = classFileBuffer
        return ((classBuffer[offset].toInt() and 0xFF) shl 8) or (classBuffer[offset + 1].toInt() and 0xFF)
    }

    /**
     * Reads a signed short value in this [ClassReader]. *This method is intended for [ ] sub classes, and is normally not needed by class generators or adapters.*
     *
     * @param offset the start offset of the value to be read in this [ClassReader].
     * @return the read value.
     */
    fun readShort(offset: Int): Short {
        val classBuffer = classFileBuffer
        return (((classBuffer[offset].toInt() and 0xFF) shl 8) or (classBuffer[offset + 1].toInt() and 0xFF)).toShort()
    }

    /**
     * Reads a signed int value in this [ClassReader]. *This method is intended for [ ] sub classes, and is normally not needed by class generators or adapters.*
     *
     * @param offset the start offset of the value to be read in this [ClassReader].
     * @return the read value.
     */
    fun readInt(offset: Int): Int {
        val classBuffer = classFileBuffer
        return (((classBuffer[offset].toInt() and 0xFF) shl 24)
                or ((classBuffer[offset + 1].toInt() and 0xFF) shl 16)
                or ((classBuffer[offset + 2].toInt() and 0xFF) shl 8)
                or (classBuffer[offset + 3].toInt() and 0xFF))
    }

    /**
     * Reads a signed long value in this [ClassReader]. *This method is intended for [ ] sub classes, and is normally not needed by class generators or adapters.*
     *
     * @param offset the start offset of the value to be read in this [ClassReader].
     * @return the read value.
     */
    fun readLong(offset: Int): Long {
        val l1 = readInt(offset).toLong()
        val l0 = readInt(offset + 4).toLong() and 0xFFFFFFFFL
        return (l1 shl 32) or l0
    }

    /**
     * Reads a CONSTANT_Utf8 constant pool entry in this [ClassReader]. *This method is
     * intended for [Attribute] subclasses, and is normally not needed by class generators or
     * adapters.*
     *
     * @param offset the start offset of an unsigned short value in this [ClassReader], whose
     * value is the index of a CONSTANT_Utf8 entry in the class's constant pool table.
     * @param charBuffer the buffer to be used to read the string. This buffer must be sufficiently
     * large. It is not automatically resized.
     * @return the String corresponding to the specified CONSTANT_Utf8 entry.
     */
    // DontCheck(AbbreviationAsWordInName): can't be renamed (for backward binary compatibility).
    @OptIn(ExperimentalContracts::class, ExperimentalExtendedContracts::class)
    fun readUTF8(offset: Int, charBuffer: CharArray): String {
        val constantPoolEntryIndex = readUnsignedShort(offset)
        check(offset != 0 && constantPoolEntryIndex != 0)
        return readUtf(constantPoolEntryIndex, charBuffer)
    }

    /**
     * Reads a CONSTANT_Utf8 constant pool entry in [.classFileBuffer].
     *
     * @param constantPoolEntryIndex the index of a CONSTANT_Utf8 entry in the class's constant pool
     * table.
     * @param charBuffer the buffer to be used to read the string. This buffer must be sufficiently
     * large. It is not automatically resized.
     * @return the String corresponding to the specified CONSTANT_Utf8 entry.
     */
    fun readUtf(constantPoolEntryIndex: Int, charBuffer: CharArray): String {
        val value = constantUtf8Values[constantPoolEntryIndex]
        if (value != null) {
            return value
        }
        val cpInfoOffset = cpInfoOffsets[constantPoolEntryIndex]
        return readUtf(
            cpInfoOffset + 2,
            readUnsignedShort(cpInfoOffset),
            charBuffer
        ).also { constantUtf8Values[constantPoolEntryIndex] = it }
    }

    /**
     * Reads an UTF8 string in [.classFileBuffer].
     *
     * @param utfOffset the start offset of the UTF8 string to be read.
     * @param utfLength the length of the UTF8 string to be read.
     * @param charBuffer the buffer to be used to read the string. This buffer must be sufficiently
     * large. It is not automatically resized.
     * @return the String corresponding to the specified UTF8 string.
     */
    private fun readUtf(utfOffset: Int, utfLength: Int, charBuffer: CharArray): String {
        var currentOffset = utfOffset
        val endOffset = currentOffset + utfLength
        var strLength = 0
        val classBuffer = classFileBuffer
        while (currentOffset < endOffset) {
            val currentByte = classBuffer[currentOffset++].toInt()
            if ((currentByte and 0x80) == 0) {
                charBuffer[strLength++] = (currentByte and 0x7F).toChar()
            } else if ((currentByte and 0xE0) == 0xC0) {
                charBuffer[strLength++] =
                    (((currentByte and 0x1F) shl 6) + (classBuffer[currentOffset++].toInt() and 0x3F)).toChar()
            } else {
                charBuffer[strLength++] = ((((currentByte and 0xF) shl 12)
                        + ((classBuffer[currentOffset++].toInt() and 0x3F) shl 6)
                        + (classBuffer[currentOffset++].toInt() and 0x3F))).toChar()
            }
        }
        return charBuffer.concatToString(endIndex = strLength)
    }


    /**
     * Reads a CONSTANT_Class, CONSTANT_String, CONSTANT_MethodType, CONSTANT_Module or
     * CONSTANT_Package constant pool entry in [.classFileBuffer]. *This method is intended
     * for [Attribute] sub classes, and is normally not needed by class generators or
     * adapters.*
     *
     * @param offset the start offset of an unsigned short value in [.classFileBuffer], whose
     * value is the index of a CONSTANT_Class, CONSTANT_String, CONSTANT_MethodType,
     * CONSTANT_Module or CONSTANT_Package entry in class's constant pool table.
     * @param charBuffer the buffer to be used to read the item. This buffer must be sufficiently
     * large. It is not automatically resized.
     * @return the String corresponding to the specified constant pool entry.
     */
    private fun readStringish(offset: Int, charBuffer: CharArray): String {
        // Get the start offset of the cp_info structure (plus one), and read the CONSTANT_Utf8 entry
        // designated by the first two bytes of this cp_info.
        return readUTF8(cpInfoOffsets[readUnsignedShort(offset)], charBuffer)
    }

    /**
     * Reads a CONSTANT_Class constant pool entry in this [ClassReader]. *This method is
     * intended for [Attribute] sub classes, and is normally not needed by class generators or
     * adapters.*
     *
     * @param offset the start offset of an unsigned short value in this [ClassReader], whose
     * value is the index of a CONSTANT_Class entry in class's constant pool table.
     * @param charBuffer the buffer to be used to read the item. This buffer must be sufficiently
     * large. It is not automatically resized.
     * @return the String corresponding to the specified CONSTANT_Class entry.
     */
    fun readClass(offset: Int, charBuffer: CharArray): String {
        return readStringish(offset, charBuffer)
    }

    /**
     * Reads a CONSTANT_Module constant pool entry in this [ClassReader]. *This method is
     * intended for [Attribute] sub classes, and is normally not needed by class generators or
     * adapters.*
     *
     * @param offset the start offset of an unsigned short value in this [ClassReader], whose
     * value is the index of a CONSTANT_Module entry in class's constant pool table.
     * @param charBuffer the buffer to be used to read the item. This buffer must be sufficiently
     * large. It is not automatically resized.
     * @return the String corresponding to the specified CONSTANT_Module entry.
     */
    fun readModule(offset: Int, charBuffer: CharArray): String {
        return readStringish(offset, charBuffer)
    }

    /**
     * Reads a CONSTANT_Package constant pool entry in this [ClassReader]. *This method is
     * intended for [Attribute] sub classes, and is normally not needed by class generators or
     * adapters.*
     *
     * @param offset the start offset of an unsigned short value in this [ClassReader], whose
     * value is the index of a CONSTANT_Package entry in class's constant pool table.
     * @param charBuffer the buffer to be used to read the item. This buffer must be sufficiently
     * large. It is not automatically resized.
     * @return the String corresponding to the specified CONSTANT_Package entry.
     */
    fun readPackage(offset: Int, charBuffer: CharArray): String {
        return readStringish(offset, charBuffer)
    }


    /**
     * Reads a CONSTANT_Dynamic constant pool entry in [.classFileBuffer].
     * 
     * @param constantPoolEntryIndex the index of a CONSTANT_Dynamic entry in the class's constant
     * pool table.
     * @param charBuffer the buffer to be used to read the string. This buffer must be sufficiently
     * large. It is not automatically resized.
     * @return the ConstantDynamic corresponding to the specified CONSTANT_Dynamic entry.
     */
    private fun readConstantDynamic(
        constantPoolEntryIndex: Int, charBuffer: CharArray
    ): ConstantDynamic {
        var constantDynamic = constantDynamicValues!![constantPoolEntryIndex]
        if (constantDynamic != null) {
            return constantDynamic
        }
        val cpInfoOffset = cpInfoOffsets[constantPoolEntryIndex]
        val nameAndTypeCpInfoOffset = cpInfoOffsets[readUnsignedShort(cpInfoOffset + 2)]
        val name = readUTF8(nameAndTypeCpInfoOffset, charBuffer)
        val descriptor = readUTF8(nameAndTypeCpInfoOffset + 2, charBuffer)
        var bootstrapMethodOffset = bootstrapMethodOffsets!![readUnsignedShort(cpInfoOffset)]
        val handle =
            readConst(readUnsignedShort(bootstrapMethodOffset), charBuffer) as Handle

        val bootstrapMethodArguments = run {
            val bsmArgCounts = readUnsignedShort(bootstrapMethodOffset + 2)
            bootstrapMethodOffset += 4
            Array(bsmArgCounts) {
                val result = readConst(readUnsignedShort(bootstrapMethodOffset), charBuffer)
                bootstrapMethodOffset += 2
                result
            }
        }
        constantDynamic = ConstantDynamic(name, descriptor, handle, bootstrapMethodArguments)
        constantDynamicValues[constantPoolEntryIndex] = constantDynamic
        return constantDynamic
    }

    /**
     * Reads a numeric or string constant pool entry in this [ClassReader]. *This method is
     * intended for [Attribute] sub classes, and is normally not needed by class generators or
     * adapters.*
     * 
     * @param constantPoolEntryIndex the index of a CONSTANT_Integer, CONSTANT_Float, CONSTANT_Long,
     * CONSTANT_Double, CONSTANT_Class, CONSTANT_String, CONSTANT_MethodType,
     * CONSTANT_MethodHandle or CONSTANT_Dynamic entry in the class's constant pool.
     * @param charBuffer the buffer to be used to read strings. This buffer must be sufficiently
     * large. It is not automatically resized.
     * @return the [Integer], [Float], [Long], [Double], [String],
     * [Type], [Handle] or [ConstantDynamic] corresponding to the specified
     * constant pool entry.
     */
    open fun readConst(constantPoolEntryIndex: Int, charBuffer: CharArray): Any {
        val cpInfoOffset = cpInfoOffsets[constantPoolEntryIndex]
        when (classFileBuffer[cpInfoOffset - 1].toInt()) {
            Symbol.CONSTANT_INTEGER_TAG -> return readInt(cpInfoOffset)
            Symbol.CONSTANT_FLOAT_TAG -> return Float.fromBits(readInt(cpInfoOffset))
            Symbol.CONSTANT_LONG_TAG -> return readLong(cpInfoOffset)
            Symbol.CONSTANT_DOUBLE_TAG -> return Double.fromBits(
                readLong(
                    cpInfoOffset
                )
            )

            Symbol.CONSTANT_CLASS_TAG -> return Type.getObjectType(
                readUTF8(
                    cpInfoOffset,
                    charBuffer
                )
            )

            Symbol.CONSTANT_STRING_TAG -> return readUTF8(cpInfoOffset, charBuffer)
            Symbol.CONSTANT_METHOD_TYPE_TAG -> return Type.getMethodType(
                readUTF8(
                    cpInfoOffset,
                    charBuffer
                )
            )

            Symbol.CONSTANT_METHOD_HANDLE_TAG -> {
                val referenceKind = readByte(cpInfoOffset)
                val referenceCpInfoOffset = cpInfoOffsets[readUnsignedShort(cpInfoOffset + 1)]
                val nameAndTypeCpInfoOffset = cpInfoOffsets[readUnsignedShort(referenceCpInfoOffset + 2)]
                val owner = readClass(referenceCpInfoOffset, charBuffer)
                val name = readUTF8(nameAndTypeCpInfoOffset, charBuffer)
                val descriptor = readUTF8(nameAndTypeCpInfoOffset + 2, charBuffer)
                val isInterface =
                    classFileBuffer[referenceCpInfoOffset - 1].toInt() == Symbol.CONSTANT_INTERFACE_METHODREF_TAG
                return Handle(referenceKind, owner, name, descriptor, isInterface)
            }

            Symbol.CONSTANT_DYNAMIC_TAG -> return readConstantDynamic(
                constantPoolEntryIndex,
                charBuffer
            )

            else -> throw IllegalArgumentException()
        }
    }
}