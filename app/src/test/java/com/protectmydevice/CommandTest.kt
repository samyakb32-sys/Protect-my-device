package com.protectmydevice

import com.protectmydevice.data.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandTest {
    @Test fun parsesKnownCommands() {
        assertEquals(Command.LOCK, Command.parse("lock"))
        assertEquals(Command.WIPE, Command.parse(" WIPE "))
        assertEquals(Command.STOP_ALARM, Command.parse("stop_alarm"))
    }

    @Test fun rejectsUnknownOrMissing() {
        assertNull(Command.parse("format_c"))
        assertNull(Command.parse(null))
        assertNull(Command.parse(""))
    }
}
