package com.teamproject.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupService;
import org.junit.jupiter.api.Test;

class GroupServiceTest {

    @Test
    void productionDefaultBlocksTestMembershipSwitchBeforeChangingData() {
        GroupService service = new GroupService(null, null, null, null, null, null, false);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> service.switchTestMembership(1L, 1L, "PAID"));

        assertEquals("TEST_MEMBERSHIP_SWITCH_DISABLED", exception.code());
    }
}
