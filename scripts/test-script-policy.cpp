#include "../engine/src/main/cpp/script_policy.h"
#include <cassert>
#include <iostream>
#include <regex>
#include <string>

static std::string utf8(int cp) {
    if(cp<128) return std::string(1,static_cast<char>(cp));
    if(cp<2048) return std::string{static_cast<char>(0xC0|(cp>>6)),static_cast<char>(0x80|(cp&63))};
    return std::string{static_cast<char>(0xE0|(cp>>12)),static_cast<char>(0x80|((cp>>6)&63)),static_cast<char>(0x80|(cp&63))};
}
int main() {
    const std::regex desktop_equivalent(R"([\s\S]*[\xD8-\xDF][\s\S]*)");
    for(int cp=1;cp<=0x097F;++cp) {
        auto piece=utf8(cp);
        bool expected=cp>=0x0600 && cp<=0x07FF;
        assert(uses_arabic_utf8_lead(piece.c_str())==expected);
        assert(std::regex_match(piece,desktop_equivalent)==expected);
    }
    assert(!uses_arabic_utf8_lead("English plus Roman Hindi 123"));
    assert(uses_arabic_utf8_lead("hello\n\xD8\xA8"));
    assert(uses_arabic_utf8_lead("\xD9")); // A split BPE lead byte must be excluded too.
    assert(!uses_arabic_utf8_lead("\xA4")); // A continuation byte shared with Hindi must remain.
    std::cout<<"2431 Unicode code points, mixed/split pieces and desktop-regex equivalence passed\n";
}
