package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DollMendingScheduleTest {
    @Test
    void shelfScanRunsOnceAtEachServerSecondBoundary() {
        final DollMendingSchedule schedule = new DollMendingSchedule();
        assertFalse(schedule.beginShelfScan(19));
        assertTrue(schedule.beginShelfScan(20));
        assertFalse(schedule.beginShelfScan(20));
        assertFalse(schedule.beginShelfScan(39));
        assertTrue(schedule.beginShelfScan(40));
    }

    @Test
    void duplicateDollsShareOneClaimPerPlayerAndTarget() {
        final DollMendingSchedule schedule = new DollMendingSchedule();
        final UUID player = UUID.randomUUID();
        assertTrue(schedule.claim(player, DollAbility.RepairTarget.HELD, 20));
        assertFalse(schedule.claim(player, DollAbility.RepairTarget.HELD, 20));
        assertFalse(schedule.claim(player, DollAbility.RepairTarget.HELD, 39));
        assertTrue(schedule.claim(player, DollAbility.RepairTarget.WORN, 20));
        assertTrue(schedule.claim(UUID.randomUUID(), DollAbility.RepairTarget.HELD, 20));
        assertTrue(schedule.claim(player, DollAbility.RepairTarget.HELD, 40));
    }
}
