package app.zapretvtb;

import android.os.ParcelFileDescriptor;
import android.system.Int64Ref;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileDescriptor;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.Socket;

/** Root-free low-level tricks: per-socket TTL and the sendfile "fake then retransmit real" trick. */
final class Raw {
    static final int IP_TTL = 2;

    static ParcelFileDescriptor dup(Socket s) { try { return ParcelFileDescriptor.fromSocket(s); } catch (Exception e) { return null; } }
    static ParcelFileDescriptor dup(DatagramSocket s) { try { return ParcelFileDescriptor.fromDatagramSocket(s); } catch (Exception e) { return null; } }

    static void ttl(FileDescriptor fd, int t) { try { Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, IP_TTL, t); } catch (Exception ignored) { } }

    static int ttl(FileDescriptor fd) {
        try {
            Method m = Os.class.getMethod("getsockoptInt", FileDescriptor.class, int.class, int.class);
            Object res = m.invoke(null, fd, OsConstants.IPPROTO_IP, IP_TTL);
            return res != null ? (int) res : 64;
        } catch (Exception e) {
            return 64;
        }
    }

    /**
     * Sends `fake` (L bytes) with a low TTL straight from the page cache, then overwrites those pages with the
     * real bytes: the fake never reaches the server, and the kernel retransmits the real data for that range.
     * Returns the number of stream bytes consumed (0 on failure).
     */
    static int fakeSend(FileDescriptor sock, File dir, byte[] fake, byte[] real, int len, int ttl, int orig) {
        File f = null;
        long sent = 0;
        try {
            f = File.createTempFile("fk", null, dir);
            try (RandomAccessFile r = new RandomAccessFile(f, "rw")) {
                r.write(fake, 0, len);
                ttl(sock, ttl);
                Int64Ref off = new Int64Ref(0);
                while (sent < len) {
                    long n = Os.sendfile(sock, r.getFD(), off, len - sent);
                    if (n <= 0) break;
                    sent += n;
                }
                Thread.sleep(4);
                ttl(sock, orig);
                r.seek(0);
                r.write(real, 0, len);
            }
        } catch (Exception e) {
            ttl(sock, orig);
        } finally {
            if (f != null) f.delete();
        }
        return (int) sent;
    }
}
