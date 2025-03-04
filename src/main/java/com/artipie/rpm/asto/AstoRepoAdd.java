/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/rpm-adapter/LICENSE.txt
 */
package com.artipie.rpm.asto;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.key.KeyExcludeFirst;
import com.artipie.asto.lock.storage.StorageLock;
import com.artipie.asto.rx.RxStorageWrapper;
import com.artipie.rpm.RepoConfig;
import com.artipie.rpm.http.RpmUpload;
import com.artipie.rpm.pkg.Package;
import com.jcabi.log.Logger;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Add packages to metadata and repository.
 * @since 1.10
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public final class AstoRepoAdd {

    /**
     * Metadata key.
     */
    private static final Key META = new Key.From("repodata");

    /**
     * Asto storage.
     */
    private final Storage asto;

    /**
     * Repository config.
     */
    private final RepoConfig cnfg;

    /**
     * Ctor.
     * @param asto Abstract storage
     * @param cnfg Repository config
     */
    public AstoRepoAdd(final Storage asto, final RepoConfig cnfg) {
        this.asto = asto;
        this.cnfg = cnfg;
    }

    /**
     * Performs whole workflow to add items, listed in {@link com.artipie.rpm.http.RpmUpload#TO_ADD}
     * location, to the repository and metadata files.
     * @return Completable action
     */
    public CompletionStage<Void> perform() {
        return this.read().thenCompose(
            list -> new AstoMetadataAdd(this.asto, this.cnfg).perform(list)
        ).thenCompose(
            temp -> new AstoCreateRepomd(this.asto, this.cnfg).perform(temp).thenCompose(
                nothing -> new AstoMetadataNames(this.asto, this.cnfg).prepareNames(temp)
                    .thenCompose(
                        keys -> {
                            final StorageLock lock = new StorageLock(this.asto, AstoRepoAdd.META);
                            return lock.acquire()
                                .thenCompose(ignored -> this.remove(AstoRepoAdd.META))
                                .thenCompose(
                                    ignored -> CompletableFuture.allOf(
                                        keys.entrySet().stream().map(
                                            entry -> this.asto
                                                .move(entry.getKey(), entry.getValue())
                                        ).toArray(CompletableFuture[]::new)
                                    )
                                )
                                .thenCompose(
                                    ignored -> this.asto.list(RpmUpload.TO_ADD).thenCompose(
                                        list -> CompletableFuture.allOf(
                                            list.stream().map(
                                                key -> this.asto.move(
                                                    key, AstoRepoAdd.removeTempPart(key)
                                                )
                                            ).toArray(CompletableFuture[]::new)
                                        )
                                    )
                                )
                                .thenCompose(ignored -> lock.release()).thenCompose(
                                    ignored -> this.remove(temp)
                                );
                        }
                    )
            )
        );
    }

    /**
     * Read new packages metadata.
     * @return Completable action with the list of packages metadata to add
     */
    private CompletionStage<List<Package.Meta>> read() {
        return SingleInterop.fromFuture(this.asto.list(RpmUpload.TO_ADD))
            .flatMapPublisher(Flowable::fromIterable)
            .parallel().runOn(Schedulers.io())
            .flatMap(
                key -> Flowable.fromFuture(
                    new AstoRpmPackage(this.asto, this.cnfg.digest()).packageMeta(
                        key, AstoRepoAdd.removeTempPart(key).string()
                    ).toCompletableFuture()
                ).onErrorResumeNext(
                    throwable -> {
                        Logger.warn(
                            this, "Failed to parse rpm package %s\n%s",
                            key.string(), throwable.getMessage()
                        );
                        return new RxStorageWrapper(this.asto).delete(key)
                            .andThen(Flowable.empty());
                    }
                )
            ).sequential().observeOn(Schedulers.io()).toList().to(SingleInterop.get());
    }

    /**
     * Removes all items found by the key.
     * @param key Key to remove items
     * @return Completable action
     */
    private CompletableFuture<Void> remove(final Key key) {
        return this.asto.list(key).thenCompose(
            list -> CompletableFuture.allOf(
                list.stream().map(this.asto::delete)
                    .toArray(CompletableFuture[]::new)
            )
        );
    }

    /**
     * Removes first {@link RpmUpload#TO_ADD} part from the key.
     * @param key Origin key
     * @return Key without {@link RpmUpload#TO_ADD} part
     */
    private static Key removeTempPart(final Key key) {
        return new KeyExcludeFirst(key, RpmUpload.TO_ADD.string());
    }
}
