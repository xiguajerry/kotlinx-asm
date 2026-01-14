package kotlinx.asm


interface AnnotationVisitor {

    fun visit(name: String?, value: Any) {}

    fun visitEnum(name: String?, descriptor: String, value: String) {}

    fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor? = null

    fun visitArray(name: String?): AnnotationVisitor? = null

    fun visitEnd() {}
}