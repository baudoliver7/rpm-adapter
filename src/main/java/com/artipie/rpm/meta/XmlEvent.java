/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/rpm-adapter/LICENSE.txt
 */
package com.artipie.rpm.meta;

import com.artipie.rpm.pkg.HeaderTags;
import com.artipie.rpm.pkg.Package;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

/**
 * Xml event to write to the output stream.
 * @since 1.5
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public interface XmlEvent {

    /**
     * Contracts {@link XMLEvent} with provided metadata.
     * @param writer Event writer where to event
     * @param meta Info to build {@link XMLEvent} with
     * @throws IOException On IO error
     */
    void add(XMLEventWriter writer, Package.Meta meta) throws IOException;

    /**
     * Implementation of {@link XmlEvent} to build event for `package` and `version` tags.
     * @since 1.5
     */
    final class PackageAndVersion implements XmlEvent {

        @Override
        public void add(final XMLEventWriter writer, final Package.Meta meta) throws IOException {
            final XMLEventFactory events = XMLEventFactory.newFactory();
            final HeaderTags tags = new HeaderTags(meta);
            final String pkg = "package";
            final String version = "version";
            try {
                writer.add(events.createStartElement("", "", pkg));
                writer.add(events.createAttribute("pkgid", meta.checksum().hex()));
                writer.add(events.createAttribute("name", tags.name()));
                writer.add(events.createAttribute("arch", tags.arch()));
                writer.add(events.createStartElement("", "", version));
                writer.add(events.createAttribute("epoch", String.valueOf(tags.epoch())));
                writer.add(events.createAttribute("ver", tags.version()));
                writer.add(events.createAttribute("rel", tags.release()));
                writer.add(events.createEndElement("", "", version));
            } catch (final XMLStreamException err) {
                throw new IOException(err);
            }
        }
    }

    /**
     * Implementation of {@link XmlEvent} to build event for {@link XmlPackage#OTHER} package.
     * @since 1.5
     */
    final class Other implements XmlEvent {

        @Override
        public void add(final XMLEventWriter writer, final Package.Meta meta) throws IOException {
            final XMLEventFactory events = XMLEventFactory.newFactory();
            final HeaderTags tags = new HeaderTags(meta);
            try {
                new PackageAndVersion().add(writer, meta);
                for (final String changelog : tags.changelog()) {
                    final ChangelogEntry entry = new ChangelogEntry(changelog);
                    final String tag = "changelog";
                    writer.add(events.createStartElement("", "", tag));
                    writer.add(events.createAttribute("date", String.valueOf(entry.date())));
                    writer.add(events.createAttribute("author", entry.author()));
                    writer.add(events.createCharacters(entry.content()));
                    writer.add(events.createEndElement("", "", tag));
                }
                writer.add(events.createEndElement("", "", "package"));
            } catch (final XMLStreamException err) {
                throw new IOException(err);
            }
        }
    }

    /**
     * Implementation of {@link XmlEvent} to build event for {@link XmlPackage#FILELISTS} package.
     * @since 1.5
     */
    final class Filelists implements XmlEvent {

        @Override
        public void add(final XMLEventWriter writer, final Package.Meta meta) throws IOException {
            final XMLEventFactory events = XMLEventFactory.newFactory();
            try {
                new PackageAndVersion().add(writer, meta);
                new Files().add(writer, meta);
                writer.add(events.createEndElement("", "", "package"));
            } catch (final XMLStreamException err) {
                throw new IOException(err);
            }
        }
    }

    /**
     * Implementation of {@link XmlEvent} to build event for `files` tag.
     * @since 1.5
     */
    final class Files implements XmlEvent {

        /**
         * Predicate to filter files. The item is NOT added to the writer if
         * the filter returns TRUE.
         */
        private final Predicate<String> filter;

        /**
         * Ctor.
         * @param filter Predicate to filter files. The item is NOT added to the writer if
         *  the filter returns TRUE
         */
        public Files(final Predicate<String> filter) {
            this.filter = filter;
        }

        /**
         * Ctor with 'always false' filter.
         */
        public Files() {
            this(name -> false);
        }

        @Override
        public void add(final XMLEventWriter writer, final Package.Meta meta) throws IOException {
            final XMLEventFactory events = XMLEventFactory.newFactory();
            final HeaderTags tags = new HeaderTags(meta);
            try {
                final String[] files = tags.baseNames().toArray(new String[0]);
                final String[] dirs = tags.dirNames().toArray(new String[0]);
                final Set<String> dirset = Arrays.stream(dirs).collect(Collectors.toSet());
                final int[] did = tags.dirIndexes();
                for (int idx = 0; idx < files.length; idx += 1) {
                    final String fle = files[idx];
                    // @checkstyle MethodBodyCommentsCheck (2 lines)
                    // @todo #388:30min This condition is not covered with unit test, extend
                    //  the test to check this case and make sure it works properly.
                    if (fle.isEmpty() || fle.charAt(0) == '.') {
                        continue;
                    }
                    final String path = String.format("%s%s", dirs[did[idx]], fle);
                    if (this.filter.test(path)) {
                        continue;
                    }
                    writer.add(events.createStartElement("", "", "file"));
                    if (dirset.contains(String.format("%s/", path))) {
                        writer.add(events.createAttribute("type", "dir"));
                    }
                    writer.add(events.createCharacters(path));
                    writer.add(events.createEndElement("", "", "file"));
                }
            } catch (final XMLStreamException err) {
                throw new IOException(err);
            }
        }
    }

}
