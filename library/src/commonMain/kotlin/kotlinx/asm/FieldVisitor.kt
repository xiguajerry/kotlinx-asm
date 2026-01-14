package kotlinx.asm

interface FieldVisitor {

    fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? = null

    fun visitTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean
    ): AnnotationVisitor? = null

    fun visitAttribute(attribute: Attribute) {}

    fun visitEnd()
}