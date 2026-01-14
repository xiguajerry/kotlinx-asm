package kotlinx.asm.insn

import kotlinx.asm.AnnotationVisitor

/**
 * A node that represents an annotation.
 *
 * @author Eric Bruneton
 */
open class AnnotationNode : AnnotationVisitor {
    lateinit var desc: String
    val values: MutableList<Any?>

    constructor(desc: String) {
        this.desc = desc
        values = mutableListOf()
    }

    internal constructor(values: MutableList<Any?>) {
        this.values = values
    }

    override fun visit(name: String?, value: Any) {
        if (::desc.isInitialized) {
            values.add(name)
        }
        when (value) {
            is ByteArray -> {
                values.add(value.toList())
            }

            is BooleanArray -> {
                values.add(value.toList())
            }

            is ShortArray -> {
                values.add(value.toList())
            }

            is CharArray -> {
                values.add(value.toList())
            }

            is IntArray -> {
                values.add(value.toList())
            }

            is LongArray -> {
                values.add(value.toList())
            }

            is FloatArray -> {
                values.add(value.toList())
            }

            is DoubleArray -> {
                values.add(value.toList())
            }

            else -> {
                values.add(value)
            }
        }
    }

    override fun visitEnum(name: String?, descriptor: String, value: String) {
        if (::desc.isInitialized) {
            values.add(name)
        }
        values.add(arrayOf<String?>(descriptor, value))
    }

    override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor {
        if (::desc.isInitialized) {
            values.add(name)
        }
        val annotation = AnnotationNode(descriptor)
        values.add(annotation)
        return annotation
    }

    override fun visitArray(name: String?): AnnotationVisitor {
        if (::desc.isInitialized) {
            values.add(name)
        }
        val array = mutableListOf<Any?>()
        values.add(array)
        return AnnotationNode(array)
    }

    override fun visitEnd() {
        // Nothing to do.
    }


    // ------------------------------------------------------------------------
    // Accept methods
    // ------------------------------------------------------------------------
    /**
     * Checks that this annotation node is compatible with the given ASM API version. This method
     * checks that this node, and all its children recursively, do not contain elements that were
     * introduced in more recent versions of the ASM API than the given version.
     *
     * @param api an ASM API version. Must be one of the `ASM`*x* values in [     ].
     */
    fun check(api: Int) {
        // nothing to do
    }

    /**
     * Makes the given visitor visit this annotation.
     *
     * @param annotationVisitor an annotation visitor. Maybe null.
     */
    fun accept(annotationVisitor: AnnotationVisitor?) {
        if (annotationVisitor != null) {
            if (values.isNotEmpty()) {
                var i = 0
                val n = values.size
                while (i < n) {
                    val name = values[i] as String
                    val value = values[i + 1]!!
                    accept(annotationVisitor, name, value)
                    i += 2
                }
            }
            annotationVisitor.visitEnd()
        }
    }

    companion object {
        /**
         * Makes the given visitor visit a given annotation value.
         *
         * @param annotationVisitor an annotation visitor. Maybe null.
         * @param name the value name.
         * @param value the actual value.
         */
        fun accept(
            annotationVisitor: AnnotationVisitor?,
            name: String?,
            value: Any
        ) {
            if (annotationVisitor != null) {
                if (value is Array<*>) {
                    val typeValue = value as Array<String>
                    annotationVisitor.visitEnum(name, typeValue[0], typeValue[1])
                } else if (value is AnnotationNode) {
                    value.accept(annotationVisitor.visitAnnotation(name, value.desc))
                } else if (value is List<*>) {
                    val arrayAnnotationVisitor = annotationVisitor.visitArray(name)
                    if (arrayAnnotationVisitor != null) {
                        (value as List<Any>).forEach {
                            accept(arrayAnnotationVisitor, null, it)
                        }
                        arrayAnnotationVisitor.visitEnd()
                    }
                } else {
                    annotationVisitor.visit(name, value)
                }
            }
        }
    }
}
