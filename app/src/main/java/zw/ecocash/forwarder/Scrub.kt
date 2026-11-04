package zw.ecocash.forwarder

/**
 * Privacy: never forward anything that could move money for someone else.
 * Remove PINs, one-time codes and tokens before the text leaves the phone.
 * Wrong-PIN notices are still forwarded (the owner needs to know), but without any digits that look like a PIN.
 */
object Scrub {
    private val rules = listOf(
        Regex("(?i)(pin|otp|one[- ]?time (?:pin|password|code)|token|password|code)\\s*(?:is|:|=)?\\s*\\d{3,8}") to "$1 [removed]",
        Regex("(?i)(verification|confirmation|reset)\\s+code\\s*(?:is|:)?\\s*\\d{3,8}") to "$1 code [removed]"
    )

    fun clean(body: String): String {
        var t = body
        for ((re, rep) in rules) t = re.replace(t, rep)
        return t
    }

    /** Only ECOCASH-type senders. Phones also get airtime promos and network notices we do not need. */
    fun wanted(sender: String, allowed: List<String>): Boolean =
        allowed.any { it.equals(sender.trim(), ignoreCase = true) }
}
