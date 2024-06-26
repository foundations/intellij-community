// we intentionally do not convert interface to Kotlin fun interface
// if it inherits from some other interface, because it is hard to deal
// with default methods which were already converted to properties
// (and in kotlin fun interface cannot have abstract property)
interface MyRunnableBase {
    val value: Int
        get() = 0
}

@FunctionalInterface
interface MyRunnable : MyRunnableBase {
    override val value: Int
}
