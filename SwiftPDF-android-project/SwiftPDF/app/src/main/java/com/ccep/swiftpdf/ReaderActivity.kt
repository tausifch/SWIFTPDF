package com.ccep.swiftpdf

import android.app.ProgressDialog
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import com.ccep.swiftpdf.databinding.ActivityReaderBinding
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var uri: Uri
    private var nightMode = false
    private var currentPage = 0
    private var totalPages = 0

    // Search cache: extracted text of every page (built once per document, then instant)
    private var pageTexts: Array<String>? = null
    private var matches: List<Int> = emptyList()
    private var matchIndex = -1
    private var lastQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        uri = intent.data ?: run { finish(); return }
        val book = BookStore.touch(this, uri, queryName(uri))
        supportActionBar?.title = book.name.removeSuffix(".pdf")

        nightMode = getSharedPreferences("swiftpdf_ui", MODE_PRIVATE)
            .getBoolean("night", false)

        loadPdf(book.lastPage)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadPdf(startPage: Int) {
        binding.pdfView.fromUri(uri)
            .defaultPage(startPage)
            .swipeHorizontal(false)          // vertical continuous scrolling
            .pageSnap(false)
            .pageFling(false)
            .autoSpacing(false)
            .spacing(6)
            .enableDoubletap(true)
            .enableAnnotationRendering(true)
            .nightMode(nightMode)
            .pageFitPolicy(FitPolicy.WIDTH)
            .scrollHandle(DefaultScrollHandle(this)) // drag handle = thunder-fast jumps
            .onPageChange { page, total ->
                currentPage = page
                totalPages = total
                binding.pageIndicator.text = getString(R.string.page_progress, page + 1, total)
            }
            .onLoad { total ->
                totalPages = total
                binding.pageIndicator.text =
                    getString(R.string.page_progress, currentPage + 1, total)
            }
            .onError {
                Toast.makeText(this, R.string.load_error, Toast.LENGTH_LONG).show()
                finish()
            }
            .onTap {
                // Tap toggles the toolbar for distraction-free reading
                val show = binding.toolbar.visibility != android.view.View.VISIBLE
                binding.toolbar.visibility =
                    if (show) android.view.View.VISIBLE else android.view.View.GONE
                true
            }
            .load()
    }

    override fun onPause() {
        super.onPause()
        // Remember exactly where the reader left off
        BookStore.updateProgress(this, uri, currentPage, totalPages)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_reader, menu)
        val favItem = menu.findItem(R.id.action_favorite)
        favItem.setIcon(
            if (BookStore.isFavorite(this, uri)) R.drawable.ic_star_filled
            else R.drawable.ic_star_outline
        )

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                doSearch(query.trim())
                return true
            }
            override fun onQueryTextChange(newText: String) = false
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_favorite -> {
                val fav = !BookStore.isFavorite(this, uri)
                BookStore.setFavorite(this, uri.toString(), fav)
                item.setIcon(if (fav) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
                Toast.makeText(
                    this,
                    if (fav) R.string.added_favorite else R.string.removed_favorite,
                    Toast.LENGTH_SHORT
                ).show()
            }
            R.id.action_night -> {
                nightMode = !nightMode
                getSharedPreferences("swiftpdf_ui", MODE_PRIVATE)
                    .edit().putBoolean("night", nightMode).apply()
                loadPdf(currentPage)
            }
            R.id.action_goto -> showGoToPage()
            R.id.action_next_match -> jumpMatch(+1)
            R.id.action_prev_match -> jumpMatch(-1)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showGoToPage() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.page_progress, currentPage + 1, totalPages)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.go_to_page)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val p = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                if (p in 1..totalPages) binding.pdfView.jumpTo(p - 1, true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ------------------------- SEARCH -------------------------

    private fun doSearch(query: String) {
        if (query.isEmpty()) return
        lifecycleScope.launch {
            ensureTextExtracted() ?: return@launch
            val texts = pageTexts ?: return@launch
            lastQuery = query
            matches = withContext(Dispatchers.Default) {
                texts.indices.filter { texts[it].contains(query, ignoreCase = true) }
            }
            if (matches.isEmpty()) {
                Toast.makeText(this@ReaderActivity, R.string.no_results, Toast.LENGTH_SHORT).show()
                return@launch
            }
            showResultsDialog(query)
        }
    }

    private fun showResultsDialog(query: String) {
        val texts = pageTexts ?: return
        val labels = matches.map { page ->
            val text = texts[page]
            val i = text.indexOf(query, ignoreCase = true)
            val start = (i - 30).coerceAtLeast(0)
            val end = (i + query.length + 40).coerceAtMost(text.length)
            val snippet = text.substring(start, end).replace('\n', ' ').trim()
            getString(R.string.result_row, page + 1, "…$snippet…")
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.results_title, matches.size))
            .setItems(labels) { _, which ->
                matchIndex = which
                binding.pdfView.jumpTo(matches[which], true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun jumpMatch(dir: Int) {
        if (matches.isEmpty()) {
            Toast.makeText(this, R.string.search_first, Toast.LENGTH_SHORT).show()
            return
        }
        matchIndex = (matchIndex + dir).mod(matches.size)
        binding.pdfView.jumpTo(matches[matchIndex], true)
        Toast.makeText(
            this,
            getString(R.string.match_position, matchIndex + 1, matches.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Extracts text of all pages once (background thread), then search is instant. */
    private suspend fun ensureTextExtracted(): Unit? {
        if (pageTexts != null) return Unit
        @Suppress("DEPRECATION")
        val dialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.indexing))
            setCancelable(false)
            show()
        }
        val result = withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { doc ->
                        val stripper = PDFTextStripper()
                        Array(doc.numberOfPages) { i ->
                            stripper.startPage = i + 1
                            stripper.endPage = i + 1
                            try { stripper.getText(doc) } catch (e: Exception) { "" }
                        }
                    }
                }
            } catch (e: Exception) { null }
        }
        dialog.dismiss()
        return if (result != null) {
            pageTexts = result
            Unit
        } else {
            Toast.makeText(this, R.string.search_unavailable, Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx) ?: "Document.pdf"
        }
        return uri.lastPathSegment ?: "Document.pdf"
    }
}
