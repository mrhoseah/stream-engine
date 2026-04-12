# SNAPSHOT Version Usage Policy

**Development:**
- Using `-SNAPSHOT` versions is acceptable for ongoing development.
- SNAPSHOT artifacts are mutable and may change with each build, allowing for rapid iteration.

**Production:**
- Do **not** use `-SNAPSHOT` versions in production deployments.
- Always release and use a fixed, non-SNAPSHOT version for production to ensure build stability, reproducibility, and traceability.

**Summary:**
- SNAPSHOT = development only
- Release version = production
