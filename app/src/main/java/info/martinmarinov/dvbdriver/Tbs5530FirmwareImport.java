// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import java.io.IOException;
import java.io.InputStream;

final class Tbs5530FirmwareImport {
    enum Result { SUCCESS, UNREADABLE, PERMISSION_DENIED, INVALID_OR_IO }

    interface Source {
        InputStream open() throws IOException;
    }

    interface Destination {
        void importFirmware(InputStream input) throws IOException;
    }

    static Result run(Source source, Destination destination) {
        final InputStream opened;
        try {
            opened = source.open();
        } catch (SecurityException denied) {
            return Result.PERMISSION_DENIED;
        } catch (IOException | RuntimeException unreadable) {
            return Result.UNREADABLE;
        }
        if (opened == null) return Result.UNREADABLE;
        try (InputStream input = opened) {
            destination.importFirmware(input);
            return Result.SUCCESS;
        } catch (SecurityException denied) {
            return Result.PERMISSION_DENIED;
        } catch (IOException | RuntimeException invalidOrIo) {
            return Result.INVALID_OR_IO;
        }
    }

    private Tbs5530FirmwareImport() {}
}
