package com.example.fitapp.module.exercise.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 文件键来自库里的 media 列：格式不对的一律当没有，路径穿越（../）走不出 exercise-media/。 */
class ExerciseMediaStorageTest {

    @TempDir
    Path root;

    @Test
    void find_existingFile_returnsPath() throws Exception {
        Path dir = Files.createDirectories(root.resolve(ExerciseMediaStorage.DIR));
        Files.writeString(dir.resolve("0025-EIeI8Vf.gif"), "gif");
        ExerciseMediaStorage s = new ExerciseMediaStorage(root.toString());

        assertEquals(dir.resolve("0025-EIeI8Vf.gif"), s.find("0025-EIeI8Vf", ExerciseMediaStorage.Kind.GIF));
        assertNull(s.find("0025-EIeI8Vf", ExerciseMediaStorage.Kind.THUMB));
    }

    @Test
    void find_badKey_returnsNull() throws Exception {
        Files.writeString(root.resolve("secret.gif"), "x");
        ExerciseMediaStorage s = new ExerciseMediaStorage(root.toString());

        assertNull(s.find("../secret", ExerciseMediaStorage.Kind.GIF));
        assertNull(s.find(null, ExerciseMediaStorage.Kind.GIF));
        assertNull(s.find("", ExerciseMediaStorage.Kind.GIF));
    }
}
