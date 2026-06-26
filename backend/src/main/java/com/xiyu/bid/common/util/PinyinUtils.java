// Utility: convert Chinese characters to pinyin for search indexing
// Pos: common/util - shared utility, no framework dependency beyond pinyin4j
package com.xiyu.bid.common.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.Locale;

/**
 * Utility for converting Chinese characters (Hanzi) to Hanyu Pinyin.
 * <p>
 * Used primarily to populate {@code users.full_name_pinyin} on persist/update,
 * enabling the user search API to match by pinyin in addition to the raw name.
 * <p>
 * Non-Chinese characters (letters, digits, punctuation) are preserved as-is.
 */
public final class PinyinUtils {

    private static final HanyuPinyinOutputFormat FORMAT;

    static {
        FORMAT = new HanyuPinyinOutputFormat();
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private PinyinUtils() {
    }

    /**
     * Converts the Chinese characters in {@code input} to lowercase pinyin
     * without tone marks, and appends a space-delimited pinyin-initials token
     * so that {@code LIKE '%zrr%'} matches "郑蓉蓉" via the initials "zrr".
     * <p>
     * The returned string has the form {@code "fullpinyin initials"} when the
     * input contains at least one Han character; non-Chinese inputs are
     * returned as-is (no initials token).
     * <p>
     * Examples:
     * <pre>
     *   toPinyin("张三")       → "zhangsan zs"
     *   toPinyin("郑蓉蓉")     → "zhengrongrong zrr"
     *   toPinyin("欧阳小明")   → "ouyangxiaoming oyxm"
     *   toPinyin("张三123")    → "zhangsan123 zs"
     *   toPinyin("hello")     → "hello"
     *   toPinyin(null)        → ""
     *   toPinyin("")          → ""
     * </pre>
     *
     * @param input the source string, may be null
     * @return the pinyin-converted string, never null
     */
    public static String toPinyin(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder full = new StringBuilder(input.length() * 2);
        StringBuilder initials = new StringBuilder();
        boolean hasHan = false;
        char[] chars = input.trim().toCharArray();
        for (char ch : chars) {
            if (Character.toString(ch).matches("\\p{Script=Han}")) {
                hasHan = true;
                try {
                    String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(ch, FORMAT);
                    if (pinyins != null && pinyins.length > 0) {
                        full.append(pinyins[0]);
                        initials.append(pinyins[0].charAt(0));
                    } else {
                        full.append(ch);
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    full.append(ch);
                }
            } else {
                full.append(ch);
            }
        }
        String result = full.toString().toLowerCase(Locale.ROOT);
        if (hasHan) {
            result = result + " " + initials.toString().toLowerCase(Locale.ROOT);
        }
        return result;
    }
}