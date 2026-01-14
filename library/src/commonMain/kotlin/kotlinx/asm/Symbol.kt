package kotlinx.asm

internal abstract class Symbol(
    /**
     * The index of this symbol in the constant pool, in the BootstrapMethods attribute, or in the
     * (ASM specific) type table of a class (depending on the [.tag] value).
     */
    val index: Int,
    /**
     * A tag indicating the type of this symbol. Must be one of the static tag values defined in this
     * class.
     */
    val tag: Int,
    /**
     * The internal name of the owner class of this symbol. Only used for [ ][.CONSTANT_FIELDREF_TAG], [.CONSTANT_METHODREF_TAG], [ ][.CONSTANT_INTERFACE_METHODREF_TAG], and [.CONSTANT_METHOD_HANDLE_TAG] symbols.
     */
    val owner: String?,
    /**
     * The name of the class field or method corresponding to this symbol. Only used for [ ][.CONSTANT_FIELDREF_TAG], [.CONSTANT_METHODREF_TAG], [ ][.CONSTANT_INTERFACE_METHODREF_TAG], [.CONSTANT_NAME_AND_TYPE_TAG], [ ][.CONSTANT_METHOD_HANDLE_TAG], [.CONSTANT_DYNAMIC_TAG] and [ ][.CONSTANT_INVOKE_DYNAMIC_TAG] symbols.
     */
    val name: String?,
    /**
     * The string value of this symbol. This is:
     *
     *
     *  * a field or method descriptor for [.CONSTANT_FIELDREF_TAG], [       ][.CONSTANT_METHODREF_TAG], [.CONSTANT_INTERFACE_METHODREF_TAG], [       ][.CONSTANT_NAME_AND_TYPE_TAG], [.CONSTANT_METHOD_HANDLE_TAG], [       ][.CONSTANT_METHOD_TYPE_TAG], [.CONSTANT_DYNAMIC_TAG] and [       ][.CONSTANT_INVOKE_DYNAMIC_TAG] symbols,
     *  * an arbitrary string for [.CONSTANT_UTF8_TAG] and [.CONSTANT_STRING_TAG]
     * symbols,
     *  * an internal class name for [.CONSTANT_CLASS_TAG], [.TYPE_TAG], [       ][.UNINITIALIZED_TYPE_TAG] and [.FORWARD_UNINITIALIZED_TYPE_TAG] symbols,
     *  * null for the other types of symbol.
     *
     */
    val value: String?,
    /**
     * The numeric value of this symbol. This is:
     *
     *
     *  * the symbol's value for [.CONSTANT_INTEGER_TAG],[.CONSTANT_FLOAT_TAG], [       ][.CONSTANT_LONG_TAG], [.CONSTANT_DOUBLE_TAG],
     *  * the CONSTANT_MethodHandle_info reference_kind field value for [       ][.CONSTANT_METHOD_HANDLE_TAG] symbols (or this value left shifted by 8 bits for
     * reference_kind values larger than or equal to H_INVOKEVIRTUAL and if the method owner is
     * an interface),
     *  * the CONSTANT_InvokeDynamic_info bootstrap_method_attr_index field value for [       ][.CONSTANT_INVOKE_DYNAMIC_TAG] symbols,
     *  * the offset of a bootstrap method in the BootstrapMethods boostrap_methods array, for
     * [.CONSTANT_DYNAMIC_TAG] or [.BOOTSTRAP_METHOD_TAG] symbols,
     *  * the bytecode offset of the NEW instruction that created an [       ][Frame.ITEM_UNINITIALIZED] type for [.UNINITIALIZED_TYPE_TAG] symbols,
     *  * the index of the [Label] (in the [SymbolTable.labelTable] table) of the NEW
     * instruction that created an [Frame.ITEM_UNINITIALIZED] type for [       ][.FORWARD_UNINITIALIZED_TYPE_TAG] symbols,
     *  * the indices (in the class' type table) of two [.TYPE_TAG] source types for [       ][.MERGED_TYPE_TAG] symbols,
     *  * 0 for the other types of symbol.
     *
     */
    val data: Long
) {
    // Instance fields.

    /**
     * Additional information about this symbol, generally computed lazily. *Warning: the value of
     * this field is ignored when comparing Symbol instances* (to avoid duplicate entries in a
     * SymbolTable). Therefore, this field should only contain data that can be computed from the
     * other fields of this class. It contains:
     *
     *
     *  * the [Type.getArgumentsAndReturnSizes] of the symbol's method descriptor for [       ][.CONSTANT_METHODREF_TAG], [.CONSTANT_INTERFACE_METHODREF_TAG] and [       ][.CONSTANT_INVOKE_DYNAMIC_TAG] symbols,
     *  * the index in the InnerClasses_attribute 'classes' array (plus one) corresponding to this
     * class, for [.CONSTANT_CLASS_TAG] symbols,
     *  * the index (in the class' type table) of the merged type of the two source types for
     * [.MERGED_TYPE_TAG] symbols,
     *  * 0 for the other types of symbol, or if this field has not been computed yet.
     *
     */
    var info: Int = 0

    val argumentsAndReturnSizes: Int
        /**
         * Returns the result [Type.getArgumentsAndReturnSizes] on [.value].
         *
         * @return the result [Type.getArgumentsAndReturnSizes] on [.value] (memoized in
         * [.info] for efficiency). This should only be used for [     ][.CONSTANT_METHODREF_TAG], [.CONSTANT_INTERFACE_METHODREF_TAG] and [     ][.CONSTANT_INVOKE_DYNAMIC_TAG] symbols.
         */
        get() {
            if (info == 0) {
                info = Type.getArgumentsAndReturnSizes(value!!)
            }
            return info
        }

    companion object {
        // Tag values for the constant pool entries (using the same order as in the JVMS).
        /** The tag value of CONSTANT_Class_info JVMS structures.  */
        const val CONSTANT_CLASS_TAG: Int = 7

        /** The tag value of CONSTANT_Fieldref_info JVMS structures.  */
        const val CONSTANT_FIELDREF_TAG: Int = 9

        /** The tag value of CONSTANT_Methodref_info JVMS structures.  */
        const val CONSTANT_METHODREF_TAG: Int = 10

        /** The tag value of CONSTANT_InterfaceMethodref_info JVMS structures.  */
        const val CONSTANT_INTERFACE_METHODREF_TAG: Int = 11

        /** The tag value of CONSTANT_String_info JVMS structures.  */
        const val CONSTANT_STRING_TAG: Int = 8

        /** The tag value of CONSTANT_Integer_info JVMS structures.  */
        const val CONSTANT_INTEGER_TAG: Int = 3

        /** The tag value of CONSTANT_Float_info JVMS structures.  */
        const val CONSTANT_FLOAT_TAG: Int = 4

        /** The tag value of CONSTANT_Long_info JVMS structures.  */
        const val CONSTANT_LONG_TAG: Int = 5

        /** The tag value of CONSTANT_Double_info JVMS structures.  */
        const val CONSTANT_DOUBLE_TAG: Int = 6

        /** The tag value of CONSTANT_NameAndType_info JVMS structures.  */
        const val CONSTANT_NAME_AND_TYPE_TAG: Int = 12

        /** The tag value of CONSTANT_Utf8_info JVMS structures.  */
        const val CONSTANT_UTF8_TAG: Int = 1

        /** The tag value of CONSTANT_MethodHandle_info JVMS structures.  */
        const val CONSTANT_METHOD_HANDLE_TAG: Int = 15

        /** The tag value of CONSTANT_MethodType_info JVMS structures.  */
        const val CONSTANT_METHOD_TYPE_TAG: Int = 16

        /** The tag value of CONSTANT_Dynamic_info JVMS structures.  */
        const val CONSTANT_DYNAMIC_TAG: Int = 17

        /** The tag value of CONSTANT_InvokeDynamic_info JVMS structures.  */
        const val CONSTANT_INVOKE_DYNAMIC_TAG: Int = 18

        /** The tag value of CONSTANT_Module_info JVMS structures.  */
        const val CONSTANT_MODULE_TAG: Int = 19

        /** The tag value of CONSTANT_Package_info JVMS structures.  */
        const val CONSTANT_PACKAGE_TAG: Int = 20

        // Tag values for the BootstrapMethods attribute entries (ASM specific tag).
        /** The tag value of the BootstrapMethods attribute entries.  */
        const val BOOTSTRAP_METHOD_TAG: Int = 64

        // Tag values for the type table entries (ASM specific tags).
        /** The tag value of a normal type entry in the (ASM specific) type table of a class.  */
        const val TYPE_TAG: Int = 128

        /**
         * The tag value of an uninitialized type entry in the type table of a class. This type is used
         * for the normal case where the NEW instruction is before the &lt;init&gt; constructor call (in
         * bytecode offset order), i.e. when the label of the NEW instruction is resolved when the
         * constructor call is visited. If the NEW instruction is after the constructor call, use the
         * [.FORWARD_UNINITIALIZED_TYPE_TAG] tag value instead.
         */
        const val UNINITIALIZED_TYPE_TAG: Int = 129

        /**
         * The tag value of an uninitialized type entry in the type table of a class. This type is used
         * for the unusual case where the NEW instruction is after the &lt;init&gt; constructor call (in
         * bytecode offset order), i.e. when the label of the NEW instruction is not resolved when the
         * constructor call is visited. If the NEW instruction is before the constructor call, use the
         * [.UNINITIALIZED_TYPE_TAG] tag value instead.
         */
        const val FORWARD_UNINITIALIZED_TYPE_TAG: Int = 130

        /** The tag value of a merged type entry in the (ASM specific) type table of a class.  */
        const val MERGED_TYPE_TAG: Int = 131
    }
}
