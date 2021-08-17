package tech.nomads.raygun

/**
 * Attempt to make the holder exception as cheap as possible to construct.
 *
 */
internal class RaygunException(private val fillStacktrace: Boolean = false) : Exception() {

    override fun fillInStackTrace(): Throwable {
        return if (fillStacktrace) super.fillInStackTrace() else this
    }
}
