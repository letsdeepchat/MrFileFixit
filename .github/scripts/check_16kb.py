"""Fails if a 64-bit native library in the APK is not ready for 16 KB memory pages.

Each arm64-v8a and x86_64 .so must have every PT_LOAD segment aligned to at least 16 KB and, when stored
uncompressed, start at a 16 KB offset inside the zip. 32-bit ABIs are exempt (Android only uses 16 KB pages
with 64-bit code).
"""
import io
import sys
import zipfile

from elftools.elf.elffile import ELFFile

PAGE = 16 * 1024
CHECKED_ABIS = ("lib/arm64-v8a/", "lib/x86_64/")


def data_offset(apk, info):
    apk.fp.seek(info.header_offset)
    header = apk.fp.read(30)
    name_len = int.from_bytes(header[26:28], "little")
    extra_len = int.from_bytes(header[28:30], "little")
    return info.header_offset + 30 + name_len + extra_len


def main(path):
    failures = []
    checked = 0
    with zipfile.ZipFile(path) as apk:
        for info in apk.infolist():
            if not info.filename.endswith(".so") or not info.filename.startswith(CHECKED_ABIS):
                continue
            checked += 1
            elf = ELFFile(io.BytesIO(apk.read(info)))
            align = min(s["p_align"] for s in elf.iter_segments() if s["p_type"] == "PT_LOAD")
            stored = info.compress_type == zipfile.ZIP_STORED
            offset_ok = not stored or data_offset(apk, info) % PAGE == 0
            status = "ok" if align >= PAGE and offset_ok else "FAIL"
            print(f"{status:4} {info.filename} p_align={align} {'stored' if stored else 'deflated'} zip16k={offset_ok}")
            if status != "ok":
                failures.append(info.filename)
    if checked == 0:
        print("No 64-bit native libraries found; nothing to check.")
    if failures:
        print(f"{len(failures)} library(ies) are not 16 KB aligned", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main(sys.argv[1])
