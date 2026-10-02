/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.app.Application
import android.util.Log
import com.fivegmag.dvbiclient.mbms.IMbmsStreamingClient
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

/**
 * Application class. Configures the Coil [ImageLoader] used for service logos with a descriptive
 * User-Agent, as the 5GMSd-Aware Application does.
 */
class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // TS 103 770 V1.2.1 clause 9.3.3: the DVB-I client acts as an MBMS-Aware Application. The
        // registration result says whether 5G Broadcast instances can be received.
        DvbiSession.mbmsClient.registerStreamingApp(
            packageName,
            listOf(IMbmsStreamingClient.DVBI_SERVICE_INSTANCE_CLASS),
            object : IMbmsStreamingClient.Callback {
                override fun registerStreamingResponse(success: Boolean, message: String) {
                    DvbiSession.mbmsRegistered = success
                    if (!success) Log.i(TAG_APP, "MBMS Client: $message")
                }

                override fun streamingServiceListUpdate() {}
                override fun serviceStarted(serviceId: String) {}
                override fun streamingServiceError(serviceId: String, message: String) {}
            },
        )
    }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                )
            }
            .build()
        return ImageLoader.Builder(this).okHttpClient(client).build()
    }

    companion object {
        const val TAG_APP = "DVB-I Client"
        val USER_AGENT = "5G-MAG-DVBIClient/${BuildConfig.VERSION_NAME} (Android; +https://www.5g-mag.com)"
    }
}
