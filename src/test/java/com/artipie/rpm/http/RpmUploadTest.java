/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/rpm-adapter/LICENSE.txt
 */
package com.artipie.rpm.http;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.blocking.BlockingStorage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.http.Headers;
import com.artipie.http.hm.RsHasStatus;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rs.RsStatus;
import com.artipie.rpm.RepoConfig;
import com.artipie.rpm.TestRpm;
import io.reactivex.Flowable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link RpmUpload}.
 *
 * @since 0.8.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class RpmUploadTest {

    @Test
    void canUploadArtifact() throws Exception {
        final Storage storage = new InMemoryStorage();
        final byte[] content = Files.readAllBytes(new TestRpm.Abc().path());
        MatcherAssert.assertThat(
            "ACCEPTED 202 returned",
            new RpmUpload(storage, new RepoConfig.Simple()).response(
                new RequestLine("PUT", "/uploaded.rpm").toString(),
                new ListOf<Map.Entry<String, String>>(),
                Flowable.fromArray(ByteBuffer.wrap(content))
            ),
            new RsHasStatus(RsStatus.ACCEPTED)
        );
        MatcherAssert.assertThat(
            "Content saved to storage",
            new BlockingStorage(storage).value(new Key.From("uploaded.rpm")),
            new IsEqual<>(content)
        );
        MatcherAssert.assertThat(
            "Metadata updated",
            new BlockingStorage(storage).list(new Key.From("repodata")).isEmpty(),
            new IsEqual<>(false)
        );
    }

    @Test
    void canReplaceArtifact() throws Exception {
        final Storage storage = new InMemoryStorage();
        final byte[] content = Files.readAllBytes(new TestRpm.Abc().path());
        final Key key = new Key.From("replaced.rpm");
        new BlockingStorage(storage).save(key, "uploaded package".getBytes());
        MatcherAssert.assertThat(
            new RpmUpload(storage, new RepoConfig.Simple()).response(
                new RequestLine("PUT", "/replaced.rpm?override=true").toString(),
                Headers.EMPTY,
                Flowable.fromArray(ByteBuffer.wrap(content))
            ),
            new RsHasStatus(RsStatus.ACCEPTED)
        );
        MatcherAssert.assertThat(
            new BlockingStorage(storage).value(key),
            new IsEqual<>(content)
        );
    }

    @Test
    void dontReplaceArtifact() throws Exception {
        final Storage storage = new InMemoryStorage();
        final byte[] content =
            "first package content".getBytes(StandardCharsets.UTF_8);
        final Key key = new Key.From("not-replaced.rpm");
        new BlockingStorage(storage).save(key, content);
        MatcherAssert.assertThat(
            new RpmUpload(storage, new RepoConfig.Simple()).response(
                new RequestLine("PUT", "/not-replaced.rpm").toString(),
                Headers.EMPTY,
                Flowable.fromArray(ByteBuffer.wrap("second package content".getBytes()))
            ),
            new RsHasStatus(RsStatus.CONFLICT)
        );
        MatcherAssert.assertThat(
            new BlockingStorage(storage).value(key),
            new IsEqual<>(content)
        );
    }

    @Test
    void skipsUpdateWhenParamSkipIsTrue() throws Exception {
        final Storage storage = new InMemoryStorage();
        final byte[] content = Files.readAllBytes(new TestRpm.Abc().path());
        MatcherAssert.assertThat(
            "ACCEPTED 202 returned",
            new RpmUpload(storage, new RepoConfig.Simple()).response(
                new RequestLine("PUT", "/my-package.rpm?skip_update=true").toString(),
                Headers.EMPTY,
                Flowable.fromArray(ByteBuffer.wrap(content))
            ),
            new RsHasStatus(RsStatus.ACCEPTED)
        );
        MatcherAssert.assertThat(
            "Content saved to storage",
            new BlockingStorage(storage).value(new Key.From(RpmUpload.TO_ADD, "my-package.rpm")),
            new IsEqual<>(content)
        );
        MatcherAssert.assertThat(
            "Metadata not updated",
            new BlockingStorage(storage).list(new Key.From("repodata")).isEmpty(),
            new IsEqual<>(true)
        );
    }

    @Test
    void skipsUpdateIfModeIsCron() throws Exception {
        final Storage storage = new InMemoryStorage();
        final byte[] content = Files.readAllBytes(new TestRpm.Abc().path());
        MatcherAssert.assertThat(
            "ACCEPTED 202 returned",
            new RpmUpload(storage, new RepoConfig.Simple(RepoConfig.UpdateMode.CRON)).response(
                new RequestLine("PUT", "/abc-package.rpm").toString(),
                Headers.EMPTY,
                Flowable.fromArray(ByteBuffer.wrap(content))
            ),
            new RsHasStatus(RsStatus.ACCEPTED)
        );
        MatcherAssert.assertThat(
            "Content saved to temp location",
            new BlockingStorage(storage).value(new Key.From(RpmUpload.TO_ADD, "abc-package.rpm")),
            new IsEqual<>(content)
        );
        MatcherAssert.assertThat(
            "Metadata not updated",
            new BlockingStorage(storage).list(new Key.From("repodata")).isEmpty(),
            new IsEqual<>(true)
        );
    }
}
