package vn.utbridora.auth.files;

import java.io.*;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Local private-volume adapter. Only generated keys are accepted; no file is mapped to a static URL. */
@Component
public class PrivateFileStorage implements FileStorage {
    private final String configuredRoot;
    public PrivateFileStorage(@Value("${app.files.storage-root:}") String configuredRoot) {this.configuredRoot=configuredRoot;}
    public static final class Staged implements FileStorage.Staged {
        private final Path path;
        private final long size;
        private final String hash;
        private boolean promoted;
        Staged(Path path,long size,String hash) {this.path=path;this.size=size;this.hash=hash;}
        public long size() {return size;}
        public String sha256() {return hash;}
        public byte[] prefix(int limit) throws IOException {try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) {return input.readNBytes(limit);}}
        @Override public void close() {if(!promoted) remove(path);}
    }
    private synchronized Path root() throws IOException {
        if(configuredRoot==null || configuredRoot.isBlank()) throw failure(503,"FILE_STORAGE_UNAVAILABLE");
        Path root=Path.of(configuredRoot);
        if(!root.isAbsolute() || root.normalize().getParent()==null) throw failure(503,"FILE_STORAGE_UNAVAILABLE");
        root=root.normalize();
        for(Path part=root;part!=null;part=part.getParent()) {
            if(Files.exists(part,LinkOption.NOFOLLOW_LINKS)) {
                var attrs=Files.readAttributes(part,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(attrs.isSymbolicLink() || !attrs.isDirectory()) throw failure(503,"FILE_STORAGE_UNAVAILABLE");
            }
        }
        Files.createDirectories(root);
        privatePermissions(root,true);
        return root;
    }
    public Staged stage(InputStream input,long limit) {
        Path temp=null;
        try {
            temp=Files.createTempFile(root(),"upload-",".part");privatePermissions(temp,false);
            MessageDigest hash=MessageDigest.getInstance("SHA-256");long size=0;
            try(var output=Files.newOutputStream(temp,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer=new byte[8192];int read;
                while((read=input.read(buffer))!=-1) {
                    size+=read;if(size>limit) throw failure(413,"FILE_TOO_LARGE");
                    hash.update(buffer,0,read);output.write(buffer,0,read);
                }
            }
            if(size==0) throw failure(422,"BUSINESS_VALIDATION");
            return new Staged(temp,size,HexFormat.of().formatHex(hash.digest()));
        } catch(IOException failure) {if(temp!=null) remove(temp);throw failure(503,"FILE_STORAGE_UNAVAILABLE");}
        catch(NoSuchAlgorithmException impossible) {if(temp!=null) remove(temp);throw new IllegalStateException(impossible);}
        catch(RuntimeException failure) {if(temp!=null) remove(temp);throw failure;}
    }
    public String promote(FileStorage.Staged input) {
        if(!(input instanceof Staged staged)) throw failure(503,"FILE_STORAGE_UNAVAILABLE");
        try {
            Path root=root();
            if(!staged.path.getParent().equals(root) || staged.promoted) throw failure(503,"FILE_STORAGE_UNAVAILABLE");
            String key=UUID.randomUUID()+".blob";Path target=root.resolve(key);
            Files.move(staged.path,target,StandardCopyOption.ATOMIC_MOVE);staged.promoted=true;
            return key;
        } catch(IOException error) {throw failure(503,"FILE_STORAGE_UNAVAILABLE");}
    }
    public SeekableByteChannel open(String key,long size,String sha256) {
        SeekableByteChannel channel=null;
        try {
            Path path=path(key);
            var attrs=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.size()!=size) throw failure(503,"FILE_STORAGE_UNAVAILABLE");
            channel=Files.newByteChannel(path,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS));
            MessageDigest hash=MessageDigest.getInstance("SHA-256");var buffer=java.nio.ByteBuffer.allocate(8192);
            long read=0;int count;
            while((count=channel.read(buffer))!=-1) {read+=count;if(read>size) throw failure(503,"FILE_STORAGE_UNAVAILABLE");buffer.flip();hash.update(buffer);buffer.clear();}
            if(read!=size || !HexFormat.of().formatHex(hash.digest()).equals(sha256)) throw failure(503,"FILE_STORAGE_UNAVAILABLE");
            channel.position(0);return channel;
        } catch(IOException error) {close(channel);throw failure(503,"FILE_STORAGE_UNAVAILABLE");}
        catch(NoSuchAlgorithmException impossible) {close(channel);throw new IllegalStateException(impossible);}
        catch(RuntimeException failure) {close(channel);throw failure;}
    }
    public void delete(String key) {
        try {remove(path(key));} catch(RuntimeException | IOException failure) {
            org.slf4j.LoggerFactory.getLogger(PrivateFileStorage.class).warn("Private file cleanup failed; object remains inaccessible without committed metadata");
        }
    }
    private Path path(String key) throws IOException {
        if(key==null || !key.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.blob")) throw failure(503,"FILE_STORAGE_UNAVAILABLE");
        return root().resolve(key);
    }
    private static void privatePermissions(Path path,boolean directory) throws IOException {
        if(Files.getFileAttributeView(path,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)!=null)
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));
    }
    private static void remove(Path path) {
        try {Files.deleteIfExists(path);} catch(IOException ignored) {
            org.slf4j.LoggerFactory.getLogger(PrivateFileStorage.class).warn("Private temporary file cleanup failed");
        }
    }
    static void close(SeekableByteChannel channel) {if(channel!=null) try {channel.close();} catch(IOException ignored) {}}
}
