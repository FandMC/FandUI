package cn.fandmc.fandui.api.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ResourceSourceTest {
    @Test
    void byteSourceCopiesAtRegistrationAndEveryLoad() throws Exception {
        byte[] input = {1, 2, 3};
        ResourceSource source = ResourceSource.bytes(input);
        input[0] = 9;

        byte[] first = source.load();
        byte[] second = source.load();
        first[1] = 8;

        assertArrayEquals(new byte[]{1, 2, 3}, second);
        assertNotSame(first, second);
    }

    @Test
    void convenienceFactoriesPublishStableImageFormatHints() throws Exception {
        ResourceSource auto = ResourceSource.bytes(new byte[]{1});
        ResourceSource png = ResourceSource.png(new byte[]{2});
        ResourceSource svg = ResourceSource.svg("<svg viewBox=\"0 0 1 1\"/>");

        assertEquals(ResourceFormat.AUTO, auto.format());
        assertEquals(ResourceFormat.PNG, png.format());
        assertEquals(ResourceFormat.SVG, svg.format());
        assertArrayEquals("<svg viewBox=\"0 0 1 1\"/>".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                svg.load());
    }
}
