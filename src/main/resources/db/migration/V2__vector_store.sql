-- Table used by Spring AI's PgVectorStore. Flyway owns it (initialize-schema=false)
-- so the schema is versioned like every other table.
-- 768 dimensions matches the default embedding model, nomic-embed-text.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE vector_store
(
    id        UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(768)
);
CREATE INDEX idx_vector_store_embedding ON vector_store USING HNSW (embedding vector_cosine_ops);
