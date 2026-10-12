package com.dpm.app

import android.app.Activity
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object AppPicker {
    private class Item(val pkg: String, val label: String)

    private fun items(a: Activity): List<Item> {
        val pm = a.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(q, 0)
            .map { Item(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != a.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun pick(a: Activity, title: String, key: String, onDone: (Int) -> Unit) {
        val list = items(a)
        val current = Store.pkgs(a, key)
        val checked = BooleanArray(list.size) { list[it].pkg in current }
        MaterialAlertDialogBuilder(a)
            .setTitle(title)
            .setMultiChoiceItems(list.map { it.label }.toTypedArray(), checked) { _, which, on ->
                checked[which] = on
            }
            .setPositiveButton("Save") { _, _ ->
                val keep = current.filter { p -> list.none { it.pkg == p } }
                val chosen = list.filterIndexed { i, _ -> checked[i] }.map { it.pkg }
                val all = (keep + chosen).toSet()
                Store.putPkgs(a, key, all)
                onDone(all.size)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
