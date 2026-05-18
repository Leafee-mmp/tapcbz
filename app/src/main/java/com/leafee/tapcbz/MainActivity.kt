package com.leafee.tapcbz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.leafee.tapcbz.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ImageAdapter
    private val allItems = mutableListOf<ImageItem>()
    private val visibleItems = mutableListOf<ImageItem>()
    private var showingHidden = false

    private val prefs by lazy { getSharedPreferences("tapcbz_prefs", Context.MODE_PRIVATE) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) scanDownloads()
        else Toast.makeText(this, "Permission required to read Downloads", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        setupButtons()
        checkPermissionsAndScan()
    }

    private fun setupRecyclerView() {
        adapter = ImageAdapter(visibleItems) { item ->
            if (showingHidden && item.hidden) {
                // Unhide on tap when in show hidden mode
                setHidden(item, false)
                refreshVisible()
            } else if (!item.hidden) {
                // Hide on tap in normal mode
                setHidden(item, true)
                refreshVisible()
            }
        }
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnScan.setOnClickListener { checkPermissionsAndScan() }

        binding.btnSelectAll.setOnClickListener {
            // Select all = unhide all hidden items
            allItems.forEach { setHidden(it, false) }
            refreshVisible()
        }

        binding.btnHide.setOnClickListener {
            // Hide all currently visible items
            visibleItems.forEach { setHidden(it, true) }
            refreshVisible()
        }

        binding.btnShowHidden.setOnClickListener {
            showingHidden = !showingHidden
            adapter.showHidden = showingHidden
            binding.btnShowHidden.text = if (showingHidden) "Hide Hidden" else "Show Hidden"
            binding.btnShowHidden.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    if (showingHidden) 0xFF2a1a2a.toInt() else 0xFF1a1a2a.toInt()
                )
            refreshVisible()
        }

        binding.btnPack.setOnClickListener {
            val name = binding.etCbzName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etCbzName.error = "Enter a name for the CBZ"
                return@setOnClickListener
            }
            val selected = allItems.filter { !it.hidden }
            if (selected.isEmpty()) {
                Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            packCbz(name, selected)
        }
    }

    // ── Hidden state persistence ─────────────────────────────────────────────

    private fun loadHiddenSet(): MutableSet<String> {
        return prefs.getStringSet("hidden_files", emptySet())!!.toMutableSet()
    }

    private fun saveHiddenSet(set: Set<String>) {
        prefs.edit().putStringSet("hidden_files", set).apply()
    }

    private fun setHidden(item: ImageItem, hidden: Boolean) {
        item.hidden = hidden
        val current = loadHiddenSet()
        if (hidden) current.add(item.name) else current.remove(item.name)
        saveHiddenSet(current)
    }

    // ── Refresh visible list ─────────────────────────────────────────────────

    private fun refreshVisible() {
        visibleItems.clear()
        if (showingHidden) {
            visibleItems.addAll(allItems)
        } else {
            visibleItems.addAll(allItems.filter { !it.hidden })
        }
        adapter.notifyDataSetChanged()
        updateSubtitle()
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    private fun checkPermissionsAndScan() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        if (needed.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) scanDownloads()
        else permissionLauncher.launch(needed)
    }

    // ── Scan ─────────────────────────────────────────────────────────────────

    private fun scanDownloads() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnPack.isEnabled = false

            val found = withContext(Dispatchers.IO) { queryDownloadImages() }
            val hiddenSet = loadHiddenSet()

            allItems.clear()
            allItems.addAll(found.map { item ->
                item.hidden = hiddenSet.contains(item.name)
                item
            })

            refreshVisible()

            binding.progressBar.visibility = View.GONE
            binding.btnPack.isEnabled = true

            if (found.isEmpty())
                Toast.makeText(this@MainActivity, "No images found in Downloads", Toast.LENGTH_SHORT).show()

            if (binding.etCbzName.text.isNullOrBlank()) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                binding.etCbzName.setText("TapCBZ_$date")
            }
        }
    }

    private fun queryDownloadImages(): List<ImageItem> {
        val items = mutableListOf<ImageItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val dateAdded = cursor.getLong(dateCol)
                val size = cursor.getLong(sizeCol)
                val uri = android.net.Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                items.add(ImageItem(id, name, uri, dateAdded, size))
            }
        }
        return items.sortedWith(naturalOrderComparator())
    }

    private fun naturalOrderComparator(): Comparator<ImageItem> = Comparator { a, b ->
        val partsA = splitNatural(a.name)
        val partsB = splitNatural(b.name)
        val len = minOf(partsA.size, partsB.size)
        for (i in 0 until len) {
            val pa = partsA[i]; val pb = partsB[i]
            val numA = pa.toLongOrNull(); val numB = pb.toLongOrNull()
            val cmp = if (numA != null && numB != null) numA.compareTo(numB)
            else pa.compareTo(pb, ignoreCase = true)
            if (cmp != 0) return@Comparator cmp
        }
        partsA.size.compareTo(partsB.size)
    }

    private fun splitNatural(s: String): List<String> =
        Regex("(\\d+|\\D+)").findAll(s).map { it.value }.toList()

    // ── Pack ─────────────────────────────────────────────────────────────────

    private fun packCbz(name: String, selected: List<ImageItem>) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnPack.isEnabled = false
            binding.btnScan.isEnabled = false
            binding.tvStatus.text = "Packing ${selected.size} images…"
            binding.tvStatus.visibility = View.VISIBLE

            val result = withContext(Dispatchers.IO) {
                CbzBuilder.build(
                    context = this@MainActivity,
                    images = selected,
                    cbzName = if (name.endsWith(".cbz", ignoreCase = true)) name else "$name.cbz"
                ) { progress, total ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.tvStatus.text = "Packing… $progress / $total"
                    }
                }
            }

            binding.progressBar.visibility = View.GONE
            binding.btnPack.isEnabled = true
            binding.btnScan.isEnabled = true

            if (result.success) {
                binding.tvStatus.text = "✓ Saved: ${result.filename}"
                Toast.makeText(this@MainActivity, "CBZ saved to Downloads!", Toast.LENGTH_LONG).show()
            } else {
                binding.tvStatus.text = "✗ Failed: ${result.error}"
                Toast.makeText(this@MainActivity, "Error: ${result.error}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Subtitle ─────────────────────────────────────────────────────────────

    private fun updateSubtitle() {
        val total = allItems.size
        val hidden = allItems.count { it.hidden }
        val selected = total - hidden
        binding.tvSubtitle.text = "$selected selected · $hidden hidden · $total total"
    }
}
