/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * The user's settings. The defaults of the two URLs come from the build (Gradle properties
 * dvbiServiceListUrl and dvbiRegistryUrl), which default to the demo laptop on the Wi-Fi.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dvbi-client", Context.MODE_PRIVATE)

    /** The service list URLs to try in order: the chosen one, then the registry's other ServiceListURI elements. */
    var serviceListUrls: List<String>
        get() = (prefs.getString(KEY_LIST_URLS, null) ?: BuildConfig.DEFAULT_SERVICE_LIST_URL).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        set(v) = prefs.edit().putString(KEY_LIST_URLS, v.joinToString("\n")).apply()

    /** The registry's ServiceListId for the chosen offering (table 12), or "" when the list was not chosen from a registry. */
    var expectedListId: String
        get() = prefs.getString(KEY_EXPECTED_ID, "") ?: ""
        set(v) = prefs.edit().putString(KEY_EXPECTED_ID, v).apply()

    /** <ServiceListRegistryEndpoint> of clause 5.1.3.2: scheme, authority and path. */
    var registryEndpoint: String
        get() = prefs.getString(KEY_REGISTRY, null) ?: BuildConfig.DEFAULT_REGISTRY_URL
        set(v) = prefs.edit().putString(KEY_REGISTRY, v.trim()).apply()

    /** ISO 3166 alpha-3 country, for the registry query (TargetCountry) and ParentalRating@countryCodes; "" when unset. */
    var country: String
        get() = prefs.getString(KEY_COUNTRY, "") ?: ""
        set(v) = prefs.edit().putString(KEY_COUNTRY, v.trim().uppercase()).apply()

    /** A Region@regionID of the service list, or "" for none. */
    var region: String
        get() = prefs.getString(KEY_REGION, "") ?: ""
        set(v) = prefs.edit().putString(KEY_REGION, v.trim()).apply()

    /** The subscription packages this client is associated with (clause 5.1.5). */
    var packages: List<String>
        get() = (prefs.getString(KEY_PACKAGES, "") ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        set(v) = prefs.edit().putString(KEY_PACKAGES, v.joinToString(",")).apply()

    /** Content rated at or above this age is restricted; 0 means no restriction. */
    var parentalThreshold: Int
        get() = prefs.getInt(KEY_PARENTAL, 0)
        set(v) = prefs.edit().putInt(KEY_PARENTAL, maxOf(0, v)).apply()

    /** ISO 639 language for multilingual names and registry ordering. */
    val language: String get() = Locale.getDefault().language

    private companion object {
        const val KEY_LIST_URLS = "serviceListUrls"
        const val KEY_EXPECTED_ID = "expectedListId"
        const val KEY_REGISTRY = "registryEndpoint"
        const val KEY_COUNTRY = "country"
        const val KEY_REGION = "region"
        const val KEY_PACKAGES = "packages"
        const val KEY_PARENTAL = "parentalThreshold"
    }
}
