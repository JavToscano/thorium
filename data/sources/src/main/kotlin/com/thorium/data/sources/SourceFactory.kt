package com.thorium.data.sources

import com.thorium.core.model.GameSource
import com.thorium.core.model.SourceConfig
import com.thorium.core.model.SourceType
import okhttp3.OkHttpClient
import java.io.File

/** Builds the adapter for a configured source. The password comes separately: it is never in [SourceConfig]. */
object SourceFactory {

    /** @throws com.thorium.core.model.SourceException.InvalidLocation when the location does not fit the type. */
    fun create(
        config: SourceConfig,
        password: String = "",
        client: OkHttpClient = HttpTransport.defaultClient(),
    ): GameSource = when (config.type) {
        SourceType.Http -> HttpIndexSource(config.location, config.username, password, config.allowInsecure, client)
        SourceType.Catalog -> CatalogSource(config.location, config.username, password, config.allowInsecure, client)
        // archive.org is always https, so plain http is never allowed for this type.
        SourceType.InternetArchive -> InternetArchiveSource(config.location, config.username, password, client = client)
        SourceType.Local -> LocalFolderSource(File(config.location))
    }
}
