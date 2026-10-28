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
    public void onlySharpLogosOfSensibleShapeAreKept() {
        assertTrue(RadioLogos.goodSize(256, 256));
        assertTrue(RadioLogos.goodSize(96, 150));
        assertEquals(false, RadioLogos.goodSize(64, 64));    // a favicon blown up to a tile looks cheap
        assertEquals(false, RadioLogos.goodSize(512, 100));  // a banner
        assertEquals(false, RadioLogos.goodSize(95, 400));
    }

    @Test
    public void candidatesComeFromTheRegionFirstWithoutDuplicatesAndAreLimited() {
        java.util.List<String[]> rows = java.util.Arrays.asList(
                row("Radio X", "http://x/us.png", "US"),
                row("Radio X", "http://x/tr1.png", "TR"),
                row("Radio X FM", "http://x/tr1.png", "TR"),   // the same logo again
                row("Radio X 2", "http://x/de.png", "DE"),
                row("Radio X 3", "http://x/fr.png", "FR"),
                row("Radio X 4", "http://x/es.png", "ES"),
                row("Radio X 5", "http://x/it.png", "IT"));
        java.util.List<String> c = RadioLogos.candidates(rows, "radiox");
        assertEquals(4, c.size());
        assertEquals("http://x/tr1.png", c.get(0));
        assertEquals("http://x/de.png", c.get(1));   // DE is also in the region list
    }

    @Test
    public void aNameWithoutSpacesAlsoGetsAShortPrefixSearch() throws Exception {
        assertEquals(2, RadioLogos.searchPaths("TRTTURKU").size());
        assertTrue(RadioLogos.searchPaths("TRTTURKU").get(1).contains("name=TRT"));
        assertEquals(1, RadioLogos.searchPaths("TRT Türkü").size()); // already has the spaces
        assertEquals(1, RadioLogos.searchPaths("BEST").size());      // too short to cut
    }
}
