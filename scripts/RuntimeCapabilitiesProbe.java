import cn.shed4329.onezip.engine.ArchiveEngine;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;

/** Headless checks against the shipped runtime, not the developer JDK. */
public final class RuntimeCapabilitiesProbe {
    public static void main(String[] args)throws Exception{
        if(!ArchiveEngine.class.getModule().isNamed())throw new AssertionError("Engine not loaded from named module");
        for(String name:List.of("GB18030","Big5","Shift_JIS","IBM437")){
            Charset charset=Charset.forName(name);
            String sample=name.equals("IBM437")?"café":name.equals("Shift_JIS")?"日本語":"中文";
            if(!sample.equals(new String(sample.getBytes(charset),charset)))throw new AssertionError("Charset roundtrip "+name);
            System.out.println("PASS runtime charset "+name);
        }
        for(String algorithm:List.of("AES/ECB/NoPadding","AES/CBC/NoPadding"))Cipher.getInstance(algorithm);
        for(String algorithm:List.of("HmacSHA1","HmacSHA256"))Mac.getInstance(algorithm);
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        for(String algorithm:List.of("SHA-1","SHA-256","MD5"))MessageDigest.getInstance(algorithm);
        new SecureRandom().nextBytes(new byte[32]);
        System.out.println("PASS runtime crypto providers");
        var engine=new ArchiveEngine();
        engine.test(Path.of("tests/fixtures/referenceArchive-7.47-aes.7z"),"onezip-fixture".toCharArray(),null,null);
        System.out.println("PASS external AES 7z");
        Set<String> selected=Set.of("crypto-test-rar4-solid-headers.rar","crypto-test-rar4-long-password.rar","crypto-test-rar4-ppm-data.rar","crypto-test-rar5-solid-data.rar","crypto-test-rar5-solid-headers.rar");
        Map<String,String> fixtures=new LinkedHashMap<>();
        for(String line:Files.readAllLines(Path.of("tests/fixtures/rar/new-expected.tsv"))){
            String[] columns=line.split("\t",-1);
            if(selected.contains(columns[0]))fixtures.put(columns[0],columns[1]);
        }
        if(fixtures.size()!=selected.size())throw new AssertionError("Missing selected RAR fixtures");
        for(var fixture:fixtures.entrySet()){
            char[] password=fixture.getValue().toCharArray();
            try{engine.test(Path.of("tests/fixtures/rar").resolve(fixture.getKey()),password,null,null);}
            finally{Arrays.fill(password,'\0');}
            System.out.println("PASS runtime "+fixture.getKey());
        }
    }
}
