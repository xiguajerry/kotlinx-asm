package kotlinx.asm

/**
 * The JVM opcodes, access flags and array type codes. This interface does not define all the JVM
 * opcodes because some opcodes are automatically handled. For example, the xLOAD and xSTORE opcodes
 * are automatically replaced by xLOAD_n and xSTORE_n opcodes when possible. The xLOAD_n and
 * xSTORE_n opcodes are therefore not defined in this interface. Likewise for LDC, automatically
 * replaced by LDC_W or LDC2_W when necessary, WIDE, GOTO_W and JSR_W.
 * 
 * @see [JVMS 6](https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-6.html)
 * 
 * @author Eric Bruneton
 * @author Eugene Kuleshov
 */
// DontCheck(InterfaceIsType): can't be fixed (for backward binary compatibility).
@Suppress("unused")
internal object Opcodes {
    // ASM API versions.
    @Deprecated("Unused") const val ASM4: Int = 4 shl 16 or (0 shl 8)
    @Deprecated("Unused") const val ASM5: Int = 5 shl 16 or (0 shl 8)
    @Deprecated("Unused") const val ASM6: Int = 6 shl 16 or (0 shl 8)
    @Deprecated("Unused") const val ASM7: Int = 7 shl 16 or (0 shl 8)
    @Deprecated("Unused") const val ASM8: Int = 8 shl 16 or (0 shl 8)
    @Deprecated("Unused") const val ASM9: Int = 9 shl 16 or (0 shl 8)

    /**
     * *Experimental, use at your own risk. This field will be renamed when it becomes stable, this
     * will break existing code using it. Only code compiled with --enable-preview can use this.*
     *
     */
    @Deprecated("This API is experimental.")
    val ASM10_EXPERIMENTAL: Int = 1 shl 24 or (10 shl 16) or (0 shl 8)

    /**
     * Internal flags used to redirect calls to deprecated methods. For instance, if a visitOldStuff
     * method in API_OLD is deprecated and replaced with visitNewStuff in API_NEW, then the
     * redirection should be done as follows:
     *
     * <pre>
     * public class StuffVisitor {
     *   ...
     *
     *   &#64;Deprecated public void visitOldStuff(int arg, ...) {
     *     // SOURCE_DEPRECATED means "a call from a deprecated method using the old 'api' value".
     *     visitNewStuf(arg | (api &#60; API_NEW ? SOURCE_DEPRECATED : 0), ...);
     *   }
     *
     *   public void visitNewStuff(int argAndSource, ...) {
     *     if (api &#60; API_NEW &#38;&#38; (argAndSource &#38; SOURCE_DEPRECATED) == 0) {
     *       visitOldStuff(argAndSource, ...);
     *     } else {
     *       int arg = argAndSource &#38; ~SOURCE_MASK;
     *       [ do stuff ]
     *     }
     *   }
     * }
     * </pre>
     *
     * <p>If 'api' is equal to API_NEW, there are two cases:
     *
     * <ul>
     *   <li>call visitNewStuff: the redirection test is skipped and 'do stuff' is executed directly.
     *   <li>call visitOldSuff: the source is not set to SOURCE_DEPRECATED before calling
     *       visitNewStuff, but the redirection test is skipped anyway in visitNewStuff, which
     *       directly executes 'do stuff'.
     * </ul>
     *
     * <p>If 'api' is equal to API_OLD, there are two cases:
     *
     * <ul>
     *   <li>call visitOldSuff: the source is set to SOURCE_DEPRECATED before calling visitNewStuff.
     *       Because of this visitNewStuff does not redirect back to visitOldStuff, and instead
     *       executes 'do stuff'.
     *   <li>call visitNewStuff: the call is redirected to visitOldStuff because the source is 0.
     *       visitOldStuff now sets the source to SOURCE_DEPRECATED and calls visitNewStuff back. This
     *       time visitNewStuff does not redirect the call, and instead executes 'do stuff'.
     * </ul>
     *
     * <h1>User subclasses</h1>
     *
     * <p>If a user subclass overrides one of these methods, there are only two cases: either 'api' is
     * API_OLD and visitOldStuff is overridden (and visitNewStuff is not), or 'api' is API_NEW or
     * more, and visitNewStuff is overridden (and visitOldStuff is not). Any other case is a user
     * programming error.
     *
     * <p>If 'api' is equal to API_NEW, the class hierarchy is equivalent to
     *
     * <pre>
     * public class StuffVisitor {
     *   &#64;Deprecated public void visitOldStuff(int arg, ...) { visitNewStuf(arg, ...); }
     *   public void visitNewStuff(int arg, ...) { [ do stuff ] }
     * }
     * class UserStuffVisitor extends StuffVisitor {
     *   &#64;Override public void visitNewStuff(int arg, ...) {
     *     super.visitNewStuff(int arg, ...); // optional
     *     [ do user stuff ]
     *   }
     * }
     * </pre>
     *
     * <p>It is then obvious that whether visitNewStuff or visitOldStuff is called, 'do stuff' and 'do
     * user stuff' will be executed, in this order.
     *
     * <p>If 'api' is equal to API_OLD, the class hierarchy is equivalent to
     *
     * <pre>
     * public class StuffVisitor {
     *   &#64;Deprecated public void visitOldStuff(int arg, ...) {
     *     visitNewStuff(arg | SOURCE_DEPRECATED, ...);
     *   }
     *   public void visitNewStuff(int argAndSource...) {
     *     if ((argAndSource & SOURCE_DEPRECATED) == 0) {
     *       visitOldStuff(argAndSource, ...);
     *     } else {
     *       int arg = argAndSource &#38; ~SOURCE_MASK;
     *       [ do stuff ]
     *     }
     *   }
     * }
     * class UserStuffVisitor extends StuffVisitor {
     *   &#64;Override public void visitOldStuff(int arg, ...) {
     *     super.visitOldStuff(int arg, ...); // optional
     *     [ do user stuff ]
     *   }
     * }
     * </pre>
     *
     * <p>and there are two cases:
     *
     * <ul>
     *   <li>call visitOldStuff: in the call to super.visitOldStuff, the source is set to
     *       SOURCE_DEPRECATED and visitNewStuff is called. Here 'do stuff' is run because the source
     *       was previously set to SOURCE_DEPRECATED, and execution eventually returns to
     *       UserStuffVisitor.visitOldStuff, where 'do user stuff' is run.
     *   <li>call visitNewStuff: the call is redirected to UserStuffVisitor.visitOldStuff because the
     *       source is 0. Execution continues as in the previous case, resulting in 'do stuff' and 'do
     *       user stuff' being executed, in this order.
     * </ul>
     *
     * <h1>ASM subclasses</h1>
     *
     * <p>In ASM packages, subclasses of StuffVisitor can typically be sub classed again by the user,
     * and can be used with API_OLD or API_NEW. Because of this, if such a subclass must override
     * visitNewStuff, it must do so in the following way (and must not override visitOldStuff):
     *
     * <pre>
     * public class AsmStuffVisitor extends StuffVisitor {
     *   &#64;Override public void visitNewStuff(int argAndSource, ...) {
     *     if (api &#60; API_NEW &#38;&#38; (argAndSource &#38; SOURCE_DEPRECATED) == 0) {
     *       super.visitNewStuff(argAndSource, ...);
     *       return;
     *     }
     *     super.visitNewStuff(argAndSource, ...); // optional
     *     int arg = argAndSource &#38; ~SOURCE_MASK;
     *     [ do other stuff ]
     *   }
     * }
     * </pre>
     *
     * <p>If a user class extends this with 'api' equal to API_NEW, the class hierarchy is equivalent
     * to
     *
     * <pre>
     * public class StuffVisitor {
     *   &#64;Deprecated public void visitOldStuff(int arg, ...) { visitNewStuf(arg, ...); }
     *   public void visitNewStuff(int arg, ...) { [ do stuff ] }
     * }
     * public class AsmStuffVisitor extends StuffVisitor {
     *   &#64;Override public void visitNewStuff(int arg, ...) {
     *     super.visitNewStuff(arg, ...);
     *     [ do other stuff ]
     *   }
     * }
     * class UserStuffVisitor extends StuffVisitor {
     *   &#64;Override public void visitNewStuff(int arg, ...) {
     *     super.visitNewStuff(int arg, ...);
     *     [ do user stuff ]
     *   }
     * }
     * </pre>
     *
     * <p>It is then obvious that whether visitNewStuff or visitOldStuff is called, 'do stuff', 'do
     * other stuff' and 'do user stuff' will be executed, in this order. If, on the other hand, a user
     * class extends AsmStuffVisitor with 'api' equal to API_OLD, the class hierarchy is equivalent to
     *
     * <pre>
     * public class StuffVisitor {
     *   &#64;Deprecated public void visitOldStuff(int arg, ...) {
     *     visitNewStuf(arg | SOURCE_DEPRECATED, ...);
     *   }
     *   public void visitNewStuff(int argAndSource, ...) {
     *     if ((argAndSource & SOURCE_DEPRECATED) == 0) {
     *       visitOldStuff(argAndSource, ...);
     *     } else {
     *       int arg = argAndSource &#38; ~SOURCE_MASK;
     *       [ do stuff ]
     *     }
     *   }
     * }
     * public class AsmStuffVisitor extends StuffVisitor {
     *   &#64;Override public void visitNewStuff(int argAndSource, ...) {
     *     if ((argAndSource &#38; SOURCE_DEPRECATED) == 0) {
     *       super.visitNewStuff(argAndSource, ...);
     *       return;
     *     }
     *     super.visitNewStuff(argAndSource, ...); // optional
     *     int arg = argAndSource &#38; ~SOURCE_MASK;
     *     [ do other stuff ]
     *   }
     * }
     * class UserStuffVisitor extends StuffVisitor {
     *   &#64;Override public void visitOldStuff(int arg, ...) {
     *     super.visitOldStuff(arg, ...);
     *     [ do user stuff ]
     *   }
     * }
     * </pre>
     *
     * <p>and, here again, whether visitNewStuff or visitOldStuff is called, 'do stuff', 'do other
     * stuff' and 'do user stuff' will be executed, in this order (exercise left to the reader).
     *
     * <h1>Notes</h1>
     *
     * <ul>
     *   <li>the SOURCE_DEPRECATED flag is set only if 'api' is API_OLD, just before calling
     *       visitNewStuff. By hypothesis, this method is not overridden by the user. Therefore, user
     *       classes can never see this flag. Only ASM subclasses must take care of extracting the
     *       actual argument value by clearing the source flags.
     *   <li>because the SOURCE_DEPRECATED flag is immediately cleared in the caller, the caller can
     *       call visitOldStuff or visitNewStuff (in 'do stuff' and 'do user stuff') on a delegate
     *       visitor without any risks (breaking the redirection logic, "leaking" the flag, etc).
     *   <li>all the scenarios discussed above are unit tested in MethodVisitorTest.
     * </ul>
     */
    const val SOURCE_DEPRECATED: Int = 0x100
    const val SOURCE_MASK: Int = SOURCE_DEPRECATED

    // Java ClassFile versions (the minor version is stored in the 16 most significant bits, and the
    // major version in the 16 least significant bits).
    const val V1_1: Int = 3 shl 16 or 45
    const val V1_2: Int = 0 shl 16 or 46
    const val V1_3: Int = 0 shl 16 or 47
    const val V1_4: Int = 0 shl 16 or 48
    const val V1_5: Int = 0 shl 16 or 49
    const val V1_6: Int = 0 shl 16 or 50
    const val V1_7: Int = 0 shl 16 or 51
    const val V1_8: Int = 0 shl 16 or 52
    const val V9: Int = 0 shl 16 or 53
    const val V10: Int = 0 shl 16 or 54
    const val V11: Int = 0 shl 16 or 55
    const val V12: Int = 0 shl 16 or 56
    const val V13: Int = 0 shl 16 or 57
    const val V14: Int = 0 shl 16 or 58
    const val V15: Int = 0 shl 16 or 59
    const val V16: Int = 0 shl 16 or 60
    const val V17: Int = 0 shl 16 or 61
    const val V18: Int = 0 shl 16 or 62
    const val V19: Int = 0 shl 16 or 63
    const val V20: Int = 0 shl 16 or 64
    const val V21: Int = 0 shl 16 or 65
    const val V22: Int = 0 shl 16 or 66
    const val V23: Int = 0 shl 16 or 67
    const val V24: Int = 0 shl 16 or 68
    const val V25: Int = 0 shl 16 or 69
    const val V26: Int = 0 shl 16 or 70
    const val V27: Int = 0 shl 16 or 71

    /**
     * Version flag indicating that the class is using 'preview' features.
     *
     *
     * `version & V_PREVIEW == V_PREVIEW` tests if a version is flagged with `V_PREVIEW`.
     */
    const val V_PREVIEW: Int = -0x10000

    // Access flags values, defined in
    // - https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.1-200-E.1
    // - https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.5-200-A.1
    // - https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.6-200-A.1
    // - https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.25
    const val ACC_PUBLIC: Int = 0x0001 // class, field, method
    const val ACC_PRIVATE: Int = 0x0002 // class, field, method
    const val ACC_PROTECTED: Int = 0x0004 // class, field, method
    const val ACC_STATIC: Int = 0x0008 // field, method
    const val ACC_FINAL: Int = 0x0010 // class, field, method, parameter
    const val ACC_SUPER: Int = 0x0020 // class
    const val ACC_SYNCHRONIZED: Int = 0x0020 // method
    const val ACC_OPEN: Int = 0x0020 // module
    const val ACC_TRANSITIVE: Int = 0x0020 // module requires
    const val ACC_VOLATILE: Int = 0x0040 // field
    const val ACC_BRIDGE: Int = 0x0040 // method
    const val ACC_STATIC_PHASE: Int = 0x0040 // module requires
    const val ACC_VARARGS: Int = 0x0080 // method
    const val ACC_TRANSIENT: Int = 0x0080 // field
    const val ACC_NATIVE: Int = 0x0100 // method
    const val ACC_INTERFACE: Int = 0x0200 // class
    const val ACC_ABSTRACT: Int = 0x0400 // class, method
    const val ACC_STRICT: Int = 0x0800 // method
    const val ACC_SYNTHETIC: Int = 0x1000 // class, field, method, parameter, module *
    const val ACC_ANNOTATION: Int = 0x2000 // class
    const val ACC_ENUM: Int = 0x4000 // class(?) field inner
    const val ACC_MANDATED: Int = 0x8000 // field, method, parameter, module, module *
    const val ACC_MODULE: Int = 0x8000 // class

    // ASM specific access flags.
    // WARNING: the 16 least significant bits must NOT be used, to avoid conflicts with standard
    // access flags, and also to make sure that these flags are automatically filtered out when
    // written in class files (because access flags are stored using 16 bits only).
    const val ACC_RECORD: Int = 0x10000 // class
    const val ACC_DEPRECATED: Int = 0x20000 // class, field, method

    // Possible values for the type operand of the NEWARRAY instruction.
    // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-6.html#jvms-6.5.newarray.
    const val T_BOOLEAN: Int = 4
    const val T_CHAR: Int = 5
    const val T_FLOAT: Int = 6
    const val T_DOUBLE: Int = 7
    const val T_BYTE: Int = 8
    const val T_SHORT: Int = 9
    const val T_INT: Int = 10
    const val T_LONG: Int = 11

    // Possible values for the reference_kind field of CONSTANT_MethodHandle_info structures.
    // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4.8.
    const val H_GETFIELD: Int = 1
    const val H_GETSTATIC: Int = 2
    const val H_PUTFIELD: Int = 3
    const val H_PUTSTATIC: Int = 4
    const val H_INVOKEVIRTUAL: Int = 5
    const val H_INVOKESTATIC: Int = 6
    const val H_INVOKESPECIAL: Int = 7
    const val H_NEWINVOKESPECIAL: Int = 8
    const val H_INVOKEINTERFACE: Int = 9

    // ASM specific stack map frame types, used in {@link ClassVisitor#visitFrame}.
    /** An expanded frame. See [ClassReader.EXPAND_FRAMES].  */
    const val F_NEW: Int = -1

    /** A compressed frame with complete frame data.  */
    const val F_FULL: Int = 0

    /**
     * A compressed frame where locals are the same as the locals in the previous frame, except that
     * additional 1-3 locals are defined, and with an empty stack.
     */
    const val F_APPEND: Int = 1

    /**
     * A compressed frame where locals are the same as the locals in the previous frame, except that
     * the last 1-3 locals are absent and with an empty stack.
     */
    const val F_CHOP: Int = 2

    /**
     * A compressed frame with exactly the same locals as the previous frame and with an empty stack.
     */
    const val F_SAME: Int = 3

    /**
     * A compressed frame with exactly the same locals as the previous frame and with a single value
     * on the stack.
     */
    const val F_SAME1: Int = 4

    // Standard stack map frame element types, used in {@link ClassVisitor#visitFrame}.
    const val TOP: Int = Frame.ITEM_TOP
    const val INTEGER: Int = Frame.ITEM_INTEGER
    const val FLOAT: Int = Frame.ITEM_FLOAT
    const val DOUBLE: Int = Frame.ITEM_DOUBLE
    const val LONG: Int = Frame.ITEM_LONG
    const val NULL: Int = Frame.ITEM_NULL
    const val UNINITIALIZED_THIS: Int = Frame.ITEM_UNINITIALIZED_THIS

    // The JVM opcode values (with the MethodVisitor method name used to visit them in comment, and
    // where '-' means 'same method name as on the previous line').
    // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-6.html.
    const val NOP: Int = 0 // visitInsn
    const val ACONST_NULL: Int = 1 // -
    const val ICONST_M1: Int = 2 // -
    const val ICONST_0: Int = 3 // -
    const val ICONST_1: Int = 4 // -
    const val ICONST_2: Int = 5 // -
    const val ICONST_3: Int = 6 // -
    const val ICONST_4: Int = 7 // -
    const val ICONST_5: Int = 8 // -
    const val LCONST_0: Int = 9 // -
    const val LCONST_1: Int = 10 // -
    const val FCONST_0: Int = 11 // -
    const val FCONST_1: Int = 12 // -
    const val FCONST_2: Int = 13 // -
    const val DCONST_0: Int = 14 // -
    const val DCONST_1: Int = 15 // -
    const val BIPUSH: Int = 16 // visitIntInsn
    const val SIPUSH: Int = 17 // -
    const val LDC: Int = 18 // visitLdcInsn
    const val ILOAD: Int = 21 // visitVarInsn
    const val LLOAD: Int = 22 // -
    const val FLOAD: Int = 23 // -
    const val DLOAD: Int = 24 // -
    const val ALOAD: Int = 25 // -
    const val IALOAD: Int = 46 // visitInsn
    const val LALOAD: Int = 47 // -
    const val FALOAD: Int = 48 // -
    const val DALOAD: Int = 49 // -
    const val AALOAD: Int = 50 // -
    const val BALOAD: Int = 51 // -
    const val CALOAD: Int = 52 // -
    const val SALOAD: Int = 53 // -
    const val ISTORE: Int = 54 // visitVarInsn
    const val LSTORE: Int = 55 // -
    const val FSTORE: Int = 56 // -
    const val DSTORE: Int = 57 // -
    const val ASTORE: Int = 58 // -
    const val IASTORE: Int = 79 // visitInsn
    const val LASTORE: Int = 80 // -
    const val FASTORE: Int = 81 // -
    const val DASTORE: Int = 82 // -
    const val AASTORE: Int = 83 // -
    const val BASTORE: Int = 84 // -
    const val CASTORE: Int = 85 // -
    const val SASTORE: Int = 86 // -
    const val POP: Int = 87 // -
    const val POP2: Int = 88 // -
    const val DUP: Int = 89 // -
    const val DUP_X1: Int = 90 // -
    const val DUP_X2: Int = 91 // -
    const val DUP2: Int = 92 // -
    const val DUP2_X1: Int = 93 // -
    const val DUP2_X2: Int = 94 // -
    const val SWAP: Int = 95 // -
    const val IADD: Int = 96 // -
    const val LADD: Int = 97 // -
    const val FADD: Int = 98 // -
    const val DADD: Int = 99 // -
    const val ISUB: Int = 100 // -
    const val LSUB: Int = 101 // -
    const val FSUB: Int = 102 // -
    const val DSUB: Int = 103 // -
    const val IMUL: Int = 104 // -
    const val LMUL: Int = 105 // -
    const val FMUL: Int = 106 // -
    const val DMUL: Int = 107 // -
    const val IDIV: Int = 108 // -
    const val LDIV: Int = 109 // -
    const val FDIV: Int = 110 // -
    const val DDIV: Int = 111 // -
    const val IREM: Int = 112 // -
    const val LREM: Int = 113 // -
    const val FREM: Int = 114 // -
    const val DREM: Int = 115 // -
    const val INEG: Int = 116 // -
    const val LNEG: Int = 117 // -
    const val FNEG: Int = 118 // -
    const val DNEG: Int = 119 // -
    const val ISHL: Int = 120 // -
    const val LSHL: Int = 121 // -
    const val ISHR: Int = 122 // -
    const val LSHR: Int = 123 // -
    const val IUSHR: Int = 124 // -
    const val LUSHR: Int = 125 // -
    const val IAND: Int = 126 // -
    const val LAND: Int = 127 // -
    const val IOR: Int = 128 // -
    const val LOR: Int = 129 // -
    const val IXOR: Int = 130 // -
    const val LXOR: Int = 131 // -
    const val IINC: Int = 132 // visitIincInsn
    const val I2L: Int = 133 // visitInsn
    const val I2F: Int = 134 // -
    const val I2D: Int = 135 // -
    const val L2I: Int = 136 // -
    const val L2F: Int = 137 // -
    const val L2D: Int = 138 // -
    const val F2I: Int = 139 // -
    const val F2L: Int = 140 // -
    const val F2D: Int = 141 // -
    const val D2I: Int = 142 // -
    const val D2L: Int = 143 // -
    const val D2F: Int = 144 // -
    const val I2B: Int = 145 // -
    const val I2C: Int = 146 // -
    const val I2S: Int = 147 // -
    const val LCMP: Int = 148 // -
    const val FCMPL: Int = 149 // -
    const val FCMPG: Int = 150 // -
    const val DCMPL: Int = 151 // -
    const val DCMPG: Int = 152 // -
    const val IFEQ: Int = 153 // visitJumpInsn
    const val IFNE: Int = 154 // -
    const val IFLT: Int = 155 // -
    const val IFGE: Int = 156 // -
    const val IFGT: Int = 157 // -
    const val IFLE: Int = 158 // -
    const val IF_ICMPEQ: Int = 159 // -
    const val IF_ICMPNE: Int = 160 // -
    const val IF_ICMPLT: Int = 161 // -
    const val IF_ICMPGE: Int = 162 // -
    const val IF_ICMPGT: Int = 163 // -
    const val IF_ICMPLE: Int = 164 // -
    const val IF_ACMPEQ: Int = 165 // -
    const val IF_ACMPNE: Int = 166 // -
    const val GOTO: Int = 167 // -
    const val JSR: Int = 168 // -
    const val RET: Int = 169 // visitVarInsn
    const val TABLESWITCH: Int = 170 // visiTableSwitchInsn
    const val LOOKUPSWITCH: Int = 171 // visitLookupSwitch
    const val IRETURN: Int = 172 // visitInsn
    const val LRETURN: Int = 173 // -
    const val FRETURN: Int = 174 // -
    const val DRETURN: Int = 175 // -
    const val ARETURN: Int = 176 // -
    const val RETURN: Int = 177 // -
    const val GETSTATIC: Int = 178 // visitFieldInsn
    const val PUTSTATIC: Int = 179 // -
    const val GETFIELD: Int = 180 // -
    const val PUTFIELD: Int = 181 // -
    const val INVOKEVIRTUAL: Int = 182 // visitMethodInsn
    const val INVOKESPECIAL: Int = 183 // -
    const val INVOKESTATIC: Int = 184 // -
    const val INVOKEINTERFACE: Int = 185 // -
    const val INVOKEDYNAMIC: Int = 186 // visitInvokeDynamicInsn
    const val NEW: Int = 187 // visitTypeInsn
    const val NEWARRAY: Int = 188 // visitIntInsn
    const val ANEWARRAY: Int = 189 // visitTypeInsn
    const val ARRAYLENGTH: Int = 190 // visitInsn
    const val ATHROW: Int = 191 // -
    const val CHECKCAST: Int = 192 // visitTypeInsn
    const val INSTANCEOF: Int = 193 // -
    const val MONITORENTER: Int = 194 // visitInsn
    const val MONITOREXIT: Int = 195 // -
    const val MULTIANEWARRAY: Int = 197 // visitMultiANewArrayInsn
    const val IFNULL: Int = 198 // visitJumpInsn
    const val IFNONNULL: Int = 199 // -
}
