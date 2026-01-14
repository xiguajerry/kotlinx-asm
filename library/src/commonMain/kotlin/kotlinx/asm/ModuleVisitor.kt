package kotlinx.asm

interface ModuleVisitor {

    fun visitMainClass(mainClass: String) {}

    fun visitPackage(packaze: String) {}

    fun visitRequire(module: String, access: Int, version: String?) {}

    fun visitExport(packaze: String, access: Int, modules: Array<String>?) {}

    fun visitOpen(packaze: String, access: Int, modules: Array<String>?) {}

    fun visitUse(service: String) {}

    fun visitProvide(service: String, providers: Array<String>) {}

    fun visitEnd() {}
}