package cn.shed4329.onezip.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

class ArchiveReplacementTest {
    @TempDir Path root;

    @Test void invalidAesWriteDoesNotMutateTheEncryptedStream() throws Exception {
        var output=new java.io.ByteArrayOutputStream();
        var aes=new ZipAesOutputStream(output,new char[]{'t','e','s','t'});
        int header=output.size();
        assertThrows(IndexOutOfBoundsException.class,()->aes.write(new byte[4],1,-1));
        assertThrows(IndexOutOfBoundsException.class,()->aes.write(new byte[4],Integer.MAX_VALUE,1));
        assertEquals(header,output.size());
        aes.write(new byte[0],0,0);
        aes.finish();
        assertEquals(header+10,output.size());
    }

    @Test
    void refusesConcurrentArchiveChange() throws Exception {
        Path archive = Files.writeString(root.resolve("archive.zip"), "original");
        var replacement = new ArchiveReplacement(archive);
        Path staging = Files.writeString(root.resolve("staged.zip"), "replacement");
        Files.writeString(archive, "another writer has changed this file");
        assertThrows(IOException.class, () -> replacement.publish(staging));
        assertEquals("another writer has changed this file", Files.readString(archive));
        assertTrue(Files.exists(staging));
    }

    @Test
    void failedCommentEditPreservesInvalidArchiveAndRemovesTemporaryFile() throws Exception {
        Path archive = Files.writeString(root.resolve("broken.zip"), "not a ZIP");
        assertThrows(IOException.class, () -> ZipComments.write(archive, "description"));
        assertEquals("not a ZIP", Files.readString(archive));
        try (var files = Files.list(root)) {
            assertEquals(List.of(archive), files.toList());
        }
    }

    @Test
    void descriptionsRoundTripWithoutChangingEntries() throws Exception {
        Path input = Files.writeString(root.resolve("中文.txt"), "unchanged content");
        Path archive = root.resolve("archive.zip");
        new ArchiveEngine()
                .compress(List.of(input), archive, ArchiveEngine.Format.ZIP, 5, null, null, null);
        for (String comment : List.of("长的中文说明 " + "x".repeat(60000), "短说明", "")) {
            ZipComments.write(archive, comment);
            assertEquals(comment, ZipComments.read(archive));
            try (var reader = new ZipArchiveReader(archive, null, null);
                    var in = reader.open(reader.entries().getFirst())) {
                assertEquals(
                        "unchanged content",
                        new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        byte[] before = Files.readAllBytes(archive);
        assertThrows(IOException.class, () -> ZipComments.write(archive, "文".repeat(22000)));
        assertArrayEquals(before, Files.readAllBytes(archive));
    }
}
