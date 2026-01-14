package kotlinx.asm

interface ClassVisitor {

    fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: List<String>
    ) {
    }

    fun visitSource(source: String?, debug: String?) {}

    fun visitModule(name: String, access: Int, version: String?): ModuleVisitor? = null

    fun visitNestHost(nestHost: String) {}

    fun visitOuterClass(owner: String, name: String?, descriptor: String?) {}

    fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? = null

    fun visitTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean
    ): AnnotationVisitor? = null

    fun visitAttribute(attribute: Attribute) {}

    fun visitNestMember(nestMember: String) {}

    fun visitPermittedSubclass(permittedSubclass: String) {}

    fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {}

    fun visitRecordComponent(
        name: String, descriptor: String, signature: String?
    ): RecordComponentVisitor? = null

    fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor? = null

    fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: List<String>?
    ): MethodVisitor? = null

    fun visitEnd() {}
}