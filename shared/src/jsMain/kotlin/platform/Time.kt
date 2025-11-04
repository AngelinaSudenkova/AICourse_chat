package platform

@Suppress("UNUSED_EXPRESSION")
actual fun currentTimeMillis(): Long {
    val date = js("new Date()")
    return (date.getTime() as Number).toLong()
}

