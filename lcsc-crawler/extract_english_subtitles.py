#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
字幕英文提取工具
从ASS/SSA格式字幕文件中提取纯英文语句
"""

import re
import sys


def extract_english_subtitles(input_file, output_file):
    """
    从字幕文件中提取英文语句

    Args:
        input_file: 输入的字幕文件路径
        output_file: 输出的纯英文文本文件路径
    """
    english_lines = []

    try:
        with open(input_file, 'r', encoding='utf-8') as f:
            for line in f:
                # 只处理对话行
                if not line.startswith('Dialogue:'):
                    continue

                # 提取文本部分（最后一个逗号之后的内容）
                parts = line.split(',', 9)  # 前9个字段是固定格式
                if len(parts) < 10:
                    continue

                text = parts[9].strip()

                # 使用正则提取 {\fn微软雅黑}{\fs14} 后面的英文
                # 格式: \N{\fn微软雅黑}{\fs14}英文内容
                match = re.search(r'\\N\{[^}]+\}\{[^}]+\}(.+?)$', text)
                if match:
                    english_text = match.group(1).strip()
                    # 过滤掉空行和纯标点
                    if english_text and not re.match(r'^[\s\W]+$', english_text):
                        english_lines.append(english_text)

        # 写入输出文件
        with open(output_file, 'w', encoding='utf-8') as f:
            for line in english_lines:
                f.write(line + '\n')

        print(f"✅ 成功提取 {len(english_lines)} 条英文语句")
        print(f"📄 输出文件: {output_file}")

    except FileNotFoundError:
        print(f"❌ 错误: 找不到文件 {input_file}")
        sys.exit(1)
    except Exception as e:
        print(f"❌ 错误: {e}")
        sys.exit(1)


def main():
    if len(sys.argv) < 2:
        print("用法: python extract_english_subtitles.py <字幕文件路径> [输出文件路径]")
        print("\n示例:")
        print("  python extract_english_subtitles.py subtitle.ass")
        print("  python extract_english_subtitles.py subtitle.ass output.txt")
        sys.exit(1)

    input_file = sys.argv[1]

    # 默认输出文件名
    if len(sys.argv) >= 3:
        output_file = sys.argv[2]
    else:
        # 自动生成输出文件名（在输入文件同目录）
        base_name = input_file.rsplit('.', 1)[0]
        output_file = f"{base_name}_english.txt"

    extract_english_subtitles(input_file, output_file)


if __name__ == '__main__':
    main()
