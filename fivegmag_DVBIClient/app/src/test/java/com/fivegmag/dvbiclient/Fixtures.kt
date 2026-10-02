/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

/** Reads a test fixture from src/test/resources/fixtures. */
object Fixtures {
    fun read(name: String): String =
        requireNotNull(Fixtures::class.java.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }.readText()
}
