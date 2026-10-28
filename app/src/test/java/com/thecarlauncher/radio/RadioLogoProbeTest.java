package com.thecarlauncher.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class RadioLogoProbeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void aMissingFolderIsSaidToBeMissing() {
        List<String> out = new ArrayList<>();
        RadioLogoProbe.describe(new File(tmp.getRoot(), "nope"), out);
        assertEquals(1, out.size());
        assertTrue(out.get(0).endsWith("none"));
    }

    @Test
    public void theLogoFilesAndTheFirstLinesOfTheDataFileAreListed() throws Exception {
        File zip = tmp.newFile("RadioLogo.zip");
        try (FileWriter w = new FileWriter(zip)) { w.write("12345"); }
        File logos = tmp.newFolder("logo");
        new File(logos, "icon_a.png").createNewFile();
        new File(logos, "icon_b.png").createNewFile();
        tmp.newFile("unrelated.txt");
        try (FileWriter w = new FileWriter(new File(tmp.getRoot(), "radio_data.txt"))) {
            w.write("322D,8800,trt\n32A1,8835,trt\n4001,9000,x\n4002,9100,y\n");
        }
        List<String> out = new ArrayList<>();
        RadioLogoProbe.describe(tmp.getRoot(), out);
        assertEquals(2, out.size());
        assertTrue(out.get(0).contains("RadioLogo.zip(5)"));
        assertTrue(out.get(0).contains("logo/(2)"));
        assertTrue(out.get(0).contains("radio_data.txt("));
        assertTrue(!out.get(0).contains("unrelated"));
        assertTrue(out.get(1).contains("[322D,8800,trt]"));
        assertTrue(out.get(1).contains("[4001,9000,x]"));
        assertTrue(!out.get(1).contains("4002")); // only the first three lines
    }
}
