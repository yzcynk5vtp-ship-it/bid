#!/usr/bin/env python3
"""
一次性修复脚本：批量反查 CRM 商机 code，修复 tenders.crm_opportunity_id 存数字 id 的问题。
CO-277 接收侧根因修复的数据修复部分。
"""
import json
import subprocess
import sys
import time
import urllib.request
import urllib.parse

DB_HOST = "winbid-01.test.rds.ehsy.com"
DB_PORT = "3306"
DB_USER = "ea_bid"
DB_PASS = "ra(D7np+Z"
DB_NAME = "winbid"

OSS_BASE = "https://base-oss-test.ehsy.com"
CRM_BASE = "https://crm-api-java-test.ehsy.com"
OSS_USER = "03595"
OSS_PASS = "123456"
OSS_SYSTEM = "CRM"


def mysql_query(sql, fetch=True):
    """执行 MySQL 查询，返回结果行列表。"""
    cmd = ["mysql", "-h", DB_HOST, "-P", DB_PORT, "-u", DB_USER, f"-p{DB_PASS}", DB_NAME, "-N", "-e", sql]
    result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True, timeout=30)
    if result.returncode != 0:
        print(f"  MySQL error: {result.stderr}", file=sys.stderr)
        return []
    if not fetch:
        return []
    return [line for line in result.stdout.strip().split("\n") if line]


def mysql_exec(sql):
    """执行 MySQL 更新语句。"""
    cmd = ["mysql", "-h", DB_HOST, "-P", DB_PORT, "-u", DB_USER, f"-p{DB_PASS}", DB_NAME, "-e", sql]
    result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True, timeout=30)
    if result.returncode != 0:
        print(f"  MySQL error: {result.stderr}", file=sys.stderr)
        return False
    return True


def http_post(url, headers=None, data=None, form=False):
    """HTTP POST 请求。"""
    if form and data:
        data = urllib.parse.urlencode(data).encode()
    elif data is not None:
        data = json.dumps(data).encode() if isinstance(data, dict) else data.encode()
    req = urllib.request.Request(url, data=data, headers=headers or {}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode())
    except Exception as e:
        print(f"  HTTP error: {e}", file=sys.stderr)
        return None


def get_crm_token():
    """获取 CRM JWT token。"""
    print("=== Step 1: Acquire OSS token ===")
    oss_resp = http_post(f"{OSS_BASE}/oauth/login", headers={"Content-Type": "application/x-www-form-urlencoded"},
                         data=f"username={OSS_USER}&password={OSS_PASS}&system={OSS_SYSTEM}")
    if not oss_resp or str(oss_resp.get("code", "")) != "0":
        print(f"OSS login failed: {oss_resp}", file=sys.stderr)
        sys.exit(1)
    oss_token = oss_resp["data"]["access_token"]
    print(f"OSS_TOKEN={oss_token[:30]}...")

    print("=== Step 2: Exchange for CRM JWT token ===")
    crm_resp = http_post(f"{CRM_BASE}/common/inner/generateToken",
                         headers={"Content-Type": "application/json", "Authorization": f"Bearer {oss_token}"},
                         data={"nickName": OSS_USER, "salesNo": OSS_USER})
    if not crm_resp or str(crm_resp.get("code", "")) != "0":
        print(f"CRM generateToken failed: {crm_resp}", file=sys.stderr)
        sys.exit(1)
    crm_token = crm_resp["data"] if isinstance(crm_resp["data"], str) else crm_resp["data"].get("token", "")
    print(f"CRM_TOKEN={crm_token[:30]}...")
    return crm_token


def main():
    crm_token = get_crm_token()

    print("=== Step 3: Fetch all tenders with numeric crm_opportunity_id ===")
    rows = mysql_query("SELECT id, crm_opportunity_id FROM tenders WHERE crm_opportunity_id REGEXP '^[0-9]+$' ORDER BY id;")
    print(f"Total {len(rows)} tenders to fix")

    print("=== Step 4: Reverse-lookup code for each numeric id and update DB ===")
    success = fail = skip = 0
    for row in rows:
        parts = row.split("\t")
        if len(parts) != 2:
            continue
        tender_id, numeric_id = parts
        print(f"--- tender {tender_id}, numeric_id={numeric_id} ---")

        resp = http_post(f"{CRM_BASE}/customer-chance/detail?id={numeric_id}",
                         headers={"Authorization": f"Bearer {crm_token}"})
        if not resp or str(resp.get("code", "")) != "0" or not resp.get("data"):
            print(f"  SKIP: no code in response (code={resp.get('code') if resp else 'None'})")
            skip += 1
            continue

        code = resp["data"].get("code", "")
        if not code:
            print("  SKIP: empty code")
            skip += 1
            continue

        print(f"  Resolved: {numeric_id} -> {code}")

        # 转义单引号
        code_escaped = code.replace("'", "\\'")
        if mysql_exec(f"UPDATE tenders SET crm_opportunity_id='{code_escaped}' WHERE id={tender_id} AND crm_opportunity_id='{numeric_id}';"):
            print("  UPDATE OK")
            success += 1
        else:
            print("  UPDATE FAILED")
            fail += 1
        time.sleep(0.2)

    print(f"\n=== Summary ===\nSuccess: {success}\nSkip: {skip}\nFail: {fail}")


if __name__ == "__main__":
    main()
