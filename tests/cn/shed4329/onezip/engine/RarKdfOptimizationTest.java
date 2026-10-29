package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RarKdfOptimizationTest {
    @Test void exactKeysAndAllIvSnapshotsMatchFrozenBaselineAcrossMutationBoundary()throws Exception{
        int count=0;for(String row:Files.readAllLines(Path.of("tests/fixtures/rar/kdf4-vectors.tsv"))){String[] p=row.split("\t",-1);byte[] rawPassword=HexFormat.of().parseHex(p[0]);char[] password=new String(rawPassword,StandardCharsets.UTF_16LE).toCharArray();byte[] salt=p[1].equals("-")?null:HexFormat.of().parseHex(p[1]);
            var keys=RarCrypto.derive(new RarCrypto.Info(false,salt,null,0,null,false),password,null);
            assertEquals(p[2],HexFormat.of().formatHex(keys.aes()),"AES key, password length "+password.length);assertEquals(p[3],HexFormat.of().formatHex(keys.iv()),"all IV snapshots, password length "+password.length);count++;
        }assertEquals(24,count);
    }
    @Test void providerWithoutCloneFallsBackWithoutChangingPasswordInput()throws Exception{
        String row=Files.readAllLines(Path.of("tests/fixtures/rar/kdf4-vectors.tsv")).stream().filter(s->s.startsWith("4100".repeat(28)+"\t0001020304050607\t")).findFirst().orElseThrow();String[] p=row.split("\t");
        Provider provider=new Provider("OneZipNoCloneTest","1.0","Test-only non-cloneable SHA-1"){};provider.put("MessageDigest.SHA-1",NoCloneSha1.class.getName());Security.insertProviderAt(provider,1);
        try{assertThrows(CloneNotSupportedException.class,()->MessageDigest.getInstance("SHA-1").clone());var keys=RarCrypto.derive(new RarCrypto.Info(false,HexFormat.of().parseHex(p[1]),null,0,null,false),"A".repeat(28).toCharArray(),null);assertEquals(p[2],HexFormat.of().formatHex(keys.aes()));assertEquals(p[3],HexFormat.of().formatHex(keys.iv()));}
        finally{Security.removeProvider(provider.getName());}
    }
    @Test void batchingStillChecksCancellationBetweenUpdates()throws Exception{
        AtomicInteger checks=new AtomicInteger();assertThrows(InterruptedIOException.class,()->RarCrypto.derive(new RarCrypto.Info(false,new byte[8],null,0,null,false),"password".toCharArray(),()->checks.incrementAndGet()>=4));assertEquals(4,checks.get());
    }
    @Test void longInputBatchingMatchesReferenceAcross128192And256ByteBoundaries()throws Exception{
        int count=0;for(String row:Files.readAllLines(Path.of("tests/fixtures/rar/kdf4-long-vectors.tsv"))){String[] p=row.split("\t",-1);char[] password=new String(HexFormat.of().parseHex(p[0]),StandardCharsets.UTF_16LE).toCharArray();byte[] salt=p[1].equals("-")?null:HexFormat.of().parseHex(p[1]);
            var keys=RarCrypto.derive(new RarCrypto.Info(false,salt,null,0,null,false),password,null);assertEquals(p[2],HexFormat.of().formatHex(keys.aes()));assertEquals(p[3],HexFormat.of().formatHex(keys.iv()));count++;
        }assertEquals(32,count);
    }
    @Test void longInputCancellationAndNonCloneableProviderKeepLegacyBehavior()throws Exception{
        AtomicInteger checks=new AtomicInteger();assertThrows(InterruptedIOException.class,()->RarCrypto.derive(new RarCrypto.Info(false,new byte[8],null,0,null,false),"A".repeat(63).toCharArray(),()->checks.incrementAndGet()>=4));assertEquals(4,checks.get());
        String row=Files.readAllLines(Path.of("tests/fixtures/rar/kdf4-vectors.tsv")).stream().filter(s->s.startsWith("4100".repeat(63)+"\t0001020304050607\t")).findFirst().orElseThrow();String[] p=row.split("\t");Provider provider=new Provider("OneZipNoCloneLongTest","1.0","Test-only non-cloneable SHA-1"){};provider.put("MessageDigest.SHA-1",NoCloneSha1.class.getName());Security.insertProviderAt(provider,1);
        try{var keys=RarCrypto.derive(new RarCrypto.Info(false,HexFormat.of().parseHex(p[1]),null,0,null,false),"A".repeat(63).toCharArray(),null);assertEquals(p[2],HexFormat.of().formatHex(keys.aes()));assertEquals(p[3],HexFormat.of().formatHex(keys.iv()));}finally{Security.removeProvider(provider.getName());}
    }
    public static final class NoCloneSha1 extends MessageDigestSpi {
        private final MessageDigest delegate;
        public NoCloneSha1(){try{delegate=MessageDigest.getInstance("SHA-1","SUN");}catch(GeneralSecurityException e){throw new ProviderException(e);}}
        protected void engineUpdate(byte b){delegate.update(b);}
        protected void engineUpdate(byte[] b,int o,int n){delegate.update(b,o,n);}
        protected byte[] engineDigest(){return delegate.digest();}
        protected void engineReset(){delegate.reset();}
    }
}
