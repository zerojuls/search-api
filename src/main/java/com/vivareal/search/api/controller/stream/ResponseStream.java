package com.vivareal.search.api.controller.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.function.Function;

/**
 * Using NDJSON Format: http://ndjson.org/
 */
public final class ResponseStream {

    private static final Logger LOG = LoggerFactory.getLogger(ResponseStream.class);

    public static <T> void iterate(OutputStream stream, Iterator<T[]> iterator, Function<T, byte[]> byteFn) {
        if (stream == null) throw new IllegalArgumentException("stream cannot be null");
        try {
            while (iterator.hasNext()) {
                for (T hit: iterator.next()) {
                    stream.write(byteFn.apply(hit));
                    stream.write('\n');
                }

                stream.flush();
            }
        } catch (IOException e) {
            LOG.error("write error on iterator stream", e);
        }
    }
}
