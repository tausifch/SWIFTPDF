package com.ccep.swiftpdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ccep.swiftpdf.databinding.ActivityMainBinding
import com.ccep.swiftpdf.databinding.ItemBookBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: BookAdapter
    private var showFavoritesOnly = false

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        // Keep permission so the book opens instantly next time, forever
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { }
        val name = queryName(uri)
        BookStore.touch(this, uri, name)
        openReader(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = BookAdapter(
            onClick = { openReader(Uri.parse(it.uri)) },
            onFavorite = { book, fav ->
                BookStore.setFavorite(this, book.uri, fav)
                refresh()
            },
            onLongClick = { book ->
                AlertDialog.Builder(this)
                    .setTitle(book.name)
                    .setItems(arrayOf(getString(R.string.remove_from_library))) { _, _ ->
                        BookStore.remove(this, book.uri)
                        refresh()
                    }
                    .show()
            }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabOpen.setOnClickListener { openPdf.launch(arrayOf("application/pdf")) }

        binding.chipAll.setOnClickListener { showFavoritesOnly = false; refresh() }
        binding.chipFavorites.setOnClickListener { showFavoritesOnly = true; refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val books = BookStore.getAll(this)
            .filter { !showFavoritesOnly || it.favorite }
            .sortedWith(
                compareByDescending<Book> { it.favorite && showFavoritesOnly }
                    .thenByDescending { it.lastOpened }
            )
        adapter.submit(books)
        binding.emptyState.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.text = getString(
            if (showFavoritesOnly) R.string.empty_favorites else R.string.empty_library
        )
    }

    private fun openReader(uri: Uri) {
        startActivity(Intent(this, ReaderActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx) ?: "Document.pdf"
        }
        return uri.lastPathSegment ?: "Document.pdf"
    }
}

class BookAdapter(
    private val onClick: (Book) -> Unit,
    private val onFavorite: (Book, Boolean) -> Unit,
    private val onLongClick: (Book) -> Unit
) : RecyclerView.Adapter<BookAdapter.VH>() {

    private val items = mutableListOf<Book>()

    fun submit(list: List<Book>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    inner class VH(val b: ItemBookBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val book = items[pos]
        h.b.title.text = book.name.removeSuffix(".pdf")
        h.b.subtitle.text = if (book.totalPages > 0)
            h.b.root.context.getString(R.string.page_progress, book.lastPage + 1, book.totalPages)
        else h.b.root.context.getString(R.string.not_opened_yet)

        h.b.btnFavorite.setImageResource(
            if (book.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        h.b.btnFavorite.setOnClickListener { onFavorite(book, !book.favorite) }
        h.b.root.setOnClickListener { onClick(book) }
        h.b.root.setOnLongClickListener { onLongClick(book); true }
    }
}
