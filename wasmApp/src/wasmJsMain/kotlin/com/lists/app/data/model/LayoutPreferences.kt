package com.lists.app.data.model

import android.content.Context
import android.content.SharedPreferences

/** Split the previously shared layout choice once, retaining the prior category layout. */
fun layoutPreferences(context: Context): SharedPreferences =
    context.getSharedPreferences("lists_ui_prefs", Context.MODE_PRIVATE).also { prefs ->
        if (prefs.getString("category_layout_mode", null) == null) {
            val previous = prefs.getString("home_layout_mode", null) ?: HomeLayoutMode.DISCRETE.name
            prefs.edit().putString("category_layout_mode", previous).apply()
        }
    }
