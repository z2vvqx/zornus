package net.valoury.shared.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupJoinPolicyTest {

    @Test
    void parsesCommandInputCaseInsensitively() {
        assertEquals(GroupJoinPolicy.PUBLIC, GroupJoinPolicy.fromInput("PUBLIC").orElseThrow());
        assertEquals(GroupJoinPolicy.PRIVATE, GroupJoinPolicy.fromInput("private").orElseThrow());
    }

    @Test
    void rejectsUnknownInput() {
        assertTrue(GroupJoinPolicy.fromInput("friends").isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> GroupJoinPolicy.fromStoredValue("friends"));
    }
}
