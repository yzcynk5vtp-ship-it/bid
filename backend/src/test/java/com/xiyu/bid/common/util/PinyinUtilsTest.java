// Test: PinyinUtils for Chinese → pinyin conversion
// Pos: Test/common/util — unit tests for pinyin conversion utility
package com.xiyu.bid.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PinyinUtils — Chinese character to pinyin conversion")
class PinyinUtilsTest {

    @Test
    @DisplayName("null input returns empty string")
    void nullInput_ReturnsEmpty() {
        assertThat(PinyinUtils.toPinyin(null)).isEmpty();
    }

    @Test
    @DisplayName("empty input returns empty string")
    void emptyInput_ReturnsEmpty() {
        assertThat(PinyinUtils.toPinyin("")).isEmpty();
    }

    @Test
    @DisplayName("whitespace input returns empty string after trim")
    void whitespaceInput_ReturnsEmpty() {
        assertThat(PinyinUtils.toPinyin("   ")).isEmpty();
    }

    @ParameterizedTest(name = "toPinyin(\"{0}\") → \"{1}\"")
    @CsvSource({
        "张三,      zhangsan",
        "李四,      lisi",
        "王五,      wangwu",
        "欧阳小明,  ouyangxiaoming",
        "hello,    hello",
        "张三123,   zhangsan123",
        "ABC,      abc",
        "Test,     test",
    })
    @DisplayName("converts Chinese characters to lowercase pinyin without tones")
    void convertsChineseToPinyin(String input, String expected) {
        assertThat(PinyinUtils.toPinyin(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("mixed Chinese and non-Chinese preserves non-Chinese characters")
    void mixedChineseAndAscii() {
        String result = PinyinUtils.toPinyin("张经理@company");
        // "张" → "zhang", "经" → "jing", "理" → "li", non-Chinese preserved
        assertThat(result).contains("zhang");
        assertThat(result).contains("@company");
        assertThat(result).contains("jing");
        assertThat(result).contains("li");
    }
}