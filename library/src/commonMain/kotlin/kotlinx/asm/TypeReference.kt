package kotlinx.asm

/**
 * A reference to a type appearing in a class, field or method declaration, or on an instruction.
 * Such a reference designates the part of the class where the referenced type is appearing (e.g. an
 * 'extends', 'implements' or 'throws' clause, a 'new' instruction, a 'catch' clause, a type cast, a
 * local variable declaration, etc).
 *
 * @author Eric Bruneton
 */
class TypeReference
/**
 * Constructs a new TypeReference.
 *
 * @param value the int encoded value of the type reference, as received in a visit method
 * related to type annotations, such as [ClassVisitor.visitTypeAnnotation].
 */(
    /**
     * The target_type and target_info structures - as defined in the Java Virtual Machine
     * Specification (JVMS) - corresponding to this type reference. target_type uses one byte, and all
     * the target_info union fields use up to 3 bytes (except localvar_target, handled with the
     * specific method [MethodVisitor.visitLocalVariableAnnotation]). Thus, both structures can
     * be stored in an int.
     *
     *
     * This int field stores target_type (called the TypeReference 'sort' in the public API of this
     * class) in its most significant byte, followed by the target_info fields. Depending on
     * target_type, 1, 2 or even 3 least significant bytes of this field are unused. target_info
     * fields which reference bytecode offsets are set to 0 (these offsets are ignored in ClassReader,
     * and recomputed in MethodWriter).
     *
     * @see [JVMS
     * 4.7.20](https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html.jvms-4.7.20)
     *
     * @see [JVMS
     * 4.7.20.1](https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html.jvms-4.7.20.1)
     */
    val value: Int
) {
    /**
     * Returns the int encoded value of this type reference, suitable for use in visit methods related
     * to type annotations, like visitTypeAnnotation.
     *
     * @return the int encoded value of this type reference.
     */

    val sort: Int
        /**
         * Returns the sort of this type reference.
         *
         * @return one of [.CLASS_TYPE_PARAMETER], [.METHOD_TYPE_PARAMETER], [     ][.CLASS_EXTENDS], [.CLASS_TYPE_PARAMETER_BOUND], [.METHOD_TYPE_PARAMETER_BOUND],
         * [.FIELD], [.METHOD_RETURN], [.METHOD_RECEIVER], [     ][.METHOD_FORMAL_PARAMETER], [.THROWS], [.LOCAL_VARIABLE], [     ][.RESOURCE_VARIABLE], [.EXCEPTION_PARAMETER], [.INSTANCEOF], [.NEW],
         * [.CONSTRUCTOR_REFERENCE], [.METHOD_REFERENCE], [.CAST], [     ][.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT], [.METHOD_INVOCATION_TYPE_ARGUMENT], [     ][.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT], or [.METHOD_REFERENCE_TYPE_ARGUMENT].
         */
        get() = this.value ushr 24

    val typeParameterIndex: Int
        /**
         * Returns the index of the type parameter referenced by this type reference. This method must
         * only be used for type references whose sort is [.CLASS_TYPE_PARAMETER], [ ][.METHOD_TYPE_PARAMETER], [.CLASS_TYPE_PARAMETER_BOUND] or [ ][.METHOD_TYPE_PARAMETER_BOUND].
         *
         * @return a type parameter index.
         */
        get() = (this.value and 0x00FF0000) shr 16

    val typeParameterBoundIndex: Int
        /**
         * Returns the index of the type parameter bound, within the type parameter [ ][.getTypeParameterIndex], referenced by this type reference. This method must only be used for
         * type references whose sort is [.CLASS_TYPE_PARAMETER_BOUND] or [ ][.METHOD_TYPE_PARAMETER_BOUND].
         *
         * @return a type parameter bound index.
         */
        get() = (this.value and 0x0000FF00) shr 8

    val superTypeIndex: Int
        /**
         * Returns the index of the "super type" of a class that is referenced by this type reference.
         * This method must only be used for type references whose sort is [.CLASS_EXTENDS].
         *
         * @return the index of an interface in the 'implements' clause of a class, or -1 if this type
         * reference references the type of the super class.
         */
        get() = ((this.value and 0x00FFFF00) shr 8).toShort().toInt()

    val formalParameterIndex: Int
        /**
         * Returns the index of the formal parameter whose type is referenced by this type reference. This
         * method must only be used for type references whose sort is [.METHOD_FORMAL_PARAMETER].
         *
         * @return a formal parameter index.
         */
        get() = (this.value and 0x00FF0000) shr 16

    val exceptionIndex: Int
        /**
         * Returns the index of the exception, in a 'throws' clause of a method, whose type is referenced
         * by this type reference. This method must only be used for type references whose sort is [ ][.THROWS].
         *
         * @return the index of an exception in the 'throws' clause of a method.
         */
        get() = (this.value and 0x00FFFF00) shr 8

    val tryCatchBlockIndex: Int
        /**
         * Returns the index of the try catch block (using the order in which they are visited with
         * visitTryCatchBlock), whose 'catch' type is referenced by this type reference. This method must
         * only be used for type references whose sort is [.EXCEPTION_PARAMETER] .
         *
         * @return the index of an exception in the 'throws' clause of a method.
         */
        get() = (this.value and 0x00FFFF00) shr 8

    val typeArgumentIndex: Int
        /**
         * Returns the index of the type argument referenced by this type reference. This method must only
         * be used for type references whose sort is [.CAST], [ ][.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT], [.METHOD_INVOCATION_TYPE_ARGUMENT], [ ][.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT], or [.METHOD_REFERENCE_TYPE_ARGUMENT].
         *
         * @return a type parameter index.
         */
        get() = this.value and 0xFF

    companion object {
        /**
         * The sort of type references that target a type parameter of a generic class. See [ ][.getSort].
         */
        const val CLASS_TYPE_PARAMETER: Int = 0x00

        /**
         * The sort of type references that target a type parameter of a generic method. See [ ][.getSort].
         */
        const val METHOD_TYPE_PARAMETER: Int = 0x01

        /**
         * The sort of type references that target the super class of a class or one of the interfaces it
         * implements. See [.getSort].
         */
        const val CLASS_EXTENDS: Int = 0x10

        /**
         * The sort of type references that target a bound of a type parameter of a generic class. See
         * [.getSort].
         */
        const val CLASS_TYPE_PARAMETER_BOUND: Int = 0x11

        /**
         * The sort of type references that target a bound of a type parameter of a generic method. See
         * [.getSort].
         */
        const val METHOD_TYPE_PARAMETER_BOUND: Int = 0x12

        /** The sort of type references that target the type of a field. See [.getSort].  */
        const val FIELD: Int = 0x13

        /** The sort of type references that target the return type of a method. See [.getSort].  */
        const val METHOD_RETURN: Int = 0x14

        /**
         * The sort of type references that target the receiver type of a method. See [.getSort].
         */
        const val METHOD_RECEIVER: Int = 0x15

        /**
         * The sort of type references that target the type of a formal parameter of a method. See [ ][.getSort].
         */
        const val METHOD_FORMAL_PARAMETER: Int = 0x16

        /**
         * The sort of type references that target the type of an exception declared in the throws clause
         * of a method. See [.getSort].
         */
        const val THROWS: Int = 0x17

        /**
         * The sort of type references that target the type of a local variable in a method. See [ ][.getSort].
         */
        const val LOCAL_VARIABLE: Int = 0x40

        /**
         * The sort of type references that target the type of a resource variable in a method. See [ ][.getSort].
         */
        const val RESOURCE_VARIABLE: Int = 0x41

        /**
         * The sort of type references that target the type of the exception of a 'catch' clause in a
         * method. See [.getSort].
         */
        const val EXCEPTION_PARAMETER: Int = 0x42

        /**
         * The sort of type references that target the type declared in an 'instanceof' instruction. See
         * [.getSort].
         */
        const val INSTANCEOF: Int = 0x43

        /**
         * The sort of type references that target the type of the object created by a 'new' instruction.
         * See [.getSort].
         */
        const val NEW: Int = 0x44

        /**
         * The sort of type references that target the receiver type of a constructor reference. See
         * [.getSort].
         */
        const val CONSTRUCTOR_REFERENCE: Int = 0x45

        /**
         * The sort of type references that target the receiver type of a method reference. See [ ][.getSort].
         */
        const val METHOD_REFERENCE: Int = 0x46

        /**
         * The sort of type references that target the type declared in an explicit or implicit cast
         * instruction. See [.getSort].
         */
        const val CAST: Int = 0x47

        /**
         * The sort of type references that target a type parameter of a generic constructor in a
         * constructor call. See [.getSort].
         */
        const val CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT: Int = 0x48

        /**
         * The sort of type references that target a type parameter of a generic method in a method call.
         * See [.getSort].
         */
        const val METHOD_INVOCATION_TYPE_ARGUMENT: Int = 0x49

        /**
         * The sort of type references that target a type parameter of a generic constructor in a
         * constructor reference. See [.getSort].
         */
        const val CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT: Int = 0x4A

        /**
         * The sort of type references that target a type parameter of a generic method in a method
         * reference. See [.getSort].
         */
        const val METHOD_REFERENCE_TYPE_ARGUMENT: Int = 0x4B

        /**
         * Returns a type reference of the given sort.
         *
         * @param sort one of [.FIELD], [.METHOD_RETURN], [.METHOD_RECEIVER], [     ][.LOCAL_VARIABLE], [.RESOURCE_VARIABLE], [.INSTANCEOF], [.NEW], [     ][.CONSTRUCTOR_REFERENCE], or [.METHOD_REFERENCE].
         * @return a type reference of the given sort.
         */
        fun newTypeReference(sort: Int): TypeReference {
            return TypeReference(sort shl 24)
        }

        /**
         * Returns a reference to a type parameter of a generic class or method.
         *
         * @param sort one of [.CLASS_TYPE_PARAMETER] or [.METHOD_TYPE_PARAMETER].
         * @param paramIndex the type parameter index.
         * @return a reference to the given generic class or method type parameter.
         */
        fun newTypeParameterReference(sort: Int, paramIndex: Int): TypeReference {
            return TypeReference((sort shl 24) or (paramIndex shl 16))
        }

        /**
         * Returns a reference to a type parameter bound of a generic class or method.
         *
         * @param sort one of [.CLASS_TYPE_PARAMETER] or [.METHOD_TYPE_PARAMETER].
         * @param paramIndex the type parameter index.
         * @param boundIndex the type bound index within the above type parameters.
         * @return a reference to the given generic class or method type parameter bound.
         */
        fun newTypeParameterBoundReference(
            sort: Int, paramIndex: Int, boundIndex: Int
        ): TypeReference {
            return TypeReference((sort shl 24) or (paramIndex shl 16) or (boundIndex shl 8))
        }

        /**
         * Returns a reference to the super class or to an interface of the 'implements' clause of a
         * class.
         *
         * @param itfIndex the index of an interface in the 'implements' clause of a class, or -1 to
         * reference the super class of the class.
         * @return a reference to the given super type of a class.
         */
        fun newSuperTypeReference(itfIndex: Int): TypeReference {
            return TypeReference((CLASS_EXTENDS shl 24) or ((itfIndex and 0xFFFF) shl 8))
        }

        /**
         * Returns a reference to the type of a formal parameter of a method.
         *
         * @param paramIndex the formal parameter index.
         * @return a reference to the type of the given method formal parameter.
         */
        fun newFormalParameterReference(paramIndex: Int): TypeReference {
            return TypeReference((METHOD_FORMAL_PARAMETER shl 24) or (paramIndex shl 16))
        }

        /**
         * Returns a reference to the type of an exception, in a 'throws' clause of a method.
         *
         * @param exceptionIndex the index of an exception in a 'throws' clause of a method.
         * @return a reference to the type of the given exception.
         */
        fun newExceptionReference(exceptionIndex: Int): TypeReference {
            return TypeReference((THROWS shl 24) or (exceptionIndex shl 8))
        }

        /**
         * Returns a reference to the type of the exception declared in a 'catch' clause of a method.
         *
         * @param tryCatchBlockIndex the index of a try catch block (using the order in which they are
         * visited with visitTryCatchBlock).
         * @return a reference to the type of the given exception.
         */
        fun newTryCatchReference(tryCatchBlockIndex: Int): TypeReference {
            return TypeReference((EXCEPTION_PARAMETER shl 24) or (tryCatchBlockIndex shl 8))
        }

        /**
         * Returns a reference to the type of a type argument in a constructor or method call or
         * reference.
         *
         * @param sort one of [.CAST], [.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT], [     ][.METHOD_INVOCATION_TYPE_ARGUMENT], [.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT], or [     ][.METHOD_REFERENCE_TYPE_ARGUMENT].
         * @param argIndex the type argument index.
         * @return a reference to the type of the given type argument.
         */
        fun newTypeArgumentReference(sort: Int, argIndex: Int): TypeReference {
            return TypeReference((sort shl 24) or argIndex)
        }

        /**
         * Puts the given target_type and target_info JVMS structures into the given ByteVector.
         *
         * @param targetTypeAndInfo a target_type and a target_info structures encoded as in [     ][.targetTypeAndInfo]. LOCAL_VARIABLE and RESOURCE_VARIABLE target types are not supported.
         * @param output where the type reference must be put.
         */
        fun putTarget(targetTypeAndInfo: Int, output: ByteVector) {
            when (targetTypeAndInfo ushr 24) {
                CLASS_TYPE_PARAMETER, METHOD_TYPE_PARAMETER, METHOD_FORMAL_PARAMETER -> output.putShort(
                    targetTypeAndInfo ushr 16
                )

                FIELD, METHOD_RETURN, METHOD_RECEIVER -> output.putByte(targetTypeAndInfo ushr 24)
                CAST, CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT, METHOD_INVOCATION_TYPE_ARGUMENT, CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT, METHOD_REFERENCE_TYPE_ARGUMENT -> output.putInt(
                    targetTypeAndInfo
                )

                CLASS_EXTENDS, CLASS_TYPE_PARAMETER_BOUND, METHOD_TYPE_PARAMETER_BOUND, THROWS, EXCEPTION_PARAMETER, INSTANCEOF, NEW, CONSTRUCTOR_REFERENCE, METHOD_REFERENCE -> output.put12(
                    targetTypeAndInfo ushr 24,
                    (targetTypeAndInfo and 0xFFFF00) shr 8
                )

                else -> throw IllegalArgumentException()
            }
        }
    }
}
