// IGNORE_K2
class C {
    String getString(boolean b) {
        return b ? "a" : null;
    }

    int foo() {
        return getString(true).length();
    }
}
