package com.vivareal.search.api.model.query;

import org.junit.Test;

import java.util.stream.Stream;

import static com.vivareal.search.api.model.query.RelationalOperator.get;
import static com.vivareal.search.api.model.query.RelationalOperator.getOperators;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RelationalOperatorTest {

    @Test
    public void testGetOperators() {
        String[] operators = getOperators();
        assertNotNull(operators);
        assertTrue(operators.length > 0);
    }

    @Test
    public void testGetOperatorBySymbol() {
        Stream.of(getOperators()).forEach(operatorId -> assertNotNull(get(operatorId)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetInvalidOperator() {
        get("NonExistent");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullInvalidOperator() {
        get(null);
    }
}
