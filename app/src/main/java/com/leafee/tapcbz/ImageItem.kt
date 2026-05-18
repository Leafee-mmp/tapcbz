package com.leafee.tapcbz

import android.net.Uri

data class ImageItem(
    val id: Long,
    val name: String,
    val uri: Uri,
    val dateAdded: Long,
    val size: Long,
    var hidden: Boolean = false
)
