/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

/**
 * Instance selection for one selected service, ETSI TS 103 770 V1.2.1 clause 5.2.13: "When
 * selecting a service, and while playing a service as conditions change and if errors occur, a
 * DVB-I client shall select one of the service's instances to play".
 *
 * Holds the instances that failed while this service is selected, so a failing instance is set
 * aside and the next one by precedence is tried; selecting the service again starts afresh.
 */
class ServiceSelection(val service: Service, private val caps: Instances.Capabilities) {

    private val failed = HashSet<Int>()

    /** Why each instance is not a candidate at [ms], or null for the candidates. */
    fun reasons(ms: Long): List<String?> = service.instances.mapIndexed { i, inst ->
        when {
            i in failed -> "failed to play"
            !Instances.isAvailable(inst.availability, ms) -> "outside its scheduled service hours (clause 5.2.5)"
            else -> Instances.cannotPlay(inst, caps)
        }
    }

    /** The instance to play at [ms], as an index into the service's instances, or null. */
    fun select(ms: Long): Int? = Instances.candidates(service.instances, ms, caps, failed).firstOrNull()

    /** Marks [index] as failed and returns the next instance to play at [ms], or null. */
    fun fail(index: Int, ms: Long): Int? {
        failed.add(index)
        return select(ms)
    }

    /**
     * When the selection must next be re-evaluated, because an instance enters or leaves its
     * scheduled service hours ("the selected service instance shall be re-evaluated", clause
     * 5.2.13), or null when no instance has scheduled hours.
     */
    fun nextReevaluation(ms: Long): Long? =
        service.instances.mapNotNull { Instances.nextChange(it.availability, ms) }.minOrNull()
}
