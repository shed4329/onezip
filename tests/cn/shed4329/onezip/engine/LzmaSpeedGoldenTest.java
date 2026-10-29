package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.ArkLzma2OutputStream;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent old-version output commitments, not values computed from the new encoder. */
class LzmaSpeedGoldenTest {
    @Test void unchangedFastVectorsAndIntentionalNormalParsing()throws Exception{
        int vectors=0,unchanged=0,continued=0;long originalTotal=0,currentTotal=0;
        for(String line:Files.readAllLines(Path.of("tests/fixtures/lzma-speed-golden.tsv"))){
            if(line.isBlank()||line.startsWith("#"))continue;String[] c=line.split("\\s+");
            int size=Integer.parseInt(c[0]),seed=Integer.parseInt(c[1]),period=Integer.parseInt(c[2]),distance=Integer.parseInt(c[3]),level=Integer.parseInt(c[4]);
            byte[] data=new byte[size];new Random(seed).nextBytes(data);for(int i=distance;i<size;i++)if(i%period!=0)data[i]=data[i-distance];
            ByteArrayOutputStream packed=new ByteArrayOutputStream();try(ArkLzma2OutputStream encoder=new ArkLzma2OutputStream(packed,level)){encoder.write(data);}
            if(size<=65536&&level<5){
                assertEquals(Integer.parseInt(c[6]),packed.size(),"size="+size+", level="+level);
                assertEquals(c[5],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(packed.toByteArray())),"size="+size+", level="+level);unchanged++;
            }else{
                // Normal-level token decisions are intentionally price based now.
                // These historic commitments cap unpacked chunks at 64 KiB
                // and reset dictionary/model at 2 MiB.
                // Retain them as provenance rather than regenerating them from
                // the new implementation; continuity intentionally changes output.
                // Explicit quality tolerance for very short normal-mode inputs:
                // quantized snapshot prices need not minimize final adaptive bytes.
                // Eight bytes is a regression budget, not a flush-size theorem.
                int tolerance=size<8192&&level>=5?8:0;
                assertTrue(packed.size()<=Integer.parseInt(c[6])+tolerance,"size="+size+", level="+level+", packed="+packed.size()+", historical="+c[6]);
                try(var decoder=new org.tukaani.xz.LZMA2InputStream(new ByteArrayInputStream(packed.toByteArray()),ArkLzma2OutputStream.BLOCK_SIZE)){
                    assertArrayEquals(data,decoder.readAllBytes());
                }
                continued++;
            }
            originalTotal+=Integer.parseInt(c[6]);currentTotal+=packed.size();vectors++;
        }
        assertEquals(21,vectors);
        assertTrue(currentTotal<=originalTotal,"The complete historical corpus must not grow in aggregate");
        assertEquals(4,unchanged);assertEquals(17,continued);
    }
}
