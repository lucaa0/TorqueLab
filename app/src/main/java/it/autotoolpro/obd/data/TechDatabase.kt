package it.autotoolpro.obd.data

import android.content.Context
import android.os.Build
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import it.autotoolpro.obd.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Esito dell'apertura del database tecnico. */
sealed interface OpenResult {
    data object Ok : OpenResult
    data object Missing : OpenResult
    data class Error(val message: String) : OpenResult
}

enum class ObjectKind { TABLE, VIEW, FTS }

data class DbObject(val name: String, val kind: ObjectKind, val rows: Long)

data class Page(val columns: List<String>, val rows: List<List<String?>>, val total: Long)

data class SearchHit(val table: String, val columns: List<String>, val rows: List<List<String?>>)

data class DbInfo(
    val sqliteVersion: String,
    val fts5: Boolean,
    val json: Boolean,
    val userVersion: Long,
    val sizeBytes: Long,
    val sha256: String,
    val tables: Int,
    val views: Int,
    val fts: Int,
)

/**
 * Database tecnico AUTO TOOL PRO 1000 (autotool.db), distribuito nell'APK.
 *
 * - Copiato dagli asset nello storage privato dell'app senza conversioni.
 * - Aperto in sola lettura; se il file è in modalità WAL e la sola lettura non riesce,
 *   viene aperto con PRAGMA query_only = ON (nessuna scrittura possibile).
 * - Una sola connessione, accesso serializzato con un mutex su Dispatchers.IO.
 *
 * Lo strato è generico (legge lo schema da sqlite_master), così non dipende dai nomi
 * delle colonne e resta valido quando il database viene aggiornato.
 */
class TechDatabase(private val context: Context) {

    private val mutex = Mutex()
    private var conn: SQLiteConnection? = null
    private var objectsCache: List<DbObject>? = null

    private val dbFile: File get() = File(File(context.filesDir, "db"), ASSET_NAME)
    private val markerFile: File get() = File(File(context.filesDir, "db"), "$ASSET_NAME.installed")

    suspend fun open(): OpenResult = io {
        if (conn != null) return@io OpenResult.Ok
        try {
            installFromAssetsIfNeeded()
            if (!dbFile.exists()) return@io OpenResult.Missing
            conn = openConnection(dbFile.absolutePath)
            OpenResult.Ok
        } catch (e: Exception) {
            OpenResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun listObjects(): List<DbObject> = io {
        objectsCache?.let { return@io it }
        val c = requireConn()
        data class Raw(val name: String, val type: String, val sql: String)
        val raw = c.query(
            "SELECT name, type, IFNULL(sql,'') FROM sqlite_master " +
                "WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' ORDER BY type, name"
        ) { st ->
            buildList { while (st.step()) add(Raw(st.getText(0), st.getText(1), st.getText(2))) }
        }
        val ftsNames = raw.filter { isFtsSql(it.sql) }.map { it.name }.toSet()
        val result = raw
            // Tabelle interne degli indici FTS (es. nome_data, nome_idx, nome_content)
            .filterNot { r -> ftsNames.any { f -> r.name != f && r.name.startsWith(f + "_") } }
            .map { r ->
                val kind = when {
                    r.name in ftsNames -> ObjectKind.FTS
                    r.type == "view" -> ObjectKind.VIEW
                    else -> ObjectKind.TABLE
                }
                val rows = runCatching {
                    c.query("SELECT COUNT(*) FROM ${q(r.name)}") { st -> st.step(); st.getLong(0) }
                }.getOrDefault(-1L)
                DbObject(r.name, kind, rows)
            }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.name }))
        objectsCache = result
        result
    }

    suspend fun page(objectName: String, filter: String, offset: Int, limit: Int): Page = io {
        val c = requireConn()
        val columns = columnsOf(c, objectName)
        val trimmed = filter.trim()
        val where = if (trimmed.isEmpty() || columns.isEmpty()) "" else
            "WHERE " + columns.joinToString(" OR ") { "CAST(${q(it)} AS TEXT) LIKE ?1 ESCAPE '\\'" }
        val args: List<Any?> = if (where.isEmpty()) emptyList() else listOf("%" + escapeLike(trimmed) + "%")

        val total = c.query("SELECT COUNT(*) FROM ${q(objectName)} $where", args) { st ->
            st.step(); st.getLong(0)
        }
        val rows = c.query("SELECT * FROM ${q(objectName)} $where LIMIT $limit OFFSET $offset", args) { st ->
            readRows(st)
        }
        Page(columns, rows, total)
    }

    /** Query di sola lettura usata dalle schermate guidate (veicoli, codici DTC). */
    suspend fun select(sql: String, args: List<Any?> = emptyList()): Page = io {
        requireConn().prepare(sql).use { st ->
            args.forEachIndexed { i, a -> st.bindText(i + 1, a.toString()) }
            val cols = (0 until st.getColumnCount()).map { st.getColumnName(it) }
            Page(cols, readRows(st), 0)
        }
    }

    /** Ricerca su tutti gli indici FTS del database; ogni parola è cercata come prefisso. */
    suspend fun search(text: String, limitPerTable: Int = 50): List<SearchHit>? {
        val ftsTables = listObjects().filter { it.kind == ObjectKind.FTS }
        if (ftsTables.isEmpty()) return null
        val match = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString(" ") { "\"" + it.replace("\"", "\"\"") + "\"*" }
        if (match.isEmpty()) return emptyList()
        return io {
            val c = requireConn()
            ftsTables.mapNotNull { t ->
                runCatching {
                    val cols = columnsOf(c, t.name)
                    val base = "SELECT * FROM ${q(t.name)} WHERE ${q(t.name)} MATCH ?1"
                    // "rank" esiste solo in FTS5; per FTS3/4 si ripete senza ordinamento
                    val rows = runCatching {
                        c.query("$base ORDER BY rank LIMIT $limitPerTable", listOf(match)) { st -> readRows(st) }
                    }.getOrElse {
                        c.query("$base LIMIT $limitPerTable", listOf(match)) { st -> readRows(st) }
                    }
                    if (rows.isEmpty()) null else SearchHit(t.name, cols, rows)
                }.getOrNull()
            }
        }
    }

    suspend fun info(): DbInfo {
        val objects = listObjects()
        return io {
            val c = requireConn()
            val version = c.query("SELECT sqlite_version()") { st -> st.step(); st.getText(0) }
            val options = c.query("PRAGMA compile_options") { st ->
                buildList { while (st.step()) add(st.getText(0)) }
            }
            val json = runCatching {
                c.query("SELECT json('{}')") { st -> st.step(); st.getText(0) } == "{}"
            }.getOrDefault(false)
            val userVersion = c.query("PRAGMA user_version") { st -> st.step(); st.getLong(0) }
            DbInfo(
                sqliteVersion = version,
                fts5 = options.any { it.contains("ENABLE_FTS5") },
                json = json,
                userVersion = userVersion,
                sizeBytes = dbFile.length(),
                sha256 = sha256(dbFile),
                tables = objects.count { it.kind == ObjectKind.TABLE },
                views = objects.count { it.kind == ObjectKind.VIEW },
                fts = objects.count { it.kind == ObjectKind.FTS },
            )
        }
    }

    // ---------------------------------------------------------------- interni

    private suspend fun <T> io(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private fun requireConn(): SQLiteConnection =
        conn ?: error("Database non aperto")

    private fun openConnection(path: String): SQLiteConnection {
        val driver = BundledSQLiteDriver()
        var readOnly: SQLiteConnection? = null
        return try {
            driver.open(path, SQLITE_OPEN_READONLY).also { c ->
                readOnly = c
                // Verifica che il file sia leggibile davvero (es. WAL senza -shm)
                c.query("SELECT COUNT(*) FROM sqlite_master") { it.step() }
            }
        } catch (e: Exception) {
            runCatching { readOnly?.close() }
            driver.open(path, SQLITE_OPEN_READWRITE).also { c ->
                c.prepare("PRAGMA query_only = ON").use { it.step() }
            }
        }
    }

    /**
     * Copia il database dagli asset quando manca o quando l'app è stata aggiornata
     * (il database nuovo arriva con l'APK nuovo). Copia su file temporaneo + rinomina.
     */
    private fun installFromAssetsIfNeeded() {
        val hasAsset = context.assets.list("")?.contains(ASSET_NAME) == true
        if (!hasAsset) return
        val stamp = installStamp()
        if (dbFile.exists() && markerFile.exists() && markerFile.readText() == stamp) return

        dbFile.parentFile?.mkdirs()
        val tmp = File(dbFile.parentFile, "$ASSET_NAME.tmp")
        context.assets.open(ASSET_NAME).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        if (!tmp.renameTo(dbFile)) {
            tmp.copyTo(dbFile, overwrite = true)
            tmp.delete()
        }
        markerFile.writeText(stamp)
    }

    @Suppress("DEPRECATION")
    private fun installStamp(): String {
        val updated = runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
            } else {
                pm.getPackageInfo(context.packageName, 0).lastUpdateTime
            }
        }.getOrDefault(0L)
        return "${BuildConfig.VERSION_CODE}-$updated"
    }

    private fun columnsOf(c: SQLiteConnection, objectName: String): List<String> =
        c.prepare("SELECT * FROM ${q(objectName)} LIMIT 0").use { st ->
            (0 until st.getColumnCount()).map { st.getColumnName(it) }
        }

    private fun readRows(st: SQLiteStatement): List<List<String?>> = buildList {
        val n = st.getColumnCount()
        while (st.step()) add((0 until n).map { readValue(st, it) })
    }

    private fun readValue(st: SQLiteStatement, i: Int): String? = when {
        st.isNull(i) -> null
        st.getColumnType(i) == SQLITE_TYPE_BLOB -> "[BLOB ${st.getBlob(i).size} byte]"
        else -> st.getText(i)
    }

    private fun <T> SQLiteConnection.query(
        sql: String,
        args: List<Any?> = emptyList(),
        block: (SQLiteStatement) -> T,
    ): T = prepare(sql).use { st ->
        args.forEachIndexed { i, a ->
            when (a) {
                null -> st.bindNull(i + 1)
                is Long -> st.bindLong(i + 1, a)
                is Int -> st.bindLong(i + 1, a.toLong())
                else -> st.bindText(i + 1, a.toString())
            }
        }
        block(st)
    }

    companion object {
        const val ASSET_NAME = "autotool.db"
        private const val SQLITE_TYPE_BLOB = 4 // codice SQLite per il tipo BLOB

        /** Nome SQL tra virgolette doppie (gli identificatori arrivano da sqlite_master). */
        fun q(name: String) = "\"" + name.replace("\"", "\"\"") + "\""

        private fun escapeLike(s: String) =
            s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        private fun isFtsSql(sql: String): Boolean {
            val u = sql.uppercase()
            return u.contains("VIRTUAL TABLE") && (u.contains("FTS5") || u.contains("FTS4") || u.contains("FTS3"))
        }

        private fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
