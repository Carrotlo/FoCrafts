package me.foesio.foCrafts;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublicMessageValidationTest {
    @TempDir Path directory;

    @Test
    void acceptsOwnerTextAndRemovedOptionalKeysWithoutWritingThem() throws Exception {
        Path file = directory.resolve("messages.yml");
        byte[] original = ("tokens:\n  theme: '§b'\n"
                + "public-gui:\n  status-locked: '§cGesloten'\n"
                + "craft-success: '§aGemaakt ×{amount}'\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(file, original);
        FoCrafts.validatePublicMessages(file);
        assertArrayEquals(original, Files.readAllBytes(file));
        Files.delete(file);
        FoCrafts.validatePublicMessages(file);
        assertFalse(Files.exists(file));
    }

    @Test
    void rejectsInvalidPublicMessageBeforeReloadingCaches() throws Exception {
        Path file = directory.resolve("messages.yml");
        Files.writeString(file, "craft-success:\n  - wrong shape\n");
        IOException error = assertThrows(IOException.class, () -> FoCrafts.validatePublicMessages(file));
        assertTrue(error.getMessage().contains("craft-success"));
    }
}
