// "Add 1st parameter to function 'foo'" "true"
fun foo(i1: Int, i2: Int, i3: Int, i4: Int) {
}

fun test() {
    foo(<caret>"", 2, 3, 4, 5)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix