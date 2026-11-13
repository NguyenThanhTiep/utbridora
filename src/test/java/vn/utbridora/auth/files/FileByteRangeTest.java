package vn.utbridora.auth.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import vn.utbridora.auth.operations.OperationFailure;

class FileByteRangeTest {
    @Test void wholeOpenSuffixAndClampedRanges() {
        assertEquals(new FileByteRange(0,99,false),FileByteRange.parse(null,100));
        assertEquals(new FileByteRange(10,19,true),FileByteRange.parse("bytes=10-19",100));
        assertEquals(new FileByteRange(10,99,true),FileByteRange.parse("bytes=10-",100));
        assertEquals(new FileByteRange(90,99,true),FileByteRange.parse("bytes=-10",100));
        assertEquals(new FileByteRange(0,99,true),FileByteRange.parse("bytes=-999",100));
        assertEquals(10,FileByteRange.parse("bytes=90-999",100).length());
    }
    @ParameterizedTest @ValueSource(strings={"","items=0-1","bytes=0-1,2-3","bytes=-","bytes=-0","bytes=100-","bytes=3-2","bytes=999999999999999999999-","bytes=1-999999999999999999999","bytes= 0-1"})
    void rejectsInvalidOrMultipleRanges(String value) {
        assertEquals(416,assertThrows(OperationFailure.class,()->FileByteRange.parse(value,100)).status);
    }
}
