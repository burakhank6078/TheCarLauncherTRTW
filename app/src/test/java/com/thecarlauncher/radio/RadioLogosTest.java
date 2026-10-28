package com.thecarlauncher.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RadioLogosTest {
    @Test
    public void turkishAndOtherAccentedNamesFoldToPlainLetters() {
        assertEquals("trtturku", RadioLogos.key("TRT Türkü"));
        assertEquals("trtturku", RadioLogos.key("TRTTURKU"));
        assertEquals("trtturku", RadioLogos.key("TRT TÜRKÜ"));
        assertEquals("cagrisig", RadioLogos.key("Çağrı ŞıĞ"));
        assertEquals("istanbul", RadioLogos.key("İSTANBUL"));
        assertEquals("radiobeogradcc", RadioLogos.key("Radio Beograd ČĆ"));
        assertEquals("", RadioLogos.key(null));
    }

    private static String[] row(String name, String favicon, String country) {
        return new String[]{name, favicon, country};
    }

    @Test
    public void theRdsNameFindsTheStationWithSpacesAndAccents() {
        java.util.List<String[]> rows = java.util.Arrays.asList(
                row("TRT 1", "http://x/trt1.png", "TR"),
                row("TRT Türkü", "http://x/turku.png", "TR"),
                row("TRT Türkü (mirror)", "http://x/mirror.png", "DE"));
        assertEquals("http://x/turku.png", RadioLogos.pick(rows, RadioLogos.key("TRTTURKU")));
    }

    @Test
    public void aStationOfTheRegionWinsAndOneWithoutALogoIsSkipped() {
        java.util.List<String[]> rows = java.util.Arrays.asList(
                row("Radio Test", "", "TR"),
                row("Radio Test FM", "http://x/world.png", "US"),
                row("Radio Test 2", "http://x/near.png", "TR"));
        assertEquals("http://x/near.png", RadioLogos.pick(rows, "radiotest"));
        assertEquals("http://x/world.png", RadioLogos.pick(rows.subList(0, 2), "radiotest")); // nothing near: any with a logo
        assertEquals(null, RadioLogos.pick(rows.subList(0, 1), "radiotest"));
        assertEquals(null, RadioLogos.pick(rows, "nothing"));
    }

    @Test
    public void aNameWithoutSpacesAlsoGetsAShortPrefixSearch() throws Exception {
        assertEquals(2, RadioLogos.searchPaths("TRTTURKU").size());
        assertTrue(RadioLogos.searchPaths("TRTTURKU").get(1).contains("name=TRT"));
        assertEquals(1, RadioLogos.searchPaths("TRT Türkü").size()); // already has the spaces
        assertEquals(1, RadioLogos.searchPaths("BEST").size());      // too short to cut
    }
}
