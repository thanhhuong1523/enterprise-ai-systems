package com.vccorp.eap.mcp.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StackBracketBalancerTest {

    private StackBracketBalancer balancer;

    @BeforeEach
    void setUp() {
        balancer = new StackBracketBalancer();
    }

    @Test
    void testBalance_ClosesUnclosedCurlyBraces() {
        String input = "{\"action\": \"listDepartments\", \"data\": {";
        String balanced = balancer.balance(input);
        assertEquals("{\"action\": \"listDepartments\", \"data\": {}}", balanced);
    }

    @Test
    void testBalance_ClosesUnclosedStringAndBraces() {
        String input = "{\"thought\": \"Đang kiểm tra";
        String balanced = balancer.balance(input);
        assertEquals("{\"thought\": \"Đang kiểm tra\"}", balanced);
    }

    @Test
    void testBalance_ClosesMixedBrackets() {
        String input = "{\"items\": [{\"id\": \"1\"";
        String balanced = balancer.balance(input);
        assertEquals("{\"items\": [{\"id\": \"1\"}]}", balanced);
    }

    @Test
    void testBalance_AlreadyBalancedRemainsUnchanged() {
        String input = "{\"action\": \"listDepartments\"}";
        String balanced = balancer.balance(input);
        assertEquals(input, balanced);
    }
}
