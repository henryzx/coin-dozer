package models

import java.util.*

data class Item(val title: String, val url: String, val date: Date, var detail: Detail? = null) : Comparable<Item> {
    override fun compareTo(other: Item): Int {
        return date.compareTo(other.date)
    }

}