package bot.trade


class ListLimit<E>(internal val limit: Int = Int.MAX_VALUE) : ArrayList<E>() {

    override fun add(element: E): Boolean {
        if (super.size > limit - 1)
            while (size > limit - 1)
                removeAt(0)

        return super.add(element)
    }

    override fun add(index: Int, element: E) {
        if (super.size > limit - 1)
            while (size > limit - 1)
                removeAt(size-1)

        super.add(index, element)
    }

    fun last(): E {
        if (isEmpty())
            throw NoSuchElementException("List is empty.")
        return this[lastIndex]
    }
}