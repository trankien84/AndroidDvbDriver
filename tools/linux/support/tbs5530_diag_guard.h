/* SPDX-License-Identifier: GPL-2.0-only */
#ifndef TBS5530_DIAG_GUARD_H
#define TBS5530_DIAG_GUARD_H

static const unsigned char tbs_diag_hash[32] = {
  0xb9,0x7c,0xf2,0x08,0xbb,0x58,0x6f,0x70,0x6b,0x26,0xaa,0x54,0x50,0xde,0x6b,0xb9,
  0x73,0x75,0xa3,0xe3,0xea,0xbe,0x83,0x3c,0xdd,0xb0,0xae,0x3a,0x4f,0x69,0xd0,0xdc
};

static int tbs_diag_gate(unsigned int vid, unsigned int pid, int enabled,
                        int bootstrap, unsigned long size,
                        const unsigned char *digest)
{
    unsigned int i, different = 0;
    if (vid != 0x734c || pid != 0x5530) return -ENODEV;
    if (!enabled || !bootstrap) return -EPERM;
    if (size != 8192 || !digest) return -EINVAL;
    for (i = 0; i < 32; i++) different |= digest[i] ^ tbs_diag_hash[i];
    return different ? -EKEYREJECTED : 0;
}

static int tbs_diag_profile(unsigned int system, unsigned int dvbt2,
                           unsigned int frequency, unsigned int bandwidth,
                           unsigned int plp)
{
    return system == dvbt2 && frequency == 594000000 && bandwidth == 8000000 &&
           plp == 0 ? 0 : -EINVAL;
}

static int tbs_diag_fw_run(unsigned int vid, unsigned int pid, int enabled,
                          int bootstrap, const unsigned char *image,
                          unsigned long size, const unsigned char *digest,
                          void *context,
                          int (*write)(void *, unsigned short,
                                       const unsigned char *, unsigned int))
{
    unsigned int step;
    unsigned char reset;
    int ret = tbs_diag_gate(vid, pid, enabled, bootstrap, size, digest);
    if (ret) return ret;
    if (!image || !write) return -EINVAL;
    for (step = 0; step < 132; step++) {
        unsigned short address;
        unsigned int length;
        const unsigned char *data;
        if (step < 2 || step >= 130) {
            reset = step < 2 ? 1 : 0;
            address = (step == 0 || step == 130) ? 0x7f92 : 0xe600;
            length = 1;
            data = &reset;
        } else {
            address = (step - 2) * 64;
            length = 64;
            data = image + address;
        }
        ret = write(context, address, data, length);
        if (ret != (int)length) return ret < 0 ? ret : -EIO;
    }
    return 0;
}
#endif
