package kotlinx.asm

/**
 * Defines additional JVM opcodes, access flags and constants which are not part of the ASM public
 * API.
 * 
 * @see [JVMS 6](https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-6.html)
 * 
 * @author Eric Bruneton
 */
internal object Constants {
    // The ClassFile attribute names, in the order they are defined in
    // https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.7-300.
    const val CONSTANT_VALUE: String = "ConstantValue"
    const val CODE: String = "Code"
    const val STACK_MAP_TABLE: String = "StackMapTable"
    const val EXCEPTIONS: String = "Exceptions"
    const val INNER_CLASSES: String = "InnerClasses"
    const val ENCLOSING_METHOD: String = "EnclosingMethod"
    const val SYNTHETIC: String = "Synthetic"
    const val SIGNATURE: String = "Signature"
    const val SOURCE_FILE: String = "SourceFile"
    const val SOURCE_DEBUG_EXTENSION: String = "SourceDebugExtension"
    const val LINE_NUMBER_TABLE: String = "LineNumberTable"
    const val LOCAL_VARIABLE_TABLE: String = "LocalVariableTable"
    const val LOCAL_VARIABLE_TYPE_TABLE: String = "LocalVariableTypeTable"
    const val DEPRECATED: String = "Deprecated"
    const val RUNTIME_VISIBLE_ANNOTATIONS: String = "RuntimeVisibleAnnotations"
    const val RUNTIME_INVISIBLE_ANNOTATIONS: String = "RuntimeInvisibleAnnotations"
    const val RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS: String = "RuntimeVisibleParameterAnnotations"
    const val RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS: String = "RuntimeInvisibleParameterAnnotations"
    const val RUNTIME_VISIBLE_TYPE_ANNOTATIONS: String = "RuntimeVisibleTypeAnnotations"
    const val RUNTIME_INVISIBLE_TYPE_ANNOTATIONS: String = "RuntimeInvisibleTypeAnnotations"
    const val ANNOTATION_DEFAULT: String = "AnnotationDefault"
    const val BOOTSTRAP_METHODS: String = "BootstrapMethods"
    const val METHOD_PARAMETERS: String = "MethodParameters"
    const val MODULE: String = "Module"
    const val MODULE_PACKAGES: String = "ModulePackages"
    const val MODULE_MAIN_CLASS: String = "ModuleMainClass"
    const val NEST_HOST: String = "NestHost"
    const val NEST_MEMBERS: String = "NestMembers"
    const val PERMITTED_SUBCLASSES: String = "PermittedSubclasses"
    const val RECORD: String = "Record"

    // ASM specific access flags.
    // WARNING: the 16 least significant bits must NOT be used, to avoid conflicts with standard
    // access flags, and also to make sure that these flags are automatically filtered out when
    // written in class files (because access flags are stored using 16 bits only).
    const val ACC_CONSTRUCTOR: Int = 0x40000 // method access flag.

    // ASM specific stack map frame types, used in {@link ClassVisitor#visitFrame}.
    /**
     * A frame inserted between already existing frames. This internal stack map frame type (in
     * addition to the ones declared in [Opcodes]) can only be used if the frame content can be
     * computed from the previous existing frame and from the instructions between this existing frame
     * and the inserted one, without any knowledge of the type hierarchy. This kind of frame is only
     * used when an unconditional jump is inserted in a method while expanding an ASM specific
     * instruction. Keep in sync with Opcodes.java.
     */
    const val F_INSERT: Int = 256

    // The JVM opcode values which are not part of the ASM public API.
    // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-6.html.
    const val LDC_W: Int = 19
    const val LDC2_W: Int = 20
    const val ILOAD_0: Int = 26
    const val ILOAD_1: Int = 27
    const val ILOAD_2: Int = 28
    const val ILOAD_3: Int = 29
    const val LLOAD_0: Int = 30
    const val LLOAD_1: Int = 31
    const val LLOAD_2: Int = 32
    const val LLOAD_3: Int = 33
    const val FLOAD_0: Int = 34
    const val FLOAD_1: Int = 35
    const val FLOAD_2: Int = 36
    const val FLOAD_3: Int = 37
    const val DLOAD_0: Int = 38
    const val DLOAD_1: Int = 39
    const val DLOAD_2: Int = 40
    const val DLOAD_3: Int = 41
    const val ALOAD_0: Int = 42
    const val ALOAD_1: Int = 43
    const val ALOAD_2: Int = 44
    const val ALOAD_3: Int = 45
    const val ISTORE_0: Int = 59
    const val ISTORE_1: Int = 60
    const val ISTORE_2: Int = 61
    const val ISTORE_3: Int = 62
    const val LSTORE_0: Int = 63
    const val LSTORE_1: Int = 64
    const val LSTORE_2: Int = 65
    const val LSTORE_3: Int = 66
    const val FSTORE_0: Int = 67
    const val FSTORE_1: Int = 68
    const val FSTORE_2: Int = 69
    const val FSTORE_3: Int = 70
    const val DSTORE_0: Int = 71
    const val DSTORE_1: Int = 72
    const val DSTORE_2: Int = 73
    const val DSTORE_3: Int = 74
    const val ASTORE_0: Int = 75
    const val ASTORE_1: Int = 76
    const val ASTORE_2: Int = 77
    const val ASTORE_3: Int = 78
    const val WIDE: Int = 196
    const val GOTO_W: Int = 200
    const val JSR_W: Int = 201

    // Constants to convert between normal and wide jump instructions.
    // The delta between the GOTO_W and JSR_W opcodes and GOTO and JUMP.
    const val WIDE_JUMP_OPCODE_DELTA: Int = GOTO_W - Opcodes.GOTO

    // Constants to convert JVM opcodes to the equivalent ASM specific opcodes, and vice versa.
    // The delta between the ASM_IFEQ, ..., ASM_IF_ACMPNE, ASM_GOTO and ASM_JSR opcodes
    // and IFEQ, ..., IF_ACMPNE, GOTO and JSR.
    const val ASM_OPCODE_DELTA: Int = 49

    // The delta between the ASM_IFNULL and ASM_IFNONNULL opcodes and IFNULL and IFNONNULL.
    const val ASM_IFNULL_OPCODE_DELTA: Int = 20

    // ASM specific opcodes, used for long forward jump instructions.
    const val ASM_IFEQ: Int = Opcodes.IFEQ + ASM_OPCODE_DELTA
    const val ASM_IFNE: Int = Opcodes.IFNE + ASM_OPCODE_DELTA
    const val ASM_IFLT: Int = Opcodes.IFLT + ASM_OPCODE_DELTA
    const val ASM_IFGE: Int = Opcodes.IFGE + ASM_OPCODE_DELTA
    const val ASM_IFGT: Int = Opcodes.IFGT + ASM_OPCODE_DELTA
    const val ASM_IFLE: Int = Opcodes.IFLE + ASM_OPCODE_DELTA
    const val ASM_IF_ICMPEQ: Int = Opcodes.IF_ICMPEQ + ASM_OPCODE_DELTA
    const val ASM_IF_ICMPNE: Int = Opcodes.IF_ICMPNE + ASM_OPCODE_DELTA
    const val ASM_IF_ICMPLT: Int = Opcodes.IF_ICMPLT + ASM_OPCODE_DELTA
    const val ASM_IF_ICMPGE: Int = Opcodes.IF_ICMPGE + ASM_OPCODE_DELTA
    const val ASM_IF_ICMPGT: Int = Opcodes.IF_ICMPGT + ASM_OPCODE_DELTA
    const val ASM_IF_ICMPLE: Int = Opcodes.IF_ICMPLE + ASM_OPCODE_DELTA
    const val ASM_IF_ACMPEQ: Int = Opcodes.IF_ACMPEQ + ASM_OPCODE_DELTA
    const val ASM_IF_ACMPNE: Int = Opcodes.IF_ACMPNE + ASM_OPCODE_DELTA
    const val ASM_GOTO: Int = Opcodes.GOTO + ASM_OPCODE_DELTA
    const val ASM_JSR: Int = Opcodes.JSR + ASM_OPCODE_DELTA
    const val ASM_IFNULL: Int = Opcodes.IFNULL + ASM_IFNULL_OPCODE_DELTA
    const val ASM_IFNONNULL: Int = Opcodes.IFNONNULL + ASM_IFNULL_OPCODE_DELTA
    const val ASM_GOTO_W: Int = 220
}
