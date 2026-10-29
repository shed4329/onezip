"""TEST ONLY: encrypt existing compressed payloads; never implements RAR compression.
Uses independent Python hashlib and PyCryptodome AES. Production has no dependency on this.
Generated archives must also pass ReferenceArchive extraction and independent SHA comparison.
"""
from pathlib import Path
import hashlib,hmac,struct,binascii,subprocess,os
from Crypto.Cipher import AES

ROOT=Path(__file__).resolve().parent.parent/'tests/fixtures/rar'
def vint(n):
    b=bytearray()
    while n>=128:b.append((n&127)|128);n>>=7
    b.append(n);return bytes(b)
def number(b,p):
    n=0;s=0
    while True:
        c=b[p];p+=1;n|=(c&127)<<s;s+=7
        if c<128:return n,p
def seed(label,n):return hashlib.sha256(label.encode()).digest()[:n]
def pad(b):return b+bytes((-len(b))%16)
def encrypt(b,key,iv):return AES.new(key,AES.MODE_CBC,iv).encrypt(pad(b))
def keys5(password,salt,count=1024):
    p=password.encode('utf-8');key=hashlib.pbkdf2_hmac('sha256',p,salt,count);hk=hashlib.pbkdf2_hmac('sha256',p,salt,count+16);v=hashlib.pbkdf2_hmac('sha256',p,salt,count+32)
    check=bytes(v[i]^v[i+8]^v[i+16]^v[i+24] for i in range(8));return key,hk,check
def keys4(password,salt):
    # Short-password standard KDF. Long-password fixture uses the explicit
    # historical schedule side effect, independently written below.
    raw=bytearray(password.encode('utf-16le')+salt)
    if len(raw)>64 and os.environ.get('RAR_FIXTURE_JAVA_CP'):
        # Optional faster assembler path, independently accepted by ReferenceArchive.
        rows=subprocess.check_output(['JAVA_HOME/bin/java.exe','-cp',os.environ['RAR_FIXTURE_JAVA_CP'],'cn.shed4329.onezip.engine.RarFixtureKeys',password,salt.hex()],text=True).splitlines();return bytes.fromhex(rows[0]),bytes.fromhex(rows[1])
    h=hashlib.sha1() if len(raw)<=64 else LegacySha();iv=bytearray()
    for i in range(0x40000):
        if len(raw)<=64:h.update(raw);h.update(i.to_bytes(3,'little'))
        else:h.update(raw,True);h.update(bytearray(i.to_bytes(3,'little')),False)
        if i%16384==0:iv.append(h.digest()[-1])
    d=h.digest();return b''.join(d[i:i+4][::-1] for i in range(0,16,4)),bytes(iv)
def rol(n,k):return ((n<<k)|(n>>(32-k)))&0xffffffff
class LegacySha:
    def __init__(self):self.h=[0x67452301,0xefcdab89,0x98badcfe,0x10325476,0xc3d2e1f0];self.buf=bytearray();self.count=0
    def block(self,b):
        w=list(struct.unpack('>16I',b));a,c,d,e,f=self.h
        for i in range(80):
            if i>=16:w[i%16]=rol(w[(i-3)%16]^w[(i-8)%16]^w[(i-14)%16]^w[i%16],1)
            mix=(c&d)|(~c&e) if i<20 else c^d^e if i<40 else (c&d)|(c&e)|(d&e) if i<60 else c^d^e
            k=0x5a827999 if i<20 else 0x6ed9eba1 if i<40 else 0x8f1bbcdc if i<60 else 0xca62c1d6
            a,c,d,e,f=(rol(a,5)+mix+f+k+w[i%16])&0xffffffff,a,rol(c,30),d,e
        self.h=[(x+y)&0xffffffff for x,y in zip(self.h,[a,c,d,e,f])];return struct.pack('<16I',*w)
    def update(self,b,legacy):
        self.count+=len(b);p=0
        if len(self.buf)+len(b)>=64:
            n=64-len(self.buf);self.buf.extend(b[:n]);self.block(self.buf);self.buf.clear();p=n
            while p+64<=len(b):
                changed=self.block(b[p:p+64])
                if legacy:b[p:p+64]=changed
                p+=64
        self.buf.extend(b[p:])
    def digest(self):
        import copy
        h=copy.deepcopy(self);n=self.count*8;b=bytearray(b'\x80'+bytes((55-len(self.buf))%64)+struct.pack('>Q',n));h.update(b,False);return struct.pack('>5I',*h.h)
def header5(body):
    data=vint(len(body))+body;return struct.pack('<I',binascii.crc32(data))+data
def make4(source,dest,password,headers=False):
    b=(ROOT/source).read_bytes();out=bytearray(b[:7]);p=7;serial=0
    while p<len(b):
        _,typ,flags,n=struct.unpack_from('<HBHH',b,p);packed=struct.unpack_from('<I',b,p+7)[0] if flags&32768 else 0;h=bytearray(b[p:p+n]);data=b[p+n:p+n+packed];p+=n+packed
        if typ==0x73 and headers:struct.pack_into('<H',h,3,flags|128)
        if typ==0x74 and flags&224!=224:
            salt=seed(dest+str(serial)+'data',8);key,iv=keys4(password,salt);data=encrypt(data,key,iv);name=struct.unpack_from('<H',h,26)[0];at=32+(8 if flags&256 else 0)+name;h[at:at]=salt;struct.pack_into('<H',h,3,flags|4|1024);struct.pack_into('<H',h,5,len(h));struct.pack_into('<I',h,7,len(data))
        struct.pack_into('<H',h,0,binascii.crc32(h[2:])&65535)
        if headers and typ!=0x73:
            salt=seed(dest+str(serial)+'header',8);key,iv=keys4(password,salt);out.extend(salt+encrypt(bytes(h),key,iv))
        else:out.extend(h)
        out.extend(data);serial+=1
    (ROOT/dest).write_bytes(out)
def make5(source,dest,password,headers=False):
    b=(ROOT/source).read_bytes();out=bytearray(b[:8]);p=8;serial=0;headerkey=None
    if headers:
        salt=seed(dest+'salt',16);headerkey,_,check=keys5(password,salt);out.extend(header5(vint(4)+vint(0)+vint(0)+vint(1)+b'\x0a'+salt+check+hashlib.sha256(check).digest()[:4]))
    while p<len(b):
        size,q=number(b,p+4);h=b[q:q+size];p=q+size;typ,q=number(h,0);flags,q=number(h,q);extra,q=number(h,q) if flags&1 else (0,q);packed,q=number(h,q) if flags&2 else (0,q);data=b[p:p+packed];p+=packed
        if typ==2:
            ff,z=number(h,q);_,z=number(h,z);_,z=number(h,z)
            if not ff&1:
                if ff&2:z+=4
                crcpos=z if ff&4 else None
                prefix=bytearray(h[q:len(h)-extra if extra else len(h)]);oldextra=h[len(h)-extra:] if extra else b''
                salt=seed(dest+str(serial)+'salt',16);iv=seed(dest+str(serial)+'iv',16);key,hk,check=keys5(password,salt);data=encrypt(data,key,iv)
                if crcpos is not None:
                    mac=hmac.digest(hk,h[crcpos:crcpos+4],'sha256');value=bytes(mac[i]^mac[i+4]^mac[i+8]^mac[i+12]^mac[i+16]^mac[i+20]^mac[i+24]^mac[i+28] for i in range(4));prefix[crcpos-q:crcpos-q+4]=value
                new=vint(1)+vint(0)+vint(3)+b'\x0a'+salt+iv+check+hashlib.sha256(check).digest()[:4];new=vint(len(new))+new+oldextra
                h=vint(typ)+vint(flags|3)+vint(len(new))+vint(len(data))+prefix+new
        raw=header5(h)
        if headers:
            iv=seed(dest+str(serial)+'headiv',16);out.extend(iv+encrypt(raw,headerkey,iv))
        else:out.extend(raw)
        out.extend(data);serial+=1
    (ROOT/dest).write_bytes(out)
if __name__=='__main__':
    make4('test_read_format_rar_compress_best.safe.rar','crypto-test-rar4-ppm-data.rar','rar-secret')
    make4('junrar-solid-rar4-solid.rar','crypto-test-rar4-solid-headers.rar','пароль密码ü',True)
    make4('test_read_format_rar_compress_best.safe.rar','crypto-test-rar4-long-password.rar','Long-RAR4-password-0123456789-ABCDEFGHIJKLMN')
    make5('junrar-rar5unpack-m3-solid-128k.rar','crypto-test-rar5-solid-data.rar','rar-secret')
    make5('junrar-rar5unpack-m3-solid-128k.rar','crypto-test-rar5-solid-headers.rar','пароль密码ü',True)
