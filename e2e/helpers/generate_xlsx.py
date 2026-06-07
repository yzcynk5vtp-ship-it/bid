#!/usr/bin/env python3
"""通过 openpyxl 生成 POI 兼容的 xlsx 文件。
读取 stdin JSON: { "rows": [[cell, cell, ...], ...] }
输出 stdout: xlsx 二进制。
"""
import json
import sys

import openpyxl


def main():
    payload = json.load(sys.stdin)
    rows = payload.get('rows') or []
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = '资质证书'
    for row in rows:
        ws.append(list(row))
    # 写 binary 到 stdout（buf）
    from io import BytesIO
    buf = BytesIO()
    wb.save(buf)
    sys.stdout.buffer.write(buf.getvalue())


if __name__ == '__main__':
    main()
