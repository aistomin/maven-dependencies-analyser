package com.github.aistomin.maven.dependencies.analyser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by aistomin on 2019-08-04.
 *
 * Test for {@link Dummy}
 */
public final class DummyTest {

    @Test
    void testDummy() {
        Assertions.assertEquals(new Integer(4), new Dummy().dummy());
    }
}
