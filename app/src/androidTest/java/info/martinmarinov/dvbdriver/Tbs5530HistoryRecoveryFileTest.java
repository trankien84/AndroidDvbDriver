// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import android.system.Os;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Unique cache fixtures only; never reads or writes actual firmware history/preferences. */
@RunWith(AndroidJUnit4.class)
public class Tbs5530HistoryRecoveryFileTest {
    private static File directory() {
        File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
                "history-recovery-fixture-" + UUID.randomUUID());
        assertTrue(directory.mkdir()); return directory;
    }
    private static byte[] read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int)file.length()]; int at=0,count;
            while(at<bytes.length&&(count=input.read(bytes,at,bytes.length-at))>0)at+=count;
            assertEquals(bytes.length,at); return bytes;
        }
    }
    @Test public void appendPreservesPrefixAndChecksAuthorityBeforeWritingAnySuffix() throws Exception {
        File directory=directory(),base=new File(directory,"fixture.txt");
        byte[] prefix="EXPLICIT_SYNTHETIC_PREFIX\n".getBytes(StandardCharsets.UTF_8);
        byte[] suffix="EXPLICIT_SYNTHETIC_APPEND\n".getBytes(StandardCharsets.UTF_8);
        try {
            try(FileOutputStream out=new FileOutputStream(base)){out.write(prefix);out.getFD().sync();}
            assertThrows(IOException.class,()->Tbs5530HistoryRecoveryStore.appendExisting(base,prefix.length,suffix,
                    ()->{throw new IOException("Synthetic consent loss");}));
            assertArrayEquals(prefix,read(base));
            Tbs5530HistoryRecoveryStore.appendExisting(base,prefix.length,suffix,()->{});
            byte[] expected=new byte[prefix.length+suffix.length];System.arraycopy(prefix,0,expected,0,prefix.length);
            System.arraycopy(suffix,0,expected,prefix.length,suffix.length);assertArrayEquals(expected,read(base));
            assertThrows(IOException.class,()->Tbs5530HistoryRecoveryStore.appendExisting(base,prefix.length,suffix,()->{}));
            assertArrayEquals(expected,read(base));
        } finally { assertTrue(base.delete());assertTrue(directory.delete()); }
    }
    @Test public void missingFileAndSymlinkNeverCreateOrOverwriteTheStore() throws Exception {
        File directory=directory(),missing=new File(directory,"missing.txt"),target=new File(directory,"target.txt"),link=new File(directory,"link.txt");
        byte[] prefix="SYNTHETIC_ONLY\n".getBytes(StandardCharsets.UTF_8);
        try {
            assertThrows(IOException.class,()->Tbs5530HistoryRecoveryStore.appendExisting(missing,0,prefix,()->{}));assertFalse(missing.exists());
            try(FileOutputStream out=new FileOutputStream(target)){out.write(prefix);out.getFD().sync();}
            Os.symlink(target.getPath(),link.getPath());
            assertThrows(IOException.class,()->Tbs5530HistoryRecoveryStore.appendExisting(link,prefix.length,prefix,()->{}));
            assertArrayEquals(prefix,read(target));
        } finally {if(link.exists())assertTrue(link.delete());if(target.exists())assertTrue(target.delete());assertTrue(directory.delete());}
    }
}
