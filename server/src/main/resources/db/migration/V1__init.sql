CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    created_at BIGINT NOT NULL,
    is_admin BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS conversations (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
    id SERIAL PRIMARY KEY,
    conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    timestamp BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS tool_calls (
    id SERIAL PRIMARY KEY,
    conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    input TEXT NOT NULL,
    result TEXT NOT NULL,
    timestamp BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS documents (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_tool_calls_conversation_id ON tool_calls(conversation_id);
CREATE INDEX IF NOT EXISTS idx_documents_title ON documents(title);
CREATE INDEX IF NOT EXISTS idx_documents_content ON documents USING gin(to_tsvector('english', content));

-- Seed data
INSERT INTO documents (title, content, created_at) VALUES
('Kotlin Multiplatform', 'Kotlin Multiplatform allows you to share code between different platforms including Android, iOS, Web, and Desktop.', EXTRACT(EPOCH FROM NOW()) * 1000),
('Ktor Framework', 'Ktor is a framework for building asynchronous servers and clients in Kotlin.', EXTRACT(EPOCH FROM NOW()) * 1000),
('Compose for Web', 'Compose for Web enables building modern web UIs using Kotlin and Jetpack Compose.', EXTRACT(EPOCH FROM NOW()) * 1000),
('Gemini AI', 'Gemini is Googles advanced AI model capable of understanding and generating text.', EXTRACT(EPOCH FROM NOW()) * 1000),
('PostgreSQL', 'PostgreSQL is a powerful open-source relational database system.', EXTRACT(EPOCH FROM NOW()) * 1000)
ON CONFLICT DO NOTHING;
