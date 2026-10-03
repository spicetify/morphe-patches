package app.spicetify.extension.spotify.theme;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ArgbColorsTest {
    @Test
    public void mixingReproducesSpotifysStockSurfaces() {
        assertEquals(0xFF202020, ArgbColors.mix(0xFF121212, 0xFFFFFFFF, 0.06));
        assertEquals(0xFF1B1B1B, ArgbColors.mix(0xFF121212, 0xFFFFFFFF, 0.04));
        assertEquals(0xFF2A2A2A, ArgbColors.mix(0xFF121212, 0xFFFFFFFF, 0.10));
        assertEquals(0xFF1ABC54, ArgbColors.mix(0xFF1ED760, 0xFF000000, 0.125));
        assertEquals(0x1A808080, ArgbColors.mix(0x1AFFFFFF, 0xFF000000, 0.5));
    }

    @Test
    public void lighteningStopsAtWhiteAndKeepsAlpha() {
        assertEquals(0xFF21263C, ArgbColors.lighten(0xFF0B1026, 22));
        assertEquals(0x80FFFFFF, ArgbColors.lighten(0x80F8F8F8, 24));
    }

    @Test
    public void contrastFollowsWcag() {
        assertEquals(21.0, ArgbColors.contrast(0xFF000000, 0xFFFFFFFF), 0.01);
        assertEquals(1.0, ArgbColors.contrast(0xFFFFFFFF, 0xFFFFFFFF), 0.0001);
    }
}
