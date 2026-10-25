package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class WallpaperCaptionsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void bufferedReaderPreservesUtf8AndLineEndings() throws Exception {
        File folder = temporary.newFolder();
        try (FileOutputStream out = new FileOutputStream(new File(folder, WallpaperCaptions.FILE_NAME))) {
            out.write(("001.jpg\tCaf\u00e9\r\n002.jpg\t\u65e5\u843d\n").getBytes(StandardCharsets.UTF_8));
        }
        Map<String, String> names = WallpaperCaptions.read(folder);
        assertEquals("Caf\u00e9", names.get("001.jpg"));
        assertEquals("\u65e5\u843d", names.get("002.jpg"));
    }

    @Test
    public void emptyAndMissingCaptionFilesAreSafe() throws Exception {
        File folder = temporary.newFolder();
        assertTrue(WallpaperCaptions.read(folder).isEmpty());
        assertTrue(new File(folder, WallpaperCaptions.FILE_NAME).createNewFile());
        assertTrue(WallpaperCaptions.read(folder).isEmpty());
    }

    @Test
    public void oversizedCaptionFileIsNotRead() throws Exception {
        File folder = temporary.newFolder();
        try (FileOutputStream out = new FileOutputStream(new File(folder, WallpaperCaptions.FILE_NAME))) {
            out.write(new byte[256 * 1024 + 1]);
        }
        assertTrue(WallpaperCaptions.read(folder).isEmpty());
    }

    @Test
    public void directoryInPlaceOfCaptionFileIsSafe() throws Exception {
        File folder = temporary.newFolder();
        assertTrue(new File(folder, WallpaperCaptions.FILE_NAME).mkdir());
        assertTrue(WallpaperCaptions.read(folder).isEmpty());
    }

    @Test
    public void readsFilenameTabName() {
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList(
                "001.jpg\tRed Spider Nebula · Hubble",
                "002.jpg\t  Falcon 9 at sunset  "));
        assertEquals("Red Spider Nebula · Hubble", names.get("001.jpg"));
        assertEquals("Falcon 9 at sunset", names.get("002.jpg"));
    }

    @Test
    public void skipsCommentsBlankAndMalformedLines() {
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList(
                "# captions for the collection", "", "   ", "no-tab-here.jpg Dusk", "\tno file", "003.jpg\t", null));
        assertTrue(names.isEmpty());
    }

    @Test
    public void cutsVeryLongNames() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 30; i++) longName.append("word ");
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList("004.jpg\t" + longName));
        assertTrue(names.get("004.jpg").length() <= WallpaperCaptions.MAX_LENGTH);
    }

    @Test
    public void laterLineWinsForTheSameFile() {
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList("005.jpg\tOld", "005.jpg\tNew"));
        assertEquals("New", names.get("005.jpg"));
        assertFalse(names.containsKey("006.jpg"));
    }

    @Test
    public void missingFolderHasNoNames() {
        assertTrue(WallpaperCaptions.read(null).isEmpty());
    }
}
