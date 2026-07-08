## 本文件与 `CLAUDE.md` 使用同一套项目规则；如两者不一致，以本文件中的 Codex 适配说明为准。

## 忽略/禁止

本项目复用项目根目录 `.claudeignore` 作为 Codex 的忽略规则来源。

- Codex 在执行文件读取、搜索、分析、编辑前，必须先查看 `.claudeignore`。
- `.claudeignore` 中匹配的路径，不得主动读取、搜索、分析、修改或输出其内容。
- 如果用户明确要求处理被 `.claudeignore` 匹配的文件，先说明该路径被忽略规则覆盖，并等待用户确认。
- `.claudeignore` 是本项目约定，不代表 Codex 原生支持；Codex 必须通过本 `AGENTS.md` 规则人工遵守。
