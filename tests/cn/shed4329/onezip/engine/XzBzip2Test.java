package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.*;
import org.apache.commons.compress.compressors.bzip2.*;
import static org.junit.jupiter.api.Assertions.*;

class XzBzip2Test {
    @TempDir Path temp;
    private static byte[] data(int n){byte[] b=new byte[n];new Random(7843).nextBytes(b);for(int i=n/3;i<n*2/3;i++)b[i]=(byte)(i%53);return b;}
    private static byte[] encode(byte[] b,boolean xz,int level)throws Exception{
        ByteArrayOutputStream raw=new ByteArrayOutputStream();try(OutputStream out=xz?new XzOutputStream(raw,level,b.length):new Bzip2OutputStream(raw,level,b.length)){
            for(int i=0;i<b.length;){int n=Math.min(8191,b.length-i);out.write(b,i,n);i+=n;}
        }return raw.toByteArray();
    }
    private static byte[] decode(byte[] b,boolean xz)throws Exception{try(InputStream in=xz?new XzInputStream(new ByteArrayInputStream(b)):new Bzip2InputStream(new ByteArrayInputStream(b))){return in.readAllBytes();}}
    private static byte[] external(byte[] b,boolean xz,int level)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(OutputStream encoded=xz?new XZOutputStream(out,new LZMA2Options(Math.min(9,level))):new BZip2CompressorOutputStream(out,Math.max(1,level))){encoded.write(b);}return out.toByteArray();}
    @Test void ownStreamsInteroperateAtEmptyTinyRunAndBlockBoundaries()throws Exception{
        for(boolean xz:new boolean[]{true,false})for(int n:new int[]{0,1,3,4,5,258,259,260,1024,100001,310000}){
            byte[] plain=data(n);if(n<1024)Arrays.fill(plain,(byte)65);byte[] encoded=encode(plain,xz,1);assertArrayEquals(plain,decode(encoded,xz));
            try(InputStream oracle=xz?new XZInputStream(new ByteArrayInputStream(encoded)):new BZip2CompressorInputStream(new ByteArrayInputStream(encoded))){assertArrayEquals(plain,oracle.readAllBytes());}
            assertArrayEquals(plain,decode(external(plain,xz,1),xz));
        }
        for(boolean xz:new boolean[]{true,false})for(int level:new int[]{0,3,5,9}){
            byte[] plain=data(950123),encoded=encode(plain,xz,level);try(InputStream oracle=xz?new XZInputStream(new ByteArrayInputStream(encoded)):new BZip2CompressorInputStream(new ByteArrayInputStream(encoded))){assertArrayEquals(plain,oracle.readAllBytes());}assertArrayEquals(plain,decode(encoded,xz));
        }
        byte[] runs=new byte[2000000];Arrays.fill(runs,(byte)'a');byte[] bzip=encode(runs,false,1);assertTrue(bzip.length<1000);assertArrayEquals(runs,decode(bzip,false));try(var oracle=new BZip2CompressorInputStream(new ByteArrayInputStream(bzip))){assertArrayEquals(runs,oracle.readAllBytes());}
    }
    @Test void xzReadsAllStandardChecksAndMultipleBlocksWithDeltaAndX86()throws Exception{
        byte[] plain=data(180000);for(int i=0;i<plain.length-8;i+=16){plain[i]=(byte)0xe8;plain[i+1]=0x12;plain[i+2]=0x34;plain[i+3]=0x56;plain[i+4]=0;}
        for(int check:new int[]{XZ.CHECK_NONE,XZ.CHECK_CRC32,XZ.CHECK_CRC64,XZ.CHECK_SHA256})for(int filter=0;filter<3;filter++){
            LZMA2Options lzma=new LZMA2Options(1);FilterOptions[] filters;
            if(filter==1){DeltaOptions delta=new DeltaOptions();delta.setDistance(7);filters=new FilterOptions[]{delta,lzma};}
            else if(filter==2){X86Options x86=new X86Options();x86.setStartOffset(8);filters=new FilterOptions[]{x86,lzma};}
            else filters=new FilterOptions[]{lzma};
            ByteArrayOutputStream out=new ByteArrayOutputStream();try(var encoder=new XZOutputStream(out,filters,check)){encoder.write(plain,0,70000);encoder.endBlock();encoder.write(plain,70000,plain.length-70000);}assertArrayEquals(plain,decode(out.toByteArray(),true),check+" / "+filter);
        }
    }
    @Test void xzOptionalBlockSizesAndSlidingDictionaryAreChecked()throws Exception{
        byte[] plain=data(1000);byte[] sized=sizedXz(plain,0,0);assertArrayEquals(plain,decode(sized,true));try(var oracle=new XZInputStream(new ByteArrayInputStream(sized))){assertArrayEquals(plain,oracle.readAllBytes());}
        for(int[] deltas:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})assertThrows(IOException.class,()->decode(sizedXz(plain,deltas[0],deltas[1]),true));
        byte[] large=data(2*1024*1024+65537),encoded=encode(large,true,1);assertArrayEquals(large,decode(encoded,true));try(var oracle=new XZInputStream(new ByteArrayInputStream(encoded))){assertArrayEquals(large,oracle.readAllBytes());}
    }
    private static byte[] sizedXz(byte[] plain,int packedDelta,int unpackedDelta)throws Exception{
        byte[] encoded=encode(plain,true,1);int indexStart=encoded.length-12-(int)((XzSupport.bits(encoded,encoded.length-8,4)+1)*4);ByteArrayInputStream indexFields=new ByteArrayInputStream(encoded,indexStart+2,encoded.length-indexStart-2);long packed=XzSupport.vli(indexFields)-20;
        ByteArrayOutputStream header=new ByteArrayOutputStream();header.write(0);header.write(0xc0);XzSupport.vli(header,packed+packedDelta);XzSupport.vli(header,plain.length+unpackedDelta);header.write(new byte[]{0x21,1,18});while(header.size()%4!=0)header.write(0);byte[] h=header.toByteArray();h[0]=(byte)(h.length/4);
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(encoded,0,12);checked(out,h);out.write(encoded,24,indexStart-24);
        ByteArrayOutputStream idx=new ByteArrayOutputStream();idx.write(0);XzSupport.vli(idx,1);XzSupport.vli(idx,h.length+4+packed+8);XzSupport.vli(idx,plain.length);while(idx.size()%4!=0)idx.write(0);checked(out,idx.toByteArray());
        ByteArrayOutputStream footer=new ByteArrayOutputStream();XzSupport.little(footer,idx.size()/4,4);footer.write(0);footer.write(4);byte[] f=footer.toByteArray();java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(f);XzSupport.little(out,crc.getValue(),4);out.write(f);out.write('Y');out.write('Z');return out.toByteArray();
    }
    private static void checked(OutputStream out,byte[] b)throws IOException{out.write(b);java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(b);XzSupport.little(out,crc.getValue(),4);}
    @Test void concatenatedStreamsAndXzAlignedPaddingAreHandled()throws Exception{
        for(boolean xz:new boolean[]{true,false}){
            ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(external(new byte[]{1,2,3},xz,1));if(xz)out.write(new byte[8]);out.write(encode(new byte[0],xz,5));out.write(encode(new byte[]{4,5},xz,9));if(xz)out.write(new byte[4]);assertArrayEquals(new byte[]{1,2,3,4,5},decode(out.toByteArray(),xz));
        }
        byte[] xz=encode(new byte[]{1},true,1);assertThrows(IOException.class,()->decode(Arrays.copyOf(xz,xz.length+1),true));
    }
    @Test void checksumsFootersFlagsAndTruncationAreNotSilentlyIgnored()throws Exception{
        for(boolean xz:new boolean[]{true,false}){
            byte[] encoded=encode(data(80000),xz,1);
            for(int n:new int[]{0,1,4,10,encoded.length/2,encoded.length-1,encoded.length-8})assertThrows(IOException.class,()->decode(Arrays.copyOf(encoded,n),xz));
            for(int offset:new int[]{0,3,10,encoded.length/2,encoded.length-2,encoded.length-7}){byte[] bad=encoded.clone();bad[offset]^=1;assertThrows(IOException.class,()->decode(bad,xz),xz+" / "+offset);}
            byte[] junk=Arrays.copyOf(encoded,encoded.length+1);junk[junk.length-1]=1;assertThrows(IOException.class,()->decode(junk,xz));
        }
        byte[] randomized=encode(new byte[]{1,2,3},false,1);randomized[14]|=(byte)128;assertTrue(assertThrows(IOException.class,()->decode(randomized,false)).getMessage().contains("randomized"));
        byte[] unknownCheck=encode(new byte[]{1,2,3},true,1);unknownCheck[7]=2;long crc=XzSupport.crc32(unknownCheck,6,2);for(int i=0;i<4;i++)unknownCheck[8+i]=(byte)(crc>>>(8*i));assertTrue(assertThrows(IOException.class,()->decode(unknownCheck,true)).getMessage().contains("check"));
        byte[] badFilter=encode(new byte[]{1,2,3},true,1);badFilter[14]=0x22;crc=XzSupport.crc32(badFilter,12,8);for(int i=0;i<4;i++)badFilter[20+i]=(byte)(crc>>>(8*i));assertTrue(assertThrows(IOException.class,()->decode(badFilter,true)).getMessage().contains("filter"));
        assertThrows(IOException.class,()->XzSupport.vli(new ByteArrayInputStream(new byte[]{(byte)128,0})));
        assertThrows(IOException.class,()->XzSupport.vli(new ByteArrayInputStream(new byte[]{-1,-1,-1,-1,-1,-1,-1,-1,-1})));
    }
    @Test void enginePreservesUnicodeAndEmptyDirectoriesAndRejectsMisuse()throws Exception{
        ArchiveEngine engine=new ArchiveEngine();byte[] plain=data(200000);Path input=Files.write(temp.resolve("input.bin"),plain),folder=Files.createDirectory(temp.resolve("資料"));Files.write(folder.resolve("內容.bin"),plain);Files.createDirectory(folder.resolve("empty"));
        for(var format:List.of(ArchiveEngine.Format.XZ,ArchiveEngine.Format.BZIP2,ArchiveEngine.Format.TAR_XZ,ArchiveEngine.Format.TAR_BZIP2)){
            String suffix=switch(format){case XZ->"xz";case BZIP2->"bz2";case TAR_XZ->"tar.xz";case TAR_BZIP2->"tar.bz2";default->throw new AssertionError();};
            Path archive=temp.resolve("input.bin."+suffix),source=ArchiveEngine.singleStreamFormat(format)?input:folder;engine.compress(List.of(source),archive,format,3,null,null,null,4);engine.test(archive,null,null,null);assertFalse(engine.list(archive,null,null).isEmpty());
            Path out=temp.resolve("out-"+suffix);engine.extract(archive,out,null,null,null,1);Path decoded=out.resolve(ArchiveEngine.singleStreamFormat(format)?"input.bin":"資料/內容.bin");assertArrayEquals(plain,Files.readAllBytes(decoded));if(ArchiveEngine.tarFormat(format))assertTrue(Files.isDirectory(out.resolve("資料/empty")));
            assertThrows(IOException.class,()->engine.extract(archive,out,null,null,null));assertArrayEquals(plain,Files.readAllBytes(decoded));
            Path secret=temp.resolve("secret-"+suffix);assertThrows(IOException.class,()->engine.compress(List.of(source),secret,format,5,"secret".toCharArray(),null,null));assertFalse(Files.exists(secret));
            AtomicBoolean stop=new AtomicBoolean();Path cancelled=temp.resolve("cancel-"+suffix);assertThrows(InterruptedIOException.class,()->engine.compress(List.of(source),cancelled,format,1,null,stop::get,(done,total,name)->{if(done>0)stop.set(true);},1));assertFalse(Files.exists(cancelled));
            Path corrupted=temp.resolve("broken-"+suffix+"."+suffix);byte[] bad=Files.readAllBytes(archive);bad[bad.length-5]^=1;Files.write(corrupted,bad);Path failOut=temp.resolve("broken-"+suffix);assertThrows(IOException.class,()->engine.extract(corrupted,failOut,null,null,null));assertFalse(Files.exists(failOut));
        }
        assertThrows(IOException.class,()->engine.compress(List.of(folder),temp.resolve("folder.xz"),ArchiveEngine.Format.XZ,1,null,null,null));
        assertThrows(IOException.class,()->engine.compress(List.of(folder),temp.resolve("folder.bz2"),ArchiveEngine.Format.BZIP2,1,null,null,null));
    }
    @Test void flushAndDeclaredSizeFailuresKeepStandardStreamSemantics()throws Exception{
        for(boolean xz:new boolean[]{true,false}){
            byte[] plain=data(10000);ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(OutputStream out=xz?new XzOutputStream(bytes,1,-1):new Bzip2OutputStream(bytes,1,-1)){out.write(plain,0,100);out.flush();out.write(plain,100,plain.length-100);}assertArrayEquals(plain,decode(bytes.toByteArray(),xz));
            OutputStream shortStream=xz?new XzOutputStream(new ByteArrayOutputStream(),1,2):new Bzip2OutputStream(new ByteArrayOutputStream(),1,2);shortStream.write(1);assertThrows(IOException.class,shortStream::close);
            OutputStream longStream=xz?new XzOutputStream(new ByteArrayOutputStream(),1,0):new Bzip2OutputStream(new ByteArrayOutputStream(),1,0);assertThrows(IOException.class,()->longStream.write(1));longStream.close();
        }
    }
}
