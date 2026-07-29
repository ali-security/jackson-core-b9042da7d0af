package com.fasterxml.jackson.core.read;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

/**
 * Unit tests for verifying that maximum nesting depth constraint
 * ({@link StreamReadConstraints#validateNestingDepth}) is enforced by all
 * blocking parser backends, and that legal documents are not rejected.
 */
public class DeepNestingParserTest
    extends com.fasterxml.jackson.core.BaseTest
{
    /*
    /**********************************************************
    /* Tests for default (1000) nesting depth limit
    /**********************************************************
     */

    // Arrays only: `[[[ ... ]]]`, one deeper than the default limit
    public void testDeepNestingArraysFails() throws Exception
    {
        final String DOC = deepNestedArrayDoc(StreamReadConstraints.DEFAULT_MAX_DEPTH + 1);
        for (int mode : ALL_MODES) {
            JsonParser p = createParser(newStreamFactory(), mode, DOC);
            try {
                _drain(p);
                fail("Should not pass (mode "+mode+")");
            } catch (StreamConstraintsException e) {
                assertEquals("Depth (1001) exceeds the maximum allowed nesting depth (1000)",
                        e.getMessage());
            } finally {
                p.close();
            }
        }
    }

    // Objects only: `{"a":{"a": ... }}`; exercises the "next token after field name" code path
    public void testDeepNestingObjectsFails() throws Exception
    {
        final String DOC = deepNestedObjectDoc(StreamReadConstraints.DEFAULT_MAX_DEPTH + 1);
        for (int mode : ALL_MODES) {
            JsonParser p = createParser(newStreamFactory(), mode, DOC);
            try {
                _drain(p);
                fail("Should not pass (mode "+mode+")");
            } catch (StreamConstraintsException e) {
                assertEquals("Depth (1001) exceeds the maximum allowed nesting depth (1000)",
                        e.getMessage());
            } finally {
                p.close();
            }
        }
    }

    // Same as above but traversed with `nextFieldName()` / `nextTextValue()`, since those
    // accessors start child contexts of their own
    public void testDeepNestingViaNextTextValueFails() throws Exception
    {
        final String DOC = deepNestedObjectDoc(StreamReadConstraints.DEFAULT_MAX_DEPTH + 1);
        for (int mode : ALL_MODES) {
            JsonParser p = createParser(newStreamFactory(), mode, DOC);
            try {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                while (p.nextFieldName() != null) {
                    p.nextTextValue();
                }
                fail("Should not pass (mode "+mode+")");
            } catch (StreamConstraintsException e) {
                assertEquals("Depth (1001) exceeds the maximum allowed nesting depth (1000)",
                        e.getMessage());
            } finally {
                p.close();
            }
        }
    }

    // And then verify that a document exactly at the limit is accepted: no false positives
    public void testDeepNestingAtLimitPasses() throws Exception
    {
        final String ARRAYS = deepNestedArrayDoc(StreamReadConstraints.DEFAULT_MAX_DEPTH);
        final String OBJECTS = deepNestedObjectDoc(StreamReadConstraints.DEFAULT_MAX_DEPTH);
        for (int mode : ALL_MODES) {
            JsonParser p = createParser(newStreamFactory(), mode, ARRAYS);
            try {
                assertEquals(StreamReadConstraints.DEFAULT_MAX_DEPTH, _drain(p));
            } finally {
                p.close();
            }
            p = createParser(newStreamFactory(), mode, OBJECTS);
            try {
                assertEquals(StreamReadConstraints.DEFAULT_MAX_DEPTH, _drain(p));
            } finally {
                p.close();
            }
        }
    }

    /*
    /**********************************************************
    /* Tests for custom nesting depth limits
    /**********************************************************
     */

    public void testCustomLowNestingDepth() throws Exception
    {
        JsonFactory f = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(10).build())
                .build();
        assertEquals(10, f.streamReadConstraints().getMaxNestingDepth());

        final String DOC = deepNestedArrayDoc(20);
        for (int mode : ALL_MODES) {
            JsonParser p = createParser(f, mode, DOC);
            try {
                _drain(p);
                fail("Should not pass (mode "+mode+")");
            } catch (StreamConstraintsException e) {
                assertEquals("Depth (11) exceeds the maximum allowed nesting depth (10)",
                        e.getMessage());
            } finally {
                p.close();
            }
        }

        // ... but a document within the custom limit must still parse
        final String OK_DOC = deepNestedArrayDoc(10);
        for (int mode : ALL_MODES) {
            JsonParser p = createParser(f, mode, OK_DOC);
            try {
                assertEquals(10, _drain(p));
            } finally {
                p.close();
            }
        }
    }

    // Raising the limit must allow documents the default would reject
    public void testCustomHighNestingDepth() throws Exception
    {
        JsonFactory f = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2000).build())
                .build();
        final String DOC = deepNestedArrayDoc(1500);
        for (int mode : ALL_MODES) {
            JsonParser p = createParser(f, mode, DOC);
            try {
                assertEquals(1500, _drain(p));
            } finally {
                p.close();
            }
        }
    }

    public void testZeroNestingDepth() throws Exception
    {
        JsonFactory f = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(0).build())
                .build();
        JsonParser p = createParser(f, MODE_INPUT_STREAM, "[ 1 ]");
        try {
            _drain(p);
            fail("Should not pass");
        } catch (StreamConstraintsException e) {
            assertEquals("Depth (1) exceeds the maximum allowed nesting depth (0)",
                    e.getMessage());
        } finally {
            p.close();
        }
    }

    public void testNegativeNestingDepthNotAllowed() throws Exception
    {
        try {
            StreamReadConstraints.builder().maxNestingDepth(-1);
            fail("Should not pass");
        } catch (IllegalArgumentException e) {
            verifyException(e, "Cannot set maxNestingDepth to a negative value");
        }
    }

    public void testDefaultConstraintsDefaults() throws Exception
    {
        assertEquals(1000, StreamReadConstraints.DEFAULT_MAX_DEPTH);
        assertEquals(StreamReadConstraints.DEFAULT_MAX_DEPTH,
                StreamReadConstraints.builder().build().getMaxNestingDepth());
        // UNLIMITED relaxes number length only, NOT nesting depth
        assertEquals(StreamReadConstraints.DEFAULT_MAX_DEPTH,
                StreamReadConstraints.UNLIMITED.getMaxNestingDepth());
    }

    /*
    /**********************************************************
    /* Tests for other parser entry points and wrappers
    /**********************************************************
     */

    // Every `createParser()` overload must go through a guarded parser, not just
    // the InputStream/Reader/DataInput ones exercised via ALL_MODES
    public void testDeepNestingAllCreateParserOverloads() throws Exception
    {
        final String DOC = deepNestedArrayDoc(StreamReadConstraints.DEFAULT_MAX_DEPTH + 1);
        final byte[] BYTES = DOC.getBytes("UTF-8");
        final JsonFactory f = newStreamFactory();

        _expectFailure("String", f.createParser(DOC));
        _expectFailure("byte[]", f.createParser(BYTES));
        _expectFailure("byte[]+offset", f.createParser(BYTES, 0, BYTES.length));
        _expectFailure("char[]", f.createParser(DOC.toCharArray()));
        _expectFailure("char[]+offset", f.createParser(DOC.toCharArray(), 0, DOC.length()));
    }

    // Delegating/decorating parsers must inherit the guard from the parser they wrap
    public void testDeepNestingViaDelegates() throws Exception
    {
        final String DOC = deepNestedArrayDoc(StreamReadConstraints.DEFAULT_MAX_DEPTH + 1);
        final JsonFactory f = newStreamFactory();

        _expectFailure("JsonParserDelegate",
                new com.fasterxml.jackson.core.util.JsonParserDelegate(f.createParser(DOC)));
        _expectFailure("FilteringParserDelegate",
                new com.fasterxml.jackson.core.filter.FilteringParserDelegate(f.createParser(DOC),
                        com.fasterxml.jackson.core.filter.TokenFilter.INCLUDE_ALL,
                        com.fasterxml.jackson.core.filter.TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH,
                        true));
        _expectFailure("JsonParserSequence",
                com.fasterxml.jackson.core.util.JsonParserSequence.createFlattened(true,
                        f.createParser(DOC), f.createParser("[ 1 ]")));
    }

    // A custom limit must survive factory copy()/rebuild(), else a copied factory
    // silently reverts to being unguarded
    public void testNestingDepthSurvivesFactoryCopy() throws Exception
    {
        JsonFactory f = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(5).build())
                .build();
        final String DOC = deepNestedArrayDoc(50);

        assertEquals(5, f.copy().streamReadConstraints().getMaxNestingDepth());
        assertEquals(5, f.rebuild().build().streamReadConstraints().getMaxNestingDepth());

        _expectFailure("direct", f.createParser(DOC), 6, 5);
        _expectFailure("copy()", f.copy().createParser(DOC), 6, 5);
        _expectFailure("rebuild()", f.rebuild().build().createParser(DOC), 6, 5);
    }

    /*
    /**********************************************************
    /* Test for the underlying context accessor
    /**********************************************************
     */

    public void testNestingDepthAccessor() throws Exception
    {
        for (int mode : ALL_MODES) {
            JsonParser p = createParser(newStreamFactory(), mode, "[{\"a\":[1]}]");
            try {
                assertEquals(0, p.getParsingContext().getNestingDepth());
                assertToken(JsonToken.START_ARRAY, p.nextToken());
                assertEquals(1, p.getParsingContext().getNestingDepth());
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertEquals(2, p.getParsingContext().getNestingDepth());
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                assertEquals(2, p.getParsingContext().getNestingDepth());
                assertToken(JsonToken.START_ARRAY, p.nextToken());
                assertEquals(3, p.getParsingContext().getNestingDepth());
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(3, p.getParsingContext().getNestingDepth());
                assertToken(JsonToken.END_ARRAY, p.nextToken());
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertToken(JsonToken.END_ARRAY, p.nextToken());
                assertEquals(0, p.getParsingContext().getNestingDepth());
            } finally {
                p.close();
            }
        }
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private void _expectFailure(String label, JsonParser p) throws Exception {
        _expectFailure(label, p, StreamReadConstraints.DEFAULT_MAX_DEPTH + 1,
                StreamReadConstraints.DEFAULT_MAX_DEPTH);
    }

    private void _expectFailure(String label, JsonParser p, int expDepth, int expMax)
        throws Exception
    {
        try {
            _drain(p);
            fail("Should not pass ("+label+")");
        } catch (StreamConstraintsException e) {
            assertEquals("Depth ("+expDepth+") exceeds the maximum allowed nesting depth ("+expMax+")",
                    e.getMessage());
        } finally {
            p.close();
        }
    }

    // Reads the document to the end, returning the maximum nesting depth seen
    private int _drain(JsonParser p) throws Exception
    {
        int maxDepth = 0;
        while (p.nextToken() != null) {
            int d = p.getParsingContext().getNestingDepth();
            if (d > maxDepth) {
                maxDepth = d;
            }
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
