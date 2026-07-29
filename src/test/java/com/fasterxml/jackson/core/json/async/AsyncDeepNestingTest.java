package com.fasterxml.jackson.core.json.async;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.async.AsyncTestBase;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.core.testsupport.AsyncReaderWrapper;

/**
 * Unit tests to verify that the maximum nesting depth constraint is also
 * enforced by the non-blocking (async) parser.
 */
public class AsyncDeepNestingTest extends AsyncTestBase
{
    public void testDeepNestingArraysFails() throws Exception
    {
        byte[] doc = _jsonDoc(deepNestedArrayDoc(StreamReadConstraints.DEFAULT_MAX_DEPTH + 1));
        // both "all at once" and byte-at-a-time feeding
        _testFails(new JsonFactory(), doc, 1000, 1001, 1000);
        _testFails(new JsonFactory(), doc, 1, 1001, 1000);
    }

    public void testDeepNestingObjectsFails() throws Exception
    {
        byte[] doc = _jsonDoc(deepNestedObjectDoc(StreamReadConstraints.DEFAULT_MAX_DEPTH + 1));
        _testFails(new JsonFactory(), doc, 1000, 1001, 1000);
        _testFails(new JsonFactory(), doc, 1, 1001, 1000);
    }

    public void testDeepNestingAtLimitPasses() throws Exception
    {
        JsonFactory f = new JsonFactory();
        byte[] arrays = _jsonDoc(deepNestedArrayDoc(StreamReadConstraints.DEFAULT_MAX_DEPTH));
        assertEquals(StreamReadConstraints.DEFAULT_MAX_DEPTH, _drainOk(f, arrays, 1000));
        byte[] objects = _jsonDoc(deepNestedObjectDoc(StreamReadConstraints.DEFAULT_MAX_DEPTH));
        assertEquals(StreamReadConstraints.DEFAULT_MAX_DEPTH, _drainOk(f, objects, 1000));
    }

    public void testCustomNestingDepth() throws Exception
    {
        JsonFactory f = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(10).build())
                .build();
        _testFails(f, _jsonDoc(deepNestedArrayDoc(20)), 100, 11, 10);
        // ... and depth within the limit still parses
        assertEquals(10, _drainOk(f, _jsonDoc(deepNestedArrayDoc(10)), 100));
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private void _testFails(JsonFactory f, byte[] doc, int bytesPerRead,
            int expDepth, int expMax) throws Exception
    {
        AsyncReaderWrapper r = asyncForBytes(f, bytesPerRead, doc, 0);
        try {
            while (r.nextToken() != null) { }
            fail("Should not pass");
        } catch (StreamConstraintsException e) {
            assertEquals("Depth ("+expDepth+") exceeds the maximum allowed nesting depth ("+expMax+")",
                    e.getMessage());
        } finally {
            r.close();
        }
    }

    private int _drainOk(JsonFactory f, byte[] doc, int bytesPerRead) throws Exception
    {
        AsyncReaderWrapper r = asyncForBytes(f, bytesPerRead, doc, 0);
        int maxDepth = 0;
        try {
            JsonToken t;
            while ((t = r.nextToken()) != null) {
                if (t == JsonToken.START_ARRAY || t == JsonToken.START_OBJECT) {
                    int d = r.getParsingContext().getNestingDepth();
                    if (d > maxDepth) {
                        maxDepth = d;
                    }
                }
            }
        } finally {
            r.close();
        }
        return maxDepth;
    }

    private String deepNestedArrayDoc(final int depth) {
        StringBuilder sb = new StringBuilder(2 * depth + 8);
        for (int i = 0; i < depth; ++i) {
            sb.append('[');
        }
        sb.append(" 1 ");
        for (int i = 0; i < depth; ++i) {
            sb.append(']');
        }
        return sb.toString();
    }

    private String deepNestedObjectDoc(final int depth) {
        StringBuilder sb = new StringBuilder(7 * depth + 8);
        for (int i = 0; i < depth; ++i) {
            sb.append("{\"a\":");
        }
        sb.append("\"val\"");
        for (int i = 0; i < depth; ++i) {
            sb.append('}');
        }
        return sb.toString();
    }
}
