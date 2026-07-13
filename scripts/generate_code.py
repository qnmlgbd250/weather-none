#!/usr/bin/env python3
"""
SkyPulse 激活码生成器（设备专属）

用法:
    python generate_code.py <设备ID>              为指定设备生成激活码
    python generate_code.py <设备ID> <设备ID> ...  为多个设备批量生成

示例:
    python generate_code.py A3F7B2C1
    python generate_code.py A3F7B2C1 D4E8F1A9 B2C3D4E5

每个激活码只能在对应的设备上使用，无法跨设备。
"""

import hmac
import hashlib
import sys

# 与 APP 端 MembershipRepository.kt 共享的密钥
SECRET = b"skypulse_hmac_2026_v1"

# Base32 字母表（RFC 4648，无填充）
B32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

# 激活码长度
CODE_LEN = 8


def base32_encode(data: bytes) -> str:
    """简化版 Base32 编码"""
    result = []
    buffer = 0
    bits_in_buffer = 0

    for byte in data:
        buffer = (buffer << 8) | byte
        bits_in_buffer += 8
        while bits_in_buffer >= 5:
            index = (buffer >> (bits_in_buffer - 5)) & 0x1F
            result.append(B32_ALPHABET[index])
            bits_in_buffer -= 5

    if bits_in_buffer > 0:
        index = (buffer << (5 - bits_in_buffer)) & 0x1F
        result.append(B32_ALPHABET[index])

    return "".join(result)


def generate_code(device_id: str) -> str:
    """
    根据设备 ID 生成专属激活码
    算法：HMAC-SHA256(SECRET, device_id) → Base32 → 前8位 → 格式化为 XXXX-XXXX
    """
    normalized = device_id.strip().upper()
    raw_hmac = hmac.new(SECRET, normalized.encode("utf-8"), hashlib.sha256).digest()
    code = base32_encode(raw_hmac)[:CODE_LEN]
    return f"{code[:4]}-{code[4:]}"


def main():
    if len(sys.argv) < 2:
        print("用法: python generate_code.py <设备ID> [设备ID ...]")
        print("示例: python generate_code.py A3F7B2C1")
        print()
        print("设备 ID 在 APP 的「设置 → 激活会员」弹窗中查看（8位十六进制）")
        sys.exit(1)

    device_ids = sys.argv[1:]

    print(f"=== SkyPulse 激活码生成器 ===\n")

    for device_id in device_ids:
        device_id = device_id.strip().upper()
        code = generate_code(device_id)
        print(f"  设备 ID: {device_id}")
        print(f"  激活码:  {code}")
        print()


if __name__ == "__main__":
    main()
