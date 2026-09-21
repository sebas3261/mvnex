package com.sebas3261.ex.infrastructure.settings;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes user settings safely (design D15): symlinks are followed so the link survives, the
 * original is backed up first, and the new content replaces it atomically with its permissions.
 */
public final class SettingsFileWriter {

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Clock clock;

    public SettingsFileWriter(Clock clock) {
        this.clock = clock;
    }

    /** Writes a new file, creating missing parent directories. */
    public void create(Path target, byte[] content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(target, content);
    }

    /** Backs up {@code target} and atomically replaces it; returns the backup path. */
    public Path replace(Path target, byte[] content) throws IOException {
        Path real = target.toRealPath();
        Path backup = backupPath(real);
        Files.copy(real, backup, StandardCopyOption.COPY_ATTRIBUTES);

        Path temp = Files.createTempFile(real.getParent(), real.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, content);
            PosixFileAttributeView posix = Files.getFileAttributeView(real, PosixFileAttributeView.class);
            if (posix != null) {
                Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(real));
            }
            try {
                Files.move(temp, real, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, real, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return backup;
    }

    private Path backupPath(Path real) throws IOException {
        String stamp = LocalDateTime.now(clock).format(BACKUP_STAMP);
        String name = real.getFileName() + "." + stamp;
        Path backup = real.resolveSibling(name + ".bak");
        for (int i = 1; Files.exists(backup); i++) {
            backup = real.resolveSibling(name + "-" + i + ".bak");
        }
        if (Files.exists(backup)) {
            throw new FileAlreadyExistsException(backup.toString());
        }
        return backup;
    }
}
