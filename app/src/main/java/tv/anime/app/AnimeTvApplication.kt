package tv.anime.app

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.google.android.gms.security.ProviderInstaller
import tv.anime.app.net.SharedOkHttp

class AnimeTvApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        // Best-effort update of the device's security provider via Google Play services.
        // This can resolve TLS chain/crypto issues on some older Android TV firmwares.
        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (t: Throwable) {
            Log.w("AnimeTvApplication", "Security provider update failed (continuing).", t)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { SharedOkHttp.client }
                    )
                )
            }
            .build()
    }
}
