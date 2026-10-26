#pragma once

// A BPE piece may contain only part of a UTF-8 character. Checking lead bytes also
// blocks Arabic characters assembled across pieces, without excluding Devanagari.
inline bool uses_arabic_utf8_lead(const char *text) noexcept {
    const auto *bytes=reinterpret_cast<const unsigned char *>(text);
    for(;*bytes;++bytes) if(*bytes>=0xD8 && *bytes<=0xDF) return true;
    return false;
}
