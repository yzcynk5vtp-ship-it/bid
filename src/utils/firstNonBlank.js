// Input: candidate values that may include null/blank strings
// Output: first meaningful value, or empty string when none exists
// Pos: src/utils/ - shared text value helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
export function firstNonBlank(...values) {
  const found = values.find((value) => value != null && String(value).trim() !== '')
  return found ?? ''
}
