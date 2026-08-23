package cn.fandmc.fandui.api.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
}
