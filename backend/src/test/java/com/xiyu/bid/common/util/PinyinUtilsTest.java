package com.xiyu.bid.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PinyinUtilsTest {

    @Test
    void toPinyin_AppendsInitialsTokenForChineseNames() {
        // 核心修复：全拼 + 空格 + 首字母缩写，让搜 zrr 能命中郑蓉蓉
        assertThat(PinyinUtils.toPinyin("郑蓉蓉")).isEqualTo("zhengrongrong zrr");
        assertThat(PinyinUtils.toPinyin("张三")).isEqualTo("zhangsan zs");
        assertThat(PinyinUtils.toPinyin("欧阳小明")).isEqualTo("ouyangxiaoming oyxm");
    }

    @Test
    void toPinyin_FullPinyinSubstringStillMatches() {
        // 旧用法不回归：搜全拼片段仍能 LIKE 命中
        String pinyin = PinyinUtils.toPinyin("张三");
        assertThat(pinyin).contains("zhangsan");
        assertThat(pinyin).contains("zs");
    }

    @Test
    void toPinyin_NoInitialsForNonChineseInput() {
        // 纯非中文输入不附加首字母缩写（避免 "hello h" 这种无意义输出）
        assertThat(PinyinUtils.toPinyin("hello")).isEqualTo("hello");
        assertThat(PinyinUtils.toPinyin("06234")).isEqualTo("06234");
    }

    @Test
    void toPinyin_MixedChineseAndDigits() {
        // 汉字+数字：全拼含数字，首字母仍只取汉字
        assertThat(PinyinUtils.toPinyin("张三123")).isEqualTo("zhangsan123 zs");
    }

    @Test
    void toPinyin_NullAndEmpty() {
        assertThat(PinyinUtils.toPinyin(null)).isEqualTo("");
        assertThat(PinyinUtils.toPinyin("")).isEqualTo("");
    }
}
