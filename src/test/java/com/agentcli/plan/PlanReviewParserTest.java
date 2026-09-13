package com.agentcli.plan;

import org.junit.jupiter.api.Test;

import static com.agentcli.plan.PlanReviewParser.Choice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlanReviewParserTest {

    @Test
    void runChoices() {
        assertEquals(Choice.RUN, PlanReviewParser.parseChoice("r"));
        assertEquals(Choice.RUN, PlanReviewParser.parseChoice("R"));
        assertEquals(Choice.RUN, PlanReviewParser.parseChoice(" run "));
        assertEquals(Choice.RUN, PlanReviewParser.parseChoice("执行"));
    }

    @Test
    void refineChoices() {
        assertEquals(Choice.REFINE, PlanReviewParser.parseChoice("i"));
        assertEquals(Choice.REFINE, PlanReviewParser.parseChoice("I"));
        assertEquals(Choice.REFINE, PlanReviewParser.parseChoice("refine"));
        assertEquals(Choice.REFINE, PlanReviewParser.parseChoice("补充要求"));
    }

    @Test
    void cancelChoices() {
        assertEquals(Choice.CANCEL, PlanReviewParser.parseChoice("c"));
        assertEquals(Choice.CANCEL, PlanReviewParser.parseChoice("C"));
        assertEquals(Choice.CANCEL, PlanReviewParser.parseChoice("cancel"));
        assertEquals(Choice.CANCEL, PlanReviewParser.parseChoice("取消"));
    }

    @Test
    void eofNullIsCancel() {
        assertEquals(Choice.CANCEL, PlanReviewParser.parseChoice(null));
    }

    @Test
    void illegalInputReturnsNull() {
        assertNull(PlanReviewParser.parseChoice(""));
        assertNull(PlanReviewParser.parseChoice("x"));
        assertNull(PlanReviewParser.parseChoice("go"));
        assertNull(PlanReviewParser.parseChoice("yes"));
    }
}