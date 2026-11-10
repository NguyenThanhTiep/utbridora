package vn.utbridora.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Qa01Week2MigrationVersionTest {
    @Test
    void versionedSqlMigrationsHaveUniqueFlywayVersions() throws IOException {
        Path location = Path.of("src/main/resources/db/migration");
        var migrations = new TreeMap<MigrationVersion, List<Path>>();

        try (var files = Files.walk(location)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                int separator = name.indexOf("__");
                if (!name.startsWith("V") || !name.toLowerCase(Locale.ROOT).endsWith(".sql")) {
                    continue;
                }
                assertTrue(separator > 1, () -> "Invalid versioned migration name: " + file);
                MigrationVersion version = MigrationVersion.fromVersion(name.substring(1, separator));
                migrations.computeIfAbsent(version, key -> new ArrayList<>()).add(location.relativize(file));
            }
        }

        assertFalse(migrations.isEmpty(), "No versioned SQL migrations found");
        var duplicates = migrations.entrySet().stream().filter(entry -> entry.getValue().size() > 1).toList();
        assertTrue(duplicates.isEmpty(), () -> "Duplicate Flyway versions: " + duplicates);
    }
}
