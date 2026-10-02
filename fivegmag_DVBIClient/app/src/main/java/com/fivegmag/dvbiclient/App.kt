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
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

/**
 * Application class. Configures the Coil [ImageLoader] used for service logos with a descriptive
 * User-Agent, as the 5GMSd-Aware Application does.
 */
class App : Application(), ImageLoaderFactory {

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
        val USER_AGENT = "5G-MAG-DVBIClient/${BuildConfig.VERSION_NAME} (Android; +https://www.5g-mag.com)"
    }
}
