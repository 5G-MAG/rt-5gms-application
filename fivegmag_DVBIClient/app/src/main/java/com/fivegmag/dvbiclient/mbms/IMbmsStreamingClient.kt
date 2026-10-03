/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.mbms

/**
 * The part of the Media Streaming Service API of 3GPP TS 26.347 V18.1.0 (clause 6.3, IDL in annex
 * B.3, interface ILTEStreamingService) that this DVB-I client, acting as an MBMS-Aware
 * Application, needs for TS 103 770 V1.2.1 clause 9.3.3: "When a DVB-I service instance with an
 * mbms:// locator is selected by the user, the DVB-I client (acting as an MBMS-Aware Application)
 * shall invoke the MBMS Client to initiate reception of the corresponding MBMS User Service."
 *
 * Method names are those of the IDL. An Android MBMS Client implements this interface; until one
 * exists, [NoMbmsClient] is used and 5G Broadcast instances are shown, checked and not played.
 */
interface IMbmsStreamingClient {

    /** One StreamingServiceInfo of getStreamingServices() (clause 6.3.3.4), the fields used here. */
    data class StreamingServiceInfo(
        val serviceId: String,
        val serviceClass: String,
        /** ServiceFormat list: (ServiceMimeType, ManifestURI). */
        val formats: List<Pair<String, String>>,
    )

    /** Callbacks of ILTEStreamingServiceCallback that this client acts on. */
    interface Callback {
        /** Clause 6.3.3.3; [success] is RegResponseCode REGISTER_SUCCESS. */
        fun registerStreamingResponse(success: Boolean, message: String)

        /** Clause 6.3.3.6. */
        fun streamingServiceListUpdate()

        /** Clause 6.3.3.8. */
        fun serviceStarted(serviceId: String)

        /** Clause 6.3.3.12. */
        fun streamingServiceError(serviceId: String, message: String)
    }

    /** Clause 6.3.3.2. */
    fun registerStreamingApp(appId: String, serviceClassList: List<String>, callback: Callback)

    /** Clause 6.3.3.4. */
    fun getStreamingServices(): List<StreamingServiceInfo>

    /** Clause 6.3.3.7. */
    fun startStreamingService(serviceId: String)

    /** Clause 6.3.3.9. */
    fun stopStreamingService(serviceId: String)

    /** Clause 6.3.3.10. */
    fun deregisterStreamingApp()

    companion object {
        /**
         * TS 103 770 V1.2.1 clause 9.3.1, table 106: the service class of an MBMS User Service that
         * carries "The media assets of a DVB-I service instance".
         */
        const val DVBI_SERVICE_INSTANCE_CLASS = "urn:dvb:metadata:serviceClass:DVB-I_Service_Instance:1"
    }
}

/**
 * Used while no MBMS Client exists on Android: registration fails, as clause 6.3.2.3 has an MBMS
 * Client answer when its functions are not accessible, so no 5G Broadcast instance is played.
 */
object NoMbmsClient : IMbmsStreamingClient {
    override fun registerStreamingApp(appId: String, serviceClassList: List<String>, callback: IMbmsStreamingClient.Callback) {
        callback.registerStreamingResponse(false, "no MBMS Client is available on this device")
    }

    override fun getStreamingServices(): List<IMbmsStreamingClient.StreamingServiceInfo> = emptyList()
    override fun startStreamingService(serviceId: String) {}
    override fun stopStreamingService(serviceId: String) {}
    override fun deregisterStreamingApp() {}
}
