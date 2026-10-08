package com.thorium.data.metadata

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.thorium.core.model.CatalogEntry
import com.thorium.core.model.GameCatalog
import java.io.File

/** Reads the bundled catalog.db (opened read-only). */
class SqliteCatalogStore(private val db: SQLiteDatabase) : CatalogStore {

    override fun byCrc(crc32: Long): List<CatalogEntry> =
        query("WHERE crc = ?", arrayOf(crc32.toString()))

    override fun byKey(platformId: String?, key: String): List<CatalogEntry> =
        if (platformId == null) query("WHERE key = ?", arrayOf(key))
        else query("WHERE platform = ? AND key = ?", arrayOf(platformId, key))

    private fun query(where: String, args: Array<String>): List<CatalogEntry> {
        val sql = "SELECT platform, name, title, region, serial, size, crc, md5 FROM games $where"
        db.rawQuery(sql, args).use { cursor ->
            val out = ArrayList<CatalogEntry>(cursor.count)
            while (cursor.moveToNext()) {
                out += CatalogEntry(
                    platformId = cursor.getString(0),
                    name = cursor.getString(1),
                    title = cursor.getString(2),
                    region = cursor.getString(3),
                    serial = cursor.getString(4),
                    sizeBytes = cursor.getLong(5),
                    crc32 = if (cursor.isNull(6)) null else cursor.getLong(6),
                    md5 = if (cursor.isNull(7)) null else cursor.getBlob(7).joinToString("") { "%02X".format(it) },
                )
            }
            return out
        }
    }
}

object BundledCatalog {
    private const val ASSET = "catalog.db"

    /**
     * Copies the bundled database next to the app's files (again after each app update) and opens
     * it. [keyOf] must be the same rule the catalog builder used for title keys.
     */
    fun open(context: Context, keyOf: (String) -> String): GameCatalog {
        val target = File(context.noBackupFilesDir, ASSET)
        val stamp = File(context.noBackupFilesDir, "$ASSET.stamp")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (!target.exists() || !stamp.exists() || stamp.readText() != version) {
            val temp = File(target.path + ".tmp")
            context.assets.open(ASSET).use { input -> temp.outputStream().use { input.copyTo(it) } }
            check(temp.renameTo(target)) { "Could not install the catalog" }
            stamp.writeText(version)
        }
        val db = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
        return CatalogMatcher(SqliteCatalogStore(db), keyOf = keyOf)
    }
}
