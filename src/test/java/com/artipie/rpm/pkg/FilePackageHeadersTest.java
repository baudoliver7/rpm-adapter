/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/rpm-adapter/LICENSE.txt
 */
package com.artipie.rpm.pkg;

import com.artipie.rpm.Digest;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.redline_rpm.header.Header;

/**
 * Tests for {@link FilePackage.Headers}.
 *
 * @since 0.6.3
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class FilePackageHeadersTest {

    @Test
    public void parseStringHeader(@TempDir final Path unused) throws Exception {
        final Header.HeaderTag tag = Header.HeaderTag.NAME;
        final Header header = new Header();
        final String expected = "string value";
        header.createEntry(tag, expected);
        MatcherAssert.assertThat(
            new FilePackage.Headers(
                header, unused, Digest.SHA256, "unused"
            ).header(tag).asString("default value"),
            new IsEqual<>(expected)
        );
    }

    @Test
    public void parseStringsHeader(@TempDir final Path unused) throws Exception {
        final Header.HeaderTag tag = Header.HeaderTag.NAME;
        final Header header = new Header();
        final String[] expected = new String[]{"s1", "s2"};
        header.createEntry(tag, expected);
        MatcherAssert.assertThat(
            new FilePackage.Headers(
                header, unused, Digest.SHA256, "unused"
            ).header(tag).asStrings(),
            new IsEqual<>(Arrays.asList(expected))
        );
    }

    @Test
    public void parseIntHeader(@TempDir final Path unused) throws Exception {
        final Header.HeaderTag tag = Header.HeaderTag.EPOCH;
        final Header header = new Header();
        final int expected = 1;
        header.createEntry(tag, expected);
        MatcherAssert.assertThat(
            new FilePackage.Headers(
                header, unused, Digest.SHA256, "unused"
            ).header(tag).asInt(0),
            new IsEqual<>(expected)
        );
    }

    @Test
    public void parseIntsHeader(@TempDir final Path unused) throws Exception {
        final Header.HeaderTag tag = Header.HeaderTag.EPOCH;
        final Header header = new Header();
        final int[] expected = new int[]{0, 1};
        header.createEntry(tag, expected);
        MatcherAssert.assertThat(
            new FilePackage.Headers(
                header, unused, Digest.SHA256, "unused"
            ).header(tag).asInts(),
            new IsEqual<>(expected)
        );
    }

    @Test
    public void parseMissingStringHeader(@TempDir final Path unused) {
        final String expected = "default string value";
        MatcherAssert.assertThat(
            new FilePackage.Headers(
                new Header(), unused, Digest.SHA256, "unused"
            ).header(Header.HeaderTag.NAME).asString(expected),
            new IsEqual<>(expected)
        );
    }

    @Test
    public void parseMissingIntHeader(@TempDir final Path unused) {
        final int expected = 0;
        MatcherAssert.assertThat(
            new FilePackage.Headers(
                new Header(), unused, Digest.SHA256, "unused"
            ).header(Header.HeaderTag.EPOCH).asInt(expected),
            new IsEqual<>(expected)
        );
    }

    @Test
    public void returnsProvidedLocation() {
        final String location = "subdir/some.rpm";
        MatcherAssert.assertThat(
            new FilePackage.Headers(
                new Header(), Paths.get("unused"), Digest.SHA256, location
            ).href(),
            new IsEqual<>(location)
        );
    }
}
