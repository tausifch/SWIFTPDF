package com.ccep.swiftpdf

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Book(
    val uri: String,
    var name: String,
    var lastPage: Int = 0,
    var totalPages: Int = 0,
    var favorite: Boolean = false,
    var lastOpened: Long = System.currentTimeMillis()
)

/**
 * Tiny, instant persistence layer (SharedPreferences + Gson).
 * Keeps recents sorted by last-opened and remembers reading position per book.
 */
object BookStore {
    private const val PREFS = "swiftpdf_books"
    private const val KEY = "books"
    private val gson = Gson()

    fun getAll(ctx: Context): MutableList<Book> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Book>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { mutableListOf() }
    }

    private fun save(ctx: Context, books: List<Book>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, gson.toJson(books)).apply()
    }

    fun find(ctx: Context, uri: Uri): Book? = getAll(ctx).firstOrNull { it.uri == uri.toString() }

    /** Add or bump a book to the top of recents. Returns the stored book. */
    fun touch(ctx: Context, uri: Uri, name: String): Book {
        val books = getAll(ctx)
        var book = books.firstOrNull { it.uri == uri.toString() }
        if (book == null) {
            book = Book(uri.toString(), name)
            books.add(book)
        }
        book.lastOpened = System.currentTimeMillis()
        if (name.isNotBlank()) book.name = name
        save(ctx, books)
        return book
    }

    fun updateProgress(ctx: Context, uri: Uri, page: Int, total: Int) {
        val books = getAll(ctx)
        books.firstOrNull { it.uri == uri.toString() }?.let {
            it.lastPage = page
            it.totalPages = total
            save(ctx, books)
        }
    }

    fun setFavorite(ctx: Context, uri: String, fav: Boolean) {
        val books = getAll(ctx)
        books.firstOrNull { it.uri == uri }?.let {
            it.favorite = fav
            save(ctx, books)
        }
    }

    fun isFavorite(ctx: Context, uri: Uri): Boolean =
        getAll(ctx).firstOrNull { it.uri == uri.toString() }?.favorite == true

    fun remove(ctx: Context, uri: String) {
        val books = getAll(ctx)
        books.removeAll { it.uri == uri }
        save(ctx, books)
    }
}
