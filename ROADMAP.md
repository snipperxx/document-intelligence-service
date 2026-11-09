# Roadmap

Work planned for the next months, roughly in order. Each item is meant to land as its own change with tests.

## Reliability
- [ ] Transactional outbox for `documents.uploaded` instead of publishing after commit, so an upload is never lost if Kafka is down at that moment.
- [ ] Dead-letter topic plus retries with backoff for transient model failures (Ollama timeouts, connection resets).
- [ ] Mark documents stuck in `PROCESSING` (worker crashed mid-run) and let an admin requeue them.

## Access control
- [ ] Move a unit to another parent (rewrite the materialized path of the whole subtree in one statement).
- [ ] Remove memberships and change roles, with an audit log of who granted what.
- [ ] Refresh tokens and rate limiting on `/api/auth/login`.

## Extraction and search
- [ ] Stream answers from `/api/ask` with Server-Sent Events.
- [ ] Re-extract a document with a different model and compare the two fact sets.
- [ ] Hybrid search (pgvector + PostgreSQL full text) and a small evaluation set to measure retrieval quality.
- [ ] DOCX and HTML text extraction next to TXT, Markdown and PDF.

## Operations and docs
- [ ] Simple web UI for uploading documents and browsing facts with their source quotes.
- [ ] Grafana dashboard for the existing Prometheus metrics (extraction time, rejected facts, queue lag).
- [ ] Architecture decision records for the main design choices.
