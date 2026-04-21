# PROJECT KNOWLEDGE BASE

**Status:** bootstrap (repo currently contains only this file)

## OVERVIEW
- Product: Personal blog system
- Stack intent: Java backend + FastAPI (Python) frontend + PostgreSQL

## PROJECT REQUIREMENTS (source of truth)
> Kept verbatim from the initial spec for continuity.

- **主题**：基于java语言实现个人博客系统
- **架构**：前端使用FastAPI相关工具实现，后端采用java语言实现,数据库使用PostgreSQL
- **功能实现**：包括但可以不限于用户注册与登录、用户注销、博客的创建、查询、修改与删除、评论的发布、查询与删除

## STRUCTURE (planned)
> These paths do not exist yet; create them as implementation begins.

```
./
├── backend/          # Java backend (recommended: Spring Boot)
├── frontend/         # FastAPI app
├── database/         # SQL migrations / schema
├── scripts/          # dev helpers (start, reset db, etc.)
└── .github/workflows # CI (optional)
```

## WHERE TO LOOK (once code exists)
| Task | Location | Notes |
|------|----------|-------|
| Backend API (auth, blogs, comments) | backend/src/main/java/** | Controllers/routers + service + persistence |
| Backend config (DB, JWT, CORS) | backend/src/main/resources/** | Prefer env var overrides |
| Frontend API gateway / client | frontend/** | FastAPI routes + templates/static if used |
| DB schema + migrations | database/** | Prefer versioned migrations |
| E2E / integration tests | e2e/** or */tests/** | Add once flows stabilize |

## DATABASE CONFIG (SECURITY)
- **DO NOT** store real secrets (passwords, JWT secrets) in git-tracked files.
- Use environment variables (examples):
  - `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`
  - `JWT_SECRET`

## CONVENTIONS (decided now)
- Monorepo with `backend/` + `frontend/` + `database/`.
- Config via environment variables; commit a `*.env.example` (without real values).

## COMMANDS (to standardize once scaffolding exists)
```bash
# backend (Maven)
cd backend
mvn test
mvn package

# frontend (Python)
cd frontend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
pytest
uvicorn main:app --reload
```

## NOTES / GOTCHAS
- The initial spec included a plaintext DB password. Treat that as an example only; replace with env-based config.
- This file should evolve into a true “where is X?” map as code is added.
