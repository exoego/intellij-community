// FIX: Replace with 'getOrElse' call
// DISABLE_ERRORS
// WITH_STDLIB
fun test(map: Map<Int, String>) {
    val s = map[1]<caret>!!
}