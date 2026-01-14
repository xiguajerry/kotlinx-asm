package kotlinx.asm

/**
 * Information about a class being parsed in a [ClassReader].
 *
 * @author Eric Bruneton
 */
internal class Context {
    /** The prototypes of the attributes that must be parsed in this class.  */
    lateinit var attributePrototypes: Array<Attribute>

    /**
     * The options used to parse this class. One or more of [ClassReader.SKIP_CODE], [ ][ClassReader.SKIP_DEBUG], [ClassReader.SKIP_FRAMES], [ClassReader.EXPAND_FRAMES] or
     * [ClassReader.EXPAND_ASM_INSNS].
     */
    var parsingOptions: Int = 0

    /** The buffer used to read strings in the constant pool.  */
    lateinit var charBuffer: CharArray

    // Information about the current method, i.e. the one read in the current (or latest) call
    // to {@link ClassReader#readMethod()}.
    /** The access flags of the current method.  */
    var currentMethodAccessFlags: Int = 0

    /** The name of the current method.  */
    lateinit var currentMethodName: String

    /** The descriptor of the current method.  */
    lateinit var currentMethodDescriptor: String

    /**
     * The labels of the current method, indexed by bytecode offset (only bytecode offsets for which a
     * label is needed have a non null associated Label).
     */
    lateinit var currentMethodLabels: Array<Label?>

    // Information about the current type annotation target, i.e. the one read in the current
    // (or latest) call to {@link ClassReader#readAnnotationTarget()}.
    /**
     * The target_type and target_info of the current type annotation target, encoded as described in
     * [TypeReference].
     */
    var currentTypeAnnotationTarget: Int = 0

    /** The target_path of the current type annotation target.  */
    var currentTypeAnnotationTargetPath: TypePath? = null

    /** The start of each local variable range in the current local variable annotation.  */
    lateinit var currentLocalVariableAnnotationRangeStarts: Array<Label?>

    /** The end of each local variable range in the current local variable annotation.  */
    lateinit var currentLocalVariableAnnotationRangeEnds: Array<Label?>

    /**
     * The local variable index of each local variable range in the current local variable annotation.
     */
    lateinit var currentLocalVariableAnnotationRangeIndices: IntArray

    // Information about the current stack map frame, i.e. the one read in the current (or latest)
    // call to {@link ClassReader#readFrame()}.
    /** The bytecode offset of the current stack map frame.  */
    var currentFrameOffset: Int = 0

    /**
     * The type of the current stack map frame. One of [Opcodes.F_FULL], [ ][Opcodes.F_APPEND], [Opcodes.F_CHOP], [Opcodes.F_SAME] or [Opcodes.F_SAME1].
     */
    var currentFrameType: Int = 0

    /**
     * The number of local variable types in the current stack map frame. Each type is represented
     * with a single array element (even long and double).
     */
    var currentFrameLocalCount: Int = 0

    /**
     * The delta number of local variable types in the current stack map frame (each type is
     * represented with a single array element - even long and double). This is the number of local
     * variable types in this frame, minus the number of local variable types in the previous frame.
     */
    var currentFrameLocalCountDelta: Int = 0

    /**
     * The types of the local variables in the current stack map frame. Each type is represented with
     * a single array element (even long and double), using the format described in [ ][MethodVisitor.visitFrame]. Depending on [.currentFrameType], this contains the types of
     * all the local variables, or only those of the additional ones (compared to the previous frame).
     */
    lateinit var currentFrameLocalTypes: Array<Any?>

    /**
     * The number stack element types in the current stack map frame. Each type is represented with a
     * single array element (even long and double).
     */
    var currentFrameStackCount: Int = 0

    /**
     * The types of the stack elements in the current stack map frame. Each type is represented with a
     * single array element (even long and double), using the format described in [ ][MethodVisitor.visitFrame].
     */
    lateinit var currentFrameStackTypes: Array<Any?>
}